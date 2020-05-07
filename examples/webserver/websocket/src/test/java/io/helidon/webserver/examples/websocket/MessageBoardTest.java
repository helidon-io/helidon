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

package io.helidon.webserver.examples.websocket;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;
import org.glassfish.tyrus.client.ClientManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.webserver.examples.websocket.Main.startWebServer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Class MessageBoardTest.
 */
public class MessageBoardTest {
    private static final Logger LOGGER = Logger.getLogger(MessageBoardTest.class.getName());

    private static WebClient restClient = WebClient.create();
    private static ClientManager websocketClient = ClientManager.createClient();
    private static WebServer server;

    private String[] messages = { "Whisky", "Tango", "Foxtrot" };

    @BeforeAll
    static void initClass() {
        server = startWebServer();
    }

    @AfterAll
    static void destroyClass() {
        server.shutdown();
    }

    @Test
    public void testBoard() throws IOException, DeploymentException, InterruptedException, ExecutionException {
        // Post messages using REST resource
        URI restUri = URI.create("http://localhost:" + server.port() + "/rest/board");
        for (String message : messages) {
            restClient.post()
                    .uri(restUri)
                    .submit(message)
                    .thenAccept(it -> assertThat(it.status(), is(Http.Status.NO_CONTENT_204)))
                    .toCompletableFuture()
                    .get();
            LOGGER.info("Posting message '" + message + "'");
        }

        // Now connect to message board using WS and them back
        URI websocketUri = URI.create("ws://localhost:" + server.port() + "/websocket/board");
        CountDownLatch messageLatch = new CountDownLatch(messages.length);
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
        messageLatch.await(1000000, TimeUnit.SECONDS);
    }
}
