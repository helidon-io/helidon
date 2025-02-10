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

import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;

/**
 * Bulkhead protects a resource that cannot serve unlimited parallel
 * requests.
 * <p>
 * When the limit of parallel execution is reached, requests are enqueued
 * until the queue length is reached. Once both the limit and queue are full,
 * additional attempts to invoke will end with a failed response with
 * {@link BulkheadException}.
 */
@RuntimeType.PrototypedBy(BulkheadConfig.class)
public interface Bulkhead extends FtHandler, RuntimeType.Api<BulkheadConfig> {

    /**
     * Counter for all the calls in a bulkhead.
     */
    String FT_BULKHEAD_CALLS_TOTAL = "ft.bulkhead.calls.total";

    /**
     * Histogram of waiting time to enter a bulkhead.
     */
    String FT_BULKHEAD_WAITINGDURATION = "ft.bulkhead.waitingDuration";

    /**
     * Gauge of number of executions running at a certain time.
     */
    String FT_BULKHEAD_EXECUTIONSRUNNING = "ft.bulkhead.executionsRunning";

    /**
     * Gauge of number of executions waiting at a certain time.
     */
    String FT_BULKHEAD_EXECUTIONSWAITING = "ft.bulkhead.executionsWaiting";

    /**
     * Gauge of number of executions rejected by the bulkhead.
     */
    String FT_BULKHEAD_EXECUTIONSREJECTED = "ft.bulkhead.executionsRejected";

    /**
     * Create {@link Bulkhead} from its configuration.
     *
     * @param config configuration of a bulkhead to create
     * @return a new bulkhead
     */
    static Bulkhead create(BulkheadConfig config) {
        return new BulkheadImpl(config);
    }

    /**
     * Create {@link Bulkhead} customizing its configuration.
     *
     * @param builderConsumer consumer to update configuration of bulkhead
     * @return a new bulkhead
     */
    static Bulkhead create(Consumer<BulkheadConfig.Builder> builderConsumer) {
        BulkheadConfig.Builder builder = BulkheadConfig.builder();
        builderConsumer.accept(builder);
        return create(builder.buildPrototype());
    }

    /**
     * Create a new bulkhead fluent API builder.
     *
     * @return a new bulkhead builder
     */
    static BulkheadConfig.Builder builder() {
        return BulkheadConfig.builder();
    }

    /**
     * Provides access to internal stats for this bulkhead.
     *
     * @return internal stats.
     */
    Stats stats();

    /**
     * Can be used to cancel a supplier while queued.
     *
     * @param supplier the supplier
     * @return outcome of cancellation
     */
    boolean cancelSupplier(Supplier<?> supplier);

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
}
