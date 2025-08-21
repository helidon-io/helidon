/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.helidon.webclient.websocket.WsClient;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
public class WebSocketClientErrorTest {

    private static final int TIMEOUT = 1000;

    private final int port;
    private final WsClient wsClient;

    WebSocketClientErrorTest(WebServer webServer) {
        this.port = webServer.port();
        this.wsClient = WsClient.builder()
                .readTimeout(Duration.ofMillis(TIMEOUT))     // expiration -> onError
                .build();
    }

    @SetUpRoute
    static void router(Router.RouterBuilder<?> router) {
        var wsRouting = WsRouting.builder();
        wsRouting.endpoint("/endpoint", () -> new WsListener() {
            // no-op to trigger a read timeout in WsClient
        });
        router.addRouting(wsRouting);
    }

    /**
     * Verify that any low-level exceptions such as a socket timeout are correctly
     * propagated to the {@link WsListener#onError} method.
     */
    @Test
    void testSocketTimeout() {
        var future = new CompletableFuture<Void>();
        wsClient.connect("http://localhost:" + port + "/endpoint", new WsListener() {
            @Override
            public void onError(WsSession session, Throwable e) {
                future.completeExceptionally(e.getCause());     // unwrap UncheckedIOException
            }
        });
        assertThrows(SocketTimeoutException.class, () -> {
            try {
                future.get(TIMEOUT * 2, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                throw e.getCause();         // unwrap SocketTimeoutException
            }
        });
    }
}
