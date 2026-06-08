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

package io.helidon.webserver.tests.http2;

import java.net.URI;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.SniSelectionPolicy;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2Route;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SniTest {
    private static final String SNI_HOST = "api.example.com";
    private static final String OTHER_HOST = "admin.example.com";
    private static final String DIFFERENT_HOST = "other.example.com";

    private static WebServer server;
    private static Http2Client client;

    @BeforeAll
    static void startServer() {
        Tls tls = tls();

        server = WebServerConfig.builder()
                .host("localhost")
                .port(0)
                .shutdownHook(false)
                .tls(tls)
                .addVirtualHost(virtualHost -> virtualHost
                        .host(SNI_HOST)
                        .tls(tls))
                .addProtocol(Http2Config.builder().build())
                .routing(routing -> routing
                        .route(Http2Route.route(Method.GET, "/", (req, res) -> res.send("ok")))
                        .route(Http2Route.route(Method.GET, "/sni", (req, res) -> res.send(sniHosts(req)))))
                .build()
                .start();

        client = Http2Client.builder()
                .baseUri(URI.create("https://localhost:" + server.port() + "/"))
                .shareConnectionCache(false)
                .tls(clientTls -> clientTls
                        .trustAll(true)
                        .endpointIdentificationAlgorithm("NONE"))
                .build();
    }

    @AfterAll
    static void stopServer() {
        if (client != null) {
            client.closeResource();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void acceptsMatchingAuthority() {
        try (Http2ClientResponse response = client.get()
                .header(HeaderValues.create(HeaderNames.HOST, SNI_HOST))
                .sni(sni -> sni.mode(SniMode.EXPLICIT)
                        .host(SNI_HOST))
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("ok"));
        }
    }

    @Test
    void exposesSniHosts() {
        try (Http2ClientResponse response = client.get("/sni")
                .header(HeaderValues.create(HeaderNames.HOST, SNI_HOST))
                .sni(sni -> sni.mode(SniMode.EXPLICIT)
                        .host(SNI_HOST))
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(SNI_HOST + "|" + SNI_HOST));
        }
    }

    @Test
    void rejectsAuthorityMismatch() {
        try (Http2ClientResponse response = client.get()
                .header(HeaderValues.create(HeaderNames.HOST, OTHER_HOST))
                .sni(sni -> sni.mode(SniMode.EXPLICIT)
                        .host(SNI_HOST))
                .request()) {

            assertThat(response.status(), is(Status.MISDIRECTED_REQUEST_421));
        }
    }

    @Test
    void rejectsUnmatchedSniAuthorityMismatch() {
        try (Http2ClientResponse response = client.get()
                .header(HeaderValues.create(HeaderNames.HOST, DIFFERENT_HOST))
                .sni(sni -> sni.mode(SniMode.EXPLICIT)
                        .host(OTHER_HOST))
                .request()) {

            assertThat(response.status(), is(Status.MISDIRECTED_REQUEST_421));
        }
    }

    @Test
    void rejectsFallbackAuthorityForConfiguredVirtualHost() {
        try (Http2ClientResponse response = client.get()
                .header(HeaderValues.create(HeaderNames.HOST, SNI_HOST))
                .sni(sni -> sni.mode(SniMode.DISABLED))
                .request()) {

            assertThat(response.status(), is(Status.MISDIRECTED_REQUEST_421));
        }
    }

    @Test
    void rejectsUnmatchedSniDuringTlsHandshake() {
        WebServer rejectingServer = rejectingServer(SniSelectionPolicy.FALLBACK, SniSelectionPolicy.REJECT);
        Http2Client rejectingClient = client(rejectingServer.port());
        try {
            assertThrows(RuntimeException.class, () -> {
                try (Http2ClientResponse response = rejectingClient.get()
                        .header(HeaderValues.create(HeaderNames.HOST, SNI_HOST))
                        .sni(sni -> sni.mode(SniMode.EXPLICIT)
                                .host(OTHER_HOST))
                        .request()) {
                    response.status();
                }
            });
        } finally {
            rejectingClient.closeResource();
            rejectingServer.stop();
        }
    }

    @Test
    void rejectsMissingSniDuringTlsHandshake() {
        WebServer rejectingServer = rejectingServer(SniSelectionPolicy.REJECT, SniSelectionPolicy.FALLBACK);
        Http2Client rejectingClient = client(rejectingServer.port());
        try {
            assertThrows(RuntimeException.class, () -> {
                try (Http2ClientResponse response = rejectingClient.get()
                        .header(HeaderValues.create(HeaderNames.HOST, SNI_HOST))
                        .sni(sni -> sni.mode(SniMode.DISABLED))
                        .request()) {
                    response.status();
                }
            });
        } finally {
            rejectingClient.closeResource();
            rejectingServer.stop();
        }
    }

    private static Tls tls() {
        Keys keys = Keys.builder()
                .keystore(store -> store
                        .passphrase("password")
                        .keystore(Resource.create("server.p12")))
                .build();
        return Tls.builder()
                .privateKey(keys)
                .privateKeyCertChain(keys)
                .build();
    }

    private static WebServer rejectingServer(SniSelectionPolicy missing, SniSelectionPolicy unmatched) {
        Tls tls = tls();
        return WebServerConfig.builder()
                .host("localhost")
                .port(0)
                .shutdownHook(false)
                .tls(tls)
                .sni(sni -> sni.missing(missing)
                        .unmatched(unmatched))
                .addVirtualHost(virtualHost -> virtualHost
                        .host(SNI_HOST)
                        .tls(tls))
                .addProtocol(Http2Config.builder().build())
                .routing(routing -> routing.route(Http2Route.route(Method.GET, "/", (req, res) -> res.send("ok"))))
                .build()
                .start();
    }

    private static Http2Client client(int port) {
        return Http2Client.builder()
                .baseUri(URI.create("https://localhost:" + port + "/"))
                .shareConnectionCache(false)
                .tls(clientTls -> clientTls
                        .trustAll(true)
                        .endpointIdentificationAlgorithm("NONE"))
                .build();
    }

    private static String sniHosts(ServerRequest request) {
        return request.sniRequestedHost().orElse("") + "|" + request.sniMatchedHost().orElse("");
    }
}
