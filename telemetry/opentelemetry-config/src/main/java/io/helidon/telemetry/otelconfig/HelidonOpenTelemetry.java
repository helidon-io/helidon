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

import java.util.Map;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.service.registry.Services;
import io.helidon.tracing.Tracer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;

/**
 * Public access to OpenTelemetry as managed via Helidon config and builders.
 */
@RuntimeType.PrototypedBy(OpenTelemetryConfig.class)
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
     * Initializes the specified {@link io.opentelemetry.api.OpenTelemetry} instance as global:
     * <ol>
     *     <li>Sets it as the global OpenTelemetry instance.</li>
     *     <li>Creates a new Helidon {@link io.helidon.tracing.Tracer} using the {@code OpenTelemetry} instance.</li>
     *     <li>Makes the Helidon {@code Tracer} the global tracer.</li>
     *     <li>Registers the {@code OpenTelemetry} instance in the Helidon service registry.</li>
     * </ol>
     * @param openTelemetry the {@code OpenTelemetry} instance to make global
     * @param serviceName service name with which to create the new global tracer
     * @throws IllegalStateException if other code has already established the OpenTelemetry global instance
     */
    static void global(OpenTelemetry openTelemetry, String serviceName) throws IllegalStateException {
        GlobalOpenTelemetry.set(openTelemetry);
        var otelTracer = openTelemetry.getTracer(serviceName);
        var helidonTracer = io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry.create(openTelemetry,
                                                                                                   otelTracer,
                                                                                                   Map.of());
        Tracer.global(helidonTracer);
        Services.set(OpenTelemetry.class, openTelemetry);
    }

    /**
     * Returns the {@link io.opentelemetry.api.OpenTelemetry} instance managed by Helidon.
     * @return the OpenTelemetry instance
     */
    OpenTelemetry openTelemetry();
}
