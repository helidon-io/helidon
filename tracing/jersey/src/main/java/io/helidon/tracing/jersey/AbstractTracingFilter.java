/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tag;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfig;
import io.helidon.tracing.config.TracingConfigUtil;
import io.helidon.tracing.jersey.client.ClientTracingFilter;
import io.helidon.tracing.jersey.client.internal.TracingContext;

import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;

/**
 * Tracing filter base.
 */
@ConstrainedTo(RuntimeType.SERVER)
@PreMatching
public abstract class AbstractTracingFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final String SPAN_PROPERTY = AbstractTracingFilter.class.getName() + ".span";
    private static final String SPAN_SCOPE_PROPERTY = AbstractTracingFilter.class.getName() + ".spanScope";
    private static final String SPAN_FINISHED_PROPERTY = AbstractTracingFilter.class.getName() + ".spanFinished";
    private static final System.Logger LOGGER = System.getLogger(AbstractTracingFilter.class.getName());
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
            Tracer tracer = context.get(Tracer.class).orElseGet(Tracer::global);
            SpanContext parentSpan = context.get(TracingConfig.class, SpanContext.class)
                    .orElseGet(() -> context.get(SpanContext.class).orElse(null));

            Span.Builder spanBuilder = tracer.spanBuilder(spanName)
                    .kind(Span.Kind.SERVER)
                    .tag(Tag.HTTP_METHOD.create(requestContext.getMethod()))
                    .tag(Tag.HTTP_URL.create(url(requestContext)))
                    .tag(Tag.COMPONENT.create("jaxrs"));

            if (null != parentSpan) {
                spanBuilder.parent(parentSpan);
            }

            configureSpan(spanBuilder);

            Span span = spanBuilder.start();
            Scope spanScope = span.activate();

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

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Span span = (Span) requestContext.getProperty(SPAN_PROPERTY);
        if (null == span) {
            return; // not tracing
        }

        if (requestContext.getProperty(SPAN_FINISHED_PROPERTY) != null) {
            if (DOUBLE_FINISH_LOGGED.compareAndSet(false, true)) {
                LOGGER.log(Level.WARNING, "Response filter called twice. Most likely a response with streaming output was"
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
            span.status(Span.Status.ERROR);
            span.addEvent("error", Map.of());
            break;
        default:
            break;
        }

        Tag.HTTP_STATUS.create(responseContext.getStatus()).apply(span);

        requestContext.setProperty(SPAN_FINISHED_PROPERTY, true);
        span.end();

        // using the helidon context is not supported here, as we may be executing in a completion stage returned
        // from a third party component
        Scope scope = (Scope) requestContext.getProperty(SPAN_SCOPE_PROPERTY);
        if (scope != null) {
            try {
                scope.close();
            } catch (Exception ignored) {
                // ignored
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
    protected void configureSpan(Span.Builder<?> spanBuilder) {
    }
}
