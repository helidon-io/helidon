package io.helidon.tracing.opentracing;

import java.util.HashMap;
import java.util.Map;

import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;

import io.opentracing.tag.Tags;

class OpenTracingSpan implements Span {
    private final io.opentracing.Span delegate;
    private final OpenTracingContext context;

    OpenTracingSpan(io.opentracing.Span delegate) {
        this.delegate = delegate;
        this.context = new OpenTracingContext(delegate.context());
    }

    @Override
    public void tag(String key, String value) {
        delegate.setTag(key, value);
    }

    @Override
    public void tag(String key, Boolean value) {
        delegate.setTag(key, value);
    }

    @Override
    public void tag(String key, Number value) {
        delegate.setTag(key, value);
    }

    @Override
    public void status(Status status) {
        if (status == Status.ERROR) {
            Tags.ERROR.set(delegate, true);
        }
    }

    @Override
    public SpanContext context() {
        return context;
    }

    @Override
    public void addEvent(String name, Map<String, ?> attributes) {
        Map<String, Object> newMap = new HashMap<>(attributes);
        newMap.put("event", name);
        delegate.log(newMap);
    }

    @Override
    public void end() {
        delegate.finish();
    }

    @Override
    public void end(Throwable throwable) {
        status(Status.ERROR);
        delegate.log(Map.of("event", "error",
                            "error.kind", "Exception",
                            "error.object", throwable,
                            "message", throwable.getMessage()));
        delegate.finish();
    }
}
