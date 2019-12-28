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

package io.helidon.tracing.zipkin;

import java.util.List;
import java.util.Map;

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
        assertThat(builder, instanceOf(ZipkinTracerBuilder.class));
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
        Map<String, List<String>> outboundHeaders = Map.of();
        outboundHeaders = provider.updateOutboundHeaders(span,
                                                         mt,
                                                         parent.context(),
                                                         outboundHeaders,
                                                         inboundHeaders);

        // all should be propagated, X_OT should be fixed
        List<String> values = outboundHeaders.get(ZipkinTracerProvider.X_OT_SPAN_CONTEXT);
        assertThat(values, is(List.of("0000816c055dc421;521c61ede905945f;51b3b1a413dce011;sr")));

        values = outboundHeaders.get(ZipkinTracerProvider.X_B3_PARENT_SPAN_ID);
        assertThat(values, is(List.of(parentSpanId)));

        values = outboundHeaders.get(ZipkinTracerProvider.X_B3_SPAN_ID);
        assertThat(values, is(List.of(spanId)));

        values = outboundHeaders.get(ZipkinTracerProvider.X_B3_TRACE_ID);
        assertThat(values, is(List.of(traceId)));
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
        Map<String, List<String>> outboundHeaders = Map.of(
                ZipkinTracerProvider.X_OT_SPAN_CONTEXT, List.of("0000816c055dc421;0000816c055dc421;0000000000000000;sr"),
                ZipkinTracerProvider.X_B3_PARENT_SPAN_ID, List.of(parentSpanId),
                ZipkinTracerProvider.X_B3_SPAN_ID, List.of(spanId),
                ZipkinTracerProvider.X_B3_TRACE_ID, List.of(traceId)
        );
        Map<String, List<String>> inboundHeaders = Map.of(
                ZipkinTracerProvider.X_OT_SPAN_CONTEXT, List.of("14"),
                ZipkinTracerProvider.X_B3_PARENT_SPAN_ID, List.of("15"),
                ZipkinTracerProvider.X_B3_SPAN_ID, List.of("16"),
                ZipkinTracerProvider.X_B3_TRACE_ID, List.of("17")
        );
        outboundHeaders = provider.updateOutboundHeaders(span,
                                                         mt,
                                                         parent.context(),
                                                         outboundHeaders,
                                                         inboundHeaders);

        // all should be propagated, X_OT should be fixed
        List<String> values = outboundHeaders.get(ZipkinTracerProvider.X_OT_SPAN_CONTEXT);
        assertThat(values, is(List.of("0000816c055dc421;521c61ede905945f;51b3b1a413dce011;sr")));

        values = outboundHeaders.get(ZipkinTracerProvider.X_B3_PARENT_SPAN_ID);
        assertThat(values, is(List.of(parentSpanId)));

        values = outboundHeaders.get(ZipkinTracerProvider.X_B3_SPAN_ID);
        assertThat(values, is(List.of(spanId)));

        values = outboundHeaders.get(ZipkinTracerProvider.X_B3_TRACE_ID);
        assertThat(values, is(List.of(traceId)));
    }
}