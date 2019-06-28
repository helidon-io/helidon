/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.tracing.Tag;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.spi.TracerProvider;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.util.GlobalTracer;

/**
 * TODO javadoc.
 */
public class TestTracerProvider implements TracerProvider {
    @Override
    public TracerBuilder<?> createBuilder() {
        return new TestTracerBuilder();
    }

    static class TestTracerBuilder implements TracerBuilder<TestTracerBuilder> {
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
            GlobalTracer.register(tracer);
            return tracer;
        }
    }

    static class TestTracer implements Tracer {
        @Override
        public ScopeManager scopeManager() {
            return null;
        }

        @Override
        public Span activeSpan() {
            return null;
        }

        @Override
        public SpanBuilder buildSpan(String operationName) {
            return new TestSpanBuilder(operationName);
        }

        static final String OPERATION_NAME_HEADER = "X-TEST-TRACER-OPERATION";

        @Override
        public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
            TestSpan span = ((TestSpanContext)spanContext).testSpan;

            TextMapInjectAdapter adapter = (TextMapInjectAdapter) carrier;
            adapter.put(OPERATION_NAME_HEADER, span.operationName);
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
        public Scope startActive(boolean finishSpanOnClose) {
             return new TestScope((TestSpan) start());
        }

        @Override
        public Span startManual() {
            return start();
        }

        @Override
        public Span start() {
            TestSpan result = new TestSpan(this);;

            if (null != parent) {
                ((TestSpanContext)parent).testSpan.addChild(result);
            }
            return result;

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
            Map<String, String> map = CollectionsHelper.mapOf();

            return map.entrySet();
        }
    }

    static class TestScope implements Scope {
        private final TestSpan testSpan;

        TestScope(TestSpan testSpan) {
            this.testSpan = testSpan;
        }

        @Override
        public void close() {
        }

        @Override
        public Span span() {
            return testSpan;
        }
    }
}
