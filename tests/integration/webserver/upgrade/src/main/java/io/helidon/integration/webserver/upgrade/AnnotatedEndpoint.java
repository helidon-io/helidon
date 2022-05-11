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

package io.helidon.integration.webserver.upgrade;

import java.io.IOException;
import java.util.logging.Logger;

import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/echo")
public class AnnotatedEndpoint {

    private static final Logger LOGGER = Logger.getLogger(AnnotatedEndpoint.class.getName());

    @OnOpen
    public void onOpen(Session session) throws IOException {
        LOGGER.fine("SERVER: onOpen " + session.getId());
        send(session, "Hello this is server calling on open!");
    }

    @OnMessage
    public void echo(Session session, String s) throws Exception {
        LOGGER.fine("SERVER: onMessage " + session.getId() + " - " + s);
        send(session, s);
    }

    private void send(Session session, String msg) {
        try {
            session.getBasicRemote().sendText(msg);
            session.getBasicRemote().flushBatch();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
