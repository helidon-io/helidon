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

package io.helidon.webclient.tests.http2;

import java.util.function.Supplier;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@ServerTest
class Http2ClientTest {
    private static final String MESSAGE = "Hello World!";
    private static final String TEST_HEADER_NAME = "custom_header";
    private static final String TEST_HEADER_VALUE = "as!fd";
    private static final Header TEST_HEADER = HeaderValues.create(HeaderNames.create(TEST_HEADER_NAME), TEST_HEADER_VALUE);
    private final Http1Client http1Client;
    private final Supplier<Http2Client> tlsClient;
    private final Supplier<Http2Client> plainClient;
    private final int tlsPort;
    private final int plainPort;

    Http2ClientTest(WebServer server, Http1Client http1Client) {
        plainPort = server.port();
        tlsPort = server.port("https");
        this.http1Client = http1Client;
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        this.tlsClient = () -> Http2Client.builder()
                .baseUri("https://localhost:" + tlsPort + "/")
                .shareConnectionCache(false)
                .tls(clientTls)
                .build();
        this.plainClient = () -> Http2Client.builder()
                .baseUri("http://localhost:" + plainPort + "/")
                .shareConnectionCache(false)
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
        router.route(Http2Route.route(Method.GET, "/", (req, res) -> res.header(TEST_HEADER)
                .send(MESSAGE)));
    }

    @SetUpRoute("https")
    static void routerHttps(HttpRouting.Builder router) {
        // explicitly on HTTP/2 only, to make sure we do upgrade
        router.route(Http2Route.route(Method.GET, "/", (req, res) -> res.header(TEST_HEADER)
                .send(MESSAGE)));
    }
    @Test
    void testHttp1() {
        // make sure the HTTP/1 route is not working
        try (Http1ClientResponse response = http1Client
                .get("/")
                .request()) {

            assertThat(response.status(), is(Status.NOT_FOUND_404));
        }
    }

    @Test
    void testSchemeValidation() {
        try (var r = Http2Client.builder()
                .baseUri("test://localhost:" + plainPort + "/")
                .shareConnectionCache(false)
                .build()
                .get("/")
                .request()) {

            fail("Should have failed because of invalid scheme.");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), startsWith("Not supported scheme test"));
        }
    }

    @Test
    void testUpgrade() {
        try (Http2ClientResponse response = plainClient.get()
                .get("/")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(MESSAGE));
            assertThat(TEST_HEADER + " header must be present in response",
                       response.headers().contains(TEST_HEADER), is(true));
        }
    }

    @Test
    void testAppProtocol() {
        try (Http2ClientResponse response = tlsClient.get()
                .get("/")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(MESSAGE));
            assertThat(TEST_HEADER + " header must be present in response",
                       response.headers().contains(TEST_HEADER), is(true));
        }
    }

    @Test
    void testPriorKnowledge() {
        try (Http2ClientResponse response = tlsClient.get()
                .get("/")
                .priorKnowledge(true)
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(MESSAGE));
            assertThat(TEST_HEADER + " header must be present in response",
                       response.headers().contains(TEST_HEADER), is(true));
        }
    }
}
