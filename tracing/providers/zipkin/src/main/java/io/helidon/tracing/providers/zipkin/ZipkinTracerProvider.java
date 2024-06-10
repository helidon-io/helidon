/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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
package io.helidon.tracing.providers.zipkin;

import java.lang.System.Logger.Level;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.providers.opentracing.spi.OpenTracingProvider;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * Zipkin java service.
 */
@Weight(Weighted.DEFAULT_WEIGHT)
public class ZipkinTracerProvider implements OpenTracingProvider {
    // original Zipkin headers (comes from old name of Zipkin - "BigBrotherBird", or "B3")
    static final String X_B3_TRACE_ID = "x-b3-traceid";
    static final String X_B3_SPAN_ID = "x-b3-spanid";
    static final String X_B3_PARENT_SPAN_ID = "x-b3-parentspanid";
    static final String X_B3_SAMPLED = "x-b3-sampled";
    static final String X_B3_FLAGS = "x-b3-flags";
    // Envoy header
    static final String X_OT_SPAN_CONTEXT = "x-ot-span-context";

    private static final System.Logger LOGGER = System.getLogger(ZipkinTracerProvider.class.getName());

    private static final List<String> TRACING_CONTEXT_PROPAGATION_HEADERS =
            List.of(X_OT_SPAN_CONTEXT, X_B3_TRACE_ID, X_B3_SPAN_ID, X_B3_PARENT_SPAN_ID, X_B3_SAMPLED, X_B3_FLAGS);

    @Override
    public ZipkinTracerBuilder createBuilder() {
        return ZipkinTracerBuilder.create();
    }

    @Override
    public void updateOutboundHeaders(Tracer tracer,
                                      SpanContext currentSpan,
                                      HeaderProvider inboundHeaders,
                                      HeaderConsumer outboundHeaders) {

        Iterator<String> inboundIterator = inboundHeaders.keys().iterator();

        if (!inboundIterator.hasNext()) {
            return;
        }

        TRACING_CONTEXT_PROPAGATION_HEADERS.forEach(header -> inboundHeaders.get(header)
                .ifPresent(it -> outboundHeaders.setIfAbsent(header, it)));

        fixXOtSpanContext(outboundHeaders);
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
     * @param headers outbound headers with the tracing context where the {@link #X_OT_SPAN_CONTEXT} record
     *            gets updated based on the {@link #X_B3_TRACE_ID}, {@link #X_B3_SPAN_ID} and
     *            {@link #X_B3_PARENT_SPAN_ID}
     */
    private void fixXOtSpanContext(HeaderConsumer headers) {
        Optional<String> ctx = headers.get(X_OT_SPAN_CONTEXT);

        if (ctx.isEmpty()) {
            return;
        }

        String value = ctx.get();
        String[] split = value.split(";");

        substitute(headers, split, X_B3_TRACE_ID, 0);
        substitute(headers, split, X_B3_SPAN_ID, 1);
        substitute(headers, split, X_B3_PARENT_SPAN_ID, 2);

        String result = String.join(";", split);
        LOGGER.log(Level.DEBUG, () -> X_OT_SPAN_CONTEXT + " header fixed: " + value + " -> " + result);
        headers.set(X_OT_SPAN_CONTEXT, result);
    }

    private static void substitute(HeaderConsumer headers, String[] split, String key, int i) {
        if ((split.length > i) && headers.contains(key)) {
            Iterator<String> strings = headers.getAll(key).iterator();
            if (strings.hasNext()) {
                split[i] = strings.next();
            }
        }
    }
}
