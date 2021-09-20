/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.example.websocket;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.glassfish.tyrus.client.ClientManager;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Class MessageBoardTest.
 */
@HelidonTest
class MessageBoardTest {
    private static final Logger LOGGER = Logger.getLogger(MessageBoardTest.class.getName());
    private static final String[] MESSAGES = { "Whisky", "Tango", "Foxtrot" };

    private final WebTarget webTarget;
    private final ServerCdiExtension server;
    private final ClientManager websocketClient = ClientManager.createClient();

    @Inject
    MessageBoardTest(WebTarget webTarget, ServerCdiExtension server) {
        this.webTarget = webTarget;
        this.server = server;
    }

    @Test
    public void testBoard() throws IOException, DeploymentException, InterruptedException {
        // Post messages using REST resource
        for (String message : MESSAGES) {
            try (Response res = webTarget.path("/rest").request().post(Entity.text(message))) {
                assertThat(res.getStatus(), is(204));
                LOGGER.info("Posting message '" + message + "'");
            }
        }

        // Now connect to message board using WS and them back
        URI websocketUri = URI.create("ws://localhost:" + server.port() + "/websocket");
        CountDownLatch messageLatch = new CountDownLatch(MESSAGES.length);
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();

        websocketClient.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig EndpointConfig) {
                try {
                    // Set message handler to receive messages
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            LOGGER.info("Client OnMessage called '" + message + "'");
                            messageLatch.countDown();
                            if (messageLatch.getCount() == 0) {
                                try {
                                    session.close();
                                } catch (IOException e) {
                                    fail("Unexpected exception " + e);
                                }
                            }
                        }
                    });

                    // Send an initial message to start receiving
                    session.getBasicRemote().sendText("SEND");
                } catch (IOException e) {
                    fail("Unexpected exception " + e);
                }
            }

            @Override
            public void onClose(Session session, CloseReason closeReason) {
                LOGGER.info("Client OnClose called '" + closeReason + "'");
            }

            @Override
            public void onError(Session session, Throwable thr) {
                LOGGER.info("Client OnError called '" + thr + "'");

            }
        }, config, websocketUri);

        // Wait until all messages are received
        assertThat("Message latch should have counted down to 0",
                   messageLatch.await(1000, TimeUnit.SECONDS),
                   is(true));
    }
}
