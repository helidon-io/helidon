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
package io.helidon.tracing.jaeger;

import java.util.Optional;

import io.helidon.common.Prioritized;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.opentelemetry.OpenTelemetryTracerProvider;
import io.helidon.tracing.spi.TracerProvider;

import jakarta.annotation.Priority;

/**
 * Jaeger java service.
 */
@Priority(Prioritized.DEFAULT_PRIORITY)
public class JaegerTracerProvider implements TracerProvider {
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
}
