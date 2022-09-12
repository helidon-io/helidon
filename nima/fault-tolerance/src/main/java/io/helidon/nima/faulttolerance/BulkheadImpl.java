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
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

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

    BulkheadImpl(Builder builder) {
        this.inProgress = new Semaphore(builder.limit(), true);
        this.name = builder.name();
        this.maxQueue = builder.queueLength();
        this.listeners = builder.queueListeners();
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
            // block current thread until permit available
            listeners.forEach(l -> l.enqueueing(supplier));
            inProgress.acquire();

            // unblocked so we can proceed with execution
            listeners.forEach(l -> l.dequeued(supplier));
            enqueued.decrementAndGet();
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, name + " invoking " + supplier);
            }
            return execute(supplier);
        } catch (InterruptedException e) {
            callsRejected.incrementAndGet();
            throw new BulkheadException("Bulkhead \"" + name + "\" interrupted while acquiring");
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
    private <T> T execute(Supplier<? extends T> task) {
        callsAccepted.incrementAndGet();
        concurrentExecutions.incrementAndGet();
        try {
            T result = task.get();
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, name + " finished execution: " + task
                        + " (success)");
            }
            return result;
        } catch (Throwable t) {
            LOGGER.log(Level.DEBUG, name + " finished execution: " + task
                    + " (failure)", t);
            throw t;
        } finally {
            concurrentExecutions.decrementAndGet();
            inProgress.release();
        }
    }
}
