package io.helidon.tracing;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

class NoOpTracer implements Tracer {
    static final SpanContext SPAN_CONTEXT = new SpanContext();
    private static final Builder BUILDER = new Builder();
    private static final Span SPAN = new Span();

    private static final Scope SCOPE = new Scope();

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public Builder spanBuilder(String name) {
        return BUILDER;
    }

    @Override
    public Optional<io.helidon.tracing.SpanContext> extract(HeaderProvider headersProvider) {
        return Optional.empty();
    }

    @Override
    public void inject(io.helidon.tracing.SpanContext spanContext,
                       HeaderProvider inboundHeadersProvider,
                       HeaderConsumer outboundHeadersConsumer) {

    }

    private static class Builder implements Span.Builder<Builder> {
        @Override
        public Span build() {
            return SPAN;
        }

        @Override
        public Builder parent(io.helidon.tracing.SpanContext spanContext) {
            return this;
        }

        @Override
        public Builder kind(Span.Kind kind) {
            return this;
        }

        @Override
        public Builder tag(String key, String value) {
            return this;
        }

        @Override
        public Builder tag(String key, Boolean value) {
            return this;
        }

        @Override
        public Builder tag(String key, Number value) {
            return this;
        }

        @Override
        public Span start(Instant instant) {
            return SPAN;
        }
    }

    private static class Span implements io.helidon.tracing.Span {

        @Override
        public void tag(String key, String value) {

        }

        @Override
        public void tag(String key, Boolean value) {

        }

        @Override
        public void tag(String key, Number value) {

        }

        @Override
        public void status(Status status) {

        }

        @Override
        public SpanContext context() {
            return SPAN_CONTEXT;
        }

        @Override
        public void addEvent(String name, Map<String, ?> attributes) {
        }

        @Override
        public void end() {
        }

        @Override
        public void end(Throwable t) {
        }

        @Override
        public io.helidon.tracing.Scope activate() {
            return SCOPE;
        }
    }

    private static class SpanContext implements io.helidon.tracing.SpanContext {

    }

    private static class Scope implements io.helidon.tracing.Scope {
        @Override
        public void close() throws Exception {
        }
    }
}
