/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Service;

@Service.PerInstance(BulkheadConfigBlueprint.class)
class BulkheadImpl implements Bulkhead {
    private static final System.Logger LOGGER = System.getLogger(BulkheadImpl.class.getName());

    private final Lock inProgressLock;
    private final Semaphore inProgress;
    private final String name;
    private final BarrierQueue queue;
    private final AtomicLong concurrentExecutions = new AtomicLong(0L);
    private final AtomicLong callsAccepted = new AtomicLong(0L);
    private final AtomicLong callsRejected = new AtomicLong(0L);
    private final AtomicLong callsWaiting = new AtomicLong(0L);
    private final List<QueueListener> listeners;
    private final Set<Supplier<?>> cancelledSuppliers = new CopyOnWriteArraySet<>();
    private final BulkheadConfig config;
    private final boolean metricsEnabled;

    private Counter callsCounterMetric;
    private Timer waitingDurationMetric;

    @Service.Inject
    BulkheadImpl(BulkheadConfig config) {
        this.inProgress = new Semaphore(config.limit(), true);
        this.name = config.name().orElseGet(() -> "bulkhead-" + System.identityHashCode(config));
        this.listeners = config.queueListeners();
        this.queue = config.queueLength() > 0
                ? new BlockingQueue(config.queueLength())
                : new ZeroCapacityQueue();
        this.inProgressLock = new ReentrantLock(true);
        this.config = config;

        this.metricsEnabled = config.enableMetrics() || MetricsUtils.defaultEnabled();
        if (metricsEnabled) {
            Tag nameTag = Tag.create("name", name);
            callsCounterMetric = MetricsUtils.counterBuilder(FT_BULKHEAD_CALLS_TOTAL, nameTag);
            waitingDurationMetric = MetricsUtils.timerBuilder(FT_BULKHEAD_WAITINGDURATION, nameTag);
            MetricsUtils.gaugeBuilder(FT_BULKHEAD_EXECUTIONSRUNNING, concurrentExecutions::get, nameTag);
            MetricsUtils.gaugeBuilder(FT_BULKHEAD_EXECUTIONSWAITING, callsWaiting::get, nameTag);
            MetricsUtils.gaugeBuilder(FT_BULKHEAD_EXECUTIONSREJECTED, callsRejected::get, nameTag);
        }
    }

    @Override
    public BulkheadConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public <T> T invoke(Supplier<? extends T> supplier) {
        // we need to hold the lock until we decide what to do with this request
        // cannot release it in between attempts, as that would give window for another thread to change the state
        inProgressLock.lock();

        // execute immediately if semaphore can be acquired
        boolean acquired;
        try {
            if (metricsEnabled) {
                callsCounterMetric.increment();
            }
            acquired = inProgress.tryAcquire();
        } catch (Throwable t) {
            inProgressLock.unlock();
            throw t;
        }
        if (acquired) {
            inProgressLock.unlock(); // we managed to get a semaphore permit, in progress lock can be released for now
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, name + " invoke immediate " + supplier);
            }
            return execute(supplier);
        }

        boolean full;
        try {
            full = queue.isFull();
        } catch (Throwable t) {
            inProgressLock.unlock();
            throw t;
        }
        if (full) {
            inProgressLock.unlock(); // this request will fail, release lock
            callsRejected.incrementAndGet();
            throw new BulkheadException("Bulkhead queue \"" + name + "\" is full");
        }

        try {
            // block current thread until barrier is retracted
            Barrier barrier;
            long start = 0L;
            try {
                listeners.forEach(l -> l.enqueueing(supplier));
                if (metricsEnabled) {
                    start = System.nanoTime();
                    callsWaiting.incrementAndGet();
                }
                barrier = queue.enqueue(supplier);
            } finally {
                try {
                    if (metricsEnabled) {
                        waitingDurationMetric.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                        callsWaiting.decrementAndGet();
                    }
                } finally {
                    inProgressLock.unlock(); // we have enqueued, now we can wait
                }
            }

            if (barrier == null) {
                throw new BulkheadException("Bulkhead queue \"" + name + "\" is full");
            }
            barrier.waitOn();

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
            Throwable throwable = SupplierHelper.unwrapThrowable(t);
            LOGGER.log(Level.DEBUG, name + " finished execution: " + supplier
                    + " (failure)", throwable);
            throw SupplierHelper.toRuntimeException(throwable);
        } finally {
            concurrentExecutions.decrementAndGet();
            inProgressLock.lock();
            try {
                boolean dequeued = queue.dequeueAndRetract();
                if (!dequeued) {
                    inProgress.release();       // nothing dequeued, one more permit
                }
            } finally {
                inProgressLock.unlock();
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
         * Check if queue is full.
         *
         * @return outcome of test
         */
        boolean isFull();

        /**
         * Enqueue supplier and block thread on barrier.
         *
         * @param supplier the supplier
         * @return barrier if supplier was enqueued or null otherwise
         */
        Barrier enqueue(Supplier<?> supplier);

        /**
         * Dequeue supplier and retract its barrier.
         *
         * @return {@code true} if a supplier was dequeued or {@code false} otherwise
         */
        boolean dequeueAndRetract();

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
        public boolean isFull() {
            return true;
        }

        @Override
        public Barrier enqueue(Supplier<?> supplier) {
            // never enqueue, should always fail execution if permits are not available
            return null;
        }

        @Override
        public boolean dequeueAndRetract() {
            return false;
        }

        @Override
        public boolean remove(Supplier<?> supplier) {
            return false;
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
        public boolean isFull() {
            lock.lock();
            try {
                return queue.size() == capacity;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Barrier enqueue(Supplier<?> supplier) {
            lock.lock();
            try {
                return doEnqueue(supplier);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean dequeueAndRetract() {
            lock.lock();
            try {
                Barrier barrier = dequeue();
                if (barrier != null) {
                    barrier.retract();
                    return true;
                }
                return false;
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

        private Barrier doEnqueue(Supplier<?> supplier) {
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
