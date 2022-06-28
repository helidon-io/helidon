/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

import java.util.Collections;
import java.util.List;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.staticcontent.StaticContentSupport;
import io.helidon.webserver.websocket.WebSocketRouting;

import jakarta.websocket.Encoder;
import jakarta.websocket.server.ServerEndpointConfig;

import static io.helidon.webserver.examples.websocket.MessageBoardEndpoint.UppercaseEncoder;

/**
 * Application demonstrates combination of websocket and REST.
 */
public class Main {

    private Main() {
    }

    static WebServer startWebServer() {
        List<Class<? extends Encoder>> encoders = Collections.singletonList(UppercaseEncoder.class);

        // Wait for webserver to start before returning
        WebServer server = WebServer.builder()
                .port(8080)
                .routing(r -> r
                        .register("/web", StaticContentSupport.builder("/WEB")
                                .welcomeFileName("index.html")
                                .build())
                        .register("/rest", new MessageQueueService())
                )
                .addRouting(WebSocketRouting.builder()
                        .endpoint("/websocket", ServerEndpointConfig.Builder.create(MessageBoardEndpoint.class, "/board")
                                .encoders(encoders)
                                .build())
                        .build()
                )
                .build()
                .start()
                .await();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/web");

        return server;
    }

    /**
     * A java main class.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        WebServer server = startWebServer();

        // Server threads are not demon. NO need to block. Just react.
        server.whenShutdown()
                .thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));

    }
}
