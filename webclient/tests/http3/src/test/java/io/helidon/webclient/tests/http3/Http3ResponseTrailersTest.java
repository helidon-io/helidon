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

package io.helidon.webclient.tests.http3;

import java.net.InetAddress;

import io.helidon.common.tls.Tls;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http1.Http1Config;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3ResponseTrailersTest {
    private static final String PAYLOAD = "payload";
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();
    private static final Header TEST_TRAILER = HeaderValues.create("test-trailer", "trailer-value");
    private static final Header DECORATED_TRAILER = HeaderValues.create("test-trailer", "service-value");

    @Test
    void shouldExposeTrailersOnDirectTypedHttp3Response() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing.get("/trailers",
                                                                                                       Http3ResponseTrailersTest::sendTrailers))) {
            Http3Client client = strictClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTlsHttp3())
                    .addService(Http3ResponseTrailersTest::decorateTrailers)
                    .build();

            try {
                try (Http3ClientResponse response = client.get("/trailers").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.headers().values(HeaderNames.TRAILER), containsInAnyOrder(TEST_TRAILER.name()));
                    assertThrows(IllegalStateException.class, response::trailers);
                    assertThat(response.as(String.class), is(PAYLOAD));
                    assertThat(response.trailers().contains(DECORATED_TRAILER), is(true));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldExposeTrailersOnFallbackTypedHttp3Response() {
        try (PlaintextEnvironment environment = PlaintextEnvironment.create()) {
            Http3Client client = Http3Client.builder()
                    .baseUri(environment.baseUri())
                    .shareConnectionCache(false)
                    .proxy(Proxy.noProxy())
                    .tls(NO_TLS)
                    .addService(Http3ResponseTrailersTest::decorateTrailers)
                    .build();

            try {
                try (Http3ClientResponse response = client.get("/trailers").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(response.headers().values(HeaderNames.TRAILER), containsInAnyOrder(TEST_TRAILER.name()));
                    assertThrows(IllegalStateException.class, response::trailers);
                    assertThat(response.as(String.class), is(PAYLOAD));
                    assertThat(response.trailers().contains(DECORATED_TRAILER), is(true));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    private static void sendTrailers(ServerRequest req, ServerResponse res) {
        res.header(HeaderNames.TRAILER, TEST_TRAILER.name());
        res.trailers().add(TEST_TRAILER);
        res.send(PAYLOAD);
    }

    private static WebClientServiceResponse decorateTrailers(WebClientService.Chain chain,
                                                              WebClientServiceRequest request) {
        WebClientServiceResponse response = chain.proceed(request);
        return WebClientServiceResponse.builder(response)
                .trailers(response.trailers().thenApply(_ -> {
                    WritableHeaders<?> headers = WritableHeaders.create();
                    headers.add(DECORATED_TRAILER);
                    return ClientResponseTrailers.create(headers);
                }))
                .build();
    }

    private static final class PlaintextEnvironment implements AutoCloseable {
        private final WebServer server;

        private PlaintextEnvironment(WebServer server) {
            this.server = server;
        }

        private static PlaintextEnvironment create() {
            WebServer server = WebServer.builder()
                    .address(InetAddress.getLoopbackAddress())
                    .port(0)
                    .bindingsDiscoverServices(false)
                    .addBinding(TcpTransportConfig.create())
                    .protocolsDiscoverServices(false)
                    .addProtocol(Http1Config.create())
                    .routing(routing -> routing.get("/trailers", Http3ResponseTrailersTest::sendTrailers))
                    .build()
                    .start();
            return new PlaintextEnvironment(server);
        }

        private String baseUri() {
            return "http://localhost:" + server.port();
        }

        @Override
        public void close() {
            server.stop();
        }
    }
}
