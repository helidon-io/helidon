/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
package io.helidon.metrics.serviceapi;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.servicecommon.rest.RestServiceSettings;
import io.helidon.servicecommon.rest.RestServiceSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;

/**
 * Behavior for supporting metrics for the Helidon Web Server.
 *
 * <p>
 * By default, {@code MetricsSupport} creates the {@code /metrics} endpoint with three sub-paths: {@code application},
 * {@code vendor}, and {@code base}.
 * <p>
 * To register these endpoints with the web server:
 * <pre>{@code
 * Routing.builder()
 *        .register(MetricsSupport.create())
 * }</pre>
 * <p>
 * This class supports finer-grained settings using {@link io.helidon.metrics.api.MetricsSettings} and
 * {@link io.helidon.servicecommon.rest.RestServiceSettings} and Helidon Config via
 * {@link #create(io.helidon.metrics.api.MetricsSettings, io.helidon.servicecommon.rest.RestServiceSettings)}.
 * <p>
 * During request handling the application metrics registry is then available as follows:
 * <pre>{@code
 *  req.context().get(MetricRegistry.class).ifPresent(reg -> reg.counter("myCounter").inc());
 * }</pre>
 */
public interface MetricsSupport extends RestServiceSupport, Service {

    /**
     * Creates a new {@code MetricsSupport} instance using default metrics settings.
     *
     * @return new metrics support using default metrics settings
     */
    static MetricsSupport create() {
        return MetricsSupportManager.create();
    }

    /**
     * Creates a new {@code MetricsSupport} instance using the specified metrics settings and REST service settings.
     *
     * @param metricsSettings metrics settings to use in initializing the metrics support
     * @param restServiceSettings REST service settings for the metrics endpoint
     *
     * @return new metrics support using specified metrics settings and REST service settings
     */
    static MetricsSupport create(MetricsSettings metricsSettings, RestServiceSettings restServiceSettings) {
        return MetricsSupportManager.create(metricsSettings, restServiceSettings);
    }

    /**
     * Creates a new {@code MetricsSupport} instance using the specified metrics settings and defaulted REST service settings.
     *
     * @param metricsSettings metrics settings to use in initializing the metrics support
     * @return new metrics support using the specified metrics settings
     */
    static MetricsSupport create(MetricsSettings metricsSettings) {
        return create(metricsSettings, defaultedMetricsRestServiceSettingsBuilder().build());
    }

    /**
     * Creates a new {@code MetricsSupport} instance using the specified configuration.
     *
     * @param config configuration to use
     * @return new metrics support instance using the provided configuration
     */
    static MetricsSupport create(Config config) {
        return MetricsSupportManager.create(MetricsSettings.create(config),
                                            defaultedMetricsRestServiceSettingsBuilder()
                                                    .config(config)
                                                    .build());
    }

    /**
     * Returns a builder for the highest-priority {@code MetricsSupport} implementation.
     *
     * @return builder for {@code MetricsSupport}
     */
    static MetricsSupport.Builder<?, ?> builder() {
        return MetricsSupportManager.builder();
    }

    /**
     * Prepares a {@link io.helidon.servicecommon.rest.RestServiceSettings.Builder} instance for metrics with the default
     * settings.
     *
     * @return the prepared builder
     */
    static RestServiceSettings.Builder defaultedMetricsRestServiceSettingsBuilder() {
        return RestServiceSettings.builder()
                .webContext(MetricsSettings.Builder.DEFAULT_CONTEXT);
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
    void prepareMetricsEndpoints(String endpointContext, Routing.Rules serviceEndpointRoutingRules);

    /**
     * Prepares the endpoint which the service exposes.
     *
     * @param defaultRoutingRules routing rules for the default routing
     * @param serviceEndpointRoutingRules routing rules (if different from default) for the service endpoint
     */
    void configureEndpoint(Routing.Rules defaultRoutingRules, Routing.Rules serviceEndpointRoutingRules);

    /**
     * Sets up vendor metrics routing using the specified routing name and routing builder.
     *
     * @param routingName routing name to use in setting up the vendor metrics
     * @param routingRules routing rules to modify
     */
    void configureVendorMetrics(String routingName, Routing.Rules routingRules);

    @Override
    void update(Routing.Rules rules);

    /**
     * Builder for {@code MetricsSupport}.
     * <p>
     *     Callers can influence how {@code MetricsSupport} behaves by assigning {@link io.helidon.metrics.api.MetricsSettings}.
     * </p>
     *
     * @param <B> builder type
     * @param <T> specific implementation type of {@code MetricsSupport}
     */

    @Configured
    interface Builder<B extends Builder<B, T>, T extends MetricsSupport> extends io.helidon.common.Builder<B, T> {

        /**
         * Returns the new {@code MetricsSupport} instance according to the builder's settings.
         *
         * @return the new metrics support
         */
        T build();

        /**
         * Assigns {@code MetricsSettings} which will be used in creating the {@code MetricsSupport} instance at build-time.
         *
         * @param metricsSettingsBuilder the metrics settings to assign for use in building the {@code MetricsSupport} instance
         * @return updated builder
         */
        @ConfiguredOption(mergeWithParent = true,
                          type = MetricsSettings.class)
        B metricsSettings(MetricsSettings.Builder metricsSettingsBuilder);

        /**
         * Set the REST service settings.
         *
         * @param restServiceSettingsBuilder REST service settings to use
         * @return updated builder
         */
        @ConfiguredOption(mergeWithParent = true,
                          type = RestServiceSettings.class)
        B restServiceSettings(RestServiceSettings.Builder restServiceSettingsBuilder);
    }
}
