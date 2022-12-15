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

package io.helidon.nima.testing.junit5.webserver;

import java.net.URI;

import io.helidon.common.context.Context;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.ListenerConfiguration;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

@ServerTest
class TestServerTest {
    private final SocketHttpClient socketClient;
    private final Http1Client httpClient;
    private final WebServer server;
    private final URI uri;
    private final URI customUri;

    TestServerTest(SocketHttpClient socketClient,
                   Http1Client httpClient,
                   WebServer server,
                   @Socket("@default") URI defaultUri,
                   @Socket("socket") URI customUri) {
        this.socketClient = socketClient;
        this.httpClient = httpClient;
        this.server = server;
        this.uri = defaultUri;
        this.customUri = customUri;
    }

    @SetUpServer
    static void setUp(WebServer.Builder builder) {
        Context serverContext = Context.create();
        serverContext.register(TestServerTest.class, "server");

        builder.context(serverContext);
    }

    @SetUpRoute("socket")
    static void routing(ListenerConfiguration.Builder l, HttpRouting.Builder r) {
        l.writeQueueLength(10);
    }

    @SetUpRoute("socket2")
    static void routing2(HttpRouting.Builder r, ListenerConfiguration.Builder l) {
        l.writeQueueLength(10);
    }

    @Test
    void testSetUpCalled() {
        assertThat(server.port("socket"), greaterThan(0));
    }

    @Test
    void testSocketClientInjected() {
        assertThat(socketClient, notNullValue());
    }

    @Test
    void testSocketClientInjectedParameter(SocketHttpClient socketClient) {
        assertThat(socketClient, notNullValue());
    }

    @Test
    void testSocketClientNamedInjectedParameter(@Socket("socket") SocketHttpClient socketClient) {
        assertThat(socketClient, notNullValue());
    }

    @Test
    void testSocketClientMixedInjectedParameter(@Socket("socket") SocketHttpClient socketClient1,
                                                SocketHttpClient socketClient2,
                                                @Socket("@default") SocketHttpClient socketClient3,
                                                @Socket("socket") SocketHttpClient socketClient4) {
        assertThat(socketClient1, notNullValue());
        assertThat(socketClient2, notNullValue());
        assertThat(socketClient1, not(sameInstance(socketClient2)));
        assertThat(socketClient2, sameInstance(socketClient3));
        assertThat(socketClient1, sameInstance(socketClient4));
    }

    @Test
    void testHttpClientInjected() {
        assertThat(httpClient, notNullValue());
    }

    @Test
    void testHttpClientInjectedParameter(Http1Client httpClient) {
        assertThat(httpClient, notNullValue());
    }

    @Test
    void testHttpClientNamedInjectedParameter(@Socket("socket") Http1Client httpClient) {
        assertThat(httpClient, notNullValue());
    }

    @Test
    void testHttpClientMixedInjectedParameter(@Socket("socket") Http1Client httpClient1,
                                              Http1Client httpClient2,
                                              @Socket("@default") Http1Client httpClient3,
                                              @Socket("socket") Http1Client httpClient4) {
        assertThat(httpClient1, notNullValue());
        assertThat(httpClient2, notNullValue());
        assertThat(httpClient1, not(sameInstance(httpClient2)));
        assertThat(httpClient2, sameInstance(httpClient3));
        assertThat(httpClient1, sameInstance(httpClient4));
    }

    @Test
    void testServerInjected() {
        assertThat(server, notNullValue());
        assertThat(server.port(), greaterThan(0));
    }

    @Test
    void testServerInjectedParameter(WebServer server) {
        assertThat(server, notNullValue());
        assertThat(server.port(), greaterThan(0));
    }

    @Test
    void testUriInjected() {
        assertThat(uri, notNullValue());
        assertThat(uri.getHost(), is("localhost"));
        assertThat(uri.getPort(), is(server.port()));
    }

    @Test
    void testCustomUriInjected() {
        assertThat(customUri, notNullValue());
        assertThat(customUri.getHost(), is("localhost"));
        assertThat(customUri.getPort(), is(server.port("socket")));
    }

    @Test
    void testUriInjectedParameter(URI uri) {
        assertThat(uri, notNullValue());
        assertThat(uri.getHost(), is("localhost"));
        assertThat(uri.getPort(), is(server.port()));
    }
}
