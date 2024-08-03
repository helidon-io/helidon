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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import static io.helidon.microprofile.telemetry.HelidonTelemetryConstants.HTTP_METHOD;
import static io.helidon.microprofile.telemetry.HelidonTelemetryConstants.HTTP_SCHEME;
import static io.helidon.microprofile.telemetry.HelidonTelemetryConstants.HTTP_STATUS_CODE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_PEER_NAME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_PEER_PORT;

/**
 * Filter to process Client request and Client response. Starts a new {@link io.opentelemetry.api.trace.Span} on request and
 * ends it on a Response.
 */
@Provider
class HelidonTelemetryClientFilter implements ClientRequestFilter, ClientResponseFilter {
    private static final System.Logger LOGGER = System.getLogger(HelidonTelemetryContainerFilter.class.getName());
    private static final String HTTP_URL = "http.url";
    private static final String SPAN_SCOPE = Scope.class.getName();
    private static final String SPAN = Span.class.getName();
    private static final Set<Response.Status.Family> ERROR_STATUS_FAMILIES = Set.of(
            Response.Status.Family.CLIENT_ERROR,
            Response.Status.Family.SERVER_ERROR);

    private final io.helidon.tracing.Tracer helidonTracer;

    @Inject
    HelidonTelemetryClientFilter(io.helidon.tracing.Tracer helidonTracer) {
        this.helidonTracer = helidonTracer;
    }


    @Override
    public void filter(ClientRequestContext clientRequestContext) {

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Starting Span in a Client Request");
        }

        //Start new span for Client request.
        // Use the Helidon wrappers so registered span listeners are notified.
        Span helidonSpan = helidonTracer.spanBuilder("HTTP " + clientRequestContext.getMethod())
                .kind(Span.Kind.CLIENT)
                .tag(HTTP_METHOD, clientRequestContext.getMethod())
                .tag(HTTP_SCHEME, clientRequestContext.getUri().getScheme())
                .tag(HTTP_URL, clientRequestContext.getUri().toString())
                .tag(NET_PEER_NAME.getKey(), clientRequestContext.getUri().getHost())
                .tag(NET_PEER_PORT.getKey(), clientRequestContext.getUri().getPort())
                .update(builder -> Span.current()
                        .map(Span::context)
                        .ifPresent(builder::parent))
                .start();

        Baggage.fromContext(Context.current())
                .forEach((key, baggageEntry) ->
                                 helidonSpan.baggage().set(key,
                                                           baggageEntry.getValue(),
                                                           baggageEntry.getMetadata().getValue()));
        Scope helidonScope = helidonSpan.activate();

        clientRequestContext.setProperty(SPAN_SCOPE, helidonScope);
        clientRequestContext.setProperty(SPAN, helidonSpan);

        helidonTracer.inject(helidonSpan.context(),
                             HeaderProvider.empty(),
                             new RequestContextHeaderInjector(clientRequestContext.getHeaders()));
    }


    @Override
    public void filter(ClientRequestContext clientRequestContext, ClientResponseContext clientResponseContext) {

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Closing Span in a Client Response");
        }

        Span span = (Span) clientRequestContext.getProperty(SPAN);
        if (span == null) {
            return;
        }
        Scope scope = (Scope) clientRequestContext.getProperty(SPAN_SCOPE);
        scope.close();

        span.tag(HTTP_STATUS_CODE, clientResponseContext.getStatus());

        // OpenTelemetry semantic conventions dictate what the span status should be.
        // https://opentelemetry.io/docs/specs/semconv/http/http-spans/#status
        if (ERROR_STATUS_FAMILIES.contains(clientResponseContext.getStatusInfo().getFamily())) {
            span.status(Span.Status.ERROR);
        }

        span.end();

        clientRequestContext.removeProperty(SPAN);
        clientRequestContext.removeProperty(SPAN_SCOPE);
    }

    private static class RequestContextHeaderInjector implements HeaderConsumer {

        private final MultivaluedMap<String, Object> requestHeaders;

        private RequestContextHeaderInjector(MultivaluedMap<String, Object> headers) {
            requestHeaders = headers;
        }

        @Override
        public void setIfAbsent(String key, String... values) {
            requestHeaders.computeIfAbsent(key, k -> List.of(values));
        }

        @Override
        public void set(String key, String... values) {
            requestHeaders.put(key, List.of(values));
        }

        @Override
        public Iterable<String> keys() {
            return requestHeaders.keySet();
        }

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable((String) requestHeaders.getFirst(key));
        }

        @Override
        public Iterable<String> getAll(String key) {
            return requestHeaders.get(key).stream().map(o -> (String) o).toList();
        }

        @Override
        public boolean contains(String key) {
            return requestHeaders.containsKey(key);
        }
    }

}
