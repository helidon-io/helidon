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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static io.helidon.nima.faulttolerance.SupplierHelper.toRuntimeException;
import static io.helidon.nima.faulttolerance.SupplierHelper.unwrapThrowable;

class BulkheadImpl implements Bulkhead {
    private static final System.Logger LOGGER = System.getLogger(BulkheadImpl.class.getName());

    private final Semaphore inProgress;
    private final String name;
    private final BarrierQueue queue;
    private final AtomicLong concurrentExecutions = new AtomicLong(0L);
    private final AtomicLong callsAccepted = new AtomicLong(0L);
    private final AtomicLong callsRejected = new AtomicLong(0L);
    private final List<QueueListener> listeners;
    private final Set<Supplier<?>> cancelledSuppliers = new CopyOnWriteArraySet<>();

    BulkheadImpl(Builder builder) {
        this.inProgress = new Semaphore(builder.limit(), true);
        this.name = builder.name();
        this.listeners = builder.queueListeners();
        this.queue = builder.queueLength() > 0
                ? new BlockingQueue(builder.queueLength())
                : new ZeroCapacityQueue();
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

        if (queue.size() == queue.capacity()) {
            callsRejected.incrementAndGet();
            throw new BulkheadException("Bulkhead queue \"" + name + "\" is full");
        }
        try {
            // block current thread until barrier is retracted
            listeners.forEach(l -> l.enqueueing(supplier));
            queue.enqueueAndWaitOn(supplier);

            // unblocked so we can proceed with execution
            listeners.forEach(l -> l.dequeued(supplier));

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
                return queue.size();
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
            if (queue.size() > 0) {
                queue.dequeueAndRetract();
            } else {
                inProgress.release();
            }
        }
    }

    @Override
    public boolean cancelSupplier(Supplier<?> supplier) {
        boolean cancelled = queue.remove(supplier);
        if (cancelled) {
            cancelledSuppliers.add(supplier);
        }
        return cancelled;
    }

    /**
     * A queue for suppliers that block on barriers.
     */
    private interface BarrierQueue {

        /**
         * Number of suppliers in queue.
         *
         * @return current number of suppliers
         */
        int size();

        /**
         * Maximum number of suppliers in queue.
         *
         * @return max number of suppliers
         */
        int capacity();

        /**
         * Enqueue supplier and block thread on barrier.
         *
         * @param supplier the supplier
         * @throws ExecutionException if exception encountered while blocked
         * @throws InterruptedException if blocking is interrupted
         */
        void enqueueAndWaitOn(Supplier<?> supplier) throws ExecutionException, InterruptedException;

        /**
         * Dequeue supplier and retract its barrier.
         */
        void dequeueAndRetract();

        /**
         * Remove supplier from queue, if present.
         *
         * @param supplier the supplier
         * @return {@code true} if supplier was removed or {@code false} otherwise
         */
        boolean remove(Supplier<?> supplier);
    }

    /**
     * A queue with capacity 0.
     */
    private static class ZeroCapacityQueue implements BarrierQueue {

        @Override
        public int size() {
            return 0;
        }

        @Override
        public int capacity() {
            return 0;
        }

        @Override
        public void enqueueAndWaitOn(Supplier<?> supplier) {
            throw new BulkheadException("Queue capacity is 0");
        }

        @Override
        public void dequeueAndRetract() {
            throw new BulkheadException("Queue capacity is 0");
        }

        @Override
        public boolean remove(Supplier<?> supplier) {
            throw new BulkheadException("Queue capacity is 0");
        }
    }

    /**
     * A queue that holds all those suppliers that don't have permits to execute at a
     * certain time. The thread running the supplier will be forced to wait on a barrier
     * until a new permit becomes available.
     */
    private static class BlockingQueue implements BarrierQueue {

        private final int capacity;
        private final ReentrantLock lock;
        private final Queue<Supplier<?>> queue;
        private final Map<Supplier<?>, Barrier> map;

        BlockingQueue(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Queue capacity must be greater than 0");
            }
            this.capacity = capacity;
            this.queue = new LinkedBlockingQueue<>(capacity);
            this.map = new IdentityHashMap<>();     // just use references
            this.lock = new ReentrantLock();
        }

        @Override
        public int size() {
            return queue.size();
        }

        @Override
        public int capacity() {
            return capacity;
        }

        @Override
        public void enqueueAndWaitOn(Supplier<?> supplier) throws ExecutionException, InterruptedException {
            Barrier barrier;
            lock.lock();
            try {
                barrier = enqueue(supplier);
            } finally {
                lock.unlock();
            }
            if (barrier != null) {
                barrier.waitOn();
            } else {
                throw new BulkheadException("Queue is full");
            }
        }

        @Override
        public void dequeueAndRetract() {
            lock.lock();
            try {
                Barrier barrier = dequeue();
                if (barrier != null) {
                    barrier.retract();
                } else {
                    throw new BulkheadException("Queue is empty");
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean remove(Supplier<?> supplier) {
            lock.lock();
            try {
                return queue.remove(supplier);
            } finally {
                lock.unlock();
            }
        }

        private Barrier dequeue() {
            Supplier<?> supplier = queue.poll();
            return supplier == null ? null : map.remove(supplier);
        }

        private Barrier enqueue(Supplier<?> supplier) {
            boolean added = queue.offer(supplier);
            return added ? map.computeIfAbsent(supplier, s -> new Barrier()) : null;
        }
    }

    /**
     * A barrier is used to force a thread to wait (block) until it is retracted.
     */
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
