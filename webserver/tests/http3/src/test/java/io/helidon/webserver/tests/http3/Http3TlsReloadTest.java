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

package io.helidon.webserver.tests.http3;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import javax.net.ssl.SSLContext;

import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3TlsReloadTest {
    private static final String SNI_HOST = "api.example.com";

    @Test
    void shouldObserveDirectTlsReloadForSharedHttp1AndHttp3Listener() throws Exception {
        try (Http3TestSupport.TestEnvironment environment = Http3TestSupport.sharedListener(Http3TlsReloadTest::routing)) {
            WebServer server = environment.server();
            SSLContext initialClientSslContext = environment.clientSslContext();
            SSLContext reloadedClientSslContext = Http3TestSupport.clientSslContext("second-valid/server.p12");
            int originalPort = environment.port();
            Http3Client persistentHttp3Client = Http3TestSupport.strictClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTls())
                    .build();

            try {
                assertResponse(Http3TestSupport.http1Client(initialClientSslContext),
                               environment.http1Get("/hello"),
                               HTTP_1_1,
                               "hello");
                assertResponse(Http3TestSupport.http3Client(initialClientSslContext),
                               environment.http3Get("/hello"),
                               HTTP_3,
                               "hello");

                String existingSocketId;
                try (Http3ClientResponse response = persistentHttp3Client.get("/socket-id").request()) {
                    assertThat(response.status().code(), is(200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    existingSocketId = response.as(String.class);
                }

                server.reloadTls(Http3TestSupport.serverTlsMaterial("second-valid/server.p12"));

                assertThat(server.port(), is(originalPort));

                try (Http3ClientResponse response = persistentHttp3Client.get("/socket-id").request()) {
                    assertThat(response.status().code(), is(200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.as(String.class), is(existingSocketId));
                }
                persistentHttp3Client.closeResource();

                SSLContext oldTrustClientSslContext = Http3TestSupport.clientSslContext("client.p12");
                Tls oldTrustClientTls = Http3TestSupport.clientTls("client.p12");
                HttpClient oldTrustHttp1Client = Http3TestSupport.http1Client(oldTrustClientSslContext);
                assertThrows(IOException.class,
                             () -> oldTrustHttp1Client.send(environment.http1Get("/hello"), ofString()));
                assertOldTrustHttp3Fails(environment, oldTrustClientTls);

                assertResponse(Http3TestSupport.http1Client(reloadedClientSslContext),
                               environment.http1Get("/hello"),
                               HTTP_1_1,
                               "hello");
                assertResponse(Http3TestSupport.http3Client(reloadedClientSslContext),
                               environment.http3Get("/hello"),
                               HTTP_3,
                               "hello");
            } finally {
                persistentHttp3Client.closeResource();
            }
        }
    }

    @Test
    void shouldObserveVirtualHostTlsReloadForNewHttp3Connections() throws Exception {
        Tls initialVirtualHostTls = Http3TestSupport.serverTls("server.p12");
        try (Http3TestSupport.TestEnvironment environment = Http3TestSupport.sharedListener(
                builder -> builder.addVirtualHost(virtualHost -> virtualHost
                        .host(SNI_HOST)
                        .tls(initialVirtualHostTls)),
                Http3TlsReloadTest::routing)) {
            WebServer server = environment.server();
            Http3Client persistentClient = sniClient(environment, clientTls("client.p12"));
            try {
                String existingSocketId = requestWithSni(persistentClient, "/socket-id");

                server.reloadVirtualHostTls(Http3TestSupport.serverTlsMaterial("second-valid/server.p12"),
                                            SNI_HOST);

                assertThat(requestWithSni(persistentClient, "/socket-id"), is(existingSocketId));
                persistentClient.closeResource();

                Http3Client oldTrustClient = sniClient(environment, clientTls("client.p12"));
                try {
                    assertThrows(RuntimeException.class, () -> requestWithSni(oldTrustClient, "/hello"));
                } finally {
                    oldTrustClient.closeResource();
                }

                Http3Client reloadedTrustClient = sniClient(environment, clientTls("second-valid/server.p12"));
                try {
                    assertThat(requestWithSni(reloadedTrustClient, "/hello"), is("hello"));
                } finally {
                    reloadedTrustClient.closeResource();
                }
            } finally {
                persistentClient.closeResource();
            }
        }
    }

    private static void routing(HttpRouting.Builder router) {
        router.get("/hello", (_, res) -> res.send("hello"));
        router.get("/socket-id", (req, res) -> res.send(req.socketId()));
    }

    private static void assertOldTrustHttp3Fails(Http3TestSupport.TestEnvironment environment, Tls oldTrustClientTls) {
        Http3Client oldTrustHttp3Client = Http3TestSupport.strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(oldTrustClientTls)
                .build();
        try {
            assertThrows(RuntimeException.class, () -> oldTrustHttp3Client.get("/hello").request());
        } finally {
            oldTrustHttp3Client.closeResource();
        }
    }

    private static void assertResponse(HttpClient client,
                                       HttpRequest request,
                                       HttpClient.Version expectedVersion,
                                       String expectedBody) throws Exception {
        HttpResponse<String> response = client.send(request, ofString());
        assertThat(response.statusCode(), is(200));
        assertThat(response.version(), is(expectedVersion));
        assertThat(response.body(), is(expectedBody));
    }

    private static Http3Client sniClient(Http3TestSupport.TestEnvironment environment, Tls tls) {
        return Http3TestSupport.strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(tls)
                .build();
    }

    private static String requestWithSni(Http3Client client, String path) {
        try (Http3ClientResponse response = client.get(path)
                .header(HeaderNames.HOST, SNI_HOST)
                .sni(sni -> sni.mode(SniMode.EXPLICIT)
                        .host(SNI_HOST))
                .request()) {
            assertThat(response.status().code(), is(200));
            assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            return response.as(String.class);
        }
    }

    private static Tls clientTls(String trustStoreResource) {
        return Tls.builder()
                .trust(trust -> trust.keystore(store -> store
                        .passphrase("password")
                        .trustStore(true)
                        .keystore(Resource.create(trustStoreResource))))
                .applicationProtocols(List.of(Http3Client.PROTOCOL_ID))
                .enabledProtocols(List.of("TLSv1.3"))
                .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                .build();
    }
}
