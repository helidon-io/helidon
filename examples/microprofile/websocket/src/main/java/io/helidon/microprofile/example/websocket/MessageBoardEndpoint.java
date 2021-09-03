/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.example.websocket;

import java.io.IOException;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

/**
 * Class MessageBoardEndpoint.
 */
@ServerEndpoint(
        value = "/websocket",
        encoders = { MessageBoardEndpoint.UppercaseEncoder.class }
)
public class MessageBoardEndpoint {
    private static final Logger LOGGER = Logger.getLogger(MessageBoardEndpoint.class.getName());

    @Inject
    private MessageQueue messageQueue;

    /**
     * OnOpen call.
     *
     * @param session The websocket session.
     * @throws IOException If error occurs.
     */
    @OnOpen
    public void onOpen(Session session) throws IOException {
        LOGGER.info("OnOpen called");
    }

    /**
     * OnMessage call.
     *
     * @param session The websocket session.
     * @param message The message received.
     * @throws Exception If error occurs.
     */
    @OnMessage
    public void onMessage(Session session, String message) throws Exception {
        LOGGER.info("OnMessage called '" + message + "'");

        // Send all messages in the queue
        if (message.equals("SEND")) {
            while (!messageQueue.isEmpty()) {
                session.getBasicRemote().sendObject(messageQueue.pop());
            }
        }
    }

    /**
     * OnError call.
     *
     * @param t The throwable.
     */
    @OnError
    public void onError(Throwable t) {
        LOGGER.info("OnError called");
    }

    /**
     * OnError call.
     *
     * @param session The websocket session.
     */
    @OnClose
    public void onClose(Session session) {
        LOGGER.info("OnClose called");
    }

    /**
     * Uppercase encoder.
     */
    public static class UppercaseEncoder implements Encoder.Text<String> {

        @Override
        public String encode(String s) {
            LOGGER.info("UppercaseEncoder encode called");
            return s.toUpperCase();
        }

        @Override
        public void init(EndpointConfig config) {
        }

        @Override
        public void destroy() {
        }
    }
}
