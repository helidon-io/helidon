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
package io.helidon.metrics.exemplartrace;

import java.util.Map;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.tracing.Baggage;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.WritableBaggage;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class MicrometerSpanContextSupplierProviderTest {

    @Test
    void testUsesPropagatedContextForSamplingAndMarking() {
        TestSpanContext helidonSpanContext = new TestSpanContext(false);
        TestSpan helidonSpan = new TestSpan(helidonSpanContext);
        Context context = Context.create();
        context.register(helidonSpanContext);
        context.register(helidonSpan);

        io.prometheus.metrics.tracer.common.SpanContext prometheusSpanContext =
                new MicrometerSpanContextSupplierProvider().get();

        Contexts.runInContext(context, () -> {
            assertThat(prometheusSpanContext.getCurrentTraceId(), is("trace-id"));
            assertThat(prometheusSpanContext.getCurrentSpanId(), is("span-id"));
            assertThat(prometheusSpanContext.isCurrentSpanSampled(), is(false));
            prometheusSpanContext.markCurrentSpanAsExemplar();
        });

        assertThat(helidonSpan.exemplar(), is(true));
    }

    private static class TestSpan implements Span {
        private final SpanContext spanContext;
        private boolean exemplar;

        private TestSpan(SpanContext spanContext) {
            this.spanContext = spanContext;
        }

        @Override
        public Span tag(String key, String value) {
            if (io.prometheus.metrics.tracer.common.SpanContext.EXEMPLAR_ATTRIBUTE_NAME.equals(key)
                    && io.prometheus.metrics.tracer.common.SpanContext.EXEMPLAR_ATTRIBUTE_VALUE.equals(value)) {
                exemplar = true;
            }
            return this;
        }

        @Override
        public Span tag(String key, Boolean value) {
            return this;
        }

        @Override
        public Span tag(String key, Number value) {
            return this;
        }

        @Override
        public void status(Status status) {
        }

        @Override
        public SpanContext context() {
            return spanContext;
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
            throw new UnsupportedOperationException();
        }

        @Override
        public WritableBaggage baggage() {
            throw new UnsupportedOperationException();
        }

        private boolean exemplar() {
            return exemplar;
        }
    }

    private record TestSpanContext(boolean sampled) implements SpanContext {

        @Override
        public String traceId() {
            return "trace-id";
        }

        @Override
        public String spanId() {
            return "span-id";
        }

        @Override
        public void asParent(Span.Builder<?> spanBuilder) {
        }

        @Override
        public Baggage baggage() {
            throw new UnsupportedOperationException();
        }
    }
}
