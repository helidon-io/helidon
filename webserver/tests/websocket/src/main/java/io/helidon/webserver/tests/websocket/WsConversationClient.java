/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.helidon.webserver.tests.websocket.WsAction.Operation.RCV;
import static io.helidon.webserver.tests.websocket.WsAction.OperationType.BINARY;
import static io.helidon.webserver.tests.websocket.WsAction.OperationType.TEXT;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A websocket client that is driven by a conversation instance.
 */
class WsConversationClient implements Runnable, AutoCloseable {
    private static final Long WAIT_SECONDS = 10L;
    private static final Logger LOGGER = Logger.getLogger(WsConversationClient.class.getName());

    private final WebSocket socket;
    private final WsConversation conversation;
    private final WsConversationListener listener;

    WsConversationClient(WebSocket socket, WsConversationListener listener, WsConversation conversation) {
        this.socket = socket;
        this.conversation = conversation;
        this.listener = listener;
    }

    @Override
    public void run() {
        Iterator<WsAction> it = conversation.actions();
        while (it.hasNext()) {
            WsAction action = it.next();
            switch (action.op) {
                case SND -> sendMessage(action);
                case RCV -> waitMessage(action);
            }
        }
    }

    @Override
    public void close() {
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye")
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void sendMessage(WsAction action) {
        try {
            switch (action.opType) {
                case TEXT -> socket.sendText(action.message, true).get(10, TimeUnit.SECONDS);
                case BINARY -> socket.sendBinary(ByteBuffer.wrap(action.message.getBytes(UTF_8)), true)
                        .get(10, TimeUnit.SECONDS);
            }
            LOGGER.log(Level.FINE, () -> "Client: " + action);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void waitMessage(WsAction action) {
        try {
            LOGGER.log(Level.FINE, () -> "Client: " + action);
            WsAction r = listener.received.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assert r != null;
            if (!r.equals(action)) {
                socket.abort();
            }
        } catch (Exception e) {
            socket.abort();
        }
    }

    static class WsConversationListener implements java.net.http.WebSocket.Listener {
        BlockingQueue<WsAction> received;

        @Override
        public void onOpen(java.net.http.WebSocket webSocket) {
            webSocket.request(Integer.MAX_VALUE);
            received = new LinkedBlockingQueue<>();
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            WsAction action = new WsAction(RCV, BINARY, new String(bytes, UTF_8));
            received.add(action);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onText(java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
            WsAction action = new WsAction(RCV, TEXT, data.toString());
            received.add(action);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(java.net.http.WebSocket webSocket, int statusCode, String reason) {
            received = null;
            return null;
        }
    }
}
