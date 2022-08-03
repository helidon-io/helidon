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
package io.helidon.webserver.websocket.test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.websocket.WebSocketRouting;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.websocket.server.ServerEndpointConfig;

public class WSTest {

    private static WebServer webServer;

    @BeforeAll
    public static void startServer() throws Exception {
        LogConfig.configureRuntime();
        webServer = WebServer.builder()
                .defaultSocket(s -> s
                                .bindAddress("localhost")
                                .port(8080)
                )
                .routing(r -> r
                        .get("/http-ctx", (req, res) -> res.send("Hello http!/http-ctx"))
                        .get((req, res) -> res.send("Hello http!"))
                )
                .addRouting(WebSocketRouting.builder()
                        .endpoint("/ws-conf", ServerEndpointConfig.Builder.create(ConfiguredEndpoint.class, "/echo").build())
                        .endpoint("/ws-annotated", AnnotatedEndpoint.class)// also /echo
                        .build()
                )
                .build()
                .start()
                .await(Duration.ofSeconds(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/ws-conf/echo", "/ws-annotated/echo"})
    void testWsEcho(String context) throws InterruptedException {
        List<String> recevied = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch countDownLatch = new CountDownLatch(2);
        WebSocket ws = HttpClient
                .newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + webServer.port() + context), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(Long.MAX_VALUE);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        recevied.add(String.valueOf(data));
                        countDownLatch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                }).join();
        ws.sendText("I am waiting here!", true);
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
        MatcherAssert.assertThat(recevied, Matchers.contains("Hello this is server calling on open!", "I am waiting here!"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/http-ctx"})
    void httpRoute(String context) throws IOException, InterruptedException {
        HttpResponse<String> res = HttpClient
                .newHttpClient()
                .send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + webServer.port() + context))
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertEquals("Hello http!" + context, res.body());
    }

    @AfterAll
    static void afterAll() {
        webServer.shutdown().await(Duration.ofSeconds(10));
    }
}
