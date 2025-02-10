/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.context.Contexts;
import io.helidon.config.mp.MpConfig;
import io.helidon.microprofile.telemetry.spi.HelidonTelemetryContainerFilterHelper;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
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
    private static final String HTTP_ROUTE = "http.route";

    private static final String SPAN_NAME_FULL_URL = "telemetry.span.full.url";

    private static final String HELPER_START_SPAN_PROPERTY = HelidonTelemetryContainerFilterHelper.class + ".startSpan";

    @Deprecated(forRemoval = true, since = "4.1")
    static final String SPAN_NAME_INCLUDES_METHOD = "telemetry.span.name-includes-method";

    private static boolean spanNameFullUrl = false;
    private static AtomicBoolean spanNameWarningLogged = new AtomicBoolean();

    private final io.helidon.tracing.Tracer helidonTracer;
    private final boolean isAgentPresent;

    /*
     MP Telemetry 1.1 adopts OpenTelemetry 1.29 semantic conventions which require the route to be in the REST span name.
     Because Helidon adopts MP Telemetry 1.1 in a dot release (4.1), this would be a backward-incompatible change. This setting,
     controllable via config, defaults to the older behavior that is backward-compatible with Helidon 4.0.x but allows users to
     select the newer, spec-compliant behavior that is backward-incompatible with Helidon 4.0.x. The default is to use the
     old behavior.
     */
    private final boolean restSpanNameIncludesMethod;

    private final List<HelidonTelemetryContainerFilterHelper> helpers;

    @jakarta.ws.rs.core.Context
    private ResourceInfo resourceInfo;

    @Inject
    HelidonTelemetryContainerFilter(io.helidon.tracing.Tracer helidonTracer,
                                    org.eclipse.microprofile.config.Config mpConfig,
                                    Instance<HelidonTelemetryContainerFilterHelper> helpersInstance) {
        this.helidonTracer = helidonTracer;
        isAgentPresent = HelidonOpenTelemetry.AgentDetector.isAgentPresent(MpConfig.toHelidonConfig(mpConfig));

        // @Deprecated(forRemoval = true) In 5.x remove the following.
        mpConfig.getOptionalValue(SPAN_NAME_FULL_URL, Boolean.class).ifPresent(e -> spanNameFullUrl = e);
        Optional<Boolean> includeMethodConfig = mpConfig.getOptionalValue(SPAN_NAME_INCLUDES_METHOD, Boolean.class);
        restSpanNameIncludesMethod = includeMethodConfig.orElse(false);
        if (!restSpanNameIncludesMethod && !spanNameWarningLogged.get()) {
            spanNameWarningLogged.set(true);
            LOGGER.log(System.Logger.Level.WARNING,
                       String.format("""
                               Current OpenTelemetry semantic conventions include the HTTP method as part of REST span
                               names. Your configuration does not set %s to true, so your service uses the legacy span name
                               format which excludes the HTTP method. This feature is deprecated and marked for removal in a
                               future major release of Helidon. Consider adding a setting of %1$s to 'true' in your
                               configuration to migrate to the current conventions.""",
                               SPAN_NAME_INCLUDES_METHOD));
        }
        // end of code to remove in 5.x.

        helpers = helpersInstance.stream().toList();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {

        if (isAgentPresent) {
            return;
        }

        boolean startSpan = helpers.stream().allMatch(h -> h.shouldStartSpan(requestContext));
        requestContext.setProperty(HELPER_START_SPAN_PROPERTY, startSpan);
        if (!startSpan) {
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE,
                           "Container filter helper(s) voted to not start a span for " + requestContext);
            }
            return;
        }

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Starting Span in a Container Request");
        }

        //Start new span for container request.
        String route = route(requestContext);
        Optional<SpanContext> extractedSpanContext =
                helidonTracer.extract(new RequestContextHeaderProvider(requestContext.getHeaders()));
        Span helidonSpan = helidonTracer.spanBuilder(spanName(requestContext, route))
                .kind(Span.Kind.SERVER)
                .tag(HTTP_METHOD, requestContext.getMethod())
                .tag(HTTP_SCHEME, requestContext.getUriInfo().getRequestUri().getScheme())
                .tag(HTTP_TARGET, resolveTarget(requestContext))
                .tag(HTTP_ROUTE, route)
                .tag(SemanticAttributes.NET_HOST_NAME.getKey(), requestContext.getUriInfo().getBaseUri().getHost())
                .tag(SemanticAttributes.NET_HOST_PORT.getKey(), requestContext.getUriInfo().getBaseUri().getPort())
                .update(builder -> extractedSpanContext.ifPresent(builder::parent))
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

        Boolean startSpanObj = (Boolean) request.getProperty(HELPER_START_SPAN_PROPERTY);
        if (startSpanObj != null && !startSpanObj) {
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

            // OpenTelemetry semantic conventions dictate what the span status should be.
            // https://opentelemetry.io/docs/specs/semconv/http/http-spans/#status
            if (response.getStatusInfo().getFamily().compareTo(Response.Status.Family.SERVER_ERROR) == 0) {
                span.status(Span.Status.ERROR);
            }
            span.end();


        } finally {
            request.removeProperty(SPAN);
            request.removeProperty(SPAN_SCOPE);
        }
    }

//    private static List<HelidonTelemetryContainerFilterHelper> helpers() {
//        return HelidonServiceLoader.create(ServiceLoader.load(HelidonTelemetryContainerFilterHelper.class)).asList();
//    }

    private String spanName(ContainerRequestContext requestContext, String route) {
        // @Deprecated(forRemoval = true) In 5.x remove the option of excluding the HTTP method from the REST span name.
        // Starting in 5.x this method should be:
        // return requestContext.getMethod() + " " + (
        //          spanNameFullUrl
        //              ? requestContext.getUriInfo().getAbsolutePath().toString()
        //              : route);
        //
        // According to recent OpenTelemetry semantic conventions for spans, the span name for a REST endpoint should be
        //
        // http-method-name low-cardinality-path
        //
        // where a low-cardinality path would be, for example /greet/{name} rather than /greet/Joe, /greet/Dmitry, etc.
        // But the semantic conventions in place with MicroProfile Telemetry 1.0 did not include the method name in the span name.
        // Users can control the span name either by requesting the full URL be used or by requesting that the method NOT
        // be included.
        return (restSpanNameIncludesMethod ? requestContext.getMethod() + " " : "") + (
                spanNameFullUrl
                        ? requestContext.getUriInfo().getAbsolutePath().toString()
                        : route);
    }

    private String route(ContainerRequestContext requestContext) {
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
                    String[] valueAndMetadata = split[1].split(";");
                    String value = valueAndMetadata.length > 0 ? valueAndMetadata[0] : "";
                    String metadata = valueAndMetadata.length > 1 ? valueAndMetadata[1] : "";
                    Baggage.builder()
                            .put(split[0], value, BaggageEntryMetadata.create(metadata))
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
