/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;
import org.glassfish.tyrus.core.ProtocolHandler;
import org.glassfish.tyrus.core.TyrusRemoteEndpoint;
import org.glassfish.tyrus.core.TyrusWebSocket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ConnectionClosingTest extends TyrusSupportBaseTest {
    private static CompletableFuture<Boolean> onOpenTriggered;
    private static CompletableFuture<Boolean> onMessageTriggered;
    private static CompletableFuture<Boolean> onCloseTriggered;
    private static CompletableFuture<CloseReason> closeReason;

    @BeforeAll
    public static void startServer() throws Exception {
        webServer(true, TestEndpoint.class);
    }

    @BeforeEach
    public void preStart() {
        onOpenTriggered = new CompletableFuture<>();
        onMessageTriggered = new CompletableFuture<>();
        onCloseTriggered = new CompletableFuture<>();
        closeReason = new CompletableFuture<>();
    }

    @Test
    public void testOnCloseIsTriggeredOnAbnormalConnectionClosure() {
        URI uri = URI.create("ws://localhost:" + webServer().port() + "/tyrus/endpoint");
        ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName());
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();

        try (Session session = client.connectToServer(new ClientEndpoint(), config, uri)) {
            assertTrue(onOpenTriggered.get(10, TimeUnit.SECONDS), "onOpenTriggered");
            session.getBasicRemote().sendText("Test message");

            assertTrue(onMessageTriggered.get(10, TimeUnit.SECONDS), "onMessageTriggered");

            TyrusRemoteEndpoint remote = (TyrusRemoteEndpoint) session.getBasicRemote();
            TyrusWebSocket socket = getPrivateField(remote, "webSocket", true, TyrusWebSocket.class);
            ProtocolHandler protocolHandler = getPrivateField(socket, "protocolHandler", false, ProtocolHandler.class);
            invokePrivateMethod(protocolHandler, "doClose");
            assertTrue(onCloseTriggered.get(10, TimeUnit.SECONDS), "onCloseTriggered");

            CloseReason reason = closeReason.get(10, TimeUnit.SECONDS);
            assertNotNull(reason, "closeReason");
            assertEquals(reason.getCloseCode(), CloseReason.CloseCodes.CLOSED_ABNORMALLY, "closeReason.code");
        } catch (Exception e) {
            fail("Unexpected exception", e);
        }
    }

    private <T> T getPrivateField(Object obj, String fieldName, boolean fromSuperclass, Class<T> clazz)
            throws NoSuchFieldException, IllegalAccessException {
        Field field;
        if (fromSuperclass) {
            field = obj.getClass().getSuperclass().getDeclaredField(fieldName);
        } else {
            field = obj.getClass().getDeclaredField(fieldName);
        }

        field.setAccessible(true);

        if (!clazz.isAssignableFrom(field.getType())) {
            throw new IllegalStateException(String.format("Expected field %s to be an instance of type %s, got %s",
                    fieldName, clazz.getName(), field.getType().getName()));
        }

        return (T) field.get(obj);
    }

    private void invokePrivateMethod(Object obj, String methodName)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = obj.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(obj);
    }

    @ServerEndpoint("/endpoint")
    public static class TestEndpoint {
        private static final Logger LOGGER = Logger.getLogger(TestEndpoint.class.getName());

        public TestEndpoint() {
            LOGGER.info("Endpoint created");
        }

        @OnOpen
        public void onOpen() {
            LOGGER.info("Connection established");
            onOpenTriggered.complete(true);
        }

        @OnMessage
        public void onMessage(String message) {
            LOGGER.info(String.format("Got message: %s", message));
            onMessageTriggered.complete(true);
        }

        @OnClose
        public void onClose(CloseReason reason) {
            LOGGER.info(String.format("Connection closed: %s %s", reason.getCloseCode(), reason.getReasonPhrase()));
            onCloseTriggered.complete(true);
            closeReason.complete(reason);
        }
    }
}
