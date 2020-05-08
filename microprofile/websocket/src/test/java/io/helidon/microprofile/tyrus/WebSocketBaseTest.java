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
 */

package io.helidon.microprofile.tyrus;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import io.helidon.microprofile.server.ServerCdiExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Class WebSocketBaseTest.
 */
public abstract class WebSocketBaseTest {

    static SeContainer container;

    @AfterAll
    static void destroyClass() {
        container.close();
    }

    public abstract String context();

    public int port() {
        ServerCdiExtension cdiExtension = CDI.current().getBeanManager().getExtension(ServerCdiExtension.class);
        return cdiExtension.port();
    }

    @Test
    public void testEchoAnnot() throws Exception {
        URI echoUri = URI.create("ws://localhost:" + port() + context() + "/echoAnnot");
        EchoClient echoClient = new EchoClient(echoUri);
        echoClient.echo("hi", "how are you?");
    }

    @ServerEndpoint("/echoAnnot")
    public static class EchoEndpointAnnot {
        private static final Logger LOGGER = Logger.getLogger(EchoEndpointAnnot.class.getName());

        @OnOpen
        public void onOpen(Session session) throws IOException {
            LOGGER.info("OnOpen called");
            verifyRunningThread(session, LOGGER);
        }

        @OnMessage
        public void echo(Session session, String message) throws Exception {
            LOGGER.info("OnMessage called '" + message + "'");
            session.getBasicRemote().sendObject(message);
            verifyRunningThread(session, LOGGER);
        }

        @OnError
        public void onError(Throwable t, Session session) throws IOException {
            LOGGER.info("OnError called");
            verifyRunningThread(session, LOGGER);
        }

        @OnClose
        public void onClose(Session session) throws IOException {
            LOGGER.info("OnClose called");
            verifyRunningThread(session, LOGGER);
        }
    }

    public static class EchoEndpointProg extends Endpoint {
        private static final Logger LOGGER = Logger.getLogger(EchoEndpointProg.class.getName());

        @Override
        public void onOpen(Session session, EndpointConfig endpointConfig) {
            LOGGER.info("OnOpen called");
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    LOGGER.info("OnMessage called '" + message + "'");
                    try {
                        session.getBasicRemote().sendObject(message);
                    } catch (Exception e) {
                        LOGGER.info(e.getMessage());
                    }
                }
            });
        }

        @Override
        public void onError(Session session, Throwable thr) {
            LOGGER.info("OnError called");
            super.onError(session, thr);
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            LOGGER.info("OnClose called");
            super.onClose(session, closeReason);
        }
    }

    /**
     * Verify that endpoint methods are running in a Helidon thread pool.
     *
     * @param session Websocket session.
     * @param logger A logger.
     * @throws IOException Exception during close.
     */
    private static void verifyRunningThread(Session session, Logger logger) throws IOException {
        Thread thread = Thread.currentThread();
        if (!thread.getName().contains("helidon")) {
            logger.warning("Websocket handler running in incorrect thread " + thread);
            session.close();
        }
    }
}
