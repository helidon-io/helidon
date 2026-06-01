/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;

import io.opentelemetry.api.OpenTelemetry;

/**
 * Public access to OpenTelemetry as managed via Helidon config and builders.
 */
public interface HelidonOpenTelemetry extends RuntimeType.Api<OpenTelemetryConfig> {

    /**
     * Top-level config key for telemetry settings.
     */
    String CONFIG_KEY = "telemetry";

    /**
     * Creates a new builder for {@code HelidonOpenTelemetry}.
     *
     * @return new builder
     */
    static OpenTelemetryConfig.Builder builder() {
        return OpenTelemetryConfig.builder();
    }

    /**
     * Creates a new {@code HelidonOpenTelemetry} from config.
     *
     * @param config the config node to use in building the result
     * @return new {@code HelidonOpenTelemetry} based on the supplied config
     */
    static HelidonOpenTelemetry create(OpenTelemetryConfig config) {
        return new HelidonOpenTelemetryImpl(config);
    }

    /**
     * Builds a new {@code HelidonOpenTelemetry} instance by revising and then building the supplied builder.
     *
     * @param consumer consumer of a builder for {@code HelidonOpenTelemetry}
     * @return new instance
     */
    static HelidonOpenTelemetry create(Consumer<OpenTelemetryConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    /**
     * This method is now a no-op.
     *
     * @param openTelemetry ignored OpenTelemetry instance
     * @param serviceName ignored service name
     * @param tags ignored tracer tags
     * @deprecated Use {@link io.helidon.service.registry.Services#set(Class, Object[])} to configure a custom telemetry
     *             instance, or let Helidon resolve the correct instance.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    static void global(OpenTelemetry openTelemetry, String serviceName, Map<String, String> tags) {
    }

    /**
     * Returns the {@link io.opentelemetry.api.OpenTelemetry} instance managed by Helidon.
     * @return the OpenTelemetry instance
     */
    OpenTelemetry openTelemetry();
}
