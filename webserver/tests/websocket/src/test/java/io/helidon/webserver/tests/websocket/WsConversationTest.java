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

package io.helidon.webserver.tests.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.websocket.WsRouting;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@ServerTest
@TestMethodOrder(MethodOrderer.MethodName.class)
class WsConversationTest {
    private static final long WAIT_SECONDS = 10L;
    private static WsConversationService service;

    private final int port;
    private final HttpClient client;

    WsConversationTest(WebServer server) {
        port = server.port();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(WAIT_SECONDS))
                .build();
    }

    @SetUpRoute
    static void router(Router.RouterBuilder<?> router) {
        service = new WsConversationService();
        router.addRouting(WsRouting.builder().endpoint("/conversation", service));
    }

    @Test
    void testConversation1() throws Exception {
        testConversation(WsConversation.fromString("SND TEXT 'hi'\n"));
    }

    @Test
    void testConversation100() throws Exception {
        testConversation(WsConversation.createRandom(100));
    }

    @Test
    void testConversation1000() throws Exception {
        testConversation(WsConversation.createRandom(1000));
    }

    private void testConversation(WsConversation conversation) throws Exception {
        service.conversation(conversation);
        WsConversationClient.WsConversationListener listener = new WsConversationClient.WsConversationListener();
        java.net.http.WebSocket socket = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/conversation"), listener)
                .get(WAIT_SECONDS, TimeUnit.SECONDS);
        try (WsConversationClient client = new WsConversationClient(socket, listener, conversation.dual())) {
            client.run();
        }
    }
}
