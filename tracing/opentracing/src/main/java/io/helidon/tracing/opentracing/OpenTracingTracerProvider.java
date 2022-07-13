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
package io.helidon.tracing.opentracing;

import java.util.Optional;

import io.helidon.common.Prioritized;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.spi.TracerProvider;

import io.opentracing.noop.NoopSpan;
import io.opentracing.util.GlobalTracer;
import jakarta.annotation.Priority;

/**
 * {@link java.util.ServiceLoader} service implementation of {@link io.helidon.tracing.spi.TracerProvider} for Open Tracing
 * tracers.
 */
@Priority(Prioritized.DEFAULT_PRIORITY + 2000)
public class OpenTracingTracerProvider implements TracerProvider {
    @Override
    public TracerBuilder<?> createBuilder() {
        return OpenTracingTracer.builder();
    }

    @Override
    public Tracer global() {
        return OpenTracingTracer.create(GlobalTracer.get());
    }

    @Override
    public void global(Tracer tracer) {
        if (tracer instanceof OpenTracingTracer opt) {
            GlobalTracer.registerIfAbsent(opt.openTracing());
        }
    }

    @Override
    public Optional<Span> currentSpan() {
        io.opentracing.Tracer tracer = GlobalTracer.get();
        return Optional.ofNullable(tracer.activeSpan())
                .flatMap(it -> it instanceof NoopSpan ? Optional.empty() : Optional.of(it))
                .map(it -> new OpenTracingSpan(tracer, it));
    }

    @Override
    public boolean available() {
        return OpenTracingProviderHelper.available();
    }
}
