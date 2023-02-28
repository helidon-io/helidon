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

package io.helidon.reactive.webserver;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.common.configurable.AllowList;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;

import io.helidon.common.http.RequestedUriDiscoveryContext;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.reactive.Multi;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.common.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType.FORWARDED;
import static io.helidon.common.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType.HOST;
import static io.helidon.common.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType.X_FORWARDED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestedUriTest {
    @ParameterizedTest
    @MethodSource("testData")
    void requestedUriTestForwarded(TestData testData) {
        BareRequest bareRequest = mock(BareRequest.class);
        SocketConfiguration sc = mock(SocketConfiguration.class);
        when(bareRequest.bodyPublisher()).thenReturn(Multi.empty());
        when(bareRequest.socketConfiguration()).thenReturn(sc);
        WebServer webServer = mock(WebServer.class);

        // set test "when"
        when(bareRequest.remoteAddress()).thenReturn(testData.remoteAddress());
        when(bareRequest.uri()).thenReturn(testData.uri());
        when(bareRequest.isSecure()).thenReturn(testData.isSecure());
        when(sc.requestedUriDiscoveryContext()).thenReturn(testData.discoveryContext());
        TestRequest request = new TestRequest(bareRequest, webServer, testData.headers(), testData.path());

        UriInfo requestedUri = request.requestedUri();
        assertThat(testData.testDescription(), requestedUri, is(testData.expectedUriInfo()));
    }

    private static Stream<TestData> testData() {
        return Stream.of(
                TestData.create("Forwarded header",
                                "myLB",
                                false,
                                URI.create("http://localhost:9090/path"),
                                List.of(FORWARDED),
                                AllowList.builder()
                                        .addAllowed("myLB")
                                        .build(),
                                Map.of(
                                        Http.Header.X_FORWARDED_HOST, List.of("myxhost"),
                                        Http.Header.X_FORWARDED_PORT, List.of("7879"),
                                        Http.Header.X_FORWARDED_PROTO, List.of("xhttps"),
                                        Http.Header.X_FORWARDED_PREFIX, List.of("/reversed"),
                                        Http.Header.FORWARDED, List.of("host=myfhost:7878;proto=https"),
                                        Http.Header.HOST, List.of("myhost:9999")
                                ),
                                path("/path"),
                                new UriInfo("https", "myfhost", 7878, "/path", Optional.empty())),
                TestData.create("Forwarded header fallback to Host header",
                                "myLB",
                                false,
                                URI.create("http://localhost:9090/path"),
                                List.of(FORWARDED),
                                AllowList.builder()
                                        .addAllowed("myLB")
                                        .build(),
                                Map.of(
                                        Http.Header.X_FORWARDED_HOST, List.of("myxhost"),
                                        Http.Header.X_FORWARDED_PORT, List.of("7879"),
                                        Http.Header.X_FORWARDED_PROTO, List.of("xhttps"),
                                        Http.Header.X_FORWARDED_PREFIX, List.of("/reversed"),
                                        Http.Header.HOST, List.of("myhost:9999")
                                ),
                                path("/path"),
                                new UriInfo("http", "myhost", 9999, "/path", Optional.empty())),
                TestData.create("X-Forwarded all headers",
                                "myLB",
                                false,
                                URI.create("http://localhost:9090/path"),
                                List.of(X_FORWARDED),
                                AllowList.builder()
                                        .addAllowed("myLB")
                                        .build(),
                                Map.of(
                                        Http.Header.X_FORWARDED_HOST, List.of("myxhost"),
                                        Http.Header.X_FORWARDED_PORT, List.of("7879"),
                                        Http.Header.X_FORWARDED_PROTO, List.of("xhttps"),
                                        Http.Header.X_FORWARDED_PREFIX, List.of("/reversed"),
                                        Http.Header.FORWARDED, List.of("host=myfhost:7878;proto=https"),
                                        Http.Header.HOST, List.of("myhost:9999")
                                ),
                                path("/path"),
                                new UriInfo("xhttps", "myxhost", 7879, "/reversed/path", Optional.empty())),
                TestData.create("X-Forwarded some headers",
                                "myLB",
                                false,
                                URI.create("http://localhost:9090/path"),
                                List.of(X_FORWARDED),
                                AllowList.builder()
                                        .addAllowed("myLB")
                                        .build(),
                                Map.of(
                                        Http.Header.X_FORWARDED_HOST, List.of("myxhost"),
                                        Http.Header.X_FORWARDED_PREFIX, List.of("/reversed"),
                                        Http.Header.FORWARDED, List.of("host=myfhost:7878;proto=https"),
                                        Http.Header.HOST, List.of("myhost")
                                ),
                                path("/path"),
                                new UriInfo("http", "myxhost", 80, "/reversed/path", Optional.empty())),
                // Falls back to Host header because the user selected X-Forwarded for the discovery type
                TestData.create("X-Forwarded fallback to Host header",
                                "myLB",
                                false,
                                URI.create("http://localhost:9090/path"),
                                List.of(X_FORWARDED),
                                AllowList.builder()
                                        .addAllowed("myLB")
                                        .build(),
                                Map.of(
                                        Http.Header.FORWARDED, List.of("host=myfhost:7878;proto=https"),
                                        Http.Header.HOST, List.of("myhost:9999")
                                ),
                                path("/path"),
                                new UriInfo("http", "myhost", 9999, "/path", Optional.empty())),
                TestData.create("Default (Host header)",
                                "myLB",
                                false,
                                URI.create("http://localhost:9090/path"),
                                List.of(HOST),
                                AllowList.builder() // allow list should be ignored; this data uses HOST
                                        .addAllowed("myLB")
                                        .build(),
                                Map.of(
                                        Http.Header.X_FORWARDED_HOST, List.of("myxhost"),
                                        Http.Header.X_FORWARDED_PORT, List.of("7879"),
                                        Http.Header.X_FORWARDED_PROTO, List.of("xhttps"),
                                        Http.Header.X_FORWARDED_PREFIX, List.of("/reversed"),
                                        Http.Header.FORWARDED, List.of("host=myfhost:7878;proto=https"),
                                        Http.Header.HOST, List.of("myhost:9999")
                                ),
                                path("/path"),
                                new UriInfo("http", "myhost", 9999, "/path", Optional.empty()))
        );

    }

    private static Request.Path path(String path) {
        return new Request.Path(path, path, Map.of(), null);
    }

    private record TestData(String testDescription,
                            String remoteAddress,
                            boolean isSecure,
                            URI uri,
                            TestRequestHeaders headers,
                            Request.Path path,
                            RequestedUriDiscoveryContext discoveryContext,
                            UriInfo expectedUriInfo) {

        static TestData create(String testDescription,
                               String remoteAddress,
                               boolean isSecure,
                               URI uri,
                               List<RequestedUriDiscoveryContext.RequestedUriDiscoveryType> discoveryTypes,
                               AllowList trustedProxies,
                               Map<Http.HeaderName, List<String>> headers,
                               Request.Path path,
                               UriInfo expectedUriInfo) {
            return new TestData(testDescription,
                                remoteAddress,
                                isSecure,
                                uri,
                                TestRequestHeaders.create(headers),
                                path,
                                TestData.discoveryContext(discoveryTypes, trustedProxies),
                                expectedUriInfo);
        }


        @Override
        public String toString() {
            return testDescription();
        }

        public URI uri() {
            return this.uri;
        }

        private static RequestedUriDiscoveryContext discoveryContext(List<RequestedUriDiscoveryContext.RequestedUriDiscoveryType>
                                                                             discoveryTypes,
                                                                     AllowList trustedProxies) {
            return RequestedUriDiscoveryContext.builder()
                    .socketId(WebServer.DEFAULT_SOCKET_NAME)
                    .discoveryTypes(discoveryTypes)
                    .trustedProxies(trustedProxies)
                    .build();
        }
    }

    private static class TestHeaderValue implements Http.HeaderValue {

        private static TestHeaderValue create(Http.HeaderName name, List<String> values) {
            return new TestHeaderValue(name, values);
        }

        private final Http.HeaderName name;
        private final List<String> values;

        private TestHeaderValue(Http.HeaderName name, List<String> values) {
            this.name = name;
            this.values = values;
        }
        @Override
        public String name() {
            return name.defaultCase();
        }

        @Override
        public Http.HeaderName headerName() {
            return name;
        }

        @Override
        public String value() {
            return values.isEmpty() ? null : values.get(0);
        }

        @Override
        public <T> T value(Class<T> type) {
            return type.cast(value());
        }

        @Override
        public List<String> allValues() {
            return values;
        }

        @Override
        public int valueCount() {
            return values.size();
        }

        @Override
        public boolean sensitive() {
            return false;
        }

        @Override
        public boolean changing() {
            return false;
        }
    }
    private static class TestRequestHeaders implements RequestHeaders {

        private static TestRequestHeaders create(Map<Http.HeaderName, List<String>> headerMap) {
            return new TestRequestHeaders(headerMap);
        }

        private final Map<Http.HeaderName, Http.HeaderValue> headers;

        private TestRequestHeaders(Map<Http.HeaderName, List<String>> headersMap) {
            headers = new HashMap<>();
            headersMap.forEach((k, v) -> headers.put(k, TestHeaderValue.create(k, v)));
        }

        @Override
        public List<String> all(Http.HeaderName name, Supplier<List<String>> defaultSupplier) {
            Http.HeaderValue result = headers.get(name);
            return result != null ? result.allValues() : defaultSupplier.get();
        }

        @Override
        public boolean contains(Http.HeaderName name) {
            return headers.containsKey(name);
        }

        @Override
        public boolean contains(Http.HeaderValue value) {
            return headers.containsValue(value);
        }

        @Override
        public Http.HeaderValue get(Http.HeaderName name) {
            return headers.get(name);
        }

        @Override
        public int size() {
            return headers.size();
        }

        @Override
        public List<HttpMediaType> acceptedTypes() {
            return List.of(HttpMediaType.create("*/*"));
        }

        @Override
        public Iterator<Http.HeaderValue> iterator() {
            return headers.values().iterator();
        }
    }
    private static class TestRequest extends Request {
        private final Request.Path path;

        TestRequest(BareRequest req, WebServer webServer, TestRequestHeaders headers, Request.Path path) {
            super(req, webServer, headers);
            this.path = path;
        }

        private static Map<Http.HeaderName, List<String>> headers(RequestHeaders rh) {
            Map<Http.HeaderName, List<String>> result = new HashMap<>();
            rh.forEach(header -> result.put(header.headerName(), header.allValues()));
            return result;
        }

        @Override
        public ServerRequest.Path path() {
            return new Request.Path(path.toString(), path.toString(), Map.of(), null);
        }

        @Override
        public void next() {

        }

        @Override
        public void next(Throwable t) {

        }

        @Override
        public Optional<SpanContext> spanContext() {
            return Optional.empty();
        }

        @Override
        public Tracer tracer() {
            return Tracer.global();
        }
    }


}