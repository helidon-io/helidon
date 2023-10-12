/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.websocket.Extension;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.junit.jupiter.api.Test;

@HelidonTest
@AddBean(ExtensionEndpointTest.TestExtension.class)
@AddBean(ExtensionEndpointTest.ExtensionEndpoint.class)
class ExtensionEndpointTest extends WebSocketBaseTest {

    @Test
    public void test() throws Exception {
        URI echoUri = URI.create("ws://localhost:" + port() + "/extAnnot");
        EchoClient echoClient = new EchoClient(echoUri, new TestExtension());
        echoClient.echo("hi", "how are you?");
    }

    @ServerEndpoint("/extAnnot")
    public static class ExtensionEndpoint {
        private static final System.Logger LOGGER = System.getLogger(ExtensionEndpoint.class.getName());

        @OnMessage
        public void echo(Session session, String message) throws Exception {
            LOGGER.log(System.Logger.Level.INFO, "OnMessage called '" + message + "'");
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
