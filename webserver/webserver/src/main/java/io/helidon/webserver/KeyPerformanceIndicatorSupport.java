/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.webserver;

/**
 * Definitions and factory methods for key performance indicator {@link Context} and {@link Metrics}.
 * <p>
 *     Helidon maintains two categories of KPI metrics:
 *     <ol>
 *         <li>basic - always collected (if the app depends on metrics) - count and meter of the number of arrived requests</li>
 *         <li>extended - disabled by default, enabled using the {@code MetricsSupport} or {@code JerseySupport} builder or using
 *         config
 *         <ul>
 *             <li>concurrent gauge of in-flight requests</li>
 *             <li>meters (rates) of
 *                 <ul>
 *                     <li>long-running requests</li>
 *                     <li>load (currently-running requests)</li>
 *                     <li>deferred requests</li>
 *                 </ul>
 *             </li>
 *         </ul>
 *      </ol>
 * <p>
 *     Helidon updates the KPI metrics in the {@code MetricsSupport} vendor metrics handler.
 * </p>
 * <p>
 *     using a per-request KPI metrics context which it notifies when the request
 *     <ol>
 *         <li>arrives,</li>
 *         <li>begins processing, and</li>
 *         <li>completes processing.</li>
 *     </ol>
 *     The KPI metrics context implementation updates the appropriate KPI
 *     metrics as the request progresses through its processing.
 */
public interface KeyPerformanceIndicatorSupport {

    /**
     * Per-request key performance indicator context, with behavior common to immediately-processed requests and deferrable ones.
     */
    interface Context {

        /**
         * Provides a {@code Context} for use with an immediate (non-deferrable) request.
         *
         * @return the new {@code Context}
         */
        static Context create() {
            return KeyPerformanceIndicatorContextFactory.immediateRequestContext();
        }

        /**
         * No-op implementation of {@code Context}.
         */
        Context NO_OP = new Context() {
        };

        /**
         * Records that handling of the request is about to begin.
         *
         * @param keyPerformanceIndicatorMetrics KPI metrics to update in this context
         */
        default void requestHandlingStarted(Metrics keyPerformanceIndicatorMetrics) {
        }

        /**
         * Records that a request has completed its processing.
         *
         * @param isSuccessful whether the request completed successfully
         */
        default void requestProcessingCompleted(boolean isSuccessful) {
        }

        /**
         * Records that handling of a request has completed.
         *
         * @param isSuccessful whether the request completed successfully
         */
        default void requestHandlingCompleted(boolean isSuccessful) {
        }
    }

    /**
     * Added per-request key performance indicator context behavior for requests for which processing might be deferred until
     * some time after receipt of the request (i.e., some time after request handling begins).
     */
    interface DeferrableRequestContext extends Context {

        /**
         * Provides a {@code Context} for use with a deferrable request.
         *
         * @return new {@code Context}
         */
        static Context create() {
            return KeyPerformanceIndicatorContextFactory.deferrableRequestContext();
        }

        /**
         * A {@link Handler} which registers a KPI deferrable request context in the request's context.
         */
        Handler CONTEXT_SETTING_HANDLER = (req, res) -> {
            req.context().register(KeyPerformanceIndicatorContextFactory.deferrableRequestContext());
            req.next();
        };

        /**
         * Records that a request is about to begin its processing.
         */
        default void requestProcessingStarted() {
        }
    }

    /**
     * Key performance indicator metrics behavior.
     */
    interface Metrics {

        /**
         * No-op implementation of {@code Metrics}.
         */
        Metrics NO_OP = new Metrics() {
        };

        /**
         * Invoked when a request has been received.
         */
        default void onRequestReceived() {
        }

        /**
         * Invoked when processing on a request has been started.
         */
        default void onRequestStarted() {
        }

        /**
         * Invoked when processing on a request has finished.
         *
         * @param isSuccessful     indicates if the request processing succeeded
         * @param processingTimeMs duration of the request processing in milliseconds
         */
        default void onRequestCompleted(boolean isSuccessful, long processingTimeMs) {
        }
    }
}
