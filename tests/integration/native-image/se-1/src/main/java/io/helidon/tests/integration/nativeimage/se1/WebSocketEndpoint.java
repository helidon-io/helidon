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
 *
 */

package io.helidon.tests.integration.nativeimage.se1;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;


public class WebSocketEndpoint extends Endpoint {

    private static final Logger LOGGER = Logger.getLogger(WebSocketEndpoint.class.getName());

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {

        StringBuilder sb = new StringBuilder();

        LOGGER.log(Level.INFO, "Session " + session.getId());
        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
                LOGGER.log(Level.INFO, "WS Receiving " + message);
                if (message.contains("SEND")) {
                    sendTextMessage(session, sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(message);
                }
            }
        });
    }

    private void sendTextMessage(Session session, String msg) {
        try {
            session.getBasicRemote().sendText(msg);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Message sending failed", e);
        }
    }
}
