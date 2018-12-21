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
package io.helidon.tracing.jersey;

import java.io.IOException;

import javax.inject.Provider;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Context;

import io.helidon.common.CollectionsHelper;
import io.helidon.tracing.jersey.client.internal.TracingContext;
import io.helidon.webserver.ServerRequest;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

/**
 * Tracing filter base.
 */
@ConstrainedTo(RuntimeType.SERVER)
@PreMatching
public abstract class AbstractTracingFilter implements ContainerRequestFilter, ContainerResponseFilter {
    /**
     * Name of the property the created span is stored in on request filter.
     */
    protected static final String SPAN_PROPERTY = AbstractTracingFilter.class.getName() + ".span";

    @Context
    private Provider<ServerRequest> request;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!tracingEnabled(requestContext)) {
            return;
        }

        ServerRequest serverRequest = this.request.get();

        Tracer tracer = serverRequest.webServer().configuration().tracer();
        SpanContext parentSpan = TracingContext.get()
                .map(TracingContext::parentSpan)
                .orElseGet(serverRequest::spanContext);

        Tracer.SpanBuilder spanBuilder = tracer
                .buildSpan(spanName(requestContext))
                .asChildOf(parentSpan)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.HTTP_METHOD.getKey(), requestContext.getMethod())
                .withTag(Tags.HTTP_URL.getKey(), requestContext.getUriInfo().getRequestUri().toString())
                .withTag(Tags.COMPONENT.getKey(), "jaxrs");

        configureSpan(spanBuilder);

        Span span = spanBuilder.start();

        requestContext.setProperty(SPAN_PROPERTY, span);

        // set the client tracing context
        TracingContext.compute(() -> TracingContext.create(tracer, requestContext.getHeaders()))
                .parentSpan(span.context());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Span span = (Span) requestContext.getProperty(SPAN_PROPERTY);
        if (span == null) {
            return; // unknown state
        }

        if (responseContext.getStatus() >= 500) {
            Tags.ERROR.set(span, true);
            span.log(CollectionsHelper.mapOf(
                    "event", "error",
                    "status", responseContext.getStatus()
            ));
        }

        span.finish();

        TracingContext.remove();
    }

    /**
     * Whether this tracing filter is enabled.
     *
     * @param context request context
     * @return true if filter should trigger and start a new span
     */
    protected abstract boolean tracingEnabled(ContainerRequestContext context);

    /**
     * Create name of the newly created span.
     *
     * @param context request context
     * @return name of the span to be created
     */
    protected abstract String spanName(ContainerRequestContext context);

    /**
     * Configure additional properties of a span that is named and has a parent.
     *
     * @param spanBuilder builder of the new span
     */
    protected abstract void configureSpan(Tracer.SpanBuilder spanBuilder);
}
