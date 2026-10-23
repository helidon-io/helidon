/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.websocket;

import java.util.List;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.Method;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.Socket;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsListener;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@ServerTest
class ConfiguredListenerRoutingTest {
    private static final WsListener ENDPOINT = new WsListener() { };

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.backlog(37)
                .writeQueueLength(17)
                .routing(http -> http.get("/server", (_, response) -> response.send("server")))
                .addRouting(WsRouting.builder().endpoint("/old", ENDPOINT))
                .putSocket("named", ListenerConfig.builder()
                        .host("localhost")
                        .port(0)
                        .writeQueueLength(23)
                        .addRouting(WsRouting.builder().endpoint("/old", ENDPOINT))
                        .build());
    }

    @SetUpRoute
    static void defaults(ListenerConfig.Builder listener, HttpRouting.Builder http, WsRouting.Builder ws) {
        listener.writeQueueLength(19);
        http.get("/route", (_, response) -> response.send("route"));
        ws.endpoint("/new", ENDPOINT);
    }

    @SetUpRoute("named")
    static void named(ListenerConfig.Builder listener, HttpRouting.Builder http, WsRouting.Builder ws) {
        listener.writeQueueLength(29);
        http.get("/named", (_, response) -> response.send("named"));
        ws.endpoint("/new", ENDPOINT);
    }

    @Test
    void defaultHttpRoutesSurvive(Http1Client client) {
        assertAll(() -> assertThat(client.get("/server").requestEntity(String.class), is("server")),
                  () -> assertThat(client.get("/route").requestEntity(String.class), is("route")));
    }

    @Test
    void defaultListenerSettingsSurvive(WebServer server) {
        assertAll(() -> assertThat(server.prototype().backlog(), is(37)),
                  () -> assertThat(server.prototype().writeQueueLength(), is(19)));
    }

    @Test
    void defaultUsesCollectedWebSocketRouting(WebServer server) throws Exception {
        assertAll(() -> assertThat(upgrade(server.port(), "/new"), is("HTTP/1.1 101 Switching Protocols")),
                  () -> assertThat(upgrade(server.port(), "/old"), is("HTTP/1.1 404 Not Found")));
    }

    @Test
    void namedHttpRouteSurvives(@Socket("named") Http1Client client) {
        assertThat(client.get("/named").requestEntity(String.class), is("named"));
    }

    @Test
    void namedListenerSettingsSurvive(WebServer server) {
        assertThat(server.prototype().sockets().get("named").writeQueueLength(), is(29));
    }

    @Test
    void namedUsesCollectedWebSocketRouting(WebServer server) throws Exception {
        assertAll(() -> assertThat(upgrade(server.port("named"), "/new"), is("HTTP/1.1 101 Switching Protocols")),
                  () -> assertThat(upgrade(server.port("named"), "/old"), is("HTTP/1.1 404 Not Found")));
    }

    private static String upgrade(int port, String path) throws Exception {
        try (var client = SocketHttpClient.create(port)) {
            client.request(Method.GET, path, "", List.of("Connection: Upgrade",
                                                       "Upgrade: websocket",
                                                       "Sec-WebSocket-Version: 13",
                                                       "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ=="));
            return client.socketReader().readLine();
        }
    }
}
