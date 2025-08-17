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

package io.helidon.webserver.observe.tracing;

import java.util.Map;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.uri.UriInfo;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tag;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.observe.tracing.spi.TracingSemanticConventionsProvider;

/**
 * Provider for Helidon's tracing semantic conventions.
 * <p>
 * Use a low weight to allow other implementations to have priority if they are present.
 */
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 50)
class HelidonTracingSemanticConventionsProvider implements TracingSemanticConventionsProvider {
    @Override
    public TracingSemanticConventions create(SpanTracingConfig spanTracingConfig,
                                             String socketName,
                                             RoutingRequest routingRequest,
                                             RoutingResponse routingResponse) {
        return HelidonTracingSemanticConventions.create(spanTracingConfig, socketName, routingRequest, routingResponse);
    }

    static class HelidonTracingSemanticConventions implements TracingSemanticConventions {

        static final String TRACING_SPAN_HTTP_REQUEST = "HTTP Request";

        private final SpanTracingConfig spanTracingConfig;
        private final String socketName;
        private final RoutingRequest routingRequest;
        private final RoutingResponse routingResponse;

        HelidonTracingSemanticConventions(SpanTracingConfig spanTracingConfig,
                                          String socketName,
                                          RoutingRequest routingRequest,
                                          RoutingResponse routingResponse) {
            this.spanTracingConfig = spanTracingConfig;
            this.socketName = socketName;
            this.routingRequest = routingRequest;
            this.routingResponse = routingResponse;
        }

        static HelidonTracingSemanticConventions create(SpanTracingConfig spanTracingConfig,
                                                        String socketName,
                                                        RoutingRequest routingRequest,
                                                        RoutingResponse routingResponse) {
            return new HelidonTracingSemanticConventions(spanTracingConfig, socketName, routingRequest, routingResponse);
        }

        @Override
        public String spanName() {
            HttpPrologue prologue = routingRequest.prologue();

            String spanName = spanTracingConfig.newName().orElse(TRACING_SPAN_HTTP_REQUEST);
            if (spanName.indexOf('%') > -1) {
                spanName = String.format(spanName,
                                         prologue.method().text(),
                                         routingRequest.path().rawPath(),
                                         routingRequest.query().rawValue());
            }
            return spanName;
        }

        @Override
        public void update(Span.Builder<?> spanBuilder) {
            spanBuilder
                    .kind(Span.Kind.SERVER)
                    .update(it -> {
                        if (!socketName.isBlank()) {
                            it.tag("helidon.socket", socketName);
                        }
                    });
        }

        @Override
        public void update(Span span) {
            updateMost(span);
        }

        @Override
        public void update(Span span, Exception e) {
            updateMost(span);
        }

        private void updateMost(Span span) {
            HttpPrologue prologue = routingRequest.prologue();
            span.tag(Tag.COMPONENT.create("helidon-webserver"));
            span.tag(Tag.HTTP_METHOD.create(prologue.method().text()));
            UriInfo uriInfo = routingRequest.requestedUri();
            span.tag(Tag.HTTP_URL.create(uriInfo.scheme() + "://" + uriInfo.authority() + uriInfo.path().path()));
            span.tag(Tag.HTTP_VERSION.create(prologue.protocolVersion()));

            Status status = routingResponse.status();
            span.tag(Tag.HTTP_STATUS.create(status.code()));

            if (status.code() >= 400) {
                span.status(Span.Status.ERROR);
                span.addEvent("error", Map.of("message", "Response HTTP status: " + status,
                                              "error.kind", status.code() < 500 ? "ClientError" : "ServerError"));
            } else {
                span.status(Span.Status.OK);
            }

        }
    }
}
