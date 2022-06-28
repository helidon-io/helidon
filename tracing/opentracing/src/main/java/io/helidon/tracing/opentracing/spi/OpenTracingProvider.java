package io.helidon.tracing.opentracing.spi;

import io.helidon.tracing.HeaderConsumer;
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
     * The outboundHeaders already contain injected from tracer via
     * {@link io.opentracing.Tracer#inject(io.opentracing.SpanContext, io.opentracing.propagation.Format, Object)}.
     * This is to enable fine grained tuning of propagated headers for each implementation.
     *
     * @param tracer          Tracer used
     * @param currentSpan     Context of current span
     * @param outboundHeaders Tracing headers map as configured by the tracer
     * @param inboundHeaders  Existing inbound headers (may be empty if not within a scope of a request)
     * @return new map of outbound headers, defaults to tracing headers
     */
    default void updateOutboundHeaders(Tracer tracer,
                                       SpanContext currentSpan,
                                       HeaderProvider inboundHeaders,
                                       HeaderConsumer outboundHeaders) {

    }
}
