/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.tracing.opentelemetry;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.LazyValue;
import io.helidon.common.Prioritized;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.spi.TracerProvider;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.annotation.Priority;

/**
 * Service loader provider implementation for {@link io.helidon.tracing.spi.TracerProvider}.
 */
@Priority(Prioritized.DEFAULT_PRIORITY + 1000)
public class OpenTelemetryTracerProvider implements TracerProvider {
    private static final System.Logger LOGGER = System.getLogger(OpenTelemetryTracerProvider.class.getName());

    private static final AtomicReference<Tracer> CONFIGURED_TRACER = new AtomicReference<>();
    private static final AtomicBoolean GLOBAL_SET = new AtomicBoolean();
    private static final LazyValue<Tracer> GLOBAL_TRACER;

    static {
        GLOBAL_TRACER = LazyValue.create(() -> {
            // try to get from configured global tracer
            Tracer tracer = CONFIGURED_TRACER.get();
            if (tracer != null) {
                return tracer;
            }
            Context global = Contexts.globalContext();
            return global.get(OpenTelemetryTracer.class)
                    .map(Tracer.class::cast)
                    .orElseGet(() -> {
                        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                            LOGGER.log(System.Logger.Level.TRACE, "Global tracer is not registered. Register it through "
                                    + "Tracer.global(HelidonOpenTelemetry.create(ot, tracer). Using global open telemetry");
                        }
                        OpenTelemetry ot = GlobalOpenTelemetry.get();
                        return new OpenTelemetryTracer(ot, ot.getTracer("helidon-service"), Map.of());
                    });
        });
    }

    /**
     * Register global tracer.
     *
     * @param tracer global tracer
     */
    public static void globalTracer(Tracer tracer) {
        GLOBAL_SET.set(true);
        CONFIGURED_TRACER.set(tracer);
    }

    /**
     * Registered global tracer, or tracer from global open telemetry.
     *
     * @return tracer
     */
    public static Tracer globalTracer() {
        return GLOBAL_TRACER.get();
    }

    @Override
    public TracerBuilder<?> createBuilder() {
        return OpenTelemetryTracer.builder();
    }

    @Override
    public Tracer global() {
        return globalTracer();
    }

    @Override
    public void global(Tracer tracer) {
        if (tracer instanceof OpenTelemetryTracer ott) {
            globalTracer(ott);
        }
        throw new IllegalArgumentException("Tracer must be an instance of Helidon OpenTelemetry tracer. "
                                                   + "Please use HelidonOpenTelemetry to create such instance");
    }

    @Override
    public Optional<Span> currentSpan() {
        return Optional.ofNullable(io.opentelemetry.api.trace.Span.current()).map(HelidonOpenTelemetry::create);
    }

    @Override
    public boolean available() {
        return GLOBAL_SET.get();
    }
}
