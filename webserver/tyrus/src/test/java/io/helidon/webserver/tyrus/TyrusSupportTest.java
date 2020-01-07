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

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.helidon.webserver.WebServer;
import org.glassfish.tyrus.client.ClientManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Class TyrusSupportTest.
 */
public class TyrusSupportTest {

    private static WebServer webServer;

    @BeforeAll
    public static void startServer() throws Exception {
        webServer = TyrusExampleMain.INSTANCE.webServer(true);
    }

    @AfterAll
    public static void stopServer() {
        webServer.shutdown();
    }

    @Test
    public void testEcho() {
        CompletableFuture<Void> openFuture = new CompletableFuture<>();
        CompletableFuture<Void> messageFuture = new CompletableFuture<>();
        CompletableFuture<Void> closeFuture = new CompletableFuture<>();

        try {
            URI uri = URI.create("ws://localhost:" + webServer.port() + "/tyrus/echo");
            ClientManager client = ClientManager.createClient();
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
                                assertTrue(message.equals("HI"));
                                messageFuture.complete(null);
                                try {
                                    session.close();
                                } catch (IOException e) {
                                    fail("Unexpected exception " + e);
                                }
                            }
                        });

                        // Send message to Echo service
                        session.getBasicRemote().sendText("hi");
                    } catch (IOException e) {
                        fail("Unexpected exception " + e);
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    closeFuture.complete(null);
                }
            }, config, uri);

            openFuture.get(10, TimeUnit.SECONDS);
            messageFuture.get(10, TimeUnit.SECONDS);
            closeFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("Unexpected exception " + e);
        }
    }
}
