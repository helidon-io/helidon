/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tracing.jersey.client.internal;

import java.util.List;
import java.util.Map;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * Context for outbound tracing.
 * This is an internal API used from jersey integration
 * with Tracing (currently MP tracing), to share a ThreadLocal instance.
 * <p>
 * This class is mutable, to support chains of tracing filters that create
 * additional spans and that need to update the parent span of this context.
 */
public final class TracingContext {
    private SpanContext parentSpan;
    private final Tracer tracer;
    private final Map<String, List<String>> inboundHeaders;
    private final boolean traceClient;

    /**
     * Create a new tracing context with client tracing enabled.
     *
     * @param tracer tracer to use
     * @param inboundHeaders inbound header to be used for context propagation
     * @return a new tracing context (not associated with current thread)
     * @see #parentSpan(SpanContext)
     */
    public static TracingContext create(Tracer tracer,
                                        Map<String, List<String>> inboundHeaders) {
        return new TracingContext(tracer, inboundHeaders, true);
    }

    /**
     * Create a new tracing context.
     *
     * @param tracer tracer to use
     * @param inboundHeaders inbound header to be used for context propagation
     * @param clientEnabled whether client tracing should be enabled or not
     * @return a new tracing context (not associated with current thread)
     * @see #parentSpan(SpanContext)
     */
    public static TracingContext create(Tracer tracer,
                                        Map<String, List<String>> inboundHeaders,
                                        boolean clientEnabled) {
        return new TracingContext(tracer, inboundHeaders, clientEnabled);
    }

    private TracingContext(Tracer tracer,
                           Map<String, List<String>> inboundHeaders,
                           boolean traceClient) {
        this.tracer = tracer;
        this.inboundHeaders = inboundHeaders;
        this.traceClient = traceClient;
    }

    /**
     * Whether client (outbound) calls should be traced.
     *
     * @return {@code true} if tracing is enabled for client calls
     */
    public boolean traceClient() {
        return traceClient;
    }

    /**
     * Parent span to use for new spans created.
     *
     * @return span context to act as a parent for newly created spans
     */
    public SpanContext parentSpan() {
        return parentSpan;
    }

    /**
     * Tracer to be used when creating new spans.
     *
     * @return tracer to use
     */
    public Tracer tracer() {
        return tracer;
    }

    /**
     * Map of headers that were received by server for an inbound call,
     * may be used to propagate additional headers fro outbound request.
     *
     * @return map of inbound headers
     */
    public Map<String, List<String>> inboundHeaders() {
        return inboundHeaders;
    }

    /**
     * Update the parent span - this may be used by filters that inject
     * a new span between to original parent and the outbound call.
     * @param context new parent span context
     */
    public void parentSpan(SpanContext context) {
        this.parentSpan = context;
    }
}
