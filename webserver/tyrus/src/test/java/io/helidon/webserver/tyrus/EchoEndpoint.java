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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.websocket.HandshakeResponse;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import static io.helidon.webserver.tyrus.UppercaseCodec.isDecoded;

/**
 * Class EchoEndpoint. Only one instance of this endpoint should be used at
 * a time. See static {@code EchoEndpoint#modifyHandshakeCalled}.
 */
@ServerEndpoint(
        value = "/echo",
        encoders = { UppercaseCodec.class },
        decoders = { UppercaseCodec.class },
        configurator = EchoEndpoint.ServerConfigurator.class
)
public class EchoEndpoint {
    private static final Logger LOGGER = Logger.getLogger(EchoEndpoint.class.getName());

    static AtomicBoolean modifyHandshakeCalled = new AtomicBoolean(false);

    /**
     * Verify that endpoint methods are running in a Helidon thread pool.
     *
     * @param session Websocket session.
     * @param logger A logger.
     * @throws IOException Exception during close.
     */
    private static void verifyRunningThread(Session session, Logger logger) throws IOException {
        Thread thread = Thread.currentThread();
        if (!thread.getName().contains("EventLoop")) {
            logger.warning("Websocket handler running in incorrect thread " + thread);
            session.close();
        }
    }

    public static class ServerConfigurator extends ServerEndpointConfig.Configurator {

        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            LOGGER.info("ServerConfigurator called during handshake");
            super.modifyHandshake(sec, request, response);
            EchoEndpoint.modifyHandshakeCalled.set(true);
        }
    }

    @OnOpen
    public void onOpen(Session session) throws IOException {
        LOGGER.info("OnOpen called");
        verifyRunningThread(session, LOGGER);
        if (!modifyHandshakeCalled.get()) {
            session.close();        // unexpected
        }
    }

    @OnMessage
    public void echo(Session session, String message) throws Exception {
        LOGGER.info("Endpoint OnMessage called '" + message + "'");
        verifyRunningThread(session, LOGGER);
        if (!isDecoded(message)) {
            throw new InternalError("Message has not been decoded");
        }
        session.getBasicRemote().sendObject(message);       // calls encoder
    }

    @OnError
    public void onError(Throwable t) {
        LOGGER.info("OnError called");
        modifyHandshakeCalled.set(false);
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        LOGGER.info("OnClose called");
        verifyRunningThread(session, LOGGER);
        modifyHandshakeCalled.set(false);
    }
}
