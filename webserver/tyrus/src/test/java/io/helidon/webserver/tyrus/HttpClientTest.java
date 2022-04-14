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

package io.helidon.webserver.tyrus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.websocket.server.ServerEndpointConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class HttpClientTest extends TyrusSupportBaseTest {
    @BeforeAll
    static void startServer() throws ExecutionException, InterruptedException, TimeoutException {
        ServerEndpointConfig.Builder builder = ServerEndpointConfig.Builder.create(
                EchoEndpoint.class, "/");
        webServer(true, builder.build());
    }

    @Test
    void testJdkClient() throws ExecutionException, InterruptedException, TimeoutException {
        URI uri = URI.create("ws://localhost:" + webServer().port() + "/tyrus/echo");
        ClientListener listener = new ClientListener();

        WebSocket webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(uri, listener)
                .get();

        webSocket.sendText("message", true);
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "finished");

        String response = listener.await();
        assertThat(response, is("message"));
    }

    private static class ClientListener implements WebSocket.Listener {
        private final CompletableFuture<String> future = new CompletableFuture<>();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            future.complete(data.toString());
            return CompletableFuture.completedFuture(data);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            future.complete(reason);
            return CompletableFuture.completedFuture(reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            future.completeExceptionally(error);
        }

        public String await() throws ExecutionException, InterruptedException, TimeoutException {
            return future.get(10, TimeUnit.SECONDS);
        }
    }
}
