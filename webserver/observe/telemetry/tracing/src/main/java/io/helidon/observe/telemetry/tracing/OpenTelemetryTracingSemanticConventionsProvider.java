/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.observe.telemetry.tracing;

import java.util.Optional;

import io.helidon.common.uri.UriInfo;
import io.helidon.http.HeaderNames;
import io.helidon.service.registry.Service;
import io.helidon.tracing.Span;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.observe.tracing.TracingSemanticConventions;
import io.helidon.webserver.observe.tracing.spi.TracingSemanticConventionsProvider;

@Service.Singleton
class OpenTelemetryTracingSemanticConventionsProvider implements TracingSemanticConventionsProvider {

    @Override
    public TracingSemanticConventions create(SpanTracingConfig spanTracingConfig,
                                             String socketName,
                                             RoutingRequest request,
                                             RoutingResponse response) {
        return new SemanticConventions(spanTracingConfig, socketName, request, response);
    }

    /**
     * Supplies span information conforming to the OpenTelemetry semantic conventions for requests.
     * <p>
     * See <a href="https://github.com/open-telemetry/semantic-conventions/blob/v1.29.0/docs/http/http-spans.md">the
     * OpenTelemetry tracing semantic conventions.</a>
     */
    static class SemanticConventions implements TracingSemanticConventions {

        private static final String HTTP_REQUEST_METHOD = "http.request.method";
        private static final String URL_PATH = "url.path";
        private static final String URL_SCHEME = "url.scheme";
        private static final String ERROR_TYPE = "error.type";
        private static final String HTTP_RESPONSE_STATUS_CODE = "http.response.status_code";
        private static final String HTTP_ROUTE = "http.route";
        private static final String SERVER_PORT = "server.port";
        private static final String URL_QUERY = "url.query";
        private static final String SERVER_ADDRESS = "server.address";
        private static final String USER_AGENT_ORIGINAL = "user_agent.original";

        private static final String HELIDON_SOCKET = "helidon.socket";

        private final String socketName;
        private final RoutingRequest request;
        private final RoutingResponse response;

        private String methodText;
        private Optional<String> matchingPattern;

        SemanticConventions(SpanTracingConfig spanTracingConfig,
                            String socketName,
                            RoutingRequest request,
                            RoutingResponse response) {
            this.socketName = socketName;
            this.request = request;
            this.response = response;
        }

        @Override
        public String spanName() {
            methodText = request.prologue().method().text();
            matchingPattern = request.matchingPattern();
            return methodText + matchingPattern
                    .map(pattern -> " " + pattern)
                    .orElse("");
        }

        @Override
        public void beforeStart(Span.Builder<?> spanBuilder) {
            UriInfo uriInfo = request.requestedUri();

            spanBuilder.kind(Span.Kind.SERVER)
                    .tag(HTTP_REQUEST_METHOD, methodText)
                    .tag(URL_PATH, uriInfo.path().path())
                    .tag(URL_SCHEME, uriInfo.scheme())
                    .update(b -> matchingPattern.ifPresent(route -> b.tag(HTTP_ROUTE, route)))
                    .tag(SERVER_PORT, Integer.toString(request.localPeer().port()))
                    .update(b -> {
                        if (!request.query().isEmpty()) {
                            b.tag(URL_QUERY, request.query().value());
                        }
                    })
                    .tag(SERVER_ADDRESS, request.requestedUri().host())
                    .update(b -> request.headers().first(HeaderNames.USER_AGENT)
                            .ifPresent(agent -> b.tag(USER_AGENT_ORIGINAL, agent)));

            // Segregated this because it's not in the OpenTelemetry semantic conventions.
            spanBuilder.tag(HELIDON_SOCKET, socketName);
        }

        @Override
        public void beforeEnd(Span span) {
            span.tag(HTTP_RESPONSE_STATUS_CODE, Integer.toString(response.status().code()));
        }

        @Override
        public void beforeEnd(Span span, Exception e) {
            span.tag(ERROR_TYPE, e.getClass().getName());
        }
    }
}
