/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tracing.spi;

import java.util.List;
import java.util.Map;

import io.helidon.tracing.TracerBuilder;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

/**
 * Java service to integrate various distributed tracers.
 * The first tracer configured will be used.
 */
@FunctionalInterface
public interface TracerProvider {
    /**
     * Create a new builder for this tracer.
     *
     * @return a tracer builder
     */
    TracerBuilder<?> createBuilder();

    /**
     * Update headers for outbound requests.
     * The outboundHeaders already contain injected from tracer via {@link Tracer#inject(SpanContext, Format, Object)}.
     * This is to enable fine grained tuning of propagated headers for each implementation.
     *
     * @param currentSpan Current span covering the outbound call
     * @param tracer Tracer used
     * @param parentSpan Parent span context (may be null)
     * @param outboundHeaders Tracing headers map as configured by the tracer
     * @param inboundHeaders Existing inbound headers (may be empty if not within a scope of a request)
     *
     * @return new map of outbound headers, defaults to tracing headers
     */
    default Map<String, List<String>> updateOutboundHeaders(Span currentSpan,
                               Tracer tracer,
                               SpanContext parentSpan,
                               Map<String, List<String>> outboundHeaders,
                               Map<String, List<String>> inboundHeaders) {

        return outboundHeaders;
    }
}
