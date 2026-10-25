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
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsListener;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ConfiguredServerRoutingTest {
    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.writeQueueLength(17)
                .routing(http -> http.get("/server", (_, response) -> response.send("server")))
                .addRouting(WsRouting.builder().endpoint("/configured", new WsListener() { }));
    }

    @Test
    void serverHttpRouteSurvives(Http1Client client) {
        assertThat(client.get("/server").requestEntity(String.class), is("server"));
    }

    @Test
    void serverListenerSettingsSurvive(WebServer server) {
        assertThat(server.prototype().writeQueueLength(), is(17));
    }

    @Test
    void serverWebSocketRouteSurvives(SocketHttpClient client) throws Exception {
        client.request(Method.GET, "/configured", "", List.of("Connection: Upgrade",
                                                            "Upgrade: websocket",
                                                            "Sec-WebSocket-Version: 13",
                                                            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ=="));
        assertThat(client.socketReader().readLine(), is("HTTP/1.1 101 Switching Protocols"));
    }
}
