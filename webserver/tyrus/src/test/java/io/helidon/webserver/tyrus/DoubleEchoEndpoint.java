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

package io.helidon.webserver.tyrus;

import java.io.IOException;
import java.util.logging.Logger;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

/**
 * Class DoubleEchoEndpoint. Echos back a message concatenated with itself.
 */
@ServerEndpoint("/doubleEcho")
public class DoubleEchoEndpoint {
    private static final Logger LOGGER = Logger.getLogger(DoubleEchoEndpoint.class.getName());

    @OnOpen
    public void onOpen(Session session) throws IOException {
        LOGGER.info("OnOpen called");
    }

    @OnMessage
    public void echo(Session session, String message) throws Exception {
        LOGGER.info("Endpoint OnMessage called '" + message + "'");
        session.getBasicRemote().sendObject(message + message);     // calls encoder
    }

    @OnError
    public void onError(Throwable t) {
        LOGGER.info("OnError called");
    }

    @OnClose
    public void onClose(Session session) {
        LOGGER.info("OnClose called");
    }
}
