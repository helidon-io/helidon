/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tracing;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.tracing.Tag;
import io.helidon.tracing.opentracing.OpenTracingTracerBuilder;
import io.helidon.tracing.opentracing.spi.OpenTracingProvider;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.util.GlobalTracer;

/**
 * Testing tracer provider.
 */
public class TestTracerProvider implements OpenTracingProvider {
    @Override
    public TestTracerBuilder createBuilder() {
        return new TestTracerBuilder();
    }



    static class TestTracerBuilder implements OpenTracingTracerBuilder<TestTracerBuilder> {
        @Override
        public TestTracerBuilder serviceName(String name) {
            return this;
        }

        @Override
        public TestTracerBuilder collectorProtocol(String protocol) {
            return this;
        }

        @Override
        public TestTracerBuilder collectorPort(int port) {
            return this;
        }

        @Override
        public TestTracerBuilder collectorHost(String host) {
            return this;
        }

        @Override
        public TestTracerBuilder collectorPath(String path) {
            return this;
        }

        @Override
        public TestTracerBuilder addTracerTag(String key, String value) {
            return this;
        }

        @Override
        public TestTracerBuilder addTracerTag(String key, Number value) {
            return this;
        }

        @Override
        public TestTracerBuilder addTracerTag(String key, boolean value) {
            return this;
        }

        @Override
        public TestTracerBuilder config(Config config) {
            return this;
        }

        @Override
        public TestTracerBuilder enabled(boolean enabled) {
            return this;
        }

        @Override
        public TestTracerBuilder registerGlobal(boolean global) {
            return this;
        }

        @Override
        public Tracer build() {
            Tracer tracer = new TestTracer();
            GlobalTracer.registerIfAbsent(tracer);
            return tracer;
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public <B> B unwrap(Class<B> builderClass) {
            if (builderClass.isAssignableFrom(getClass())) {
                return builderClass.cast(this);
            }
            throw new IllegalArgumentException("Not possible");
        }
    }

    static class TestTracer implements Tracer {
        @Override
        public ScopeManager scopeManager() {
            return new ScopeManager() {
                private Scope active;
                private Span activeSpan;

                @Override
                public Scope activate(Span span) {
                    active = new Scope() {
                        @Override
                        public void close() {
                            activeSpan = null;
                        }

                    };
                    activeSpan = span;
                    return active;
                }

                @Override
                public Span activeSpan() {
                    return activeSpan;
                }
            };
        }

        @Override
        public Span activeSpan() {
            return null;
        }

        @Override
        public Scope activateSpan(Span span) {
            return null;
        }

        @Override
        public void close() {

        }

        @Override
        public SpanBuilder buildSpan(String operationName) {
            return new TestSpanBuilder(operationName);
        }

        static final String OPERATION_NAME_HEADER = "X-TEST-TRACER-OPERATION";

        @Override
        public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
            TestSpan span = ((TestSpanContext) spanContext).testSpan;

            TextMap textMap = (TextMap) carrier;
            textMap.put(OPERATION_NAME_HEADER, span.operationName);
        }

        @Override
        public <C> SpanContext extract(Format<C> format, C carrier) {
            return null;
        }
    }

    static class TestSpanBuilder implements Tracer.SpanBuilder {
        private final String operationName;
        private final List<Tag<?>> tags = new LinkedList<>();

        private SpanContext parent;

        public TestSpanBuilder(String operationName) {
            this.operationName = operationName;
        }

        @Override
        public Tracer.SpanBuilder asChildOf(SpanContext parent) {
            this.parent = parent;
            return this;
        }

        @Override
        public Tracer.SpanBuilder asChildOf(Span parent) {
            this.parent = parent.context();
            return this;
        }

        @Override
        public Tracer.SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
            return this;
        }

        @Override
        public Tracer.SpanBuilder ignoreActiveSpan() {
            return this;
        }

        @Override
        public Tracer.SpanBuilder withTag(String key, String value) {
            tags.add(Tag.create(key, value));
            return this;
        }

        @Override
        public Tracer.SpanBuilder withTag(String key, boolean value) {
            tags.add(Tag.create(key, value));
            return this;
        }

        @Override
        public Tracer.SpanBuilder withTag(String key, Number value) {
            tags.add(Tag.create(key, value));
            return this;
        }

        @Override
        public Tracer.SpanBuilder withStartTimestamp(long microseconds) {
            return this;
        }

        @Override
        public Span start() {
            TestSpan result = new TestSpan(this);
            ;

            if (null != parent) {
                ((TestSpanContext) parent).testSpan.addChild(result);
            }
            return result;

        }

        @Override
        public <T> Tracer.SpanBuilder withTag(io.opentracing.tag.Tag<T> tag, T value) {
            return this;
        }
    }

    static final class TestSpan implements Span {
        private final LinkedList<Tag<?>> tags;
        private final String operationName;
        private final SpanContext parent;
        private final List<TestSpan> children = new LinkedList<>();

        private TestSpan(TestSpanBuilder builder) {
            this.tags = new LinkedList<>(builder.tags);
            this.operationName = builder.operationName;
            this.parent = builder.parent;
        }

        private void addChild(TestSpan testSpan) {
            this.children.add(testSpan);
        }

        @Override
        public SpanContext context() {
            return new TestSpanContext(this);
        }

        @Override
        public <T> Span setTag(io.opentracing.tag.Tag<T> tag, T value) {
            return this;
        }

        @Override
        public Span setTag(String key, String value) {
            return this;
        }

        @Override
        public Span setTag(String key, boolean value) {
            return this;
        }

        @Override
        public Span setTag(String key, Number value) {
            return this;
        }

        @Override
        public Span log(Map<String, ?> fields) {
            return this;
        }

        @Override
        public Span log(long timestampMicroseconds, Map<String, ?> fields) {
            return this;
        }

        @Override
        public Span log(String event) {
            return this;
        }

        @Override
        public Span log(long timestampMicroseconds, String event) {
            return this;
        }

        @Override
        public Span setBaggageItem(String key, String value) {
            return this;
        }

        @Override
        public String getBaggageItem(String key) {
            return null;
        }

        @Override
        public Span setOperationName(String operationName) {
            return this;
        }

        @Override
        public void finish() {

        }

        @Override
        public void finish(long finishMicros) {

        }
    }

    static class TestSpanContext implements SpanContext {
        private final TestSpan testSpan;

        TestSpanContext(TestSpan testSpan) {
            this.testSpan = testSpan;
        }

        @Override
        public Iterable<Map.Entry<String, String>> baggageItems() {
            Map<String, String> map = Map.of();

            return map.entrySet();
        }

        @Override
        public String toTraceId() {
            return testSpan.parent.toTraceId();
        }

        @Override
        public String toSpanId() {
            return testSpan.toString();
        }
    }
}
