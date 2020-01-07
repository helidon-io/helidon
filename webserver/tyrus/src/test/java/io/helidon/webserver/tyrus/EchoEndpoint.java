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

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.logging.Logger;

@ServerEndpoint(
        value = "/echo",
        encoders = { UppercaseCodec.class },
        decoders = { UppercaseCodec.class },
        configurator = ServerConfigurator.class
)
public class EchoEndpoint {
    private static final Logger LOGGER = Logger.getLogger(EchoEndpoint.class.getName());

    @OnOpen
    public void onOpen(Session session) throws IOException {
        LOGGER.info("OnOpen called");
    }

    @OnMessage
    public void echo(Session session, String message) throws IOException {
        LOGGER.info("OnMessage called with '" + message + "'");
        session.getBasicRemote().sendText(message);
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
