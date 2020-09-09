/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.helidon.common.LazyValue;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

class BulkheadImpl implements Bulkhead {
    private static final Logger LOGGER = Logger.getLogger(BulkheadImpl.class.getName());

    private final LazyValue<? extends ExecutorService> executor;
    private final Queue<DelayedTask<?>> queue;
    private final Semaphore inProgress;
    private final String name;

    private final AtomicLong concurrentExecutions = new AtomicLong(0L);
    private final AtomicLong callsAccepted = new AtomicLong(0L);
    private final AtomicLong callsRejected = new AtomicLong(0L);

    BulkheadImpl(Bulkhead.Builder builder) {
        this.executor = builder.executor();
        this.inProgress = new Semaphore(builder.limit(), true);
        this.name = builder.name();

        if (builder.queueLength() == 0) {
            queue = new NoQueue();
        } else {
            this.queue = new LinkedBlockingQueue<>(builder.queueLength());
        }
    }

    @Override
    public <T> Single<T> invoke(Supplier<? extends CompletionStage<T>> supplier) {
        return invokeTask(DelayedTask.createSingle(supplier));
    }

    @Override
    public <T> Multi<T> invokeMulti(Supplier<? extends Flow.Publisher<T>> supplier) {
        return invokeTask(DelayedTask.createMulti(supplier));
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

    // this method must be called while NOT holding a permit
    private <R> R invokeTask(DelayedTask<R> task) {
        if (inProgress.tryAcquire()) {
            LOGGER.finest(() -> name + " invoke immediate: " + task);

            // free permit, we can invoke
            execute(task);
            return task.result();
        } else {
            // no free permit, let's try to enqueue
            if (queue.offer(task)) {
                LOGGER.finest(() -> name + " enqueue: " + task);

                R result = task.result();
                if (result instanceof Single<?>) {
                    Single<Object> single = (Single<Object>) result;
                    return (R) single.onCancel(() -> queue.remove(task));
                }
                return result;
            } else {
                LOGGER.finest(() -> name + " reject: " + task);

                callsRejected.incrementAndGet();
                return task.error(new BulkheadException("Bulkhead queue \"" + name + "\" is full"));
            }
        }
    }

    // this method must be called while holding a permit
    private void execute(DelayedTask<?> task) {
        callsAccepted.incrementAndGet();
        concurrentExecutions.incrementAndGet();

        task.execute()
                .handle((it, throwable) -> {
                    concurrentExecutions.decrementAndGet();
                    // we do not care about execution, but let's record it in debug
                    LOGGER.finest(() -> name + " finished execution: " + task
                            + " (" + (throwable == null ? "success" : "failure") + ")");
                    DelayedTask<?> polled = queue.poll();
                    if (polled != null) {
                        LOGGER.finest(() -> name + " invoke in executor: " + polled);

                        // chain executions from queue until all are executed
                        executor.get().submit(() -> execute(polled));
                    } else {
                        LOGGER.finest(() -> name + " permit released after: " + task);

                        // nothing in the queue, release permit
                        inProgress.release();
                    }
                    return null;
                });
    }

    private static class NoQueue extends ArrayDeque<DelayedTask<?>> {
        @Override
        public boolean offer(DelayedTask delayedTask) {
            return false;
        }

        @SuppressWarnings("ReturnOfNull")
        @Override
        public DelayedTask<?> poll() {
            // this queue is empty, poll must return null
            return null;
        }
    }
}
