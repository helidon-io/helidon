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

package io.helidon.webserver.tests.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.websocket.WsConfig;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsCloseCodes;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class DisabledWsConfigTest {
    private final int port;

    DisabledWsConfigTest(WebServer server) {
        this.port = server.port();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.protocolsDiscoverServices(false)
                .addProtocol(Http1Config.create())
                .addProtocol(new TestWsConfig("disabled", 1024, false))
                .addProtocol(new TestWsConfig("enabled", 5, true));
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(WsRouting.builder().endpoint("/echo", new EchoService()));
    }

    @Test
    void disabledConfigurationDoesNotSetFrameLimit() throws Exception {
        CloseListener listener = new CloseListener();
        WebSocket webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/echo"), listener)
                .get(5, TimeUnit.SECONDS);

        webSocket.sendText("too-large", true).get(5, TimeUnit.SECONDS);

        assertThat(listener.closeCode().get(5, TimeUnit.SECONDS), is(WsCloseCodes.TOO_BIG));
    }

    private record TestWsConfig(String name, int maxFrameLength, boolean enabled) implements WsConfig {
        @Override
        public Set<String> origins() {
            return Set.of();
        }
    }

    private static final class CloseListener implements WebSocket.Listener {
        private final CompletableFuture<Integer> closeCode = new CompletableFuture<>();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            closeCode.completeExceptionally(new AssertionError("Oversized WebSocket frame was accepted"));
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeCode.complete(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closeCode.completeExceptionally(error);
        }

        CompletableFuture<Integer> closeCode() {
            return closeCode;
        }
    }
}
