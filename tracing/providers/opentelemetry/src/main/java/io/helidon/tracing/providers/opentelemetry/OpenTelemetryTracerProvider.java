/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.spi.TracerProvider;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;

/**
 * Service loader provider implementation for {@link io.helidon.tracing.spi.TracerProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 50)
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
     * Creates a new provider; reserved for service loading.
     */
    @Deprecated
    public OpenTelemetryTracerProvider() {
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

    /**
     * Returns a {@link io.helidon.tracing.Span} representing the currently-active OpenTelemetry span with any current baggage
     * set on the returned span.
     *
     * @return optional of the current span
     */
    public static Optional<Span> activeSpan() {
        io.opentelemetry.context.Context otelContext = io.opentelemetry.context.Context.current();

        // OTel Span.current() returns a no-op span if there is no current one. Use fromContextOrNull instead to distinguish.
        io.opentelemetry.api.trace.Span otelSpan =
                io.opentelemetry.api.trace.Span.fromContextOrNull(otelContext);

        if (otelSpan == null) {
            return Optional.empty();
        }

        // OTel Baggage.current() returns empty baggage if there is no current one. That's OK for baggage.
        io.opentelemetry.api.baggage.Baggage otelBaggage = io.opentelemetry.api.baggage.Baggage.current();

        // Create the span directly with the retrieved baggage. Ideally, it will be our writable baggage because we had put it
        // there in the context.
        return Optional.of(HelidonOpenTelemetry.create(otelSpan, otelBaggage));
    }

    /**
     * Returns a Helidon {@link io.helidon.tracing.Tracer} which wraps the provided OpenTelemetry
     * {@link io.opentelemetry.api.trace.Tracer}.
     *
     * @param openTelemetryTracer native OTel tracer to wrap
     * @return Helidon tracer wrapping the native OTel tracer
     */
    public static Tracer tracer(io.opentelemetry.api.trace.Tracer openTelemetryTracer) {
        return new OpenTelemetryTracer(GlobalOpenTelemetry.get(), openTelemetryTracer, Map.of());
    }

    /**
     * Returns a Helidon {@link io.helidon.tracing.Span} which wraps the provided OpenTelemetry
     * {@link io.opentelemetry.api.trace.Span}.
     *
     * @param openTelemetrySpan native OTel span to wrap
     * @param isNoop            whether the native span is a no-op span
     * @return Helidon span wrapping the native OTel span
     */
    public static Span span(Tracer helidonTracer, io.opentelemetry.api.trace.Span openTelemetrySpan, boolean isNoop) {
        return new OpenTelemetrySpan(helidonTracer, openTelemetrySpan, isNoop);
    }

    /**
     * Returns a Helidon {@link io.helidon.tracing.Scope} which wraps the provided OpenTelemetry
     * {@link io.opentelemetry.context.Scope}.
     *
     * @param helidonTracer      Helidon {@link io.helidon.tracing.Tracer}
     * @param helidonSpan        Helidon {@link io.helidon.tracing.Span} associated with the scope
     * @param openTelemetryScope OpenTelemetry {@code Scope} to be wrapped
     * @return Helidon {@link Scope} wrapping the OpenTelemetry {@code Scope}
     */
    public static Scope scope(Tracer helidonTracer, Span helidonSpan, io.opentelemetry.context.Scope openTelemetryScope) {
        return new OpenTelemetryScope(helidonTracer.unwrap(OpenTelemetryTracer.class),
                                      helidonSpan.unwrap(OpenTelemetrySpan.class),
                                      openTelemetryScope);
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
        return activeSpan();
    }

    @Override
    public boolean available() {
        return GLOBAL_SET.get();
    }

}
