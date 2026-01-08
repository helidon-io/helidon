/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.service.registry.Services;
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
                        /*
                        To preserve backward compatibility with the global(Tracer) method on this class while also working with
                        OpenTelemetry config via Helidon config, try to initialize OTel using our configured support.
                         */
                        Optional<OpenTelemetry> fromService = Services.first(OpenTelemetry.class);
                        if (fromService.isPresent()) {
                            /*
                            If we are here then CONFIGURED_TRACER was not set earlier but the call to Services.first was able
                            to initialize OpenTelemetry. Part of that logic is to set CONFIGURED_TRACER. So here we just
                            return what is now (or certainly should be) a non-empty CONFIGURED_TRACER.
                             */
                            var recentTracer = CONFIGURED_TRACER.get();
                            if (recentTracer != null) {
                                LOGGER.log(System.Logger.Level.TRACE, "Global tracer has been set via telemetry configuration");
                                return recentTracer;
                            }
                            LOGGER.log(System.Logger.Level.TRACE,
                                       "OpenTelemetry was initialized via the service registry but the global tracer was "
                                               + "unexpectedly not set.");
                        }
                        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                            LOGGER.log(System.Logger.Level.TRACE, "Global tracer is not registered. Register it through "
                                    + "Tracer.global(HelidonOpenTelemetry.create(ot, tracer). Using global open telemetry");
                        }
                        OpenTelemetry ot = GlobalOpenTelemetry.get();
                        return OpenTelemetryTracer.builder()
                                .openTelemetry(ot)
                                .delegate(ot.getTracer("helidon-service"))
                                .build();
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

    @Override
    public TracerBuilder<?> createBuilder() {
        return OpenTelemetryTracerBuilder.create();
    }

    @Override
    public Tracer global() {
        return globalTracer();
    }

    @Override
    public void global(Tracer tracer) {
        if (tracer instanceof OpenTelemetryTracer ott) {
            globalTracer(ott);
            return;
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
