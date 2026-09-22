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
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3ProtocolPreferenceTest {
    private static final String HELLO = "Hello";
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();

    private TestEnvironment environment;

    @BeforeEach
    void beforeEach() throws Exception {
        environment = TestEnvironment.createSharedListener(routing -> routing.get("/hello", (_, res) -> res.send(HELLO)));
    }

    @AfterEach
    void afterEach() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void shouldUseHttp1WhenGenericClientHasTcpFallbackProtocols() {
        WebClient client = WebClient.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .tls(environment.clientTls())
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .addProtocolConfig(Http3ClientProtocolConfig.create())
                .build();

        try {
            try (HttpClientResponse response = client.get("/hello").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldUseHttp3WhenGenericClientHasNoTcpFallbackProtocols() {
        WebClient client = WebClient.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .tls(environment.clientTls())
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolConfig(Http3ClientProtocolConfig.create())
                .build();

        try {
            try (HttpClientResponse response = client.get("/hello").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldUseHttp3WhenGenericClientHasPriorKnowledge() {
        WebClient client = WebClient.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .tls(environment.clientTls())
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .addProtocolConfig(Http3ClientProtocolConfig.builder()
                                           .priorKnowledge(true)
                                           .build())
                .build();

        try {
            try (HttpClientResponse response = client.get("/hello").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldFailExplicitHttp3RequestWithoutTcpFallbackWhenHttp3CannotBeUsed() {
        try (PlaintextEnvironment plaintext = PlaintextEnvironment.create()) {
            WebClient client = WebClient.builder()
                    .baseUri(plaintext.baseUri())
                    .shareConnectionCache(false)
                    .proxy(Proxy.noProxy())
                    .tls(NO_TLS)
                    .addProtocolPreference(Http3Client.PROTOCOL_ID)
                    .addProtocolConfig(Http3ClientProtocolConfig.create())
                    .build();

            try {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                              () -> client.get("/hello")
                                                                      .protocolId(Http3Client.PROTOCOL_ID)
                                                                      .request());
                assertThat(exception.getMessage(),
                           is("HTTP/3 fallback requires at least one configured TCP protocol."));
            } finally {
                client.closeResource();
            }
        }
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
                    .routing(routing -> routing.get("/hello", (_, res) -> res.send(HELLO)))
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
