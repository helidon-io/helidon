/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.microprofile.testing.junit5.Socket;

import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.ws.rs.client.WebTarget;

@HelidonTest
abstract class WebSocketBaseTest {

    @Inject
    private WebTarget target;

    @Inject
    @Socket("other")
    private WebTarget otherTarget;

    public int port() {
        return target.getUri().getPort();
    }

    public WebTarget target() {
        return target;
    }

    public int otherPort() {
        return otherTarget.getUri().getPort();
    }

    public WebTarget otherTarget() {
        return otherTarget;
    }

    @ServerEndpoint("/echoAnnot")
    public static class EchoEndpointAnnot {
        private static final Logger LOGGER = Logger.getLogger(EchoEndpointAnnot.class.getName());

        @OnOpen
        public void onOpen(Session session) throws IOException {
            LOGGER.info("OnOpen called");
        }

        @OnMessage
        public void echo(Session session, String message) throws Exception {
            LOGGER.info("OnMessage called '" + message + "'");
            session.getBasicRemote().sendObject(message);
        }

        @OnError
        public void onError(Throwable t, Session session) throws IOException {
            LOGGER.info("OnError called");
        }

        @OnClose
        public void onClose(Session session) throws IOException {
            LOGGER.info("OnClose called");
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
            LOGGER.log(Level.SEVERE, "OnError called", thr);
            super.onError(session, thr);
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            LOGGER.info("OnClose called");
            super.onClose(session, closeReason);
        }
    }
}
