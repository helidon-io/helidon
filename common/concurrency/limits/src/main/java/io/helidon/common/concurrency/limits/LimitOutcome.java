/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.concurrency.limits;

/**
 * Information about the result of applying a concurrency limiting algorithm to a work item.
 * <p>
 * An outcome has several orthogonal attributes:
 * <h2>Disposition</h2>
 * <ul>
 *     <li>accepted
 *     <p>The limit algorithm accepted the work item for execution. </li>
 *     <li>rejected
 *     <p>The limit algorithm rejected the work item due to concurrency limit constraints. No further changes
 *     occur to a rejected outcome.</li>
 * </ul>
 * <h2>Deferral</h2>
 * The limit algorithm decides whether to accept or reject the work item either:
 * <ul>
 *     <li><em>immediately</em> upon learning of the work item (in which case there is no waiting time), or</li>
 *     <li><em>deferred</em> in which case the algorithm makes its decision some time after the work item arrives,
 *     leading to some amount of waiting time.</li>
 * </ul>
 * <h2>Execution result</h2>
 * If the algorithm accepts a work item, then later the code using the limit algorithm should
 * invoke exactly one of the token's {@code dropped}, {@code ignore}, or {@code success} methods.
 */
public interface LimitOutcome {

    /**
     * Describes the disposition of a work item based on the concurrency limit configuration and current state.
     */
    enum Disposition {

        /**
         * Indicates that the limit algorithm accepted the work item; the outcome instance <em>does not</em> implement
         * the {@link io.helidon.common.concurrency.limits.LimitOutcome.Deferred} interface.
         */
        ACCEPTED,

        /**
         * Indicates that the limit algorithm rejected the work item; the outcome instance also implements
         * the {@link io.helidon.common.concurrency.limits.LimitOutcome.Deferred} interface.
         */
        REJECTED
    }

    /**
     * Describes when the limit algorithm decided on the outcome relative to the receipt of the work item.
     */
    enum Timing {

        /**
         * The outcome was an immediate decision by the limit algorithm as soon as the work item arrived; the work
         * item was never on the virtual queue, and the outcome instance does <em>not</em> implement
         * {@link io.helidon.common.concurrency.limits.LimitOutcome.Deferred}.
         */
        IMMEDIATE,

        /**
         * The outcome was a deferred decided, made only after the work item was on the virtual queue; the outcome instance
         * <em>does</em> implement {@link io.helidon.common.concurrency.limits.LimitOutcome.Deferred}.
         */
        DEFERRED
    }

    /**
     * The origin of the concurrency limit usage (e.g., a socket name for HTTP request processing).
     *
     * @return origin name
     */
    String originName();

    /**
     * The type of the concurrency limit implementation.
     *
     * @return limit implementation type
     */
    String algorithmType();

    /**
     * Returns the {@link io.helidon.common.concurrency.limits.LimitOutcome.Disposition} of the work item.
     *
     * @return work item's disposition
     */
    Disposition disposition();

    /**
     * Returns the {@link io.helidon.common.concurrency.limits.LimitOutcome.Timing} of the decision regarding the
     * work item.
     *
     * @return timing of the limit algorithm's decision
     */
    Timing timing();

    /**
     * Information about a deferred work item's processing.
     */
    interface Deferred extends LimitOutcome {

        /**
         * When the wait period for the work item began.
         *
         * @return nanoseconds time when the work item started its wait
         */
        long waitStart();

        /**
         * When the wait period for the work item ended.
         *
         * @return nanoseconds time when the work item ended its wait
         */
        long waitEnd();
    }

    /**
     * Information about a work item that the limit implementation accepted for execution.
     */
    interface Accepted extends LimitOutcome {
        /**
         * Describes the result of executing an accepted work item.
         */
        enum ExecutionResult {

            /**
             * Indicates that the caller of the limit algorithm invoked
             * {@link io.helidon.common.concurrency.limits.Limit.Token#success()}.
             */
            SUCCEEDED,

            /**
             * Indicates that the caller of the limit algorithm invoked
             * {@link io.helidon.common.concurrency.limits.Limit.Token#dropped()}.
             */
            DROPPED,

            /**
             * Indicates that the caller of the limit algorithm invoked
             * {@link io.helidon.common.concurrency.limits.Limit.Token#ignore()}.
             */
            IGNORED
        }

        /**
         * Returns the {@link io.helidon.common.concurrency.limits.LimitOutcome.Accepted.ExecutionResult} reporting the result
         * of executing the work item.
         *
         * @return execution result
         * @throws IllegalStateException if the execution result has not yet been set in the outcome
         */
        ExecutionResult executionResult() throws IllegalStateException;
    }

}
