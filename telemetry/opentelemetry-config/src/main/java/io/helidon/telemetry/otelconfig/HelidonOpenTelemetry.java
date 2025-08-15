/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;

import io.opentelemetry.api.OpenTelemetry;

/**
 * Helidon management of OpenTelemetry.
 */
@RuntimeType.PrototypedBy(OpenTelemetryConfig.class)
public class HelidonOpenTelemetry implements RuntimeType.Api<OpenTelemetryConfig> {

    static final String TYPE = "otel";

    private static final System.Logger LOGGER = System.getLogger(OpenTelemetry.class.getName());
    private final OpenTelemetryConfig config;

    HelidonOpenTelemetry(OpenTelemetryConfig config) {
        this.config = config;
    }

    static OpenTelemetryConfig.Builder builder() {
        return OpenTelemetryConfig.builder();
    }

    static HelidonOpenTelemetry create(OpenTelemetryConfig config) {
        return new HelidonOpenTelemetry(config);
    }

    static HelidonOpenTelemetry create(Consumer<OpenTelemetryConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    @Override
    public OpenTelemetryConfig prototype() {
        return config;
    }

}
