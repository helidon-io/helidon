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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Priority;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.Prioritized;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.spi.TracerProvider;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

import static io.helidon.common.CollectionsHelper.listOf;

/**
 * Zipkin java service.
 */
@Priority(Prioritized.DEFAULT_PRIORITY)
public class ZipkinTracerProvider implements TracerProvider {
    // original Zipkin headers (comes from old name of Zipkin - "BigBrotherBird", or "B3")
    static final String X_B3_TRACE_ID = "x-b3-traceid";
    static final String X_B3_SPAN_ID = "x-b3-spanid";
    static final String X_B3_PARENT_SPAN_ID = "x-b3-parentspanid";
    static final String X_B3_SAMPLED = "x-b3-sampled";
    static final String X_B3_FLAGS = "x-b3-flags";
    // Envoy header
    static final String X_OT_SPAN_CONTEXT = "x-ot-span-context";

    private static final Logger LOGGER = Logger.getLogger(ZipkinTracerProvider.class.getName());

    private static final List<String> TRACING_CONTEXT_PROPAGATION_HEADERS =
            listOf(X_OT_SPAN_CONTEXT, X_B3_TRACE_ID, X_B3_SPAN_ID, X_B3_PARENT_SPAN_ID, X_B3_SAMPLED, X_B3_FLAGS);

    @Override
    public TracerBuilder<?> createBuilder() {
        return ZipkinTracerBuilder.create();
    }

    @Override
    public Map<String, List<String>> updateOutboundHeaders(Span currentSpan,
                                                           Tracer tracer,
                                                           SpanContext parentSpan,
                                                           Map<String, List<String>> outboundHeaders,
                                                           Map<String, List<String>> inboundHeaders) {

        // copy all existing headers to the result
        Map<String, List<String>> result = new HashMap<>(outboundHeaders);

        if (inboundHeaders.isEmpty()) {
            // nothing to do, default to interface implementation
            return result;
        }

        TRACING_CONTEXT_PROPAGATION_HEADERS.forEach(header -> result.computeIfAbsent(header, inboundHeaders::get));

        fixXOtSpanContext(result);

        return result;
    }

    /**
     * Updates the {@link #X_OT_SPAN_CONTEXT} with the current tracing context. This header
     * is used by the tracing proxy (e.g., Envoy) to correlate the tracing between services.
     * <p>
     * The format of {@link #X_OT_SPAN_CONTEXT} is: <code>{@literal <}trace-id{@literal
     * >};{@literal <}span-id{@literal >};{@literal <}parent-span-id{@literal >};{@literal <}flags{@literal >}</code>.
     * <p>
     * The first three items need to be updated with the current tracing context which might
     * have changed between the incoming server call and the current outgoing client call.
     *
     * @param map the map with the tracing context where the {@link #X_OT_SPAN_CONTEXT} record
     *            gets updated based on the {@link #X_B3_TRACE_ID}, {@link #X_B3_SPAN_ID} and
     *            {@link #X_B3_PARENT_SPAN_ID}
     */
    private void fixXOtSpanContext(Map<String, List<String>> map) {
        if (!map.containsKey(X_OT_SPAN_CONTEXT)) {
            return;
        }

        List<String> values = map.get(X_OT_SPAN_CONTEXT);

        if (values.isEmpty()) {
            return;
        }

        String value = values.get(0);
        String[] split = value.split(";");

        substitute(map, split, X_B3_TRACE_ID, 0);
        substitute(map, split, X_B3_SPAN_ID, 1);
        substitute(map, split, X_B3_PARENT_SPAN_ID, 2);

        String result = String.join(";", split);
        LOGGER.fine(() -> X_OT_SPAN_CONTEXT + " header fixed: " + value + " -> " + result);
        map.put(X_OT_SPAN_CONTEXT, CollectionsHelper.listOf(result));
    }

    private static void substitute(Map<String, List<String>> map, String[] split, String key, int i) {
        if ((split.length > i) && map.containsKey(key)) {
            List<String> strings = map.get(key);
            if (!strings.isEmpty()) {
                split[i] = strings.get(0);
            }
        }
    }
}
