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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;

import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.common.pki.Keys;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.api.Proxy;
import io.helidon.nima.webclient.api.Proxy.ProxyType;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig.Builder;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class HttpsProxyTest {

    private static final String PROXY_HOST = "localhost";
    private static int PROXY_PORT;
    private static HttpProxy httpProxy;

    private final Http1Client client;

    HttpsProxyTest(WebServer server) {
        int port = server.port();

        Tls tls = Tls.builder()
                .trustAll(true)
                .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                .build();

        client = Http1Client.builder()
                .baseUri("https://localhost:" + port)
                .tls(tls)
                .build();
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        httpProxy = new HttpProxy(0);
        httpProxy.start();
        PROXY_PORT = httpProxy.connectedPort();
    }

    @AfterAll
    public static void afterAll() {
        httpProxy.stop();
    }

    @SetUpServer
    static void server(Builder builder) {
        Keys privateKeyConfig = Keys.builder()
                .keystore(keystore -> keystore
                        .keystore(Resource.create("server.p12"))
                        .passphrase("password"))
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

    @Test
    void testNoProxy() {
        noProxyChecks();
    }

    @Test
    void testNoProxyTypeDefaultsToNone() {
        noProxyChecks();
    }

    @Test
    void testNoProxyTypeButHasHost() {
        Proxy proxy = Proxy.builder().host(PROXY_HOST).port(PROXY_PORT).build();
        successVerify(proxy);
    }

    @Test
    void testProxyNoneTypeButHasHost() {
        Proxy proxy = Proxy.builder().type(ProxyType.NONE).host(PROXY_HOST).port(PROXY_PORT).build();
        successVerify(proxy);
    }

    @Test
    void testSimpleProxy() {
        Proxy proxy = Proxy.builder().type(ProxyType.HTTP).host(PROXY_HOST).port(PROXY_PORT).build();
        successVerify(proxy);
    }

    @Test
    void testSystemProxy() {
        ProxySelector original = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress(PROXY_HOST, PROXY_PORT)));
            Proxy proxy = Proxy.create();
            successVerify(proxy);
        } finally {
            ProxySelector.setDefault(original);
        }
    }

    private void successVerify(Proxy proxy) {
        try (Http1ClientResponse response = client.get("/get").proxy(proxy).request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
        }
    }

    private void noProxyChecks() {
        try (Http1ClientResponse response = client.get("/get").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
        }
    }

    private static class Routes {
        private static String get() {
            return "Hello";
        }
    }
}
