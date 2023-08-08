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

package io.helidon.microprofile.tyrus;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.ws.rs.client.WebTarget;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(ApplicationScopeTest.WebsocketEndpoint.class)
class ApplicationScopeTest {

    static Semaphore semaphore = new Semaphore(0);

    @Inject
    protected WebsocketEndpoint endpoint;

    @Inject
    protected WebTarget webTarget;

    @Test
    void test() throws Exception {
        // two message sent over different sessions
        Session session_1 = connectToWebsocket("websocket/id-1");
        session_1.getBasicRemote().sendText("A message 1");
        Session session_2 = connectToWebsocket("websocket/id-2");
        session_2.getBasicRemote().sendText("A message 2");

        // wait until first two messages received
        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS), is(true));
        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS), is(true));

        // verify application scoped bean persists session closing
        assertThat(endpoint.messageMap().size(), is(2));
        session_2.close();
        assertThat(endpoint.messageMap().size(), is(2));

        // and that we can push more messages to map if needed
        Session session_3 = connectToWebsocket("websocket/id-3");
        session_3.getBasicRemote().sendText("A message 3");

        // wait until last message received
        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS), is(true));

        // verify application scoped bean
        assertThat(endpoint.messageMap().size(), is(3));

        // close other sessions
        session_1.close();
        session_3.close();
    }

    public Session connectToWebsocket(String path) {
        Endpoint endpoint = new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig config) {
            }
        };

        try {
            ClientManager clientManager = ClientManager.createClient(JdkClientContainer.class.getName());
            return clientManager.connectToServer(endpoint,
                    new URI("ws://localhost:" + webTarget.getUri().getPort() + "/" + path));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @ApplicationScoped
    @ServerEndpoint("/websocket/{id}")
    public static class WebsocketEndpoint {

        private final Map<String, List<String>> messageMap = new ConcurrentHashMap<>();

        @OnOpen
        public void onOpen(@PathParam("id") String id, Session session) {
            messageMap.put(id, new ArrayList<>());
        }

        @OnMessage
        public void onMessage(@PathParam("id") String id, Session session, String message) {
            messageMap.get(id).add(message);
            semaphore.release();
        }

        public Map<String, List<String>> messageMap() {
            return messageMap;
        }

        // Verify single instance is created/destroyed by CDI

        private static final AtomicBoolean live = new AtomicBoolean();

        @PostConstruct
        void construct() {
            assertThat(live.compareAndSet(false, true), is(true));
        }

        @PreDestroy
        void destroy() {
            assertThat(live.compareAndSet(true, false), is(true));
        }
    }
}
