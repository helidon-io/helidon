package io.helidon.tracing.opentracing;

import io.helidon.tracing.Span;

import io.opentracing.Tracer;

public final class OpenTracing {
    public static io.helidon.tracing.Tracer create(Tracer tracer) {
        return OpenTracingTracer.create(tracer);
    }
    public static Span create(Tracer tracer, io.opentracing.Span span) {
        return new OpenTracingSpan(tracer, span);
    }
}
