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
package io.helidon.tracing.providers.opentracing;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.spi.TracerProvider;

import io.opentracing.noop.NoopSpan;
import io.opentracing.util.GlobalTracer;

/**
 * {@link java.util.ServiceLoader} service implementation of {@link io.helidon.tracing.spi.TracerProvider} for Open Tracing
 * tracers.
 * <p>
 *     When dealing with the global tracer, manage both the Helidon one and also the OpenTracing one in concert, whether
 *     defaulting them or assigning them via {@link #global(io.helidon.tracing.Tracer)}.
 * </p>
 */
@Weight(Weighted.DEFAULT_WEIGHT - 50) // low weight, so it is easy to override
public class OpenTracingTracerProvider implements TracerProvider {

    private static final LazyValue<List<SpanListener>> SPAN_LISTENERS =
            LazyValue.create(() -> HelidonServiceLoader.create(ServiceLoader.load(SpanListener.class)).asList());

    private LazyValue<Tracer> globalHelidonTracer = LazyValue.create(() -> {
        Config tracingConfig = GlobalConfig.config().get("tracing");

        // The service name is required, so assign a default one that might be overridden by config.
        io.opentracing.Tracer openTracingTracer = OpenTracingTracerBuilder.create("helidon-open-tracing-service")
                .config(tracingConfig)
                .build();
        GlobalTracer.registerIfAbsent(openTracingTracer);
        return OpenTracingTracer.create(GlobalTracer.get());
    });

    @Override
    public TracerBuilder<?> createBuilder() {
        return OpenTracingTracer.builder();
    }

    @Override
    public Tracer global() {
        return globalHelidonTracer.get();
    }

    @Override
    public void global(Tracer tracer) {
        if (tracer instanceof OpenTracingTracer opt) {
            GlobalTracer.registerIfAbsent(() -> {
                io.opentracing.Tracer openTracingTracer = opt.openTracing();
                globalHelidonTracer = LazyValue.create(OpenTracingTracer.create(openTracingTracer));
                return openTracingTracer;
            });
        }
    }

    @Override
    public Optional<Span> currentSpan() {
        io.opentracing.Tracer tracer = GlobalTracer.get();
        return Optional.ofNullable(tracer.activeSpan())
                .flatMap(it -> it instanceof NoopSpan ? Optional.empty() : Optional.of(it))
                .map(it -> new OpenTracingSpan(tracer, it,
                                               SPAN_LISTENERS.get()));
    }

    @Override
    public boolean available() {
        return OpenTracingProviderHelper.available();
    }

}
