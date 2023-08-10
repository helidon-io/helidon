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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.websocket.WsConfig;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.webserver.websocket.WsUpgrader;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class WebSocketOriginTest {
    private final HttpClient client;
    private final int port;

    public WebSocketOriginTest(URI uri) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.port = uri.getPort();
    }

    @SetUpServer
    static void updateServer(WebServerConfig.Builder builder) {
        builder.addConnectionSelector(Http1ConnectionSelector.builder()
                                              .config(Http1Config.create())
                                              .addUpgrader(WsUpgrader.create(WsConfig.builder()
                                                                                     .addOrigin("WebServerTest")
                                                                                     .build()))
                                              .build());
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(WsRouting.builder()
                                  .endpoint("/single", WebSocketOriginTest::single));
    }

    @Test
    void testSingle() throws ExecutionException, InterruptedException, TimeoutException {
        List<String> received = new LinkedList<>();
        CompletableFuture<Boolean> wsCompleted = new CompletableFuture<>();

        java.net.http.WebSocket webSocket = client.newWebSocketBuilder()
                .header("Origin", "WebServerTest")
                .buildAsync(URI.create("ws://localhost:" + port + "/single"),
                            new java.net.http.WebSocket.Listener() {
                                @Override
                                public CompletionStage<?> onText(java.net.http.WebSocket webSocket,
                                                                 CharSequence data,
                                                                 boolean last) {
                                    received.add(data.toString());
                                    wsCompleted.complete(last);
                                    return wsCompleted;
                                }
                            })
                .get(5, TimeUnit.SECONDS);
        webSocket.sendText("lower", true);
        webSocket.sendClose(WsCloseCodes.NORMAL_CLOSE, "finished");
        Boolean wasLast = wsCompleted.get(5, TimeUnit.SECONDS);
        assertThat(wasLast, is(true));
        assertThat(received, hasItem("LOWER"));
    }

    private static WsListener single() {
        return new WsListener() {
            @Override
            public void onMessage(WsSession session, String text, boolean last) {
                session.send(text.toUpperCase(Locale.ROOT), true);
            }
        };
    }
}
