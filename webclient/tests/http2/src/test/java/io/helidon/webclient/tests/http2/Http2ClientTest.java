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

import io.helidon.common.configurable.Resource;
import io.helidon.http.Http;
import io.helidon.http.Http.Header;
import io.helidon.http.Http.HeaderNames;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class Http2ClientTest {
    private static final String MESSAGE = "Hello World!";
    private static final String TEST_HEADER_NAME = "custom_header";
    private static final String TEST_HEADER_VALUE = "as!fd";
    private static final Header TEST_HEADER = Http.Headers.create(HeaderNames.create(TEST_HEADER_NAME), TEST_HEADER_VALUE);
    private final Http1Client http1Client;
    private final Supplier<Http2Client> tlsClient;
    private final Supplier<Http2Client> plainClient;

    Http2ClientTest(WebServer server, Http1Client http1Client) {
        int plainPort = server.port();
        int tlsPort = server.port("https");
        this.http1Client = http1Client;
        Tls insecureTls = Tls.builder()
                // insecure setup, as we have self-signed certificate
                .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                .trustAll(true)
                .build();
        this.tlsClient = () -> Http2Client.builder()
                .baseUri("https://localhost:" + tlsPort + "/")
                .tls(insecureTls)
                .shareConnectionCache(false)
                .build();
        this.plainClient = () -> Http2Client.builder()
                .baseUri("http://localhost:" + plainPort + "/")
                .shareConnectionCache(false)
                .build();
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        Keys privateKeyConfig = Keys.builder()
                .keystore(keystore -> keystore
                        .keystore(Resource.create("certificate.p12"))
                        .keystorePassphrase("helidon"))
                .build();

        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig.privateKey().get())
                .privateKeyCertChain(privateKeyConfig.certChain())
                .build();

        serverBuilder.putSocket("https",
                                socketBuilder -> socketBuilder.tls(tls));
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        // explicitly on HTTP/2 only, to make sure we do upgrade
        router.route(Http2Route.route(Http.Method.GET, "/", (req, res) -> res.header(TEST_HEADER)
                .send(MESSAGE)));
    }

    @Test
    void testHttp1() {
        // make sure the HTTP/1 route is not working
        try (Http1ClientResponse response = http1Client
                .get("/")
                .request()) {

            assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
        }
    }

    @Test
    void testUpgrade() {
        try (Http2ClientResponse response = plainClient.get()
                .get("/")
                .request()) {

            assertThat(response.status(), is(Http.Status.OK_200));
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

            assertThat(response.status(), is(Http.Status.OK_200));
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

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.as(String.class), is(MESSAGE));
            assertThat(TEST_HEADER + " header must be present in response",
                       response.headers().contains(TEST_HEADER), is(true));
        }
    }
}
