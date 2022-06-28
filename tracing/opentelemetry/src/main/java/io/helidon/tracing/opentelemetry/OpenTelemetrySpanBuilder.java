package io.helidon.tracing.opentelemetry;

import java.time.Instant;

import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;

class OpenTelemetrySpanBuilder implements Span.Builder<OpenTelemetrySpanBuilder> {
    private final SpanBuilder spanBuilder;

    OpenTelemetrySpanBuilder(SpanBuilder spanBuilder) {
        this.spanBuilder = spanBuilder;
    }

    @Override
    public Span build() {
        return start();
    }

    @Override
    public OpenTelemetrySpanBuilder parent(SpanContext spanContext) {
        spanBuilder.addLink(((OpenTelemetrySpanContext) spanContext).openTelemetry());
        return this;
    }

    @Override
    public OpenTelemetrySpanBuilder kind(Span.Kind kind) {
        switch (kind) {
        case SERVER -> spanBuilder.setSpanKind(SpanKind.SERVER);
        case CLIENT -> spanBuilder.setSpanKind(SpanKind.CLIENT);
        case PRODUCER -> spanBuilder.setSpanKind(SpanKind.PRODUCER);
        case CONSUMER -> spanBuilder.setSpanKind(SpanKind.CONSUMER);
        default -> spanBuilder.setSpanKind(SpanKind.INTERNAL);
        }

        return this;
    }

    @Override
    public OpenTelemetrySpanBuilder tag(String key, String value) {
        spanBuilder.setAttribute(key, value);
        return this;
    }

    @Override
    public OpenTelemetrySpanBuilder tag(String key, Boolean value) {
        spanBuilder.setAttribute(key, value);
        return this;
    }

    @Override
    public OpenTelemetrySpanBuilder tag(String key, Number value) {
        if (value instanceof Double || value instanceof Float) {
            spanBuilder.setAttribute(key, value.doubleValue());
        } else {
            spanBuilder.setAttribute(key, value.longValue());
        }

        return this;
    }

    @Override
    public Span start(Instant instant) {
        spanBuilder.setStartTimestamp(instant);
        io.opentelemetry.api.trace.Span span = spanBuilder.startSpan();
        return new OpenTelemetrySpan(span);
    }
}
