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

package io.helidon.microprofile.server;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Class EchoEndpointTest.
 */
public class WebSocketTest {

    private static Server server;

    @BeforeAll
    static void initClass() {
        server = Server.create();
        server.start();
    }

    @AfterAll
    static void destroyClass() {
        server.stop();
    }

    @Test
    public void testEcho() throws Exception {
        URI echoUri = URI.create("ws://localhost:" + server.port() + "/websocket/echo");
        EchoClient echoClient = new EchoClient(echoUri);
        echoClient.echo("hi", "how are you?");
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

    @ServerEndpoint("/echo")
    public static class EchoEndpoint {
        private static final Logger LOGGER = Logger.getLogger(EchoEndpoint.class.getName());

        @OnOpen
        public void onOpen(Session session) throws IOException {
            LOGGER.info("OnOpen called");
            verifyRunningThread(session, LOGGER);
        }

        @OnMessage
        public void echo(Session session, String message) throws Exception {
            LOGGER.info("Endpoint OnMessage called '" + message + "'");
            verifyRunningThread(session, LOGGER);
            session.getBasicRemote().sendObject(message);
        }

        @OnError
        public void onError(Throwable t) throws IOException {
            LOGGER.info("OnError called");
        }

        @OnClose
        public void onClose(Session session) throws IOException {
            LOGGER.info("OnClose called");
            verifyRunningThread(session, LOGGER);
        }
    }
}
