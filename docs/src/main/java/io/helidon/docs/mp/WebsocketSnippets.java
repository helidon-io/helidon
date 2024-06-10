/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.mp;

import java.io.IOException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerApplicationConfig;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@SuppressWarnings("ALL")
class WebsocketSnippets {

    // stub
    static class MessageQueue {

        final Queue<String> queue = new ConcurrentLinkedQueue<>();

        void push(String s) {
            queue.add(s);
        }

        String pop() {
            return queue.poll();
        }

        boolean isEmpty() {
            return queue.isEmpty();
        }
    }

    // stub
    static class UppercaseEncoder implements Encoder.Text<String> {

        @Override
        public String encode(String s) {
            return s.toUpperCase();
        }

        @Override
        public void init(EndpointConfig config) {
        }

        @Override
        public void destroy() {
        }
    }

    // tag::snippet_1[]
    @Path("rest")
    public class MessageQueueResource {

        @Inject
        private MessageQueue messageQueue;

        @POST
        @Consumes("text/plain")
        public void push(String s) {
            messageQueue.push(s);
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @ServerEndpoint(
            value = "/websocket",
            encoders = {
                    UppercaseEncoder.class
            })
    public class MessageBoardEndpoint {

        @Inject
        private MessageQueue messageQueue;

        @OnMessage
        public void onMessage(Session session, String message)
                throws EncodeException, IOException {

            if (message.equals("SEND")) {
                while (!messageQueue.isEmpty()) {
                    session.getBasicRemote()
                            .sendObject(messageQueue.pop());
                }
            }
        }
    }
    // end::snippet_2[]

    class Snippet3 {

        // tag::snippet_3[]
        @ApplicationScoped
        @RoutingPath("/web")
        public class MessageBoardApplication implements ServerApplicationConfig {
            @Override
            public Set<ServerEndpointConfig> getEndpointConfigs(
                    Set<Class<? extends Endpoint>> endpoints) {
                return Set.of(); // No programmatic endpoints
            }

            @Override
            public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> endpoints) {
                return endpoints; // Returned scanned endpoints
            }
        }
        // end::snippet_3[]
    }

    class Snippet4 {

        // tag::snippet_4[]
        @ApplicationScoped
        @RoutingPath("/web")
        @RoutingName(value = "admin", required = true)
        public class MessageBoardApplication implements ServerApplicationConfig {
            @Override
            public Set<ServerEndpointConfig> getEndpointConfigs(
                    Set<Class<? extends Endpoint>> endpoints) {
                return Set.of(); // No programmatic endpoints
            }

            @Override
            public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> endpoints) {
                return endpoints; // Returned scanned endpoints
            }
        }
        // end::snippet_4[]
    }

}
