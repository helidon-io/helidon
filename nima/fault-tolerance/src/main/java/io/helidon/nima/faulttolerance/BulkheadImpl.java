/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.nima.faulttolerance;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static io.helidon.nima.faulttolerance.SupplierHelper.toRuntimeException;
import static io.helidon.nima.faulttolerance.SupplierHelper.unwrapThrowable;

class BulkheadImpl implements Bulkhead {
    private static final System.Logger LOGGER = System.getLogger(BulkheadImpl.class.getName());

    private final Semaphore inProgress;
    private final String name;
    private final int maxQueue;
    private final AtomicLong concurrentExecutions = new AtomicLong(0L);
    private final AtomicLong callsAccepted = new AtomicLong(0L);
    private final AtomicLong callsRejected = new AtomicLong(0L);
    private final AtomicInteger enqueued = new AtomicInteger();
    private final List<QueueListener> listeners;

    private BarrierQueue barrierQueue = null;
    private final Set<Supplier<?>> cancelledSuppliers = new CopyOnWriteArraySet<>();

    BulkheadImpl(Builder builder) {
        this.inProgress = new Semaphore(builder.limit(), true);
        this.name = builder.name();
        this.maxQueue = builder.queueLength();
        this.listeners = builder.queueListeners();
        if (this.maxQueue > 0) {
            barrierQueue = new BarrierQueue(this.maxQueue);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public <T> T invoke(Supplier<? extends T> supplier) {
        // execute immediately if semaphore can be acquired
        if (inProgress.tryAcquire()) {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, name + " invoke immediate " + supplier);
            }
            return execute(supplier);
        }

        int queueLength = enqueued.incrementAndGet();
        if (queueLength > maxQueue) {
            enqueued.decrementAndGet();
            callsRejected.incrementAndGet();
            throw new BulkheadException("Bulkhead queue \"" + name + "\" is full");
        }
        try {
            assert barrierQueue != null;

            // block current thread until barrier is retracted
            listeners.forEach(l -> l.enqueueing(supplier));
            barrierQueue.enqueueAndWaitOn(supplier);

            // unblocked so we can proceed with execution
            listeners.forEach(l -> l.dequeued(supplier));
            enqueued.decrementAndGet();

            // do not run if cancelled while queued
            if (cancelledSuppliers.remove(supplier)) {
                return null;
            }

            // invoke supplier now
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, name + " invoking " + supplier);
            }
            return execute(supplier);
        } catch (InterruptedException e) {
            callsRejected.incrementAndGet();
            throw new BulkheadException("Bulkhead \"" + name + "\" interrupted while acquiring");
        } catch (ExecutionException e) {
            throw new BulkheadException(e.getMessage());
        }
    }

    @Override
    public Stats stats() {
        return new Stats() {
            @Override
            public long concurrentExecutions() {
                return concurrentExecutions.get();
            }

            @Override
            public long callsAccepted() {
                return callsAccepted.get();
            }

            @Override
            public long callsRejected() {
                return callsRejected.get();
            }

            @Override
            public long waitingQueueSize() {
                return enqueued.get();
            }
        };
    }

    // this method must be called while holding a permit
    private <T> T execute(Supplier<? extends T> supplier) {
        callsAccepted.incrementAndGet();
        concurrentExecutions.incrementAndGet();
        try {
            T result = supplier.get();
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, name + " finished execution: " + supplier
                        + " (success)");
            }
            return result;
        } catch (Throwable t) {
            Throwable throwable = unwrapThrowable(t);
            LOGGER.log(Level.DEBUG, name + " finished execution: " + supplier
                    + " (failure)", throwable);
            throw toRuntimeException(throwable);
        } finally {
            concurrentExecutions.decrementAndGet();
            if (barrierQueue != null) {
                barrierQueue.dequeueAndRetract();
            }
            inProgress.release();
        }
    }

    @Override
    public boolean cancelSupplier(Supplier<?> supplier) {
        boolean cancelled = barrierQueue.remove(supplier);
        if (cancelled) {
            enqueued.decrementAndGet();
            cancelledSuppliers.add(supplier);
        }
        return cancelled;
    }

    private static class BarrierQueue {

        private final ReentrantLock lock = new ReentrantLock();
        private final Queue<Supplier<?>> queue;
        private final Map<Supplier<?>, Barrier> map = new ConcurrentHashMap<>();

        BarrierQueue(int size) {
            if (size <= 0) {
                throw new IllegalStateException("BarrierQueue size must be greater that 0");
            }
            this.queue = new LinkedBlockingQueue<>(size);
        }

        Barrier enqueue(Supplier<?> supplier) {
            lock.lock();
            try {
                boolean added = queue.offer(supplier);
                return added ? map.computeIfAbsent(supplier, s -> new Barrier()) : null;
            } finally {
                lock.unlock();
            }
        }

        void enqueueAndWaitOn(Supplier<?> supplier) throws ExecutionException, InterruptedException {
            Barrier barrier = enqueue(supplier);
            if (barrier != null) {
                barrier.waitOn();
            }
        }

        Barrier dequeue() {
            lock.lock();
            try {
                Supplier<?> supplier = queue.poll();
                return supplier == null ? null : map.remove(supplier);
            } finally {
                lock.unlock();
            }
        }

        void dequeueAndRetract() {
            Barrier barrier = dequeue();
            if (barrier != null) {
                barrier.retract();
            }
        }

        boolean remove(Supplier<?> supplier) {
            return queue.remove(supplier);
        }
    }

    private static class Barrier {
        private final CompletableFuture<Void> future = new CompletableFuture<>();

        void waitOn() throws ExecutionException, InterruptedException {
            future.get();
        }

        void retract() {
            future.complete(null);
        }
    }
}
