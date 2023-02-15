/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.client;

import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.common.pki.KeyConfig;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.common.tls.TlsClientAuth;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.Proxy;
import io.helidon.nima.webclient.Proxy.ProxyType;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.concurrent.Executors;

@ServerTest
class HttpsProxyTest {

    private static final String PROXY_HOST = "localhost";
    private static final int PROXY_PORT = 18081;
    private static final String USER = "test";
    private static final String PASSWORD = "password";
    private static final HttpProxy httpProxy = new HttpProxy(PROXY_PORT, USER, PASSWORD);

    private final Http1Client client;
    private final WebServer server;

    @SetUpServer
    static void server(WebServer.Builder builder) {
        KeyConfig privateKeyConfig = KeyConfig.keystoreBuilder()
                .keystore(Resource.create("server.p12"))
                .keystorePassphrase("password")
                .build();

        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig.privateKey().get())
                .privateKeyCertChain(privateKeyConfig.certChain())
                .trustAll(true)
                .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                .build();

        builder.tls(tls);
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(GET, "/get", Routes::get);
    }

    @BeforeAll
    public static void beforeAll() {
        assertThat(httpProxy.start(), is(true));
    }

    @AfterAll
    public static void afterAll() {
        assertThat(httpProxy.stop(), is(true));
    }

    HttpsProxyTest(WebServer server) {
        this.server = server;
        int port = server.port();

        Tls tls = Tls.builder()
                .trustAll(true)
                .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                .build();

        client = WebClient.builder()
                .baseUri("https://localhost:" + port)
                .tls(tls)
                .build();
    }

    @Test
    void testNoProxy() {
        try (Http1ClientResponse response = client.get("/get").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
        }
    }

    @Test
    void testNoProxyType() {
        Proxy proxy = Proxy.builder().username(USER)
                .password(PASSWORD.toCharArray()).host(PROXY_HOST).port(PROXY_PORT).build();
        successVerify(proxy);
    }

    @Test
    void testSimpleProxy() {
        Proxy proxy = Proxy.builder().type(ProxyType.HTTP).username(USER)
                .password(PASSWORD.toCharArray()).host(PROXY_HOST).port(PROXY_PORT).build();
        successVerify(proxy);
    }

    @Test
    @Disabled
    void testUnauthorized() {
        Proxy proxy = Proxy.builder().type(ProxyType.HTTP).username(USER)
                .password("password!".toCharArray()).host(PROXY_HOST).port(PROXY_PORT).build();
        error(proxy, Http.Status.UNAUTHORIZED_401);
    }

    private void successVerify(Proxy proxy) {
        try (Http1ClientResponse response = client.get("/get").proxy(proxy).request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
        }
    }

    private void error(Proxy proxy, Http.Status status) {
        try (Http1ClientResponse response = client.get("/get").proxy(proxy).request()) {
            assertThat(response.status(), is(status));
        }
    }

    private static class Routes {
        private static String get() {
            return "Hello";
        }
    }
}
