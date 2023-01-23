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

package io.helidon.microprofile.tyrus;

import java.lang.System.Logger.Level;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;

public  class EchoEndpointProg extends Endpoint {
    private static final System.Logger LOGGER = System.getLogger(EchoEndpointProg.class.getName());

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        LOGGER.log(Level.INFO, "OnOpen called");
        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
                LOGGER.log(Level.INFO, "OnMessage called '" + message + "'");
                try {
                    session.getBasicRemote().sendObject(message);
                } catch (Exception e) {
                    LOGGER.log(Level.INFO, e.getMessage());
                }
            }
        });
    }

    @Override
    public void onError(Session session, Throwable thr) {
        LOGGER.log(Level.ERROR, "OnError called", thr);
        super.onError(session, thr);
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        LOGGER.log(Level.INFO, "OnClose called");
        super.onClose(session, closeReason);
    }
}
