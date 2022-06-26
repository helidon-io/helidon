package io.helidon.tracing.opentracing.spi;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.opentracing.OpenTracingTracerBuilder;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;

public interface OpenTracingProvider {
    /**
     * Create a new builder for this tracer.
     *
     * @return a tracer builder
     */
    OpenTracingTracerBuilder<?> createBuilder();

    /**
     * Update headers for outbound requests.
     * The outboundHeaders already contain injected from tracer via {@link io.opentracing.Tracer#inject(io.opentracing.SpanContext, io.opentracing.propagation.Format, Object)}.
     * This is to enable fine grained tuning of propagated headers for each implementation.
     *
     * @param tracer Tracer used
     * @param parentSpan Parent span context (may be null)
     * @param outboundHeaders Tracing headers map as configured by the tracer
     * @param inboundHeaders Existing inbound headers (may be empty if not within a scope of a request)
     *
     * @return new map of outbound headers, defaults to tracing headers
     */
    default Map<String, List<String>> updateOutboundHeaders(Tracer tracer,
                                                            SpanContext parentSpan,
                                                            HeaderProvider outboundHeaders,
                                                            BiConsumer<String, String> inboundHeaders) {

        return outboundHeaders;
    }
}
