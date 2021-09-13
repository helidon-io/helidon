/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfigUtil;
import io.helidon.tracing.jersey.client.ClientTracingFilter;
import io.helidon.tracing.jersey.client.internal.TracingContext;
import io.helidon.webserver.ServerRequest;

import io.opentracing.Scope;
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
    private static final String SPAN_PROPERTY = AbstractTracingFilter.class.getName() + ".span";
    private static final String SPAN_SCOPE_PROPERTY = AbstractTracingFilter.class.getName() + ".spanScope";
    private static final String SPAN_FINISHED_PROPERTY = AbstractTracingFilter.class.getName() + ".spanFinished";
    private static final Logger LOGGER = Logger.getLogger(AbstractTracingFilter.class.getName());
    private static final AtomicBoolean DOUBLE_FINISH_LOGGED = new AtomicBoolean();

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!tracingEnabled(requestContext)) {
            return;
        }

        Context context = Contexts.context().orElseThrow(() -> new IllegalStateException("Context must be available in Jersey"));

        String spanName = spanName(requestContext);
        SpanTracingConfig spanConfig = TracingConfigUtil.spanConfig(ClientTracingFilter.JAX_RS_TRACING_COMPONENT,
                                                                    spanName,
                                                                    context);

        if (spanConfig.enabled()) {
            spanName = spanConfig.newName().orElse(spanName);
            Tracer tracer = context.get(Tracer.class).orElseGet(GlobalTracer::get);
            SpanContext parentSpan = context.get(ServerRequest.class, SpanContext.class)
                    .orElseGet(() -> context.get(SpanContext.class).orElse(null));

            Tracer.SpanBuilder spanBuilder = tracer.buildSpan(spanName)
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                    .withTag(Tags.HTTP_METHOD.getKey(), requestContext.getMethod())
                    .withTag(Tags.HTTP_URL.getKey(), url(requestContext))
                    .withTag(Tags.COMPONENT.getKey(), "jaxrs");

            if (null != parentSpan) {
                spanBuilder.asChildOf(parentSpan);
            }

            configureSpan(spanBuilder);

            Span span = spanBuilder.start();
            Scope spanScope = tracer.scopeManager().activate(span);

            context.register(span);
            context.register(spanScope);
            context.register(ClientTracingFilter.class, span.context());
            requestContext.setProperty(SPAN_PROPERTY, span);
            requestContext.setProperty(SPAN_SCOPE_PROPERTY, spanScope);

            if (context.get(TracingContext.class).isEmpty()) {
                context.register(TracingContext.create(tracer, requestContext.getHeaders()));
            }

            context.get(TracingContext.class).ifPresent(tctx -> tctx.parentSpan(span.context()));
            if (null == parentSpan) {
                // register current span as the parent span for other (unless already exists)
                context.register(span.context());
            }
        }
    }

    /**
     * Resolves host name based on the "host" header. If this header is not set, then
     * {@link URI#toString()} is called.
     *
     * @param requestContext request context
     * @return resolved url
     */
    protected String url(ContainerRequestContext requestContext) {
        String hostHeader = requestContext.getHeaderString("host");
        URI requestUri = requestContext.getUriInfo().getRequestUri();

        if (null != hostHeader) {
            // let us use host header instead of local interface
            return requestUri.getScheme()
                    + "://"
                    + hostHeader
                    + requestUri.getPath();
        }

        return requestUri.toString();
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Span span = (Span) requestContext.getProperty(SPAN_PROPERTY);
        if (null == span) {
            return; // not tracing
        }

        if (requestContext.getProperty(SPAN_FINISHED_PROPERTY) != null) {
            if (DOUBLE_FINISH_LOGGED.compareAndSet(false, true)) {
                LOGGER.warning("Response filter called twice. Most likely a response with streaming output was"
                                       + " returned, where response had 200 status code, but streaming failed with another "
                                       + "error. Status: " + responseContext.getStatusInfo());
            }

            return; // tracing already finished
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
            span.log(Map.of("event", "error"));
            break;
        default:
            break;
        }

        Tags.HTTP_STATUS.set(span, responseContext.getStatus());

        requestContext.setProperty(SPAN_FINISHED_PROPERTY, true);
        span.finish();

        // using the helidon context is not supported here, as we may be executing in a completion stage future returned
        // from a third party component
        // Context context = Contexts.context().orElseThrow(() ->
        //      new IllegalStateException("Context must be available in Jersey"));
        // context.get(Scope.class).ifPresent(Scope::close);

        Scope scope = (Scope) requestContext.getProperty(SPAN_SCOPE_PROPERTY);
        if (scope != null) {
            scope.close();
        }
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
