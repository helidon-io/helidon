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

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/echoAnnot")
public class EchoEndpoint {
    private static final System.Logger LOGGER = System.getLogger(EchoEndpoint.class.getName());

    @OnOpen
    public void onOpen(Session session) {
        LOGGER.log(Level.INFO, "OnOpen called");
    }

    @OnMessage
    public void echo(Session session, String message) throws Exception {
        LOGGER.log(Level.INFO, "OnMessage called '" + message + "'");
        session.getBasicRemote().sendObject(message);
    }

    @OnError
    public void onError(Throwable t, Session session) {
        LOGGER.log(Level.INFO, "OnError called");
    }

    @OnClose
    public void onClose(Session session) {
        LOGGER.log(Level.INFO, "OnClose called");
    }
}
