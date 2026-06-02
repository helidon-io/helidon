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

import io.helidon.common.Api;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.tracing.Span;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.spi.TracerProvider;

/**
 * Service loader provider implementation for {@link io.helidon.tracing.spi.TracerProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 50)
public class OpenTelemetryTracerProvider implements TracerProvider {
    private static final AtomicBoolean APPLICATION_OPEN_TELEMETRY_SELECTED = new AtomicBoolean();

    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public OpenTelemetryTracerProvider() {
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
    public Optional<Span> currentSpan() {
        return activeSpan();
    }

    @Override
    public boolean available() {
        return APPLICATION_OPEN_TELEMETRY_SELECTED.get();
    }

    static void applicationOpenTelemetrySelected() {
        APPLICATION_OPEN_TELEMETRY_SELECTED.set(true);
    }

    static void resetForTest() {
        APPLICATION_OPEN_TELEMETRY_SELECTED.set(false);
    }
}
