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

package io.helidon.webclient.metrics;

import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.spi.WebClientServiceProvider;

/**
 * Provider for the {@code http-metrics} WebClient service.
 */
@Api.Internal
public final class WebClientTransportMetricsProvider implements WebClientServiceProvider {
    /**
     * Constructor required by {@link java.util.ServiceLoader}.
     */
    public WebClientTransportMetricsProvider() {
    }

    @Override
    public String configKey() {
        return "http-metrics";
    }

    @Override
    public WebClientService create(Config config, String name) {
        return WebClientTransportMetrics.builder()
                .enabled(Objects.requireNonNull(config, "config").exists())
                .config(config)
                .name(Objects.requireNonNull(name, "name"))
                .build();
    }

    @Override
    public WebClientService create(Config config, String name, ServiceRegistry serviceRegistry) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(serviceRegistry, "serviceRegistry");
        return WebClientTransportMetrics.create(WebClientTransportMetricsConfig.builder()
                                                       .enabled(config.exists())
                                                       .config(config)
                                                       .name(name)
                                                       .buildPrototype(),
                                               () -> serviceRegistry.get(MeterRegistry.class));
    }
}
