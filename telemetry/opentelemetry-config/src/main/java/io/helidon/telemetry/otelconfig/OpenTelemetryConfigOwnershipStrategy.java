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

package io.helidon.telemetry.otelconfig;

import java.util.Objects;

import io.helidon.config.Config;
import io.helidon.service.registry.Service;
import io.helidon.telemetry.opentelemetry.spi.OpenTelemetryOwnershipStrategy;

import io.opentelemetry.api.OpenTelemetry;

@Service.Singleton
class OpenTelemetryConfigOwnershipStrategy implements OpenTelemetryOwnershipStrategy {

    @Override
    public boolean active(Config rootConfig) {
        Config telemetryConfig = telemetryConfig(rootConfig);
        return telemetryConfig.exists()
                && telemetryConfig.get("enabled").asBoolean().orElse(true)
                && telemetryConfig.get("registered").asBoolean().orElse(true);
    }

    @Override
    public String serviceName(Config rootConfig) {
        return telemetryConfig(rootConfig).get("service")
                .asString()
                .orElseThrow(() -> new IllegalStateException("Missing required telemetry.service setting"));
    }

    @Override
    public OpenTelemetry create(Config rootConfig) {
        OpenTelemetryConfig config = OpenTelemetryConfig.builder()
                .config(telemetryConfig(rootConfig))
                .registered(false)
                .global(false)
                .buildPrototype();
        return HelidonOpenTelemetry.create(config).openTelemetry();
    }

    @Override
    public boolean global(Config rootConfig) {
        return telemetryConfig(rootConfig).get("global").asBoolean().orElse(true);
    }

    @Override
    public void selected(Config rootConfig, OpenTelemetry openTelemetry) {
        HelidonOpenTelemetryImpl.configureMdc();
    }

    private static Config telemetryConfig(Config rootConfig) {
        return Objects.requireNonNull(rootConfig).get(HelidonOpenTelemetry.CONFIG_KEY);
    }
}
