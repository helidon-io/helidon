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

package io.helidon.webserver.examples.websocket;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.websocket.Encoder;
import javax.websocket.server.ServerEndpointConfig;

import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.tyrus.TyrusSupport;

import static io.helidon.webserver.examples.websocket.MessageBoardEndpoint.UppercaseEncoder;

/**
 * Application demonstrates combination of websocket and REST.
 */
public class Main {

    private Main() {
    }

    /**
     * Creates new {@link Routing}.
     *
     * @return the new instance
     */
    static Routing createRouting() {
        List<Class<? extends Encoder>> encoders = Collections.singletonList(UppercaseEncoder.class);

        return Routing.builder()
                .register("/rest", new MessageQueueService())
                .register("/websocket",
                        TyrusSupport.builder().register(
                                ServerEndpointConfig.Builder.create(MessageBoardEndpoint.class, "/board")
                                        .encoders(encoders).build())
                                .build())
                .build();
    }

    static WebServer startWebServer() {
        ServerConfiguration config = ServerConfiguration.builder()
                .port(8080)
                .build();
        WebServer server = WebServer.create(config, createRouting());

        // Start webserver
        CompletableFuture<Void> started = new CompletableFuture<>();
        server.start().thenAccept(ws -> {
            System.out.println("WEB server is up! http://localhost:" + ws.port());
            started.complete(null);
        });

        // Wait for webserver to start before returning
        try {
            started.toCompletableFuture().get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
