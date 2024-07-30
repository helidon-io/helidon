/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tracing.providers.opentelemetry;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.tracing.Span;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;

import io.opentelemetry.sdk.trace.ReadableSpan;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;

class TestSpanListenerUnwrap {

    @Test
    void testSpanListenerUnwrap() {
        // Acts as if we were trying to map span information to JFR events.
        Tracer tracer = TracerBuilder.create("OTelTracer").build();

        AtomicReference<UnwrapResults> unwrapResults = new AtomicReference<>();

        SpanListener l1 = new SpanListener() {
            @Override
            public void started(Span span) throws SpanListener.ForbiddenOperationException {
                io.opentelemetry.api.trace.Span otelSpan = span.unwrap(io.opentelemetry.api.trace.Span.class);
                ReadableSpan readableSpan = (ReadableSpan) otelSpan;
                unwrapResults.set(UnwrapResults.create(readableSpan));
            }
        };

        tracer.register(l1);

        Span.Builder<?> spanBuilder = tracer.spanBuilder("myOperation");
        spanBuilder.tag("tag1", 3L);
        Span span = spanBuilder.start();
        try {
            assertThat("Span name", unwrapResults.get().spanName, is("myOperation"));
            assertThat("Tracer ID", unwrapResults.get().traceId, is(span.context().traceId()));
            assertThat("Span ID", unwrapResults.get().spanId, is(span.context().spanId()));
            assertThat("Tags", unwrapResults.get().tags, hasEntry("tag1", 3L));
        } finally {
            span.end();
        }
    }

    private record UnwrapResults(String traceId, String spanId, String spanName, Map<String, Object> tags) {

        private static UnwrapResults create(ReadableSpan readableSpan) {
            Map<String, Object> tags = new HashMap<>();
            readableSpan.toSpanData().getAttributes().forEach((attrKey, value) -> tags.put(attrKey.getKey(), value));
            return new UnwrapResults(readableSpan.getSpanContext().getTraceId(),
                              readableSpan.getSpanContext().getSpanId(),
                              readableSpan.getName(),
                              tags);
        }
    }
}
