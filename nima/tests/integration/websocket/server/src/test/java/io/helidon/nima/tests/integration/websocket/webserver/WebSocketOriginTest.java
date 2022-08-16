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

package io.helidon.nima.tests.integration.websocket.webserver;

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

import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webserver.Router;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http1.Http1ConnectionProvider;
import io.helidon.nima.websocket.CloseCodes;
import io.helidon.nima.websocket.WsListener;
import io.helidon.nima.websocket.WsSession;
import io.helidon.nima.websocket.webserver.WebSocketRouting;
import io.helidon.nima.websocket.webserver.WsUpgradeProvider;

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
    static void updateServer(WebServer.Builder builder) {
        builder.addConnectionProvider(Http1ConnectionProvider.builder()
                                              .addUpgradeProvider(WsUpgradeProvider.builder()
                                                                          .addOrigin("WarpTest")
                                                                          .build())
                                              .build());
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(WebSocketRouting.builder()
                                  .endpoint("/single", WebSocketOriginTest::single));
    }

    @Test
    void testSingle() throws ExecutionException, InterruptedException, TimeoutException {
        List<String> received = new LinkedList<>();
        CompletableFuture<Boolean> wsCompleted = new CompletableFuture<>();

        java.net.http.WebSocket webSocket = client.newWebSocketBuilder()
                .header("Origin", "WarpTest")
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
        webSocket.sendClose(CloseCodes.NORMAL_CLOSE, "finished");
        Boolean wasLast = wsCompleted.get(5, TimeUnit.SECONDS);
        assertThat(wasLast, is(true));
        assertThat(received, hasItem("LOWER"));
    }

    private static WsListener single() {
        return new WsListener() {
            @Override
            public void receive(WsSession session, String text, boolean last) {
                session.send(text.toUpperCase(Locale.ROOT), true);
            }
        };
    }
}
