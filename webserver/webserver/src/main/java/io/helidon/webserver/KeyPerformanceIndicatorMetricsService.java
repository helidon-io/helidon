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
 * Service that updates per-request key performance indicator (KPI) metrics.
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
 *                     <li>load (Jersey only) (currently-running requests)</li>
 *                     <li>queued requests (Jersey only)</li>
 *                 </ul>
 *             </li>
 *         </ul>
 *      </ol>
 * <p>
 *     Helidon updates the basic KPI metrics in the {@code MetricsSupport} vendor metrics handler. It updates the extended
 *     ones in the same place for SE applications and in {@code JerseySupport} for MP applications.
 * </p>
 * <p>
 *     In either case, the client code obtains a new KPI service context for each request and then notifies that context when
 *     work on that request starts and when it is complete. The KPI service context implementation updates the appropriate KPI
 *     metrics as the request progresses through its processing.
 * </p>
 */
public interface KeyPerformanceIndicatorMetricsService {

    /**
     * Prefix for key performance indicator metrics names.
     */
    String METRICS_NAME_PREFIX = "requests";

    /**
     * Name for metric counting total requests received.
     */
    String REQUESTS_COUNT_NAME = "count";

    /**
     * Name for metric recording rate of requests received.
     */
    String REQUESTS_METER_NAME = "meter";

    /**
     * Name for metric recording current number of requests being processed.
     */
    String INFLIGHT_REQUESTS_NAME = "in-flight";

    /**
     * Name for metric recording rate of requests with processing time exceeding a threshold.
     */
    String LONG_RUNNING_REQUESTS_NAME = "long-running";

    /**
     * Name for metric recording rate of requests processed.
     */
    String LOAD_NAME = "load";

    /**
     * Name for metric recording rate of requests queued before processing.
     */
    String QUEUED_NAME = "queued";

    /**
     * Returns a {@link Context} suitable for tracking requests handled directly (e.g., from the {@code MetricsSupport} handler).
     *
     * @return Context to be invoked for directly-handled requests
     */
    default Context context() {
        return Context.NO_OP;
    }

    /**
     * Per-request metrics context.
     * <p>
     * Helidon instantiates the appropriate implementation of {@code Context} as soon as possible after becoming aware
     * of a new request, even if work on the request does not begin immediately. As that work begins, Helidon invokes the
     * {@code requestHandlingStarted} method. Once the work completes, successfully or not, Helidon invokes {@code requestHandlingCompleted}.
     * </p>
     * <p>
     *     In some cases, certain requests might be able to be queued. The code which handles such requests informs the context
     *     of such changes in state. Different implementations of {@code Context} for different environments apportions the
     *     work of updating the key performance indicator metrics in different ways.
     * </p>
     */
    interface Context {

        Context NO_OP = new Context() {
        };

        /**
         * Records that handling of the request is about to begin.
         */
        default void requestHandlingStarted() {
        }

        /**
         * Records that a request is about to begin its processing.
         */
        default void requestProcessingStarted() {
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

}
