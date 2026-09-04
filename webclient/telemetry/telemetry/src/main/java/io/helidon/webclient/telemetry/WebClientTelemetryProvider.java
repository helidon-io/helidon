/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.tracing.Tracer;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.spi.WebClientServiceProvider;
import io.helidon.webclient.telemetry.metrics.WebClientTelemetryMetrics;
import io.helidon.webclient.telemetry.tracing.WebClientTelemetryTracing;

/**
 * Provider for a grouping service which gathers telemetry-related webclient services.
 */
public class WebClientTelemetryProvider implements WebClientServiceProvider {
    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public WebClientTelemetryProvider() {
    }

    @Override
    public String configKey() {
        return "telemetry";
    }

    @Override
    public WebClientService create(Config config, String name) {
        return create(config, WebClientTelemetryMetrics::create, WebClientTelemetryTracing::create);
    }

    @Override
    public WebClientService create(Config config, String name, ServiceRegistry serviceRegistry) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(name);
        Objects.requireNonNull(serviceRegistry);
        return create(config,
                      metricsConfig -> WebClientTelemetryMetrics.create(metricsConfig, serviceRegistry),
                      tracingConfig -> WebClientTelemetryTracing.create(tracingConfig,
                                                                        serviceRegistry.supply(Tracer.class)));
    }

    private WebClientService create(Config config,
                                    Function<Config, WebClientService> metricsFactory,
                                    Function<Config, WebClientService> tracingFactory) {
        List<WebClientService> subservices = new ArrayList<>();
        if (config.get("metrics").exists()) {
            subservices.add(metricsFactory.apply(config.get("metrics")));
        }
        if (config.get("tracing").exists()) {
            subservices.add(tracingFactory.apply(config.get("tracing")));
        }

        return subservices.isEmpty()
                ? WebClientService.Chain::proceed
                : (chain, clientRequest) -> {

                    var subserviceIterator = subservices.listIterator(subservices.size());
                    WebClientService.Chain last = chain;

                    while (subserviceIterator.hasPrevious()) {
                        last = new Subchain(last, subserviceIterator.previous());
                    }

                    return last.proceed(clientRequest);
                };
    }

    /**
     * Chain implementation inspired by the one in the webclient component.
     */
    private record Subchain(WebClientService.Chain next, WebClientService service) implements WebClientService.Chain {

        @Override
        public WebClientServiceResponse proceed(WebClientServiceRequest clientRequest) {
            return service.handle(next, clientRequest);
        }
    }
}
