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

import java.util.Objects;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.service.registry.Service;
import io.helidon.telemetry.opentelemetry.spi.OpenTelemetryOwnershipStrategy;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

import io.opentelemetry.api.OpenTelemetry;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 80)
class TracingOpenTelemetryOwnershipStrategy implements OpenTelemetryOwnershipStrategy, OpenTelemetryTracerFactory {

    @Override
    public boolean active(Config rootConfig) {
        Config tracingConfig = tracingConfig(rootConfig);
        return tracingConfig.exists()
                && tracingConfig.get("service").asString().asOptional().isPresent()
                && tracingConfig.get("enabled").asBoolean().orElse(true)
                && tracingConfig.get("registered").asBoolean().orElse(true);
    }

    @Override
    public String serviceName(Config rootConfig) {
        return tracingConfig(rootConfig).get("service")
                .asString()
                .orElseThrow(() -> new IllegalStateException("Missing required tracing.service setting"));
    }

    @Override
    public OpenTelemetry create(Config rootConfig) {
        return OpenTelemetryTracerBuilder.create()
                .config(tracingConfig(rootConfig))
                .registerGlobal(false)
                .buildPrototype()
                .openTelemetry();
    }

    @Override
    public boolean global(Config rootConfig) {
        return tracingConfig(rootConfig).get("global").asBoolean().orElse(true);
    }

    @Override
    public Tracer createTracer(Config rootConfig, OpenTelemetry openTelemetry) {
        Config tracingConfig = tracingConfig(rootConfig);
        return OpenTelemetryTracerBuilder.create()
                .config(tracingConfig)
                .openTelemetry(openTelemetry)
                .delegate(openTelemetry.getTracer(serviceName(rootConfig)))
                .registerGlobal(false)
                .build();
    }

    @Override
    public void selected(Config rootConfig, OpenTelemetry openTelemetry) {
        OpenTelemetryOwnershipStrategy.super.selected(rootConfig, openTelemetry);
        // Initialize neutral tracing and MDC support while this OTel provider is known to be the selected application owner.
        OpenTelemetryTracerProvider.applicationOpenTelemetrySelected();
        Span.current();
    }

    private static Config tracingConfig(Config rootConfig) {
        return Objects.requireNonNull(rootConfig).get(OpenTelemetryTracerConfigBlueprint.TRACING_CONFIG_KEY);
    }
}
