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

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.buffers.BufferData;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A websocket service that is driven by a conversation instance.
 */
class WsConversationService implements WsListener {
    private static final Long WAIT_SECONDS = 10L;
    private static final Logger LOGGER = Logger.getLogger(WsConversationService.class.getName());

    private WsConversation conversation;
    private Iterator<WsAction> actions;
    private BlockingQueue<WsAction> received;

    WsConversationService() {
    }

    WsConversationService(WsConversation conversation) {
        this.conversation = conversation;
    }

    WsConversation conversation() {
        return conversation;
    }

    void conversation(WsConversation conversation) {
        this.conversation = conversation;
    }

    @Override
    public void onOpen(WsSession session) {
        Objects.requireNonNull(conversation);

        received = new LinkedBlockingQueue<>();
        actions = conversation.actions();
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()).submit(() -> {
            while (actions.hasNext()) {
                WsAction action = actions.next();
                switch (action.op) {
                    case SND -> sendMessage(action, session);
                    case RCV -> waitMessage(action, session);
                }
            }
        });
    }

    @Override
    public void onClose(WsSession session, int status, String reason) {
        received = null;
        actions = null;
        conversation = null;
    }

    @Override
    public void onMessage(WsSession session, String text, boolean last) {
        received.add(new WsAction(WsAction.Operation.RCV, WsAction.OperationType.TEXT, text));
    }

    @Override
    public void onMessage(WsSession session, BufferData buffer, boolean last) {
        int n = buffer.available();
        received.add(new WsAction(WsAction.Operation.RCV, WsAction.OperationType.BINARY, buffer.readString(n, UTF_8)));
    }

    @Override
    public void onError(WsSession session, Throwable t) {
    }

    private void sendMessage(WsAction action, WsSession session) {
        switch (action.opType) {
            case TEXT -> session.send(action.message, true);
            case BINARY -> session.send(BufferData.create(action.message.getBytes(UTF_8)), true);
        }
        LOGGER.log(Level.FINE, () -> "Server: " + action);
    }

    private void waitMessage(WsAction action, WsSession session) {
        try {
            LOGGER.log(Level.FINE, () -> "Server: " + action);
            WsAction r = received.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assert r != null;
            if (!r.equals(action)) {
                session.terminate();
            }
        } catch (Exception e) {
            session.terminate();
        }
    }
}
