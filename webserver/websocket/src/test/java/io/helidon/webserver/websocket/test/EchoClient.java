/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.webserver.websocket.test;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Class EchoClient.
 */
public class EchoClient {
    private static final Logger LOGGER = Logger.getLogger(EchoClient.class.getName());

    private static final ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName());
    private static final long TIMEOUT_SECONDS = 40;

    private final URI uri;
    private final BiFunction<String, String, Boolean> equals;

    public EchoClient(URI uri) {
        this(uri, String::equals);
    }

    public EchoClient(URI uri, BiFunction<String, String, Boolean> equals) {
        this.uri = uri;
        this.equals = equals;
    }

    /**
     * Sends each message one by one and compares echoed value ignoring cases.
     *
     * @param messages Messages to send.
     * @throws Exception If an error occurs.
     */
    public void echo(String... messages) throws Exception {
        CountDownLatch messageLatch = new CountDownLatch(messages.length);
        CompletableFuture<Void> openFuture = new CompletableFuture<>();
        CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();

        client.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig EndpointConfig) {
                openFuture.complete(null);
                try {
                    // Register message handler. Tyrus has problems with lambdas here
                    // so an inner class with an onMessage method is required.
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            LOGGER.info("Client OnMessage called '" + message + "'");

                            int index = messages.length - (int) messageLatch.getCount();
                            assertTrue(equals.apply(messages[index], message));

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

                    // Send message to Echo service
                    for (String msg : messages) {
                        session.getBasicRemote().sendText(msg);
                    }
                } catch (IOException e) {
                    fail("Unexpected exception " + e);
                }
            }

            @Override
            public void onClose(Session session, CloseReason closeReason) {
                closeFuture.complete(null);
            }
        }, config, uri);

        openFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        closeFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!messageLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            fail("Timeout expired without receiving echo of all messages");
        }
    }
}
