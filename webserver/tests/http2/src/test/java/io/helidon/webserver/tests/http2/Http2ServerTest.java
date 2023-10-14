/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
import java.util.Optional;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class Http2ServerTest {
    public static final String MESSAGE = "Hello World!";
    private static final String TEST_HEADER_NAME = "custom_header";
    private static final String TEST_HEADER_VALUE = "as!fd";
    private static final Header TEST_HEADER = HeaderValues.create(HeaderNames.create(TEST_HEADER_NAME), TEST_HEADER_VALUE);
    private final int plainPort;
    private final int tlsPort;
    private final Http1Client http1Client;
    private final Tls clientTls;

    Http2ServerTest(WebServer server, Http1Client http1Client) {
        this.plainPort = server.port();
        this.tlsPort = server.port("https");
        this.http1Client = http1Client;
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
                                socketBuilder -> socketBuilder.tls(tls));
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        // explicitly on HTTP/2 only, to make sure we do upgrade
        router.route(Http2Route.route(GET, "/", (req, res) -> res.header(TEST_HEADER).send(MESSAGE)))
                .route(Http2Route.route(GET, "/query", Http2ServerTest::queryEndpoint));
    }

    @SetUpRoute("https")
    static void routerHttps(HttpRouting.Builder router) {
        // explicitly on HTTP/2 only, to make sure we do upgrade
        router.route(Http2Route.route(GET, "/", (req, res) -> res.header(TEST_HEADER).send(MESSAGE)))
                .route(Http2Route.route(GET, "/query", Http2ServerTest::queryEndpoint));
    }

    @Test
    void testHttp1() {
        // make sure the HTTP/1 route is not working

        Http1ClientResponse response = http1Client.get("/")
                .request();

        assertThat(response.status(), is(Status.NOT_FOUND_404));
    }

    @Test
    void testUpgrade() throws IOException, InterruptedException {
        HttpResponse<String> response = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .send(HttpRequest.newBuilder()
                              .timeout(Duration.ofSeconds(5))
                              .uri(URI.create("http://localhost:" + plainPort + "/"))
                              .GET()
                              .build(),
                      HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), is(MESSAGE));
        assertThat(response.headers().firstValue(TEST_HEADER_NAME), is(Optional.of(TEST_HEADER_VALUE)));
    }

    @Test
    void testQueryParam() throws IOException, InterruptedException {
        HttpResponse<String> response = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .send(HttpRequest.newBuilder()
                              .timeout(Duration.ofSeconds(5))
                              .uri(URI.create("http://localhost:" + plainPort + "/query?param=paramValue"))
                              .GET()
                              .build(),
                      HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), is("paramValue"));
    }

    @Test
    void testAppProtocol() throws IOException, InterruptedException {
        HttpResponse<String> response = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .sslContext(clientTls.sslContext())
                .build()
                .send(HttpRequest.newBuilder()
                              .timeout(Duration.ofSeconds(5))
                              .uri(URI.create("https://localhost:" + tlsPort + "/"))
                              .GET()
                              .build(),
                      HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), is(MESSAGE));
        assertThat(response.headers().firstValue(TEST_HEADER_NAME), is(Optional.of(TEST_HEADER_VALUE)));
        System.clearProperty("jdk.internal.httpclient.disableHostnameVerification");
    }

    @Test
    void testAppProtocol2() throws IOException, InterruptedException {
        HttpResponse<String> response = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .sslContext(clientTls.sslContext())
                .build()
                .send(HttpRequest.newBuilder()
                              .timeout(Duration.ofSeconds(5))
                              .uri(URI.create("https://localhost:" + tlsPort + "/query?param=paramValue"))
                              .GET()
                              .build(),
                      HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), is("paramValue"));
        System.clearProperty("jdk.internal.httpclient.disableHostnameVerification");
    }

    private static void queryEndpoint(ServerRequest req, ServerResponse res) {
        res.send(req.query().get("param"));
    }
}
