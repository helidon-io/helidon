/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.telemetry;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.context.Contexts;
import io.helidon.config.mp.MpConfig;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ext.Provider;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.Resource;

import static io.helidon.microprofile.telemetry.HelidonTelemetryConstants.HTTP_METHOD;
import static io.helidon.microprofile.telemetry.HelidonTelemetryConstants.HTTP_SCHEME;
import static io.helidon.microprofile.telemetry.HelidonTelemetryConstants.HTTP_STATUS_CODE;

/**
 * Filter to process Server request and Server response. Starts a new {@link io.opentelemetry.api.trace.Span} on request and
 * ends it on a Response.
 */
@Provider
class HelidonTelemetryContainerFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final System.Logger LOGGER = System.getLogger(HelidonTelemetryContainerFilter.class.getName());
    private static final String SPAN = Span.class.getName();
    private static final String SPAN_SCOPE = Scope.class.getName();
    private static final String HTTP_TARGET = "http.target";

    private static final String SPAN_NAME_FULL_URL = "telemetry.span.full.url";

    private static boolean spanNameFullUrl = false;

    private final io.helidon.tracing.Tracer helidonTracer;
    private final boolean isAgentPresent;


    @jakarta.ws.rs.core.Context
    private ResourceInfo resourceInfo;

    @Inject
    HelidonTelemetryContainerFilter(io.helidon.tracing.Tracer helidonTracer,
                                    org.eclipse.microprofile.config.Config mpConfig) {
        this.helidonTracer = helidonTracer;
        isAgentPresent = HelidonOpenTelemetry.AgentDetector.isAgentPresent(MpConfig.toHelidonConfig(mpConfig));

        mpConfig.getOptionalValue(SPAN_NAME_FULL_URL, Boolean.class).ifPresent(e -> spanNameFullUrl = e);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {

        if (isAgentPresent) {
            return;
        }

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Starting Span in a Container Request");
        }

        //Start new span for container request.
        Span helidonSpan = helidonTracer.spanBuilder(spanName(requestContext))
                .kind(Span.Kind.SERVER)
                .tag(HTTP_METHOD, requestContext.getMethod())
                .tag(HTTP_SCHEME, requestContext.getUriInfo().getRequestUri().getScheme())
                .tag(HTTP_TARGET, resolveTarget(requestContext))
                .update(builder -> helidonTracer.extract(new RequestContextHeaderProvider(requestContext.getHeaders()))
                        .ifPresent(builder::parent))
                .start();

        Scope helidonScope = helidonSpan.activate();

        requestContext.setProperty(SPAN, helidonSpan);
        requestContext.setProperty(SPAN_SCOPE, helidonScope);

        // Handle OpenTelemetry Baggage from the current OpenTelemetry Context.
        handleBaggage(requestContext, Context.current());

    }

    @Override
    public void filter(final ContainerRequestContext request, final ContainerResponseContext response) {

        if (isAgentPresent) {
            return;
        }

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Closing Span in a Container Request");
        }

        try {
            Span span = (Span) request.getProperty(SPAN);
            if (span == null) {
                return;
            }
            Scope scope = (Scope) request.getProperty(SPAN_SCOPE);
            scope.close();

            span.tag(HTTP_STATUS_CODE, response.getStatus());
            span.end();


        } finally {
            request.removeProperty(SPAN);
            request.removeProperty(SPAN_SCOPE);
        }
    }

    private String spanName(ContainerRequestContext requestContext) {
        // According to recent OpenTelemetry semantic conventions for spans, the span name for a REST endpoint should be
        //
        // http-method-name low-cardinality-path
        //
        // where a low-cardinality path would be, for example /greet/{name} rather than /greet/Joe, /greet/Dmitry, etc.
        // But the version of semantic conventions in force when the MP Telemetry spec was published did not include the
        // http-method-name. So our code omits that for now to pass the MP Telemetry TCK.

        if (spanNameFullUrl) {
            return requestContext.getUriInfo().getAbsolutePath().toString();
        }
        ExtendedUriInfo extendedUriInfo = (ExtendedUriInfo) requestContext.getUriInfo();

        // Derive the original path (including path parameters) of the matched resource from the bottom up.
        Deque<String> derivedPath = new LinkedList<>();

        Resource resource = extendedUriInfo.getMatchedModelResource();
        while (resource != null) {
            String resourcePath = resource.getPath();
            if (resourcePath != null && !resourcePath.equals("/") && !resourcePath.isBlank()) {
                derivedPath.push(resourcePath);
                if (!resourcePath.startsWith("/")) {
                    derivedPath.push("/");
                }
            }
            resource = resource.getParent();
        }

        derivedPath.push(applicationPath());
        return String.join("", derivedPath);
    }

    private String applicationPath() {
        Application app = Contexts.context()
                .flatMap(it -> it.get(Application.class))
                .orElseThrow(() -> new IllegalStateException("Application missing from context"));

        if (app == null) {
            return "";
        }
        ApplicationPath applicationPath = getRealClass(app.getClass()).getAnnotation(ApplicationPath.class);
        return (applicationPath == null || applicationPath.value().equals("/")) ? "" : applicationPath.value();
    }

    // Resolve target string.
    private String resolveTarget(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getRequestUri().getPath();
        String rawQuery = requestContext.getUriInfo().getRequestUri().getRawQuery();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            return path + "?" + rawQuery;
        }
        return path;
    }

    // Extract OpenTelemetry Baggage from Request Headers.
    private void handleBaggage(ContainerRequestContext containerRequestContext, Context context) {
        List<String> baggageProperties = containerRequestContext.getHeaders().get("baggage");
        if (baggageProperties != null) {
            for (String b : baggageProperties) {
                String[] split = b.split("=");
                if (split.length == 2) {
                    Baggage.builder()
                            .put(split[0], split[1])
                            .build()
                            .storeInContext(context)
                            .makeCurrent();
                }
            }
        }
    }

    private static Class<?> getRealClass(Class<?> object) {
        Class<?> result = object;
        while (result.isSynthetic()) {
            result = result.getSuperclass();
        }
        return result;
    }
}
