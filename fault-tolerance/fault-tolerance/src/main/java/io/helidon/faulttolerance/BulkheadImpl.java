/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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

        QueuedInvocation queuedInvocation = null;
        try {
            // block current thread until barrier is retracted
            boolean waitingCounted = false;
            try {
                listeners.forEach(l -> l.enqueueing(supplier));
                if (metricsEnabled) {
                    callsWaiting.incrementAndGet();
                    waitingCounted = true;
                }
                queuedInvocation = queue.enqueue(supplier);
            } finally {
                inProgressLock.unlock(); // we have enqueued, now we can wait
            }

            if (queuedInvocation == null) {
                if (waitingCounted) {
                    callsWaiting.decrementAndGet();
                }
                callsRejected.incrementAndGet();
                throw new BulkheadException("Bulkhead queue \"" + name + "\" is full");
            }
            long waitStartedAt = 0L;
            if (waitingCounted) {
                waitStartedAt = System.nanoTime();
            }
            try {
                queuedInvocation.waitOn();
            } finally {
                if (waitingCounted) {
                    waitingDurationMetric.record(System.nanoTime() - waitStartedAt, TimeUnit.NANOSECONDS);
                    callsWaiting.decrementAndGet();
                }
            }

            // do not run if cancelled while queued
            if (queuedInvocation.cancelled()) {
                return null;
            }

            // unblocked so we can proceed with execution
            listeners.forEach(l -> l.dequeued(supplier));

            // invoke supplier now
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, name + " invoking " + supplier);
            }
            return execute(supplier);
        } catch (InterruptedException e) {
            if (queuedInvocation != null) {
                if (queue.remove(queuedInvocation)) {
                    queuedInvocation.interrupt();
                } else if (queuedInvocation.started()) {
                    handOffOrReleasePermit();
                }
            }
            callsRejected.incrementAndGet();
            Thread.currentThread().interrupt();
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
            handOffOrReleasePermit();
        }
    }

    private void handOffOrReleasePermit() {
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

    @Override
    public boolean cancelSupplier(Supplier<?> supplier) {
        QueuedInvocation queuedInvocation = queue.remove(supplier);
        if (queuedInvocation != null) {
            queuedInvocation.cancel();
            return true;
        }
        return false;
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
         * @return queued invocation if supplier was enqueued or null otherwise
         */
        QueuedInvocation enqueue(Supplier<?> supplier);

        /**
         * Dequeue supplier and release its queued invocation.
         *
         * @return {@code true} if a supplier was dequeued or {@code false} otherwise
         */
        boolean dequeueAndRetract();

        /**
         * Remove supplier from queue by identity, if present, and return its queued invocation for external completion.
         *
         * @param supplier the supplier
         * @return queued invocation associated with the removed supplier, or {@code null} if the supplier was not removed
         */
        QueuedInvocation remove(Supplier<?> supplier);

        /**
         * Remove a specific queued invocation if it is still in the queue.
         *
         * @param queuedInvocation queued invocation to remove
         * @return {@code true} if the invocation was removed or {@code false} otherwise
         */
        boolean remove(QueuedInvocation queuedInvocation);
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
        public QueuedInvocation enqueue(Supplier<?> supplier) {
            // never enqueue, should always fail execution if permits are not available
            return null;
        }

        @Override
        public boolean dequeueAndRetract() {
            return false;
        }

        @Override
        public QueuedInvocation remove(Supplier<?> supplier) {
            return null;
        }

        @Override
        public boolean remove(QueuedInvocation queuedInvocation) {
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
        private final Queue<QueuedInvocation> queue;

        BlockingQueue(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Queue capacity must be greater than 0");
            }
            this.capacity = capacity;
            this.queue = new LinkedBlockingQueue<>(capacity);
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
        public QueuedInvocation enqueue(Supplier<?> supplier) {
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
                QueuedInvocation queuedInvocation = dequeue();
                if (queuedInvocation != null) {
                    queuedInvocation.start();
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public QueuedInvocation remove(Supplier<?> supplier) {
            lock.lock();
            try {
                for (Iterator<QueuedInvocation> iterator = queue.iterator(); iterator.hasNext();) {
                    QueuedInvocation queuedInvocation = iterator.next();
                    if (queuedInvocation.supplier() == supplier) {
                        iterator.remove();
                        return queuedInvocation;
                    }
                }
                return null;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean remove(QueuedInvocation queuedInvocation) {
            lock.lock();
            try {
                for (Iterator<QueuedInvocation> iterator = queue.iterator(); iterator.hasNext();) {
                    if (iterator.next() == queuedInvocation) {
                        iterator.remove();
                        return true;
                    }
                }
                return false;
            } finally {
                lock.unlock();
            }
        }

        private QueuedInvocation dequeue() {
            return queue.poll();
        }

        private QueuedInvocation doEnqueue(Supplier<?> supplier) {
            QueuedInvocation queuedInvocation = new QueuedInvocation(supplier);
            boolean added = queue.offer(queuedInvocation);
            return added ? queuedInvocation : null;
        }
    }

    private static class QueuedInvocation {
        private final AtomicReference<State> state = new AtomicReference<>(State.QUEUED);
        private final Supplier<?> supplier;
        private final Barrier barrier = new Barrier();

        private QueuedInvocation(Supplier<?> supplier) {
            this.supplier = supplier;
        }

        private Supplier<?> supplier() {
            return supplier;
        }

        private void waitOn() throws ExecutionException, InterruptedException {
            barrier.waitOn();
        }

        private boolean start() {
            boolean started = state.compareAndSet(State.QUEUED, State.STARTED);
            if (started) {
                barrier.retract();
            }
            return started;
        }

        private boolean started() {
            return state.get() == State.STARTED;
        }

        private boolean cancelled() {
            return state.get() == State.CANCELLED;
        }

        private void cancel() {
            if (state.compareAndSet(State.QUEUED, State.CANCELLED)) {
                barrier.retract();
            }
        }

        private void interrupt() {
            state.compareAndSet(State.QUEUED, State.INTERRUPTED);
        }

        private enum State {
            QUEUED,
            STARTED,
            CANCELLED,
            INTERRUPTED
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
