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
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3PlaintextFallbackTest {
    private static final String HELLO = "Hello";
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();

    private PlaintextEnvironment environment;

    @BeforeEach
    void beforeEach() {
        environment = PlaintextEnvironment.create();
    }

    @AfterEach
    void afterEach() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void shouldFallbackTypedHttp3ClientToHttp1ForPlaintextEndpoint() {
        RecordingTransportObserverService observer = new RecordingTransportObserverService();
        Http3Client client = Http3Client.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .tls(NO_TLS)
                .protocolConfig(Http3ClientProtocolConfig.create())
                .servicesDiscoverServices(false)
                .addService(observer)
                .build();
        try {
            try (Http3ClientResponse response = client.get("/hello").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }

        assertThat(observer.requests(), is(1));
        assertThat(observer.registrationsOpened(), is(1));
        assertThat(observer.registrationsClosed(), is(1));
        assertThat(observer.registrationCompletions(), is(1));
    }

    @Test
    void shouldFailTypedHttp3ClientForPlaintextEndpointWhenPriorKnowledgeIsEnabled() {
        Http3Client client = newHttp3Client(Http3ClientProtocolConfig.builder()
                                                     .priorKnowledge(true)
                                                     .build());
        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                          () -> client.get("/hello").request());
            assertThat(exception.getMessage(),
                       is("HTTP/3 priorKnowledge is enabled, but this request cannot use HTTP/3."));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldIgnoreAltSvcForPlaintextGenericClient() {
        WebClient client = newGenericClient(Http3ClientProtocolConfig.create());
        try {
            try (HttpClientResponse first = client.get("/hello").request();
                 HttpClientResponse second = client.get("/hello").request()) {
                assertThat(first.status(), is(Status.OK_200));
                assertThat(first.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(first.headers().contains(HeaderNames.ALT_SVC), is(true));
                assertThat(first.entity().as(String.class), is(HELLO));
                assertThat(second.status(), is(Status.OK_200));
                assertThat(second.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(second.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldFallbackExplicitHttp3RequestToHttp1ForPlaintextEndpoint() {
        WebClient client = newGenericClient(Http3ClientProtocolConfig.create());
        try {
            try (HttpClientResponse response = client.get("/hello")
                    .protocolId(Http3Client.PROTOCOL_ID)
                    .request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldFailExplicitHttp3RequestForPlaintextEndpointWhenPriorKnowledgeIsEnabled() {
        WebClient client = newGenericClient(Http3ClientProtocolConfig.builder()
                                                     .priorKnowledge(true)
                                                     .build());
        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                          () -> client.get("/hello")
                                                                  .protocolId(Http3Client.PROTOCOL_ID)
                                                                  .request());
            assertThat(exception.getMessage(),
                       is("HTTP/3 priorKnowledge is enabled, but this request cannot use HTTP/3."));
        } finally {
            client.closeResource();
        }
    }

    private Http3Client newHttp3Client(Http3ClientProtocolConfig protocolConfig) {
        return Http3Client.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .tls(NO_TLS)
                .protocolConfig(protocolConfig)
                .build();
    }

    private WebClient newGenericClient(Http3ClientProtocolConfig protocolConfig) {
        return WebClient.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .tls(NO_TLS)
                .altSvc(ClientAltSvcConfig.create())
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .addProtocolConfig(protocolConfig)
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
                    .routing(routing -> routing.get("/hello", (req, res) -> res
                            .header(HeaderNames.ALT_SVC, "h3=\":" + req.localPeer().port() + "\"")
                            .send(HELLO)))
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
