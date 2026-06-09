/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.compatibility.app;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.service.registry.Service;
import io.helidon.tracing.Baggage;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.WritableBaggage;

@Service.Singleton
public class TestTracerFactory implements Supplier<Tracer> {
    private final TestSpanExporter exporter = new TestSpanExporter();
    private final Tracer tracer = new RecordingTracer(exporter);

    @Override
    public Tracer get() {
        return tracer;
    }

    public TestSpanExporter exporter() {
        return exporter;
    }

    private static class RecordingTracer implements Tracer {
        private final TestSpanExporter exporter;

        RecordingTracer(TestSpanExporter exporter) {
            this.exporter = exporter;
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Span.Builder<?> spanBuilder(String name) {
            return new RecordingSpanBuilder(exporter, name);
        }

        @Override
        public Optional<SpanContext> extract(HeaderProvider headersProvider) {
            return Optional.empty();
        }

        @Override
        public void inject(SpanContext spanContext,
                           HeaderProvider inboundHeadersProvider,
                           HeaderConsumer outboundHeadersConsumer) {
        }

        @Override
        public Tracer register(SpanListener listener) {
            return this;
        }
    }

    private static class RecordingSpanBuilder implements Span.Builder<RecordingSpanBuilder> {
        private final TestSpanExporter exporter;
        private final String name;
        private final Map<String, Object> tags = new HashMap<>();
        private Span.Kind kind = Span.Kind.INTERNAL;

        RecordingSpanBuilder(TestSpanExporter exporter, String name) {
            this.exporter = exporter;
            this.name = name;
        }

        @Override
        public Span build() {
            return start();
        }

        @Override
        public RecordingSpanBuilder parent(SpanContext spanContext) {
            return this;
        }

        @Override
        public RecordingSpanBuilder kind(Span.Kind kind) {
            this.kind = kind;
            return this;
        }

        @Override
        public RecordingSpanBuilder tag(String key, String value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public RecordingSpanBuilder tag(String key, Boolean value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public RecordingSpanBuilder tag(String key, Number value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public Span start(Instant instant) {
            return new RecordingSpan(exporter, name, kind, tags);
        }
    }

    @SuppressWarnings("removal")
    private static class RecordingSpan implements Span {
        private static final WritableBaggage EMPTY_BAGGAGE = new EmptyBaggage();
        private final TestSpanExporter exporter;
        private final String name;
        private final Span.Kind kind;
        private final Map<String, Object> tags;

        RecordingSpan(TestSpanExporter exporter, String name, Span.Kind kind, Map<String, Object> tags) {
            this.exporter = exporter;
            this.name = name;
            this.kind = kind;
            this.tags = new HashMap<>(tags);
        }

        @Override
        public Span tag(String key, String value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public Span tag(String key, Boolean value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public Span tag(String key, Number value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public void status(Status status) {
        }

        @Override
        public SpanContext context() {
            return new RecordingSpanContext();
        }

        @Override
        public void addEvent(String name, Map<String, ?> attributes) {
        }

        @Override
        public void end() {
            exporter.record(name, kind, tags, null);
        }

        @Override
        public void end(Throwable t) {
            exporter.record(name, kind, tags, t);
        }

        @Override
        public Scope activate() {
            return new RecordingScope();
        }

        @Override
        public WritableBaggage baggage() {
            return EMPTY_BAGGAGE;
        }
    }

    private static class RecordingSpanContext implements SpanContext {
        @Override
        public String traceId() {
            return "compatibility";
        }

        @Override
        public String spanId() {
            return "compatibility";
        }

        @Override
        public void asParent(Span.Builder<?> spanBuilder) {
        }

        @Override
        public Baggage baggage() {
            return RecordingSpan.EMPTY_BAGGAGE;
        }
    }

    private static class RecordingScope implements Scope {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }

    private static class EmptyBaggage implements WritableBaggage {
        @Override
        public Optional<String> get(String key) {
            return Optional.empty();
        }

        @Override
        public Set<String> keys() {
            return Set.of();
        }

        @Override
        public boolean containsKey(String key) {
            return false;
        }

        @Override
        public WritableBaggage set(String key, String value) {
            return this;
        }
    }
}
