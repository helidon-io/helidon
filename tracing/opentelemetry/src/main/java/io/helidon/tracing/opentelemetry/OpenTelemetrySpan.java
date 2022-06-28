package io.helidon.tracing.opentelemetry;

import java.util.Map;

import io.helidon.tracing.Scope;
import io.helidon.tracing.SpanContext;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

class OpenTelemetrySpan implements io.helidon.tracing.Span {
    private final Span delegate;

    OpenTelemetrySpan(Span span) {
        this.delegate = span;
    }

    @Override
    public void tag(String key, String value) {
        delegate.setAttribute(key, value);
    }

    @Override
    public void tag(String key, Boolean value) {
        delegate.setAttribute(key, value);
    }

    @Override
    public void tag(String key, Number value) {
        if (value instanceof Double || value instanceof Float) {
            delegate.setAttribute(key, value.doubleValue());
        } else {
            delegate.setAttribute(key, value.longValue());
        }
    }

    @Override
    public void status(Status status) {
        switch(status) {
        case OK -> delegate.setStatus(StatusCode.OK);
        case ERROR -> delegate.setStatus(StatusCode.ERROR);
        default -> {}
        }
    }

    @Override
    public SpanContext context() {
        return new OpenTelemetrySpanContext(delegate.getSpanContext());
    }

    @Override
    public void addEvent(String name, Map<String, ?> attributes) {
        delegate.addEvent(name, toAttributes(attributes));
    }

    @Override
    public void end() {
        delegate.end();
    }

    @Override
    public Scope activate() {
        return new OpenTelemetryScope(delegate.makeCurrent());
    }

    @Override
    public void end(Throwable t) {
        delegate.recordException(t);
        delegate.setStatus(StatusCode.ERROR);
        delegate.end();
    }

    private Attributes toAttributes(Map<String, ?> attributes) {
        AttributesBuilder builder = Attributes.builder();
        attributes.forEach((key, value) -> {
            if (value instanceof Long l) {
                builder.put(key, l);
            } else if (value instanceof Boolean b) {
                builder.put(key, b);
            } else if (value instanceof Double d) {
                builder.put(key, d);
            } else {
                builder.put(key, String.valueOf(value));
            }
        });
        return builder.build();
    }
}
