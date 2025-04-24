/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class Ipv6ServerTest {
    public static final Duration TIMEOUT = Duration.ofSeconds(5);
    public static final String MESSAGE = "Hello World!";
    private final int plainPort;
    private final int tlsPort;
    private final Tls clientTls;

    Ipv6ServerTest(WebServer server) {
        this.plainPort = server.port();
        this.tlsPort = server.port("https");
        this.clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        Keys privateKeyConfig = Keys.builder()
                .keystore(store -> store
                        .passphrase("password")
                        .keystore(Resource.create("server.p12")))
                .build();

        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig)
                .privateKeyCertChain(privateKeyConfig)
                .build();

        serverBuilder.putSocket("https",
                                socketBuilder -> socketBuilder.tls(tls).host("[::0]"));

        serverBuilder.host("[::0]");
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(GET, "/", (req, res) -> res.send(MESSAGE));
    }

    @SetUpRoute("https")
    static void routerHttps(HttpRouting.Builder router) {
        router.route(GET, "/", (req, res) -> res.send(MESSAGE));
    }

    @Test
    void testH2c() throws IOException, InterruptedException {
        try (var client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(TIMEOUT)
                .sslContext(clientTls.sslContext())
                .build()) {
            var response = client.send(HttpRequest.newBuilder()
                                               .timeout(TIMEOUT)
                                               .uri(URI.create("http://[::1]:" + plainPort))
                                               .GET()
                                               .build(),
                                       HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), is(200));
            assertThat(response.body(), is(MESSAGE));
        }
    }

    @Test
    void testH2() throws IOException, InterruptedException {
        try (var client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(TIMEOUT)
                .sslContext(clientTls.sslContext())
                .build()) {
            var response = client.send(HttpRequest.newBuilder()
                                               .timeout(TIMEOUT)
                                               .uri(URI.create("https://[::1]:" + tlsPort))
                                               .GET()
                                               .build(),
                                       HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), is(200));
            assertThat(response.body(), is(MESSAGE));
        }

    }

    @Test
    void testH1() throws IOException, InterruptedException {
        try (var client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(TIMEOUT)
                .sslContext(clientTls.sslContext())
                .build()) {
            var response = client.send(HttpRequest.newBuilder()
                                               .timeout(TIMEOUT)
                                               .uri(URI.create("http://[::1]:" + plainPort))
                                               .GET()
                                               .build(),
                                       HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), is(200));
            assertThat(response.body(), is(MESSAGE));
        }
    }

    @Test
    void testH1Tls() throws IOException, InterruptedException {
        try (var client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(TIMEOUT)
                .sslContext(clientTls.sslContext())
                .build()) {
            var response = client.send(HttpRequest.newBuilder()
                                               .timeout(TIMEOUT)
                                               .uri(URI.create("https://[::1]:" + tlsPort))
                                               .GET()
                                               .build(),
                                       HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), is(200));
            assertThat(response.body(), is(MESSAGE));
        }

    }
}
