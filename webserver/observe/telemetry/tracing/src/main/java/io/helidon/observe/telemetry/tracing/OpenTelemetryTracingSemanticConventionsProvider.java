/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.uri.UriInfo;
import io.helidon.http.HeaderNames;
import io.helidon.service.registry.Service;
import io.helidon.tracing.Span;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.observe.tracing.TracingSemanticConventions;
import io.helidon.webserver.observe.tracing.spi.TracingSemanticConventionsProvider;

import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;

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
     * See <a href="https://github.com/open-telemetry/semantic-conventions/blob/v1.37.0/docs/http/http-spans.md">the
     * OpenTelemetry tracing semantic conventions.</a>
     */
    static class SemanticConventions implements TracingSemanticConventions {

        private static final String HTTP_REQUEST_METHOD = HttpAttributes.HTTP_REQUEST_METHOD.getKey();
        private static final String URL_PATH = UrlAttributes.URL_PATH.getKey();
        private static final String URL_SCHEME = UrlAttributes.URL_SCHEME.getKey();
        private static final String ERROR_TYPE = ErrorAttributes.ERROR_TYPE.getKey();
        private static final String HTTP_RESPONSE_STATUS_CODE = HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey();
        private static final String HTTP_ROUTE = HttpAttributes.HTTP_ROUTE.getKey();
        private static final String SERVER_PORT = ServerAttributes.SERVER_PORT.getKey();
        private static final String URL_QUERY = UrlAttributes.URL_QUERY.getKey();
        private static final String SERVER_ADDRESS = ServerAttributes.SERVER_ADDRESS.getKey();
        private static final String USER_AGENT_ORIGINAL = UserAgentAttributes.USER_AGENT_ORIGINAL.getKey();

        // The following are for maintaining compatibility with MicroProfile Telemetry 1.x.
        @Deprecated(since = "4.4.0", forRemoval = true)
        private static final String NET_HOST_NAME = "net.host.name";

        @Deprecated(since = "4.4.0", forRemoval = true)
        private static final String NET_HOST_PORT = "net.host.port";
        // end of compatibility



        private static final String HELIDON_SOCKET = "helidon.socket";

        private final String socketName;
        private final RoutingRequest request;
        private final RoutingResponse response;

        private String methodText;

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
            return methodText;
        }

        @Override
        public void beforeStart(Span.Builder<?> spanBuilder) {
            UriInfo uriInfo = request.requestedUri();

            spanBuilder.kind(Span.Kind.SERVER)
                    .tag(HTTP_REQUEST_METHOD, methodText)
                    .tag(URL_PATH, uriInfo.path().path())
                    .tag(URL_SCHEME, uriInfo.scheme())
                    .tag(SERVER_PORT, Integer.toString(request.localPeer().port()))
                    .update(b -> {
                        if (!request.query().isEmpty()) {
                            b.tag(URL_QUERY, request.query().value());
                        }
                    })
                    .tag(SERVER_ADDRESS, request.requestedUri().host())
                    .tag(NET_HOST_NAME, request.requestedUri().host())
                    .tag(NET_HOST_PORT, request.localPeer().port())
                    .update(b -> request.headers().first(HeaderNames.USER_AGENT)
                            .ifPresent(agent -> b.tag(USER_AGENT_ORIGINAL, agent)));

            // Segregated this because it's not in the OpenTelemetry semantic conventions.
            spanBuilder.tag(HELIDON_SOCKET, socketName);
        }

        @Override
        public void beforeEnd(Span span) {
            commonBeforeEnd(span);
            span.tag(HTTP_RESPONSE_STATUS_CODE, Integer.toString(response.status().code()));
        }

        @Override
        public void beforeEnd(Span span, Exception e) {
            commonBeforeEnd(span);
            span.tag(ERROR_TYPE, e.getClass().getName());
        }

        private void commonBeforeEnd(Span span) {
            request.matchingPattern().ifPresent(mp -> span.tag(HTTP_ROUTE, mp));
        }
    }
}
