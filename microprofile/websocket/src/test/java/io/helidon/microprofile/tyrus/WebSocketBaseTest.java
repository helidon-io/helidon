/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tyrus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket.Listener;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.nima.websocket.CloseCodes;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Base class for WebSocket/Tyrus echo tests.
 */
public abstract class WebSocketBaseTest {
    private static final int WAIT_MILLIS = 50000000;
    private static final int INVOCATION_COUNTER = 10;
    private static final String HELLO_WORLD = "Hello World";

    static SeContainer container;

    private final HttpClient client;

    WebSocketBaseTest() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    static void destroyClass() {
        container.close();
    }

    public abstract String context();

    public int port() {
        ServerCdiExtension cdiExtension = CDI.current().getBeanManager().getExtension(ServerCdiExtension.class);
        return cdiExtension.port();
    }

    @Test
    public void testEchoAnnot() throws Exception {
        EchoListener listener = new EchoListener();
        URI echoUri = URI.create("ws://localhost:" + port() + context() + "/echoAnnot");
        java.net.http.WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(echoUri, listener)
                .get(WAIT_MILLIS, TimeUnit.SECONDS);

        await(ws.sendText(HELLO_WORLD, true));
        assertThat(listener.awaitEcho(), is(HELLO_WORLD));

        ws.sendClose(CloseCodes.NORMAL_CLOSE, "normal").get();
    }

    public void await(CompletableFuture<?> future) throws Exception {
        future.get(WAIT_MILLIS, TimeUnit.MILLISECONDS);
    }

    @ServerEndpoint("/echoAnnot")
    public static class EchoEndpointAnnot {
        private static final Logger LOGGER = Logger.getLogger(EchoEndpointAnnot.class.getName());

        @OnOpen
        public void onOpen(Session session) {
            LOGGER.info("OnOpen called");
        }

        @OnMessage
        public void echo(Session session, String message) throws Exception {
            LOGGER.info("OnMessage called '" + message + "'");
            session.getBasicRemote().sendObject(message);
        }

        @OnError
        public void onError(Throwable t, Session session) {
            LOGGER.info("OnError called");
        }

        @OnClose
        public void onClose(Session session) {
            LOGGER.info("OnClose called");
        }
    }

    public static class EchoEndpointProg extends Endpoint {
        private static final Logger LOGGER = Logger.getLogger(EchoEndpointProg.class.getName());

        @Override
        public void onOpen(Session session, EndpointConfig endpointConfig) {
            LOGGER.info("OnOpen called");
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    LOGGER.info("OnMessage called '" + message + "'");
                    try {
                        session.getBasicRemote().sendObject(message);
                    } catch (Exception e) {
                        LOGGER.info(e.getMessage());
                    }
                }
            });
        }

        @Override
        public void onError(Session session, Throwable thr) {
            LOGGER.log(Level.SEVERE, "OnError called", thr);
            super.onError(session, thr);
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            LOGGER.info("OnClose called");
            super.onClose(session, closeReason);
        }
    }

    private static class EchoListener implements Listener {

        private final CompletableFuture<String> echoFuture = new CompletableFuture<>();

        @Override
        public void onOpen(java.net.http.WebSocket webSocket) {
            webSocket.request(INVOCATION_COUNTER);
        }

        @Override
        public CompletionStage<?> onText(java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
            echoFuture.complete(String.valueOf(data));
            return null;
        }

       String awaitEcho() throws Exception {
            return echoFuture.get(WAIT_MILLIS, TimeUnit.MILLISECONDS);
        }
    }
}
