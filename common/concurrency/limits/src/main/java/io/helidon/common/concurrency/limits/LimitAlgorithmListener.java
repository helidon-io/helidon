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
 * Listener to events related to a limit algorithm's processing of a work item (e.g., an incoming HTTP request).
 *
 * @param <CTX> type of the listener context provided by this listener
 */
public interface LimitAlgorithmListener<CTX extends LimitAlgorithmListener.Context> {

    /**
     * Indicates if the listener is enabled or not.
     *
     * @return true if the listener is enabled; false otherwise
     */
    boolean enabled();

    /**
     * Records the outcome related to an acceptance of the work item by the limit algorithm.
     *
     * @param acceptedLimitOutcome {@link io.helidon.common.concurrency.limits.LimitOutcome.Accepted} outcome
     * @return the listener context for the accepted work item
     */
    CTX onAccept(LimitOutcome.Accepted acceptedLimitOutcome);

    /**
     * Records the outcome related to a rejection of the work item by the limit algorihtm.
     *
     * @param rejectedLimitOutcome {@linkplain io.helidon.common.concurrency.limits.LimitOutcome rejected outcome}
     * @return the listener context for the rejected work item
     */
    CTX onReject(LimitOutcome rejectedLimitOutcome);

    /**
     * Records the completion of the limit algorithm's processing of an accepted work item.
     *
     * @param listenerContext listener context returned from the earlier invocation of
     * {@link #onAccept(io.helidon.common.concurrency.limits.LimitOutcome.Accepted)}
     * @param execResult execution result reported by the user of the limit algorithm
     */
    void onFinish(CTX listenerContext, LimitOutcome.Accepted.ExecutionResult execResult);

    /**
     * Common superinterface for all listener context types.
     */
    interface Context {

        /**
         * Indicates if the listener context should be propagated using a mechanism available to the caller of the limit
         * algorithm.
         * <p>
         * A component which provides a listener might do some work in the listener context after the limit algorithm
         * has completed its processing&mdash;for example, in an HTTP listener which is invoked well after the limit algorithm
         * processing completes. In such cases, this method returns {@code true} as a cue to the caller of the limit algorithm
         * to propagate this context in a way that the downstream logic can find it&mdash;for example, in an HTTP request's
         * request context.
         *
         * @return true if this context should be propagated; false otherwise
         */
        default boolean shouldBePropagated() {
            return false;
        }
    }
}
