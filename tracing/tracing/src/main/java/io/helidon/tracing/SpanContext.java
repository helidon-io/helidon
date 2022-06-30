package io.helidon.tracing;

public interface SpanContext {
    String traceId();
    String spanId();
}
