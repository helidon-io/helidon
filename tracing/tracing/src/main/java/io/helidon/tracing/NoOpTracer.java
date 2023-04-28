/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.tracing;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

class NoOpTracer implements Tracer {
    private static final NoOpTracer INSTANCE = new NoOpTracer();
    private static final SpanContext SPAN_CONTEXT = new SpanContext();
    private static final Builder BUILDER = new Builder();
    private static final Span SPAN = new Span();

    private static final Scope SCOPE = new Scope();

    private NoOpTracer() {
    }

    static Tracer instance() {
        return INSTANCE;
    }

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
        public io.helidon.tracing.Span tag(String key, String value) {
            return this;
        }

        @Override
        public io.helidon.tracing.Span tag(String key, Boolean value) {
            return this;
        }

        @Override
        public io.helidon.tracing.Span tag(String key, Number value) {
            return this;
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
        public Scope activate() {
            return SCOPE;
        }

        @Override
        public Span baggage(String key, String value) {
            return this;
        }

        @Override
        public Optional<String> baggage(String key) {
            return Optional.empty();
        }
    }

    private static class SpanContext implements io.helidon.tracing.SpanContext {
        @Override
        public String traceId() {
            return "no-op";
        }

        @Override
        public String spanId() {
            return "no-op";
        }

        @Override
        public void asParent(io.helidon.tracing.Span.Builder<?> spanBuilder) {
        }
    }

    private static class Scope implements io.helidon.tracing.Scope {
        @Override
        public void close() {
        }

        @Override
        public boolean isClosed() {
            return true;
        }
    }
}
