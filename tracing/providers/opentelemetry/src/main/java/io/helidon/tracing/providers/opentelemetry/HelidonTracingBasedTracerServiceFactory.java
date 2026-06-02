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

package io.helidon.tracing.providers.opentelemetry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.telemetry.opentelemetry.ApplicationOpenTelemetry;
import io.helidon.telemetry.opentelemetry.spi.OpenTelemetryOwnershipStrategy;
import io.helidon.tracing.Tracer;

import io.opentelemetry.api.OpenTelemetry;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class HelidonTracingBasedTracerServiceFactory implements Supplier<Tracer> {

    private final ServiceRegistry registry;
    private final Config config;
    private final Supplier<List<OpenTelemetryOwnershipStrategy>> strategies;
    private final Supplier<OpenTelemetry> openTelemetry;

    @Service.Inject
    HelidonTracingBasedTracerServiceFactory(ServiceRegistry registry,
                                            Config config,
                                            Supplier<List<OpenTelemetryOwnershipStrategy>> strategies,
                                            Supplier<OpenTelemetry> openTelemetry) {
        this.registry = registry;
        this.config = config;
        this.strategies = strategies;
        this.openTelemetry = openTelemetry;
    }

    @Override
    public Tracer get() {
        List<OpenTelemetryOwnershipStrategy> strategyList = strategies.get();
        OpenTelemetry canonicalOpenTelemetry = ApplicationOpenTelemetry.applicationOpenTelemetry(registry,
                                                                                                config,
                                                                                                strategyList,
                                                                                                openTelemetry::get);
        OpenTelemetryOwnershipStrategy strategy = selectedStrategy(config, strategyList).orElse(null);
        Tracer tracer = applicationTracer(config, strategy, canonicalOpenTelemetry);
        if (tracer.enabled()) {
            OpenTelemetryTracerProvider.applicationOpenTelemetrySelected();
        }
        return tracer;
    }

    private static Tracer applicationTracer(Config rootConfig,
                                            OpenTelemetryOwnershipStrategy strategy,
                                            OpenTelemetry openTelemetry) {
        if (tracingDisabled(rootConfig) || (strategy == null && telemetryDisabled(rootConfig))) {
            return Tracer.noOp();
        }

        if (strategy instanceof OpenTelemetryTracerFactory tracerFactory) {
            return tracerFactory.createTracer(rootConfig, openTelemetry);
        }

        String serviceName = serviceName(rootConfig, strategy);
        return HelidonOpenTelemetry.create(openTelemetry, openTelemetry.getTracer(serviceName), Map.of());
    }

    private static Optional<OpenTelemetryOwnershipStrategy> selectedStrategy(Config rootConfig,
                                                                             List<OpenTelemetryOwnershipStrategy> strategies) {
        return strategies.stream()
                .map(Objects::requireNonNull)
                .filter(strategy -> strategy.active(rootConfig))
                .findFirst();
    }

    private static boolean tracingDisabled(Config rootConfig) {
        Config tracingConfig = rootConfig.get(OpenTelemetryTracerConfigBlueprint.TRACING_CONFIG_KEY);
        return tracingConfig.exists()
                && !tracingConfig.get("enabled").asBoolean().orElse(true);
    }

    private static boolean telemetryDisabled(Config rootConfig) {
        Config telemetryConfig = rootConfig.get("telemetry");
        return telemetryConfig.exists()
                && !telemetryConfig.get("enabled").asBoolean().orElse(true);
    }

    private static String serviceName(Config rootConfig, OpenTelemetryOwnershipStrategy strategy) {
        if (strategy != null) {
            return strategy.serviceName(rootConfig);
        }
        Optional<String> telemetryServiceName = serviceNameIfConfigured(rootConfig.get("telemetry"));
        if (telemetryServiceName.isPresent()) {
            return telemetryServiceName.get();
        }

        Optional<String> tracingServiceName =
                serviceNameIfConfigured(rootConfig.get(OpenTelemetryTracerConfigBlueprint.TRACING_CONFIG_KEY));
        if (tracingServiceName.isPresent()) {
            return tracingServiceName.get();
        }

        return HelidonOpenTelemetry.DEFAULT_SERVICE_NAME;
    }

    private static Optional<String> serviceNameIfConfigured(Config config) {
        if (!config.exists()) {
            return Optional.empty();
        }
        return config.get("service").asString().asOptional();
    }
}
