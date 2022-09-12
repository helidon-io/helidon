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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Bulkhead protects a resource that cannot serve unlimited parallel
 * requests.
 * <p>
 * When the limit of parallel execution is reached, requests are enqueued
 * until the queue length is reached. Once both the limit and queue are full,
 * additional attempts to invoke will end with a failed response with
 * {@link io.helidon.nima.faulttolerance.BulkheadException}.
 */
public interface Bulkhead extends FtHandler {
    /**
     * A new builder for {@link io.helidon.nima.faulttolerance.Bulkhead}.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Provides access to internal stats for this bulkhead.
     *
     * @return internal stats.
     */
    Stats stats();

    /**
     * Provides statistics during the lifetime of a bulkhead, such as
     * concurrent executions, accepted/rejected calls and queue size.
     */
    interface Stats {

        /**
         * Number of concurrent executions at this time.
         *
         * @return concurrent executions.
         */
        long concurrentExecutions();

        /**
         * Number of calls accepted on the bulkhead.
         *
         * @return calls accepted.
         */
        long callsAccepted();

        /**
         * Number of calls rejected on the bulkhead.
         *
         * @return calls rejected.
         */
        long callsRejected();

        /**
         * Size of waiting queue at this time.
         *
         * @return size of waiting queue.
         */
        long waitingQueueSize();
    }

    /**
     * A Bulkhead listener for queueing operations.
     */
    interface QueueListener {

        /**
         * Called right before blocking on the internal semaphore's queue.
         *
         * @param supplier the supplier to be enqueued
         * @param <T> type of value returned by supplier
         */
        default <T> void enqueueing(Supplier<? extends T> supplier) {
        }

        /**
         * Called after semaphore is acquired and before supplier is called.
         *
         * @param supplier the supplier to execute
         * @param <T> type of value returned by supplier
         */
        default <T> void dequeued(Supplier<? extends T> supplier) {
        }
    }

    /**
     * Fluent API builder for {@link io.helidon.nima.faulttolerance.Bulkhead}.
     */
    class Builder implements io.helidon.common.Builder<Builder, Bulkhead> {
        private static final int DEFAULT_LIMIT = 10;
        private static final int DEFAULT_QUEUE_LENGTH = 10;

        private int limit = DEFAULT_LIMIT;
        private int queueLength = DEFAULT_QUEUE_LENGTH;
        private String name = "Bulkhead-" + System.identityHashCode(this);
        private List<QueueListener> listeners = new ArrayList<>();

        private Builder() {
        }

        @Override
        public Bulkhead build() {
            return new BulkheadImpl(this);
        }


        /**
         * Maximal number of parallel requests going through this bulkhead.
         * When the limit is reached, additional requests are enqueued.
         *
         * @param limit maximal number of parallel calls, defaults is {@value DEFAULT_LIMIT}
         * @return updated builder instance
         */
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Maximal number of enqueued requests waiting for processing.
         * When the limit is reached, additional attempts to invoke
         * a request will receive a {@link BulkheadException}.
         *
         * @param queueLength length of queue
         * @return updated builder instance
         */
        public Builder queueLength(int queueLength) {
            this.queueLength = queueLength;
            return this;
        }

        /**
         * A name assigned for debugging, error reporting or configuration purposes.
         *
         * @param name the name
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder addQueueListener(QueueListener listener) {
            listeners.add(listener);
            return this;
        }

        int limit() {
            return limit;
        }

        int queueLength() {
            return queueLength;
        }

        String name() {
            return name;
        }

        List<QueueListener> queueListeners() {
            return listeners;
        }
    }
}
