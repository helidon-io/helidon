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
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.webserver.websocket.WsRouting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;

@ServerTest
class WebSocketTest {
    private static EchoService service;

    private final int port;
    private final HttpClient client;

    private volatile boolean isNormalClose = true;

    WebSocketTest(WebServer server) {
        port = server.port();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @SetUpRoute
    static void router(Router.RouterBuilder<?> router) {
        service = new EchoService();
        router.addRouting(WsRouting.builder().endpoint("/echo", service));
    }

    @BeforeEach
    void resetClosed() {
        service.resetClosed();
    }

    @AfterEach
    void checkNormalClose() {
        if (isNormalClose) {
            EchoService.CloseInfo closeInfo = service.closeInfo();
            assertThat(closeInfo, notNullValue());
            assertThat(closeInfo.status(), is(WsCloseCodes.NORMAL_CLOSE));
            assertThat(closeInfo.reason(), is("normal"));
        }
    }

    @Test
    void testOnce() throws Exception {
        TestListener listener = new TestListener();

        java.net.http.WebSocket ws = client.newWebSocketBuilder()
                .subprotocols("chat", "mute")
                // .header(EXTENSIONS.defaultCase(), "webserver") rejected by client
                .buildAsync(URI.create("ws://localhost:" + port + "/echo"), listener)
                .get(5, TimeUnit.SECONDS);
        assertThat(ws.getSubprotocol(), is("chat"));    // negotiated
        ws.request(10);

        ws.sendText("Hello", true).get(5, TimeUnit.SECONDS);
        ws.sendClose(WsCloseCodes.NORMAL_CLOSE, "normal").get(5, TimeUnit.SECONDS);

        List<String> results = listener.results().received;
        assertThat(results, contains("Hello"));
    }

    @Test
    void testMulti() throws Exception {
        TestListener listener = new TestListener();

        java.net.http.WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/echo"), listener)
                .get(5, TimeUnit.SECONDS);
        ws.request(10);

        ws.sendText("First", true).get(5, TimeUnit.SECONDS);
        ws.sendText("Second", true).get(5, TimeUnit.SECONDS);
        ws.sendClose(WsCloseCodes.NORMAL_CLOSE, "normal").get(5, TimeUnit.SECONDS);
        assertThat(listener.results().received, contains("First", "Second"));
    }

    @Test
    void testFragmentedAndMulti() throws Exception {
        TestListener listener = new TestListener();

        java.net.http.WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/echo"), listener)
                .get(5, TimeUnit.SECONDS);
        ws.request(10);

        ws.sendText("First", false).get(5, TimeUnit.SECONDS);
        ws.sendText("Second", true).get(5, TimeUnit.SECONDS);
        ws.sendText("Third", true).get(5, TimeUnit.SECONDS);
        ws.sendClose(WsCloseCodes.NORMAL_CLOSE, "normal").get(5, TimeUnit.SECONDS);

        assertThat(listener.results().received, contains("FirstSecond", "Third"));
    }

    /**
     * Tests sending long text messages. Note that any message longer than 16K
     * will be chunked into 16K pieces by the JDK client.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void testLongTextMessages() throws Exception {
        TestListener listener = new TestListener();

        java.net.http.WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/echo"), listener)
                .get(5, TimeUnit.SECONDS);
        ws.request(10);

        String s100 = randomString(100);            // less than one byte
        ws.sendText(s100, true).get(5, TimeUnit.SECONDS);
        String s10000 = randomString(10000);        // less than two bytes
        ws.sendText(s10000, true).get(5, TimeUnit.SECONDS);
        ws.sendClose(WsCloseCodes.NORMAL_CLOSE, "normal").get(5, TimeUnit.SECONDS);

        assertThat(listener.results().received, contains(s100, s10000));
    }

    /**
     * Test sending a single text message that will fit into a single JDK client frame
     * of 16K but exceeds max-frame-length set in application.yaml for the server.
     *
     * @throws Exception if an error occurs
     */
    @Test
    void testTooLongTextMessage() throws Exception {
        TestListener listener = new TestListener();

        java.net.http.WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/echo"), listener)
                .get(5, TimeUnit.SECONDS);
        ws.request(10);

        String s10001 = randomString(10001);      // over the limit of 10000
        ws.sendText(s10001, true).get(5, TimeUnit.SECONDS);
        assertThat(listener.results().statusCode, is(1009));
        assertThat(listener.results().reason, is("Payload too large"));
        isNormalClose = false;
    }

    private static class TestListener implements java.net.http.WebSocket.Listener {

        record Results(int statusCode, String reason, List<String> received) {
        }

        final List<String> received = new LinkedList<>();
        final List<String> buffered = new LinkedList<>();
        private final CompletableFuture<Results> response = new CompletableFuture<>();

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
        public CompletionStage<?> onClose(java.net.http.WebSocket webSocket, int statusCode, String reason) {
            response.complete(new Results(statusCode, reason, received));
            return null;
        }

        Results results() throws ExecutionException, InterruptedException, TimeoutException {
            return response.get(10, TimeUnit.SECONDS);
        }
    }

    private static String randomString(int length) {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        return new Random().ints(leftLimit, rightLimit + 1)
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
