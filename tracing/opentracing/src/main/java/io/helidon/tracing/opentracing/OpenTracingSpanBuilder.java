package io.helidon.tracing.opentracing;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;

import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

class OpenTracingSpanBuilder implements Span.Builder<OpenTracingSpanBuilder> {
    private final Tracer.SpanBuilder delegate;
    private final Tracer tracer;

    OpenTracingSpanBuilder(Tracer tracer, Tracer.SpanBuilder delegate) {
        this.tracer = tracer;
        this.delegate = delegate;
    }

    @Override
    public Span build() {
        return new OpenTracingSpan(tracer, delegate.start());
    }

    @Override
    public OpenTracingSpanBuilder parent(SpanContext spanContext) {
        if (spanContext instanceof OpenTracingContext otc) {
            delegate.asChildOf(otc.openTracing());
        }
        return this;
    }

    @Override
    public OpenTracingSpanBuilder kind(Span.Kind kind) {
        String spanKind;

        switch(kind) {
        case SERVER -> spanKind = Tags.SPAN_KIND_SERVER;
        case CLIENT -> spanKind = Tags.SPAN_KIND_CLIENT;
        case PRODUCER -> spanKind = Tags.SPAN_KIND_PRODUCER;
        case CONSUMER -> spanKind = Tags.SPAN_KIND_CONSUMER;
        default -> spanKind = null;
        }

        if (spanKind != null) {
            delegate.withTag(Tags.SPAN_KIND.getKey(), spanKind);
        }
        return this;
    }

    @Override
    public OpenTracingSpanBuilder tag(String key, String value) {
        delegate.withTag(key, value);
        return this;
    }

    @Override
    public OpenTracingSpanBuilder tag(String key, Boolean value) {
        delegate.withTag(key, value);
        return this;
    }

    @Override
    public OpenTracingSpanBuilder tag(String key, Number value) {
        delegate.withTag(key, value);
        return this;
    }

    @Override
    public Span start(Instant instant) {
        long micro = TimeUnit.MILLISECONDS.toMicros(instant.toEpochMilli());
        return new OpenTracingSpan(tracer, delegate.withStartTimestamp(micro).start());
    }
}
