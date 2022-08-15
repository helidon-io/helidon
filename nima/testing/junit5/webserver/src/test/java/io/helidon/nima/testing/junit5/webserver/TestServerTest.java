/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

@ServerTest
class TestServerTest {
    private final SocketHttpClient socketClient;
    private final Http1Client httpClient;
    private final WebServer server;
    private final URI uri;

    TestServerTest(SocketHttpClient socketClient, Http1Client httpClient, WebServer server, URI uri) {
        this.socketClient = socketClient;
        this.httpClient = httpClient;
        this.server = server;
        this.uri = uri;
    }

    @SetUpServer
    static void setUp(WebServer.Builder builder) {
        builder.socket("socket", it -> it.port(0));
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
    void testHttpClientInjected() {
        assertThat(httpClient, notNullValue());
    }

    @Test
    void testHttpClientInjectedParameter(Http1Client httpClient) {
        assertThat(httpClient, notNullValue());
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
        if (server != null) {
            assertThat(uri.getPort(), is(server.port()));
        }
    }

    @Test
    void testUriInjectedParameter(URI uri) {
        assertThat(uri, notNullValue());
        assertThat(uri.getHost(), is("localhost"));
        if (server != null) {
            assertThat(uri.getPort(), is(server.port()));
        }
    }
}
