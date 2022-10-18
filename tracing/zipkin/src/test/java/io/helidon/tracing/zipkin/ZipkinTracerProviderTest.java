/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.tracing.zipkin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.config.testing.OptionalMatcher;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.TracerBuilder;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link ZipkinTracerProvider}.
 */
class ZipkinTracerProviderTest {
    @Test
    void testService() {
        TracerBuilder<?> builder = TracerBuilder.create("myService");
        // try to unwrap
        ZipkinTracerBuilder unwrap = builder.unwrap(ZipkinTracerBuilder.class);
        assertThat(unwrap, instanceOf(ZipkinTracerBuilder.class));
    }

    @Test
    void testContextInjection() {
        MockTracer mt = new MockTracer();
        MockSpan parent = mt.buildSpan("parentOperation").start();
        MockSpan span = mt.buildSpan("anOperation")
                .asChildOf(parent)
                .start();

        String parentSpanId = "51b3b1a413dce011";
        String spanId = "521c61ede905945f";
        String traceId = "0000816c055dc421";

        ZipkinTracerProvider provider = new ZipkinTracerProvider();
        Map<String, List<String>> inboundHeaders = Map.of(
                ZipkinTracerProvider.X_OT_SPAN_CONTEXT, List.of("0000816c055dc421;0000816c055dc421;0000000000000000;sr"),
                ZipkinTracerProvider.X_B3_PARENT_SPAN_ID, List.of(parentSpanId),
                ZipkinTracerProvider.X_B3_SPAN_ID, List.of(spanId),
                ZipkinTracerProvider.X_B3_TRACE_ID, List.of(traceId)
        );
        Map<String, List<String>> outboundHeaders = new HashMap<>();
        HeaderConsumer headerConsumer = HeaderConsumer.create(outboundHeaders);
        provider.updateOutboundHeaders(mt,
                                       span.context(),
                                       HeaderProvider.create(inboundHeaders),
                                       headerConsumer);

        // all should be propagated, X_OT should be fixed
        assertThat("Fixed-up X_OT header",
                   headerConsumer.get(ZipkinTracerProvider.X_OT_SPAN_CONTEXT),
                   OptionalMatcher.value(is("0000816c055dc421;521c61ede905945f;51b3b1a413dce011;sr")));

        assertThat("Parent span ID header",
                   headerConsumer.get(ZipkinTracerProvider.X_B3_PARENT_SPAN_ID),
                   OptionalMatcher.value(is(parentSpanId)));

        assertThat("Span ID header",
                   headerConsumer.get(ZipkinTracerProvider.X_B3_SPAN_ID),
                   OptionalMatcher.value(is(spanId)));

        assertThat("Trace ID header",
                   headerConsumer.get(ZipkinTracerProvider.X_B3_TRACE_ID),
        OptionalMatcher.value(is(traceId)));
    }

    @Test
    void testContextInjectionPreserveHeaders() {
        MockTracer mt = new MockTracer();
        MockSpan parent = mt.buildSpan("parentOperation").start();
        MockSpan span = mt.buildSpan("anOperation")
                .asChildOf(parent)
                .start();

        String parentSpanId = "51b3b1a413dce011";
        String spanId = "521c61ede905945f";
        String traceId = "0000816c055dc421";

        ZipkinTracerProvider provider = new ZipkinTracerProvider();
        Map<String, List<String>> outboundHeaders = new HashMap<>();
        outboundHeaders.put(
                ZipkinTracerProvider.X_OT_SPAN_CONTEXT, List.of("0000816c055dc421;0000816c055dc421;0000000000000000;sr"));
        outboundHeaders.put(ZipkinTracerProvider.X_B3_PARENT_SPAN_ID, List.of(parentSpanId));
        outboundHeaders.put(ZipkinTracerProvider.X_B3_SPAN_ID, List.of(spanId));
        outboundHeaders.put(ZipkinTracerProvider.X_B3_TRACE_ID, List.of(traceId));

        Map<String, List<String>> inboundHeaders = Map.of(
                ZipkinTracerProvider.X_OT_SPAN_CONTEXT, List.of("14"),
                ZipkinTracerProvider.X_B3_PARENT_SPAN_ID, List.of("15"),
                ZipkinTracerProvider.X_B3_SPAN_ID, List.of("16"),
                ZipkinTracerProvider.X_B3_TRACE_ID, List.of("17")
        );

        HeaderConsumer headerConsumer = HeaderConsumer.create(outboundHeaders);
        provider.updateOutboundHeaders(mt,
                                       span.context(),
                                       HeaderProvider.create(inboundHeaders),
                                       headerConsumer);

        // all should be propagated, X_OT should be fixed
        assertThat("Fixed-up X_OT header",
                   headerConsumer.get(ZipkinTracerProvider.X_OT_SPAN_CONTEXT),
                OptionalMatcher.value(is("0000816c055dc421;521c61ede905945f;51b3b1a413dce011;sr")));

        assertThat("Parent span ID header",
                   headerConsumer.get(ZipkinTracerProvider.X_B3_PARENT_SPAN_ID),
                   OptionalMatcher.value(is(parentSpanId)));

        assertThat("Span ID header",
                   headerConsumer.get(ZipkinTracerProvider.X_B3_SPAN_ID),
                   OptionalMatcher.value(is(spanId)));

        assertThat("Trace ID header",
                   headerConsumer.get(ZipkinTracerProvider.X_B3_TRACE_ID),
                   OptionalMatcher.value(is(traceId)));
    }
}
