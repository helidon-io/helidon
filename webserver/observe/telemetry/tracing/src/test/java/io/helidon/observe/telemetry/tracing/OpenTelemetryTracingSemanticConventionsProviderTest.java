/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.observe.tracing.TracingSemanticConventions;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

class OpenTelemetryTracingSemanticConventionsProviderTest {

    @Test
    void beforeStartOmitsDeprecatedMicroProfileTelemetryHostTags() {
        var request = request();
        TracingSemanticConventions conventions = new OpenTelemetryTracingSemanticConventionsProvider()
                .create(SpanTracingConfig.ENABLED, "", request, null);
        var spanBuilder = new RecordingSpanBuilder();

        conventions.spanName();
        conventions.beforeStart(spanBuilder);

        assertThat("Span tags", spanBuilder.tags(), allOf(
                hasEntry("server.address", "helidon.example"),
                hasEntry("server.port", "8080"),
                not(hasKey("net.host.name")),
                not(hasKey("net.host.port"))));
    }

    private static RoutingRequest request() {
        var uriInfo = UriInfo.builder()
                .scheme("http")
                .host("helidon.example")
                .port(8080)
                .path("/greet")
                .query(UriQuery.empty())
                .build();
        var prologue = HttpPrologue.create("HTTP/1.1", "HTTP", "1.1", Method.GET, "/greet", false);
        var peerInfo = peerInfo();
        var headers = ServerRequestHeaders.create();

        return (RoutingRequest) Proxy.newProxyInstance(
                OpenTelemetryTracingSemanticConventionsProviderTest.class.getClassLoader(),
                new Class<?>[] {RoutingRequest.class},
                (_, method, _) -> switch (method.getName()) {
                case "prologue" -> prologue;
                case "requestedUri" -> uriInfo;
                case "query" -> UriQuery.empty();
                case "headers" -> headers;
                case "localPeer" -> peerInfo;
                default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static PeerInfo peerInfo() {
        return new PeerInfo() {
            @Override
            public InetSocketAddress address() {
                return new InetSocketAddress("127.0.0.1", 8080);
            }

            @Override
            public String host() {
                return "127.0.0.1";
            }

            @Override
            public int port() {
                return 8080;
            }

            @Override
            public Optional<java.security.Principal> tlsPrincipal() {
                return Optional.empty();
            }

            @Override
            public Optional<java.security.cert.Certificate[]> tlsCertificates() {
                return Optional.empty();
            }
        };
    }

    private static final class RecordingSpanBuilder implements Span.Builder<RecordingSpanBuilder> {
        private final Map<String, Object> tags = new LinkedHashMap<>();

        @Override
        public RecordingSpanBuilder parent(SpanContext spanContext) {
            return this;
        }

        @Override
        public RecordingSpanBuilder kind(Span.Kind kind) {
            return this;
        }

        @Override
        public RecordingSpanBuilder tag(String key, String value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public RecordingSpanBuilder tag(String key, Boolean value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public RecordingSpanBuilder tag(String key, Number value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public Span start(Instant instant) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Span build() {
            throw new UnsupportedOperationException();
        }

        Map<String, Object> tags() {
            return tags;
        }
    }
}
