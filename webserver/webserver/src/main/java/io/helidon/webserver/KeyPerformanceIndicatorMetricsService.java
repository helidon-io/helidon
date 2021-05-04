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

import io.helidon.common.LazyValue;

/**
 * Service that updates per-request key performance indicator (KPI) metrics.
 * <p>
 *     Helidon maintains two categories of KPI metrics:
 *     <ol>
 *         <li>basic - always collected (if the app depends on metrics) - count and meter of the number of arrived requests</li>
 *         <li>extended - disabled by default, enabled using the {@code MetricsSupport} builder or using config
 *         <ul>
 *             <li>concurrent gauge of in-flight requests</li>
 *             <li>meters (rates) of
 *                 <ul>
 *                     <li>long-running requests</li>
 *                     <li>load (currently-running requests)</li>
 *                     <li>queued requests</li>
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
     * Returns a lazy value for the highest-priority available implementation of the interface.
     *
     * In MP applications, the service loader selects the MP implementation.
     */
    LazyValue<KeyPerformanceIndicatorMetricsService> KPI_METRICS_SERVICE =
            LazyValue.create(KeyPerformanceIndicatorMetricsServiceLoader::load);

    /**
     * Returns a {@link Context} suitable for use from the {@code MetricsSupport} handler.
     *
     * @return Context to be invoked from {@code MetricsSupport}
     */
    default Context metricsSupportHandlerContext() {
        return Context.NO_OP;
    }

    /**
     * Returns a {@link Context} suitable for use from {@code JerseySupport}.
     *
     * @return Context to be invoked from {@code JerseySupport}
     */
    default Context jerseyContext() {
        return Context.NO_OP;
    }

    /**
     * Initializes the KPI metrics service with the specified metrics name prefix.
     *
     * @param metricsNamePrefix prefix to use for the names of the KPI metrics
     */
    default void initialize(String metricsNamePrefix) {
    }

    /**
     * Per-request metrics context.
     * <p>
     * Helidon instantiates the appropriate implementation of {@code Context} as soon as possible after becoming aware
     * of a new request, even if work on the request does not begin immediately. As that work begins, Helidon invokes the
     * {@code requestStarted} method. Once the work completes, successfully or not, Helidon invokes {@code requestCompleted}.
     * </p>
     */
    interface Context {

        Context NO_OP = new Context() {
        };

        /**
         * Records that work on the request is about to begin.
         */
        default void requestStarted() {
        }

        /**
         * Records that work on the request has completed.
         *
         * @param isSuccessful indicates whether the request completed successfully
         */
        default void requestCompleted(boolean isSuccessful) {
        }
    }
}
