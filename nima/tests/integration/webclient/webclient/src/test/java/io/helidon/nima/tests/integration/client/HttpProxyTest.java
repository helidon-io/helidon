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

import static io.helidon.common.http.Http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.InetSocketAddress;
import java.net.ProxySelector;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.Proxy;
import io.helidon.nima.webclient.Proxy.ProxyType;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ServerTest
class HttpProxyTest {

    private static final String PROXY_HOST = "localhost";
    private int proxyPort;
    private HttpProxy httpProxy;

    private final Http1Client client;
    
    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(GET, "/get", Routes::get);
    }

    @BeforeEach
    public void before() {
        httpProxy = new HttpProxy(0);
        httpProxy.start();
        proxyPort = httpProxy.connectedPort();
    }

    @AfterEach
    public void after() {
        httpProxy.stop();
    }

    HttpProxyTest(Http1Client client) {
        this.client = client;
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
    void testNoHosts() {
        Proxy proxy = Proxy.builder().host(PROXY_HOST).port(proxyPort).addNoProxy(PROXY_HOST).build();
        try (Http1ClientResponse response = client.get("/get").proxy(proxy).request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
        }
        assertThat(httpProxy.counter(), is(0));
    }

    @Test
    void testNoProxyTypeButHasHost() {
        Proxy proxy = Proxy.builder().host(PROXY_HOST).port(proxyPort).build();
        successVerify(proxy);
    }

    @Test
    void testProxyNoneTypeButHasHost() {
        Proxy proxy = Proxy.builder().type(ProxyType.NONE).host(PROXY_HOST).port(proxyPort).build();
        successVerify(proxy);
    }

    @Test
    void testSimpleProxy() {
        Proxy proxy = Proxy.builder().type(ProxyType.HTTP).host(PROXY_HOST).port(proxyPort).build();
        successVerify(proxy);
    }

    @Test
    void testSystemProxy() {
        ProxySelector original = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress(PROXY_HOST, proxyPort)));
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
        assertThat(httpProxy.counter(), is(1));
    }

    private void noProxyChecks() {
        try (Http1ClientResponse response = client.get("/get").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
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
