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

import io.helidon.config.Config;
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

    private final List<WebClientService> subservices = new ArrayList<>();

    /**
     * For service loader use only.
     */
    @Deprecated
    public WebClientTelemetryProvider() {
    }

    @Override
    public String configKey() {
        return "telemetry";
    }

    @Override
    public WebClientService create(io.helidon.common.config.Config config, String name) {
        return create((Config) config, name);
    }

    /**
     * Creates a new client telemetry service.
     *
     * @param config client telemetry config
     * @param name   component name
     * @return new webclient service instance for client telemetry
     */
    public WebClientService create(Config config, String name) {

        if (config.get("metrics").exists()) {
            subservices.add(WebClientTelemetryMetrics.create(config.get("metrics")));
        }
        if (config.get("tracing").exists()) {
            subservices.add(WebClientTelemetryTracing.create(config.get("tracing")));
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
