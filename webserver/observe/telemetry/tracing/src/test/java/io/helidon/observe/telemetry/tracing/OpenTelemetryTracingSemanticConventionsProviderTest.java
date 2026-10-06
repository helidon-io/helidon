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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.observe.tracing.TracingSemanticConventions;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;

class OpenTelemetryTracingSemanticConventionsProviderTest {

    @Test
    void exportsNumericAttributesForSuccessfulResponse() {
        assertResponseAttributes(Status.OK_200);
    }

    @Test
    void exportsNumericAttributesForClientErrorResponse() {
        assertResponseAttributes(Status.NOT_FOUND_404);
    }

    @Test
    void exportsNumericAttributesForServerErrorResponse() {
        assertResponseAttributes(Status.SERVICE_UNAVAILABLE_503);
    }

    @Test
    void exportsNumericPortAndPreservesExceptionAttributes() {
        var failure = new IllegalStateException("Request failed");
        var span = exportSpan(Status.SERVICE_UNAVAILABLE_503, failure);

        assertAll(
                () -> assertThat("Server port", span.getAttributes().get(AttributeKey.longKey("server.port")), is(8080L)),
                () -> assertThat("String server port", span.getAttributes().get(AttributeKey.stringKey("server.port")),
                                 nullValue()),
                () -> assertThat("Unavailable response status",
                                 span.getAttributes().get(AttributeKey.longKey("http.response.status_code")), nullValue()),
                () -> assertThat("String response status",
                                 span.getAttributes().get(AttributeKey.stringKey("http.response.status_code")), nullValue()),
                () -> assertThat("Error type", span.getAttributes().get(AttributeKey.stringKey("error.type")),
                                 is(IllegalStateException.class.getName())),
                () -> assertThat("Span status", span.getStatus().getStatusCode(), is(StatusCode.ERROR)),
                () -> assertThat("Exception events", span.getEvents(), hasSize(1)),
                () -> assertThat("Exception event", span.getEvents().getFirst().getName(), is("exception")));
    }

    @Test
    void beforeStartOmitsDeprecatedMicroProfileTelemetryHostTags() {
        var request = request();
        TracingSemanticConventions conventions = new OpenTelemetryTracingSemanticConventionsProvider()
                .create(SpanTracingConfig.ENABLED, "", request, null);
        var spanBuilder = new RecordingSpanBuilder();

        conventions.spanName();
        conventions.beforeStart(spanBuilder);

        assertThat("Server port", spanBuilder.tags().get("server.port"), is(8080));
        assertThat("Span tags", spanBuilder.tags(), allOf(
                hasEntry("http.request.method", "GET"),
                hasEntry("server.address", "helidon.example"),
                not(hasKey("http.request.method_original")),
                not(hasKey("net.host.name")),
                not(hasKey("net.host.port"))));
    }

    @Test
    void unknownMethodUsesOtherAndPreservesOriginal() {
        for (String method : new String[] {"get", "GeT", "CUSTOM"}) {
            var request = request(Method.create(method));
            TracingSemanticConventions conventions = new OpenTelemetryTracingSemanticConventionsProvider()
                    .create(SpanTracingConfig.ENABLED, "", request, null);
            var spanBuilder = new RecordingSpanBuilder();

            assertThat(method + " span name", conventions.spanName(), is("HTTP"));
            conventions.beforeStart(spanBuilder);

            assertThat(method + " span tags", spanBuilder.tags(), allOf(
                    hasEntry("http.request.method", "_OTHER"),
                    hasEntry("http.request.method_original", method)));
        }
    }

    private static RoutingRequest request() {
        return request(Method.GET);
    }

    private static RoutingRequest request(Method requestMethod) {
        var uriInfo = UriInfo.builder()
                .scheme("http")
                .host("helidon.example")
                .port(8080)
                .path("/greet")
                .query(UriQuery.empty())
                .build();
        var prologue = HttpPrologue.create("HTTP/1.1", "HTTP", "1.1", requestMethod, "/greet", false);
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
                case "matchingPattern" -> Optional.of("/greet");
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

    private static void assertResponseAttributes(Status status) {
        var span = exportSpan(status, null);
        var attributes = span.getAttributes();

        assertAll(
                () -> assertThat("Span kind", span.getKind(), is(SpanKind.SERVER)),
                () -> assertThat("Server port", attributes.get(AttributeKey.longKey("server.port")), is(8080L)),
                () -> assertThat("HTTP status", attributes.get(AttributeKey.longKey("http.response.status_code")),
                                 is((long) status.code())),
                () -> assertThat("String server port", attributes.get(AttributeKey.stringKey("server.port")), nullValue()),
                () -> assertThat("String HTTP status", attributes.get(AttributeKey.stringKey("http.response.status_code")),
                                 nullValue()),
                () -> assertThat("Request method", attributes.get(AttributeKey.stringKey("http.request.method")), is("GET")),
                () -> assertThat("Server address", attributes.get(AttributeKey.stringKey("server.address")),
                                 is("helidon.example")),
                () -> assertThat("Route", attributes.get(AttributeKey.stringKey("http.route")), is("/greet")),
                () -> assertThat("Error type", attributes.get(AttributeKey.stringKey("error.type")), nullValue()));
    }

    private static SpanData exportSpan(Status status, Exception failure) {
        var exporter = new RecordingSpanExporter();
        try (var tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()) {
            var telemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
            var tracer = HelidonOpenTelemetry.create(telemetry, telemetry.getTracer("semantic-conventions-test"), Map.of());
            var response = (RoutingResponse) Proxy.newProxyInstance(
                    OpenTelemetryTracingSemanticConventionsProviderTest.class.getClassLoader(),
                    new Class<?>[] {RoutingResponse.class},
                    (_, method, _) -> switch (method.getName()) {
                    case "status" -> status;
                    default -> throw new UnsupportedOperationException(method.getName());
                    });
            var conventions = new OpenTelemetryTracingSemanticConventionsProvider()
                    .create(SpanTracingConfig.ENABLED, "", request(), response);
            var span = tracer.spanBuilder(conventions.spanName()).update(conventions::beforeStart).start();
            if (failure == null) {
                conventions.beforeEnd(span);
                span.end();
            } else {
                conventions.beforeEnd(span, failure);
                span.end(failure);
            }

            assertThat("Exported spans", exporter.spans, hasSize(1));
            return exporter.spans.getFirst();
        }
    }

    private static final class RecordingSpanExporter implements SpanExporter {
        private final List<SpanData> spans = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            this.spans.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
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
