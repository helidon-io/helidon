/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.ext.Provider;

import static io.helidon.microprofile.telemetry.HelidonTelemetryConstants.HTTP_METHOD;
import static io.helidon.microprofile.telemetry.HelidonTelemetryConstants.HTTP_SCHEME;
import static io.helidon.microprofile.telemetry.HelidonTelemetryConstants.HTTP_STATUS_CODE;


/**
 * Filter to process Client request and Client response. Starts a new {@link io.opentelemetry.api.trace.Span} on request and
 * ends it on a Response.
 */
@Provider
class HelidonTelemetryClientFilter implements ClientRequestFilter, ClientResponseFilter {
    private static final System.Logger LOGGER = System.getLogger(HelidonTelemetryContainerFilter.class.getName());
    private static final String HTTP_URL = "http.url";
    private static final String OTEL_CLIENT_SCOPE = "otel.span.client.scope";
    private static final String OTEL_CLIENT_SPAN = "otel.span.client.span";
    private static final String OTEL_CLIENT_CONTEXT = "otel.span.client.context";

    // Extract the current OpenTelemetry Context. Required for a parent/child relationship
    // to be correctly rebuilt in the next filter.
    private static final TextMapSetter<ClientRequestContext> CONTEXT_HEADER_EXTRACTOR =
            (carrier, key, value) -> carrier.getHeaders().add(key, value);

    private final Tracer tracer;
    private final OpenTelemetry openTelemetry;

    @Inject
    HelidonTelemetryClientFilter(Tracer tracer, OpenTelemetry openTelemetry) {
        this.tracer = tracer;
        this.openTelemetry = openTelemetry;
    }


    @Override
    public void filter(ClientRequestContext clientRequestContext) {

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Starting Span in a Client Request");
        }

        //Start new span for Client request.
        Span span = tracer.spanBuilder("HTTP " + clientRequestContext.getMethod())
                .setParent(Context.current())
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(HTTP_METHOD, clientRequestContext.getMethod())
                .setAttribute(HTTP_SCHEME, clientRequestContext.getUri().getScheme())
                .setAttribute(HTTP_URL, clientRequestContext.getUri().toString())
                .startSpan();

        Scope scope = span.makeCurrent();
        clientRequestContext.setProperty(OTEL_CLIENT_SCOPE, scope);
        clientRequestContext.setProperty(OTEL_CLIENT_SPAN, span);
        clientRequestContext.setProperty(OTEL_CLIENT_CONTEXT, Context.current());

        // Propagate OpenTelemetry context to next filter
        openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), clientRequestContext,
                CONTEXT_HEADER_EXTRACTOR);
    }


    @Override
    public void filter(ClientRequestContext clientRequestContext, ClientResponseContext clientResponseContext) {

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Closing Span in a Client Response");
        }

        // End span for Client request.
        Context context = (Context) clientRequestContext.getProperty(OTEL_CLIENT_CONTEXT);
        if (context == null) {
            return;
        }

        Span span = (Span) clientRequestContext.getProperty(OTEL_CLIENT_SPAN);
        span.setAttribute(HTTP_STATUS_CODE, clientResponseContext.getStatus());
        span.end();

        Scope scope = (Scope) clientRequestContext.getProperty(OTEL_CLIENT_SCOPE);
        scope.close();

        clientRequestContext.removeProperty(OTEL_CLIENT_SPAN);
        clientRequestContext.removeProperty(OTEL_CLIENT_SCOPE);
        clientRequestContext.removeProperty(OTEL_CLIENT_CONTEXT);
    }

}
