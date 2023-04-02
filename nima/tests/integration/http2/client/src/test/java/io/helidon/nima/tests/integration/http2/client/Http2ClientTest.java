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

package io.helidon.nima.tests.integration.http2.client;

import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.pki.KeyConfig;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http2.webclient.Http2;
import io.helidon.nima.http2.webclient.Http2Client;
import io.helidon.nima.http2.webclient.Http2ClientResponse;
import io.helidon.nima.http2.webserver.Http2Route;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class Http2ClientTest {
     static final String MESSAGE = "Hello World!";
    private static final String TEST_HEADER_NAME = "custom_header";
    private static final String TEST_HEADER_VALUE = "as!fd";
    private static final HeaderValue TEST_HEADER = Header.create(Header.create(TEST_HEADER_NAME), TEST_HEADER_VALUE);
    private final Http1Client http1Client;
    private final Http2Client tlsClient;
    private final Http2Client plainClient;

    Http2ClientTest(WebServer server, Http1Client http1Client) {
        int plainPort = server.port();
        int tlsPort = server.port("https");
        this.http1Client = http1Client;
        Tls insecureTls = Tls.builder()
                // insecure setup, as we have self-signed certificate
                .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                .trustAll(true)
                .build();
        this.tlsClient = WebClient.builder(Http2.PROTOCOL)
                .baseUri("https://localhost:" + tlsPort + "/")
                .tls(insecureTls)
                .build();
        this.plainClient = WebClient.builder(Http2.PROTOCOL)
                .baseUri("http://localhost:" + plainPort + "/")
                .build();
    }

    @SetUpServer
    static void setUpServer(WebServer.Builder serverBuilder) {
        KeyConfig privateKeyConfig = KeyConfig.keystoreBuilder()
                .keystore(Resource.create("certificate.p12"))
                .keystorePassphrase("helidon")
                .build();

        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig.privateKey().get())
                .privateKeyCertChain(privateKeyConfig.certChain())
                .build();

        serverBuilder.socket("https",
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

        Http1ClientResponse response = http1Client.get("/")
                .request();

        assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
    }

    @Test
    void testUpgrade() {
        Http2ClientResponse response = plainClient.get("/")
                .request();

        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.as(String.class), is(MESSAGE));
        assertThat(TEST_HEADER + " header must be present in response",
                   response.headers().contains(TEST_HEADER), is(true));
    }

    @Test
    void testAppProtocol() {
        Http2ClientResponse response = tlsClient.get("/")
                .request();

        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.as(String.class), is(MESSAGE));
        assertThat(TEST_HEADER + " header must be present in response",
                   response.headers().contains(TEST_HEADER), is(true));
    }

    @Test
    void testPriorKnowledge() {
        Http2ClientResponse response = tlsClient.get("/")
                .priorKnowledge(true)
                .request();

        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.as(String.class), is(MESSAGE));
        assertThat(TEST_HEADER + " header must be present in response",
                   response.headers().contains(TEST_HEADER), is(true));
    }
}
