package io.helidon.tracing.opentracing;

import java.util.Optional;

import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.spi.TracerProvider;

import io.opentracing.noop.NoopSpan;
import io.opentracing.util.GlobalTracer;

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
}
