package io.helidon.tracing.opentelemetry;

import io.helidon.tracing.SpanContext;

class OpenTelemetrySpanContext implements SpanContext {
    private final io.opentelemetry.api.trace.SpanContext delegate;

    OpenTelemetrySpanContext(io.opentelemetry.api.trace.SpanContext context) {
        this.delegate = context;
    }

    public io.opentelemetry.api.trace.SpanContext openTelemetry() {
        return delegate;
    }
}
