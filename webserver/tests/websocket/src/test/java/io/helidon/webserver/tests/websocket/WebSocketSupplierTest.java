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

package io.helidon.webserver.tests.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Checks that a {@code WsListener} supplier is called exactly once per connection.
 * In particular, that the same listener is shared between the connection upgrade
 * and the connection handling phases.
 */
@ServerTest
class WebSocketSupplierTest {

    private final int port;
    private final HttpClient client;

    private static final AtomicInteger supplierCalls = new AtomicInteger();

    WebSocketSupplierTest(WebServer server) {
        port = server.port();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @SetUpRoute
    static void router(Router.RouterBuilder<?> router) {
        Supplier<WsListener> supplier = () -> {
            EchoService service = new EchoService();
            supplierCalls.getAndIncrement();
            return service;
        };
        router.addRouting(WsRouting.builder().endpoint("/echo", supplier));
    }

    @Test
    void testSingleSupplier() throws Exception {
        java.net.http.WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/echo"),
                        new java.net.http.WebSocket.Listener() {})
                .get(5, TimeUnit.SECONDS);
        ws.request(10);
        ws.sendText("Hello", true).get(5, TimeUnit.SECONDS);
        ws.sendClose(WsCloseCodes.NORMAL_CLOSE, "normal").get(5, TimeUnit.SECONDS);

        // enforce one listener per connection -- single call to supplier
        assertThat(supplierCalls.get(), is(1));
    }
}
