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
package io.helidon.metrics.api;

import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;

/**
 * Behavior of metrics support implementations.
 */
public interface MetricsSupport extends Service {

    /**
     * Creates a new {@code MetricsSupport} getInstance using default metrics settings.
     *
     * @return new metrics support using default metrics settings
     */
    static MetricsSupport create() {
        return MetricsSupportManager.create();
    }

    /**
     * Creates a new {@code MetricsSupport} getInstance using the specified metrics settings.
     *
     * @param metricsSettings metrics settings to use in initializing the metrics support
     * @return new metrics support using specified metrics settings
     */
    static MetricsSupport create(MetricsSettings metricsSettings) {
        return MetricsSupportManager.create(metricsSettings);
    }

    /**
     * Prepares the family of {@code /metrics} endpoints.
     * <p>
     *     By default, requests to the metrics endpoints trigger a 404 response with an explanatory message that metrics are
     *     disabled. Implementations of this interface can provide more informative endpoints.
     * </p>
     *
     * @param endpointContext context (typically /metrics)
     * @param serviceEndpointRoutingRules routing rules to update with the disabled metrics endpoints
     */
    default void prepareMetricsEndpoints(String endpointContext, Routing.Rules serviceEndpointRoutingRules) {
        NoOpMetricsSupport.createEndpointForDisabledMetrics(endpointContext, serviceEndpointRoutingRules);
    }

    /**
     * Builder for {@code MetricsSupport}.
     *
     * @param <T> specific implementation type of {@code MetricsSupport}
     */
    interface Builder<T extends MetricsSupport> extends io.helidon.common.Builder<T> {

        /**
         * Returns the new {@code MetricsSupport} getInstance according to the builder's settings.
         *
         * @return the new metrics support
         */
        T build();

        Builder<T> metricsSettings(MetricsSettings.Builder metricsSettingsBuilder);
    }
}
