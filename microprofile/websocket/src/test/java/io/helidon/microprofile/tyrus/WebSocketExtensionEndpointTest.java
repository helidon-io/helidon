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

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.CDI;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.helidon.microprofile.server.ServerCdiExtension;
import javax.websocket.Extension;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

/**
 * A test that mixes Websocket endpoints and extensions in the same application.
 */
public class WebSocketExtensionEndpointTest {

    static SeContainer container;

    @BeforeAll
    static void initClass() {
        container = SeContainerInitializer.newInstance()
                .addBeanClasses(ExtensionEndpointAnnot.class, TestExtension.class)
                .initialize();
    }

    @AfterAll
    static void destroyClass() {
        container.close();
    }

    public int port() {
        ServerCdiExtension cdiExtension = CDI.current().getBeanManager().getExtension(ServerCdiExtension.class);
        return cdiExtension.port();
    }

    @Test
    public void test() throws Exception {
        URI echoUri = URI.create("ws://localhost:" + port() + "/extAnnot");
        EchoClient echoClient = new EchoClient(echoUri, new TestExtension());
        echoClient.echo("hi", "how are you?");
    }

    @ServerEndpoint("/extAnnot")
    public static class ExtensionEndpointAnnot {
        private static final Logger LOGGER = Logger.getLogger(ExtensionEndpointAnnot.class.getName());

        @OnMessage
        public void echo(Session session, String message) throws Exception {
            LOGGER.info("OnMessage called '" + message + "'");
            if (session.getNegotiatedExtensions().isEmpty()) {
                throw new IllegalStateException();
            }
            session.getBasicRemote().sendObject(message);
        }
    }

    public static class TestExtension implements Extension {
        @Override
        public String getName() {
            return "testExtension";
        }

        @Override
        public List<Parameter> getParameters() {
            return Collections.emptyList();
        }
    }
}
