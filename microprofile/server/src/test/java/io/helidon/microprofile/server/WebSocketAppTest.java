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

import javax.enterprise.context.Dependent;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;
import javax.ws.rs.ApplicationPath;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Class WebSocketAppTest.
 */
public class WebSocketAppTest {

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
    public void testEchoAnnot() throws Exception {
        URI echoUri = URI.create("ws://localhost:" + server.port() + "/web/echoAnnot");
        EchoClient echoClient = new EchoClient(echoUri);
        echoClient.echo("hi", "how are you?");
    }

    @Test
    public void testEchoProg() throws Exception {
        URI echoUri = URI.create("ws://localhost:" + server.port() + "/web/echoProg");
        EchoClient echoClient = new EchoClient(echoUri);
        echoClient.echo("hi", "how are you?");
    }

    @Dependent      // scanned by CDI
    @ApplicationPath("/web")
    public static class EndpointApplication implements ServerApplicationConfig {
        @Override
        public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpoints) {
            ServerEndpointConfig.Builder builder = ServerEndpointConfig.Builder.create(
                    EchoEndpointProg.class, "/echoProg");
            return Collections.singleton(builder.build());
        }

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> endpoints) {
            return Collections.singleton(EchoEndpointAnnot.class);
        }
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
        public void onError(Throwable t) {
            LOGGER.info("OnError called");
        }

        @OnClose
        public void onClose(Session session) {
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
            LOGGER.info("OnError called");
            super.onError(session, thr);
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            LOGGER.info("OnClose called");
            super.onClose(session, closeReason);
        }
    }
}
