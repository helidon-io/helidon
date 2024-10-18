/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.tests;

import java.net.InetSocketAddress;
import java.net.ProxySelector;

import io.helidon.http.Status;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientRequestBase;
import io.helidon.webclient.api.HttpClient;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.Proxy.ProxyType;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ServerTest
class HttpProxyTest {

    private static final String PROXY_HOST = "localhost";
    private int proxyPort;
    private HttpProxy httpProxy;

    private final HttpClient<?> clientHttp1;
    private final HttpClient<?> clientHttp2;

    HttpProxyTest(WebServer server) {
        String uri = "http://localhost:" + server.port();
        this.clientHttp1 = Http1Client.builder()
                .baseUri(uri)
                .proxy(Proxy.noProxy())
                .build();
        this.clientHttp2 = Http2Client.builder()
                .baseUri(uri)
                .proxy(Proxy.noProxy())
                .build();
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(GET, "/get", Routes::get);
    }

    @BeforeEach
    void before() {
        httpProxy = new HttpProxy(0);
        httpProxy.start();
        proxyPort = httpProxy.connectedPort();
        assertThat(httpProxy.counter(), is(0));
    }

    @AfterEach
    void after() {
        httpProxy.stop();
    }

    @Test
    void testDefaultIsSystem1() {
        ProxySelector original = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress(PROXY_HOST, proxyPort)));
            Proxy proxy = Proxy.builder().build();
            assertEquals(ProxyType.SYSTEM, proxy.type());
            successVerify(proxy, clientHttp1);
        } finally {
            ProxySelector.setDefault(original);
        }
    }

    @Test
    void testDefaultIsSystem2() {
        ProxySelector original = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress(PROXY_HOST, proxyPort)));
            Proxy proxy = Proxy.builder().build();
            assertEquals(ProxyType.SYSTEM, proxy.type());
            successVerify(proxy, clientHttp2);
        } finally {
            ProxySelector.setDefault(original);
        }
    }

    @Test
    void testNoProxy1() {
        noProxyChecks(clientHttp1);
    }

    @Test
    void testNoProxy2() {
        noProxyChecks(clientHttp2);
    }

    @Test
    void testNoHosts1() {
        noHosts(clientHttp1);
    }

    @Test
    void testNoHosts2() {
        noHosts(clientHttp2);
    }

    @Test
    void testNoProxyTypeButHasHost1() {
        Proxy proxy = Proxy.builder().forceHttpConnect(true).host(PROXY_HOST).port(proxyPort).build();
        successVerify(proxy, clientHttp1);
    }

    @Test
    void testNoProxyTypeButHasHost2() {
        Proxy proxy = Proxy.builder().forceHttpConnect(true).host(PROXY_HOST).port(proxyPort).build();
        successVerify(proxy, clientHttp2);
    }

    @Test
    void testProxyNoneTypeButHasHost1() {
        Proxy proxy = Proxy.builder().forceHttpConnect(true).type(ProxyType.NONE).host(PROXY_HOST).port(proxyPort).build();
        successVerify(proxy, clientHttp1);
    }

    @Test
    void testProxyNoneTypeButHasHost2() {
        Proxy proxy = Proxy.builder().forceHttpConnect(true).type(ProxyType.NONE).host(PROXY_HOST).port(proxyPort).build();
        successVerify(proxy, clientHttp2);
    }

    @Test
    void testSimpleProxy1() {
        Proxy proxy = Proxy.builder().forceHttpConnect(true).type(ProxyType.HTTP).host(PROXY_HOST).port(proxyPort).build();
        successVerify(proxy, clientHttp1);
    }

    @Test
    void testSimpleProxy2() {
        Proxy proxy = Proxy.builder().forceHttpConnect(true).type(ProxyType.HTTP).host(PROXY_HOST).port(proxyPort).build();
        successVerify(proxy, clientHttp2);
    }

    @Test
    void testSystemProxy1() {
        ProxySelector original = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress(PROXY_HOST, proxyPort)));
            Proxy proxy = Proxy.create();
            successVerify(proxy, clientHttp1);
        } finally {
            ProxySelector.setDefault(original);
        }
    }

    @Test
    void testSystemProxy2() {
        ProxySelector original = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress(PROXY_HOST, proxyPort)));
            Proxy proxy = Proxy.create();
            successVerify(proxy, clientHttp2);
        } finally {
            ProxySelector.setDefault(original);
        }
    }

    private void noHosts(HttpClient<?> client) {
        Proxy proxy = Proxy.builder().host(PROXY_HOST).port(proxyPort).addNoProxy(PROXY_HOST).build();
        try (HttpClientResponse response = client.get("/get").proxy(proxy).request()) {
            assertThat(response.status(), is(Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
        }
        assertThat(httpProxy.counter(), is(0));
    }

    private void successVerify(Proxy proxy, HttpClient<?> client) {
        ClientRequest<?> request = client.get("/get").proxy(proxy);
        try (HttpClientResponse response = request.request()) {
            assertThat(response.status(), is(Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
        }
        boolean proxyConnection = request.headers().contains(ClientRequestBase.PROXY_CONNECTION);
        if (client == clientHttp1) {
            assertTrue(proxyConnection, "HTTP1 requires Proxy-Connection header");
        } else {
            assertFalse(proxyConnection, "HTTP2 does not allow Proxy-Connection header");
        }
        assertThat(httpProxy.counter(), is(1));
    }

    private void noProxyChecks(HttpClient<?> client) {
        try (HttpClientResponse response = client.get("/get").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
        }
        assertThat(httpProxy.counter(), is(0));
    }

    private static class Routes {
        private static String get() {
            return "Hello";
        }
    }
}
