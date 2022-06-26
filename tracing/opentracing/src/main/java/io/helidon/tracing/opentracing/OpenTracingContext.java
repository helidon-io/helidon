package io.helidon.tracing.opentracing;

import io.opentracing.SpanContext;

class OpenTracingContext implements io.helidon.tracing.SpanContext {
    private final SpanContext delegate;

    OpenTracingContext(SpanContext context) {
        this.delegate = context;
    }

    SpanContext openTracing() {
        return delegate;
    }
}
