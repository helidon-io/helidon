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

import java.util.stream.Stream;

import io.helidon.common.http.Http;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;

import org.eclipse.microprofile.metrics.MetricRegistry;

/**
 * No-op implementation of {@link MetricsSupport}.
 * <p>
 *     Apps and other Helidon components which use {@code MetricSupport} (such as
 * the MP metrics component) can very easily take advantage of the no-op implementation of the metrics registries and the metrics
 * themselves if metrics is disabled via configuration or settings simply by using the {@code MetricsSupport} factory methods
 * which, based on the metrics settings, might choose this implementation.
 * </p>
 * <p>This implementation sets up the usual metrics-related endpoints but always sends a 404 response with an explanatory
 * message.</p>
 */
class NoOpMetricsSupport implements MetricsSupport {

    private static final String DISABLED_ENDPOINT_MESSAGE = "metrics is disabled";
    private static final Handler DISABLED_ENDPOINT_HANDLER =
            (req, res) -> res.status(Http.Status.NOT_FOUND_404.code()).send(DISABLED_ENDPOINT_MESSAGE);

    static NoOpMetricsSupport.Builder builder() {
        return new Builder();
    }

    static NoOpMetricsSupport create(MetricsSettings metricsSettings) {
        return new NoOpMetricsSupport(metricsSettings);
    }

    static void createEndpointForDisabledMetrics(String endpointContext, Routing.Rules serviceEndpointRoutingRules) {
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

    private final MetricsSettings metricsSettings;

    @Override
    public void configureEndpoint(Routing.Rules defaultRoutingRules, Routing.Rules serviceEndpointRoutingRules) {
        createEndpointForDisabledMetrics(metricsSettings.restServiceSettings().webContext(), serviceEndpointRoutingRules);
    }

    private NoOpMetricsSupport(Builder builder) {
        this.metricsSettings = builder.metricsSettingsBuilder.build();
    }

    private NoOpMetricsSupport(MetricsSettings metricsSettings) {
        this.metricsSettings = metricsSettings;
    }

    static class Builder implements MetricsSupport.Builder<NoOpMetricsSupport> {

        private MetricsSettings.Builder metricsSettingsBuilder;

        @Override
        public NoOpMetricsSupport.Builder metricsSettings(MetricsSettings.Builder metricsSettingsBuilder) {
            this.metricsSettingsBuilder = metricsSettingsBuilder;
            return this;
        }

        @Override
        public NoOpMetricsSupport build() {
            return new NoOpMetricsSupport(this);
        }
    }
}
