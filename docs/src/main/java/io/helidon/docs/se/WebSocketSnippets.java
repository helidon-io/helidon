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
package io.helidon.docs.se;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.staticcontent.StaticContentService;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

@SuppressWarnings("ALL")
class WebSocketSnippets {
    // tag::snippet_1[]
    record MessageQueueService(Queue<String> messageQueue) implements HttpService {
        @Override
        public void routing(HttpRules routingRules) {
            routingRules.post("/board", (req, res) -> {
                messageQueue.add(req.content().as(String.class));
                res.status(204).send();
            });
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    record MessageBoardEndpoint(Queue<String> messageQueue) implements WsListener {
        @Override
        public void onMessage(WsSession session, String text, boolean last) {
            // Send all messages in the queue
            if (text.equals("send")) {
                while (!messageQueue.isEmpty()) {
                    session.send(messageQueue.poll(), last);
                }
            }
        }
    }
    // end::snippet_2[]

    void snippet_3(WebServerConfig.Builder server) {
        // tag::snippet_3[]
        StaticContentService staticContent = StaticContentService.builder("/WEB")
                .welcomeFileName("index.html")
                .build();
        Queue<String> messageQueue = new ConcurrentLinkedQueue<>();
        server.routing(it -> it
                        .register("/web", staticContent)
                        .register("/rest", new MessageQueueService(messageQueue)))
                .addRouting(WsRouting.builder()
                                    .endpoint("/websocket/board", new MessageBoardEndpoint(messageQueue)));
        // end::snippet_3[]
    }
}
