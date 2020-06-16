
/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.messaging.mp.example;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import io.helidon.common.reactive.Single;

/**
 * Register all WebSocket connection as subscribers
 * of broadcasting {@link java.util.concurrent.SubmissionPublisher}
 * in the {@link io.helidon.messaging.mp.example.MsgProcessingBean}.
 * <p>
 * When connection is closed, cancel subscription and remove reference.
 */
@ServerEndpoint("/ws/messages")
public class WebSocketEndpoint {

    private static final Logger LOGGER = Logger.getLogger(WebSocketEndpoint.class.getName());

    private final Map<String, Single<Void>> subscriberRegister = new HashMap<>();

    @Inject
    private MsgProcessingBean msgProcessingBean;

    @OnOpen
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        System.out.println("New WebSocket client connected with session " + session.getId());

        Single<Void> single = msgProcessingBean.subscribeMulti()
                // Watch for errors coming from upstream
                .onError(throwable -> LOGGER.log(Level.SEVERE, "Upstream error!", throwable))
                // Send every item coming from upstream over web socket
                .forEach(s -> sendTextMessage(session, s));

        //Save forEach single promise for later cancellation
        subscriberRegister.put(session.getId(), single);
    }

    @OnClose
    public void onClose(final Session session, final CloseReason closeReason) {
        LOGGER.info("Closing session " + session.getId());
        // Properly unsubscribe from SubmissionPublisher
        Optional.ofNullable(subscriberRegister.remove(session.getId()))
                .ifPresent(Single::cancel);
    }

    private void sendTextMessage(Session session, String msg) {
        try {
            session.getBasicRemote().sendText(msg);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Message sending over WebSocket failed", e);
        }
    }
}
