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

package io.helidon.declarative.tests.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.websocket.WsCloseCodes;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;

@ServerTest
public class DeclarativeWebSocketTest {
    private final int port;
    private final EchoEndpoint endpoint;
    private final HttpClient client;

    public DeclarativeWebSocketTest(WebServer server, EchoEndpoint endpoint) {
        this.port = server.port();
        this.endpoint = endpoint;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Test
    public void testWebSocket() throws InterruptedException, ExecutionException, TimeoutException {
        TestListener listener = new TestListener();

        java.net.http.WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/websocket/echo/test"), listener)
                .get(5, TimeUnit.SECONDS);
        ws.request(10);

        ws.sendText("Hello", false).get(5, TimeUnit.SECONDS);
        ws.sendText(" World", true).get(5, TimeUnit.SECONDS);
        ws.sendClose(WsCloseCodes.NORMAL_CLOSE, "normal").get(5, TimeUnit.SECONDS);

        List<String> results = listener.textResults().received;
        assertThat(results, contains("Hello World"));
        assertThat(endpoint.lastError(), nullValue());
        assertThat(endpoint.lastUser(), is("test"));
        assertThat(endpoint.lastClose(), is(new EchoEndpoint.Close("normal", WsCloseCodes.NORMAL_CLOSE)));
        HttpPrologue prologue = endpoint.lastHttpPrologue();
        assertThat(prologue.method(), is(Method.GET));
        endpoint.reset();
    }

    // repeating the same test to make sure string builder is not shared
    @Test
    public void testWebSocket2() throws InterruptedException, ExecutionException, TimeoutException {
        TestListener listener = new TestListener();

        java.net.http.WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/websocket/echo/test"), listener)
                .get(5, TimeUnit.SECONDS);
        ws.request(10);

        ws.sendText("Hello", false).get(5, TimeUnit.SECONDS);
        ws.sendText(" World", true).get(5, TimeUnit.SECONDS);
        ws.sendClose(WsCloseCodes.NORMAL_CLOSE, "normal").get(5, TimeUnit.SECONDS);

        List<String> results = listener.textResults().received;
        assertThat(results, contains("Hello World"));
        assertThat(endpoint.lastError(), nullValue());
        assertThat(endpoint.lastUser(), is("test"));
        assertThat(endpoint.lastClose(), is(new EchoEndpoint.Close("normal", WsCloseCodes.NORMAL_CLOSE)));
        HttpPrologue prologue = endpoint.lastHttpPrologue();
        assertThat(prologue.method(), is(Method.GET));
        endpoint.reset();
    }

    @Test
    public void testBinary() throws InterruptedException, ExecutionException, TimeoutException {
        TestListener listener = new TestListener();

        java.net.http.WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/websocket/echo/test"), listener)
                .get(5, TimeUnit.SECONDS);
        ws.request(10);

        ws.sendBinary(ByteBuffer.wrap("Hello".getBytes()), false).get(5, TimeUnit.SECONDS);
        ws.sendBinary(ByteBuffer.wrap(" World".getBytes()), true).get(5, TimeUnit.SECONDS);
        ws.sendClose(WsCloseCodes.NORMAL_CLOSE, "normal").get(5, TimeUnit.SECONDS);

        List<byte[]> binaryResults = listener.binaryResults().data();
        int size = binaryResults.stream()
                .mapToInt(it -> it.length)
                .sum();
        byte[] result = new byte[size];
        int position = 0;
        for (byte[] binaryResult : binaryResults) {
            System.arraycopy(binaryResult, 0, result, position, binaryResult.length);
            position += binaryResult.length;
        }
        assertThat(new String(result), is("Hello World"));
    }

    private static class TestListener implements java.net.http.WebSocket.Listener {

        final List<String> received = new LinkedList<>();
        final List<String> buffered = new LinkedList<>();
        final List<byte[]> buffers = new LinkedList<>();

        private final CompletableFuture<TextResults> textResponse = new CompletableFuture<>();
        private final CompletableFuture<BinaryResults> binaryResponse = new CompletableFuture<>();

        @Override
        public void onOpen(java.net.http.WebSocket webSocket) {
            webSocket.request(10);
        }

        @Override
        public CompletionStage<?> onText(java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
            buffered.add(data.toString());

            if (last) {
                received.add(String.join("", buffered));
                buffered.clear();
            }

            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] buffer = new byte[data.remaining()];
            data.get(buffer);
            buffers.add(buffer);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(java.net.http.WebSocket webSocket, int statusCode, String reason) {
            textResponse.complete(new TextResults(statusCode, reason, received));
            binaryResponse.complete(new BinaryResults(statusCode, reason, buffers));
            return null;
        }

        TextResults textResults() throws ExecutionException, InterruptedException, TimeoutException {
            return textResponse.get(10, TimeUnit.SECONDS);
        }

        BinaryResults binaryResults() throws ExecutionException, InterruptedException, TimeoutException {
            return binaryResponse.get(10, TimeUnit.SECONDS);
        }

        record TextResults(int statusCode, String reason, List<String> received) {
        }

        record BinaryResults(int statusCode, String reason, List<byte[]> data) {
        }
    }
}
