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
package io.helidon.metrics.serviceapi;

import java.util.logging.Logger;
import java.util.stream.Stream;

import io.helidon.common.http.Http;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.servicecommon.rest.HelidonRestServiceSupport;
import io.helidon.servicecommon.rest.RestServiceSettings;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;

import org.eclipse.microprofile.metrics.MetricRegistry;

/**
 * Minimal implementation of {@link io.helidon.metrics.serviceapi.MetricsSupport}.
 * <p>
 *     Apps and other Helidon components which use {@code MetricSupport} (such as
 * the MP metrics component) can very easily take advantage of the minimal implementation of the metrics registries and the
 * metrics
 * themselves if metrics is disabled via configuration or settings simply by using the {@code MetricsSupport} factory methods
 * which, based on the metrics settings, might choose this implementation.
 * </p>
 * <p>This implementation sets up the usual metrics-related endpoints but always sends a 404 response with an explanatory
 * message.</p>
 */
public class MinimalMetricsSupport extends HelidonRestServiceSupport implements MetricsSupport {

    static final String DISABLED_ENDPOINT_MESSAGE = "Metrics is disabled";
    private static final Handler DISABLED_ENDPOINT_HANDLER =
            (req, res) -> res.status(Http.Status.NOT_FOUND_404.code()).send(DISABLED_ENDPOINT_MESSAGE);

    static MinimalMetricsSupport.Builder builder() {
        return new Builder();
    }

    static MinimalMetricsSupport create(RestServiceSettings restServiceSettings) {
        return new MinimalMetricsSupport(restServiceSettings);
    }

    /**
     * Adds routing rules so metrics-related requests go to the "not available" endpoint.
     *
     * @param endpointContext web context for metrics
     * @param serviceEndpointRoutingRules routing rules for the metrics service
     */
    public static void createEndpointForDisabledMetrics(String endpointContext, Routing.Rules serviceEndpointRoutingRules) {
        // routing to top-level root (/metrics)
        serviceEndpointRoutingRules
                .get(endpointContext, DISABLED_ENDPOINT_HANDLER)
                .options(endpointContext, DISABLED_ENDPOINT_HANDLER);

        // routing to GET and OPTIONS for each metrics scope (registry type) and a specific metric within each scope:
        // application, base, vendor
        Stream.of(MetricRegistry.Type.values())
                .map(MetricRegistry.Type::name)
                .map(String::toLowerCase)
                .forEach(type -> Stream.of("", "/{metric}") // for the whole scope and for a specific metric within that scope
                        .map(suffix -> endpointContext + "/" + type + suffix)
                        .forEach(path ->
                                         serviceEndpointRoutingRules
                                                 .get(path, DISABLED_ENDPOINT_HANDLER)
                                                 .options(path, DISABLED_ENDPOINT_HANDLER)
                        ));
    }

    private final RestServiceSettings restServiceSettings;

    @Override
    protected void postConfigureEndpoint(Routing.Rules defaultRules, Routing.Rules serviceEndpointRoutingRules) {
        createEndpointForDisabledMetrics(restServiceSettings.webContext(), serviceEndpointRoutingRules);
    }

    @Override
    public void prepareMetricsEndpoints(String endpointContext, Routing.Rules serviceEndpointRoutingRules) {
        createEndpointForDisabledMetrics(endpointContext, serviceEndpointRoutingRules);
    }

    @Override
    public void update(Routing.Rules rules) {
        configureEndpoint(rules, rules);
    }

    @Override
    public void configureVendorMetrics(String routingName, Routing.Rules routingRules) {
    }

    private MinimalMetricsSupport(RestServiceSettings restServiceSettings) {
        this(Logger.getLogger(MinimalMetricsSupport.class.getName()),
             restServiceSettings,
             "metrics");
    }

    private MinimalMetricsSupport(Builder builder) {
        this(Logger.getLogger(MinimalMetricsSupport.class.getName()),
             builder.restServiceSettingsBuilder.build(),
             "metrics");
    }

    private MinimalMetricsSupport(Logger logger, RestServiceSettings restServiceSettings, String serviceName) {
        super(logger, restServiceSettings, serviceName);
        this.restServiceSettings = restServiceSettings;
    }

    static class Builder implements MetricsSupport.Builder<MinimalMetricsSupport> {

        private RestServiceSettings.Builder restServiceSettingsBuilder = RestServiceSettings.builder()
                .webContext("/metrics");

        @Override
        public Builder metricsSettings(MetricsSettings.Builder metricsSettingsBuilder) {
            return this;
        }

        @Override
        public MetricsSupport.Builder<MinimalMetricsSupport> restServiceSettings(
                RestServiceSettings.Builder restServiceSettingsBuilder) {
            this.restServiceSettingsBuilder = restServiceSettingsBuilder;
            return this;
        }

        @Override
        public MinimalMetricsSupport build() {
            return new MinimalMetricsSupport(this);
        }
    }
}
