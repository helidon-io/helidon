/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.tracing.providers.opentracing.spi;

import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.providers.opentracing.OpenTracingTracerBuilder;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * Open Tracing SPI to include various tracers.
 */
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
     * @param inboundHeaders  Existing inbound headers (may be empty if not within a scope of a request)
     * @param outboundHeaders Tracing headers map as configured by the tracer
     */
    default void updateOutboundHeaders(Tracer tracer,
                                       SpanContext currentSpan,
                                       HeaderProvider inboundHeaders,
                                       HeaderConsumer outboundHeaders) {

    }
}
