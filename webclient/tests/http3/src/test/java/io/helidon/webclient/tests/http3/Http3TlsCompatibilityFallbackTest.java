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
import java.net.URI;

import io.helidon.common.tls.Tls;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3TlsCompatibilityFallbackTest {
    private static final String HELLO = "Hello";
    private static final String TLS_V13 = "TLSv1.3";

    private TestEnvironment environment;
    private Tls tlsWithoutTls13;

    @BeforeEach
    void beforeEach() throws Exception {
        environment = TestEnvironment.createSharedListener(routing -> routing.get("/hello", (_, res) -> res.send(HELLO)));
        tlsWithoutTls13 = environment.clientTlsWithoutTls13();
    }

    @AfterEach
    void afterEach() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void shouldFallbackTypedHttp3ClientToHttp1WhenTlsParametersDoNotSupportTls13() {
        Http3Client client = newHttp3Client(Http3ClientProtocolConfig.create());
        try {
            try (Http3ClientResponse response = client.get("/hello").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldFailTypedHttp3ClientWhenTlsParametersDoNotSupportTls13AndPriorKnowledgeIsEnabled() {
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
    void shouldFallbackExplicitHttp3RequestToHttp1WhenTlsParametersDoNotSupportTls13() {
        String baseUri = "https://http3.invalid:" + URI.create(environment.baseUri()).getPort();
        WebClient client = WebClient.builder()
                .baseUri(baseUri)
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .dnsResolver((_, _) -> InetAddress.getLoopbackAddress())
                .tls(tlsWithoutTls13)
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .addProtocolConfig(Http3ClientProtocolConfig.create())
                .build();
        try {
            try (HttpClientResponse response = client.get("/hello")
                    .sni(it -> it.mode(SniMode.EXPLICIT).host("localhost"))
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
    void shouldFailGenericRequestWhenHttp3IsOnlyConfiguredProtocolAndTlsParametersDoNotSupportTls13() {
        WebClient client = newGenericClient(Http3ClientProtocolConfig.create(), false);
        try {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                             () -> client.get("/hello").request());
            assertThat(exception.getMessage(), containsString("did not discover any HTTP version willing to handle it"));
            assertThat(exception.getMessage(), containsString("HTTP versions supported: [h3]"));
        } finally {
            client.closeResource();
        }
    }

    private Http3Client newHttp3Client(Http3ClientProtocolConfig protocolConfig) {
        return Http3Client.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .tls(tlsWithoutTls13)
                .protocolConfig(protocolConfig)
                .build();
    }

    private WebClient newGenericClient(Http3ClientProtocolConfig protocolConfig, boolean withTcpFallback) {
        var builder = WebClient.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .tls(tlsWithoutTls13)
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolConfig(protocolConfig);

        if (withTcpFallback) {
            builder.addProtocolPreference(Http1Client.PROTOCOL_ID);
        }

        return builder.build();
    }

}
