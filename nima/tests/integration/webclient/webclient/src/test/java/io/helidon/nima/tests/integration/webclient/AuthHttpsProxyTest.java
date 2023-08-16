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

package io.helidon.nima.tests.integration.webclient;

import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.common.pki.Keys;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http2.webclient.Http2Client;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.api.HttpClient;
import io.helidon.nima.webclient.api.HttpClientResponse;
import io.helidon.nima.webclient.api.Proxy;
import io.helidon.nima.webclient.api.Proxy.ProxyType;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig.Builder;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

@ServerTest
class AuthHttpsProxyTest {

    private static final String PROXY_HOST = "localhost";
    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private int proxyPort;
    private HttpProxy httpProxy;

    private final HttpClient<?> clientHttp1;
    private final HttpClient<?> clientHttp2;

    AuthHttpsProxyTest(WebServer server) throws CertificateException {
        int port = server.port();

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(Resource.create("certificate.pem").bytes()));
        Tls clientTls = Tls.builder()
                .trust(trust -> trust.addCert(cert))
                .build();

        this.clientHttp1 = Http1Client.builder()
                .baseUri("https://localhost:" + port)
                .tls(clientTls)
                .proxy(Proxy.noProxy())
                .build();
        this.clientHttp2 = Http2Client.builder()
                .baseUri("https://localhost:" + port)
                .tls(clientTls)
                .proxy(Proxy.noProxy())
                .build();
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(GET, "/get", Routes::get);
    }

    @SetUpServer
    static void server(Builder builder) {
        Keys privateKeyConfig = Keys.builder()
                .keystore(store -> store
                        .passphrase("password")
                        .keystore(Resource.create("server.p12")))
                .build();

        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig)
                .privateKeyCertChain(privateKeyConfig)
                .build();

        builder.host("localhost").tls(tls);
    }

    @BeforeEach
    void before() {
        httpProxy = new HttpProxy(0, USER, PASSWORD);
        httpProxy.start();
        proxyPort = httpProxy.connectedPort();
        assertThat(httpProxy.counter(), is(0));
    }

    @AfterEach
    void after() {
        httpProxy.stop();
    }

    @Test
    void testUserPasswordCorrect1() {
        Proxy proxy = Proxy.builder().type(ProxyType.HTTP).host(PROXY_HOST)
                .username(USER).password(PASSWORD.toCharArray()).port(proxyPort).build();
        successVerify(proxy, clientHttp1);
    }

    @Test
    void testUserPasswordCorrect2() {
        Proxy proxy = Proxy.builder().type(ProxyType.HTTP).host(PROXY_HOST)
                .username(USER).password(PASSWORD.toCharArray()).port(proxyPort).build();
        successVerify(proxy, clientHttp2);
    }

    @Test
    void testUserPasswordNotCorrect1() {
        Proxy proxy = Proxy.builder().type(ProxyType.HTTP).host(PROXY_HOST)
                .username(USER).password("wrong".toCharArray()).port(proxyPort).build();
        failVerify(proxy, clientHttp1);
    }

    @Test
    void testUserPasswordNotCorrect2() {
        Proxy proxy = Proxy.builder().type(ProxyType.HTTP).host(PROXY_HOST)
                .username(USER).password("wrong".toCharArray()).port(proxyPort).build();
        failVerify(proxy, clientHttp2);
    }

    private void failVerify(Proxy proxy, HttpClient<?> client) {
        try (HttpClientResponse response = client.get("/get").proxy(proxy).request()) {
            fail("Expected exception");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("Proxy sent wrong HTTP response code: 401 Unauthorized"));
        }
        assertThat(httpProxy.counter(), is(1));
    }

    private void successVerify(Proxy proxy, HttpClient<?> client) {
        try (HttpClientResponse response = client.get("/get").proxy(proxy).request()) {
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
