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
 */
public interface LimitOutcome {

    /**
     * Describes the disposition of a work item based on the concurrency limit configuration and current state.
     */
    enum Disposition {

        /**
         * Indicates that the limit algorithm accepted the work item.
         */
        ACCEPTED,

        /**
         * Indicates that the limit algorithm rejected the work item.
         */
        REJECTED
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
        enum ExecResult {

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
         * Returns the {@link io.helidon.common.concurrency.limits.LimitOutcome.Accepted.ExecResult} reporting the result
         * of executing the work item.
         *
         * @return execution result
         * @throws IllegalStateException if the execution result has not yet been set in the outcome
         */
        ExecResult execResult() throws IllegalStateException;
    }

}
