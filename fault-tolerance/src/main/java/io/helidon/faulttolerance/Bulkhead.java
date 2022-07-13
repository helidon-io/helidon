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

package io.helidon.faulttolerance;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Bulkhead protects a resource that cannot serve unlimited parallel
 * requests.
 * <p>
 * When the limit of parallel execution is reached, requests are enqueued
 * until the queue length is reached. Once both the limit and queue are full,
 * additional attempts to invoke will end with a failed response with
 * {@link io.helidon.faulttolerance.BulkheadException}.
 */
public interface Bulkhead extends FtHandler {
    /**
     * A new builder for {@link io.helidon.faulttolerance.Bulkhead}.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API builder for {@link io.helidon.faulttolerance.Bulkhead}.
     */
    @Configured
    class Builder implements io.helidon.common.Builder<Builder, Bulkhead> {
        private static final int DEFAULT_LIMIT = 10;
        private static final int DEFAULT_QUEUE_LENGTH = 10;

        private LazyValue<? extends ExecutorService> executor = FaultTolerance.executor();
        private int limit = DEFAULT_LIMIT;
        private int queueLength = DEFAULT_QUEUE_LENGTH;
        private String name = "Bulkhead-" + System.identityHashCode(this);
        private boolean cancelSource = true;

        private Builder() {
        }

        @Override
        public Bulkhead build() {
            return new BulkheadImpl(this);
        }

        /**
         * Configure executor service to use for executing tasks asynchronously.
         *
         * @param executor executor service supplier
         * @return updated builder instance
         */
        public Builder executor(Supplier<? extends ExecutorService> executor) {
            this.executor = LazyValue.create(Objects.requireNonNull(executor));
            return this;
        }

        /**
         * Maximal number of parallel requests going through this bulkhead.
         * When the limit is reached, additional requests are enqueued.
         *
         * @param limit maximal number of parallel calls, defaults is {@value DEFAULT_LIMIT}
         * @return updated builder instance
         */
        @ConfiguredOption("10")
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Maximal number of enqueued requests waiting for processing.
         * When the limit is reached, additional attempts to invoke
         * a request will receive a {@link io.helidon.faulttolerance.BulkheadException}.
         *
         * @param queueLength length of queue
         * @return updated builder instance
         */
        @ConfiguredOption("10")
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
        @ConfiguredOption("Bulkhead-")
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Policy to cancel any source stage if the value return by {@link Bulkhead#invoke}
         * is cancelled. Default is {@code true}; mostly used by FT MP to change default.
         *
         * @param cancelSource cancel source policy
         * @return updated builder instance
         */
        @ConfiguredOption("true")
        public Builder cancelSource(boolean cancelSource) {
            this.cancelSource = cancelSource;
            return this;
        }

        /**
         * <p>
         * Load all properties for this bulkhead from configuration.
         * </p>
         * <table class="config">
         * <caption>Configuration</caption>
         * <tr>
         *     <th>key</th>
         *     <th>default value</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>name</td>
         *     <td>Bulkhead-N</td>
         *     <td>Name used for debugging</td>
         * </tr>
         * <tr>
         *     <td>limit</td>
         *     <td>{@value DEFAULT_LIMIT}</td>
         *     <td>Max number of parallel calls</td>
         * </tr>
         * <tr>
         *     <td>queue-length</td>
         *     <td>{@value DEFAULT_QUEUE_LENGTH}</td>
         *     <td>Max number of queued calls</td>
         * </tr>
         * <tr>
         *     <td>cancel-source</td>
         *     <td>true</td>
         *     <td>Cancel task source if task is cancelled</td>
         * </tr>
         * </table>
         *
         * @param config the config node to use
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("limit").asInt().ifPresent(this::limit);
            config.get("queue-length").asInt().ifPresent(this::queueLength);
            config.get("name").asString().ifPresent(this::name);
            config.get("cancel-source").asBoolean().ifPresent(this::cancelSource);
            return this;
        }

        int limit() {
            return limit;
        }

        int queueLength() {
            return queueLength;
        }

        LazyValue<? extends ExecutorService> executor() {
            return executor;
        }

        String name() {
            return name;
        }

        boolean cancelSource() {
            return cancelSource;
        }
    }

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
     * Provides access to internal stats for this bulkhead.
     *
     * @return internal stats.
     */
    Stats stats();
}
