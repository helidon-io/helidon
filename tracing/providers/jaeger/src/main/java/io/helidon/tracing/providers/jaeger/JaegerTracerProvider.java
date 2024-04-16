/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.tracing.providers.jaeger;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerProvider;
import io.helidon.tracing.spi.TracerProvider;

/**
 * Jaeger java service.
 */
@Weight(Weighted.DEFAULT_WEIGHT)
public class JaegerTracerProvider implements TracerProvider {

    private static final List<SpanListener> SPAN_LISTENERS = HelidonServiceLoader.create(ServiceLoader.load(
            SpanListener.class)).asList();

    @Override
    public Tracer global() {
        return OpenTelemetryTracerProvider.globalTracer();
    }

    @Override
    public void global(Tracer tracer) {
        OpenTelemetryTracerProvider.globalTracer(tracer);
    }

    @Override
    public Optional<Span> currentSpan() {
        return OpenTelemetryTracerProvider.activeSpan();
    }

    @Override
    public JaegerTracerBuilder createBuilder() {
        return JaegerTracerBuilder.create();
    }

    @Override
    public boolean available() {
        return true;
    }

    static List<SpanListener> lifeCycleListeners() {
        return SPAN_LISTENERS;
    }
}
