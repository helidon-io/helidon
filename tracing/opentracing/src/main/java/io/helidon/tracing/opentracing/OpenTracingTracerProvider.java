package io.helidon.tracing.opentracing;

import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.spi.TracerProvider;

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
}
