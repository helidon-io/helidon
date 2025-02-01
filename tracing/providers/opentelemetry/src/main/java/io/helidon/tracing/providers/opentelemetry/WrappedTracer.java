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
package io.helidon.tracing.providers.opentelemetry;

import io.helidon.tracing.Wrapper;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

class WrappedTracer implements Tracer, Wrapper {

    private final io.helidon.tracing.Tracer helidonTracer;

    static WrappedTracer create(io.helidon.tracing.Tracer helidonTracer) {
        return new WrappedTracer(helidonTracer);
    }

    private WrappedTracer(io.helidon.tracing.Tracer helidonTracer) {
        this.helidonTracer = helidonTracer;
    }

    @Override
    public SpanBuilder spanBuilder(String spanName) {
        return WrappedSpanBuilder.create(helidonTracer, spanName);
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        if (c.isInstance(helidonTracer)) {
            return c.cast(helidonTracer);
        }
        R otelTracer = helidonTracer.unwrap(c);
        if (c.isInstance(otelTracer)) {
            return c.cast(otelTracer);
        }
        throw new IllegalArgumentException("Cannot provide an instance of " + c.getName()
                                                   + "; the wrapped telemetry tracer has type "
                                                   + otelTracer.getClass().getName()
                                                   + " and the wrapped Helidon tracer has type "
                                                   + helidonTracer.getClass().getName());
    }
}
