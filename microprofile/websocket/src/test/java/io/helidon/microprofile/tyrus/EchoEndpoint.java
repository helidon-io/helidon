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

package io.helidon.microprofile.tyrus;

import java.util.logging.Logger;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/echoAnnot")
public class EchoEndpoint {
    private static final Logger LOGGER = Logger.getLogger(EchoEndpoint.class.getName());

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
