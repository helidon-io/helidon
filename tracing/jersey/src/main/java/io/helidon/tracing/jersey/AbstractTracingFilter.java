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
package io.helidon.tracing.jersey;

import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.tracing.config.TracedConfigUtil;
import io.helidon.tracing.config.TracedSpan;
import io.helidon.tracing.jersey.client.internal.TracingContext;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

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

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!tracingEnabled(requestContext)) {
            return;
        }

        Context context = Contexts.context().orElseThrow(() -> new IllegalStateException("Context must be available in Jersey"));

        String spanName = spanName(requestContext);
        TracedSpan spanConfig = TracedConfigUtil.spanConfig("jax-rs", spanName);

        if (spanConfig.enabled().orElse(true)) {
            spanName = spanConfig.newName().orElse(spanName);
            Tracer tracer = context.get(Tracer.class).orElseGet(GlobalTracer::get);
            SpanContext parentSpan = context.get(SpanContext.class).orElse(null);

            Tracer.SpanBuilder spanBuilder = tracer.buildSpan(spanName)
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                    .withTag(Tags.HTTP_METHOD.getKey(), requestContext.getMethod())
                    .withTag(Tags.HTTP_URL.getKey(), requestContext.getUriInfo().getRequestUri().toString())
                    .withTag(Tags.COMPONENT.getKey(), "jaxrs");

            if (null != parentSpan) {
                spanBuilder.asChildOf(parentSpan);
            }

            configureSpan(spanBuilder);

            Span span = spanBuilder.start();

            requestContext.setProperty(SPAN_PROPERTY, span);

            if (!context.get(TracingContext.class).isPresent()) {
                context.register(TracingContext.create(tracer, requestContext.getHeaders()));
            }

            context.get(TracingContext.class).ifPresent(tctx -> tctx.parentSpan(span.context()));
            if (null == parentSpan) {
                // register current span as the parent span for other (unless already exists)
                context.register(span.context());
            }
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Span span = (Span) requestContext.getProperty(SPAN_PROPERTY);
        if (span == null) {
            return; // not tracing
        }

        switch (responseContext.getStatusInfo().getFamily()) {
        case INFORMATIONAL:
        case SUCCESSFUL:
        case REDIRECTION:
        case OTHER:
            // do nothing for successful (and unknown) responses
            break;
        case CLIENT_ERROR:
        case SERVER_ERROR:
            Tags.ERROR.set(span, true);
            span.log(CollectionsHelper.mapOf("event", "error"));
            break;
        default:
            break;
        }

        Tags.HTTP_STATUS.set(span, responseContext.getStatus());

        span.finish();
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
