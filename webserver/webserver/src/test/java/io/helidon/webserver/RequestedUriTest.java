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

package io.helidon.webserver;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.common.configurable.AllowList;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpRequest;
import io.helidon.common.http.UriInfo;
import io.helidon.common.reactive.Multi;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.webserver.SocketConfiguration.RequestedUriDiscoveryType.FORWARDED;
import static io.helidon.webserver.SocketConfiguration.RequestedUriDiscoveryType.HOST;
import static io.helidon.webserver.SocketConfiguration.RequestedUriDiscoveryType.X_FORWARDED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertAll;
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
        when(sc.requestedUriDiscoveryEnabled()).thenReturn(testData.requestedUriDiscoveryEnabled());
        when(sc.trustedProxies()).thenReturn(testData.trustedProxies());
        when(sc.requestedUriDiscoveryTypes()).thenReturn(testData.discoveryTypes());
        Request request = new TestRequest(bareRequest, webServer, new HashRequestHeaders(testData.headers()), testData.path());

        assertThat(testData.testDescription(), request.requestedUri(), is(testData.expectedUriInfo()));
    }

    @Test
    void ipV6Test() {
        WebServer server = WebServer.builder()
                .addRouting(Routing.builder()
                                    .get("/uri", (req, res) -> {
                                        try {
                                            res.send(req.requestedUri().toUri().toString());
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            res.status(Http.Status.INTERNAL_SERVER_ERROR_500)
                                                    .send(e.getClass().getName() + ":" + e.getMessage());
                                        }
                                    })
                )
                .build()
                .start()
                .await();

        int port = server.port();
        WebClient client = WebClient.builder()
                .followRedirects(true)
                .baseUri("http://[::1]:" + port)
                .build();

        WebClientResponse response = client.get()
                .path("/uri")
                .request()
                .await(Duration.ofSeconds(10));

        assertAll(
                () -> assertThat(response.content().as(String.class).await(), is("http://[::1]:" + port + "/uri")),
                () -> assertThat(response.status(), is(Http.Status.OK_200))
        );
    }

    private static Stream<TestData> testData() {
        return Stream.of(
                new TestData("Forwarded header",
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
                new TestData("Forwarded header fallback to Host header",
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
                new TestData("X-Forwarded all headers",
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
                new TestData("X-Forwarded some headers",
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
                new TestData("X-Forwarded fallback to Host header",
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
                new TestData("Default (Host header)",
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

    private static HttpRequest.Path path(String path) {
        return new Request.Path(path, path, Map.of(), null);
    }

    private record TestData(String testDescription,
                            String remoteAddress,
                            boolean isSecure,
                            URI uri,
                            List<SocketConfiguration.RequestedUriDiscoveryType> discoveryTypes,
                            AllowList trustedProxies,
                            Map<String, List<String>> headers,
                            HttpRequest.Path path,
                            UriInfo expectedUriInfo) {

        @Override
        public String toString() {
            return testDescription();
        }

        /*
         * Basically copied from a part ofSocketConfiguration#requestedUriDiscoveryEnabled() to mimic that behavior.
         */
        private boolean requestedUriDiscoveryEnabled() {
            return discoveryTypes.isEmpty() || trustedProxies != null;
        }
    }

    private static class TestRequest extends Request {
        private final HttpRequest.Path path;

        TestRequest(BareRequest req, WebServer webServer, HashRequestHeaders headers, HttpRequest.Path path) {
            super(req, webServer, headers);
            this.path = path;
        }

        @Override
        public HttpRequest.Path path() {
            return path;
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
