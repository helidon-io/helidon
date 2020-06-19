
/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.messaging.se.example;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

import javax.websocket.server.ServerEndpointConfig;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.StaticContentSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.tyrus.TyrusSupport;

/**
 * The application main class.
 */
public final class Main {

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     * @throws IOException if there are problems reading logging properties
     */
    public static void main(final String[] args) throws IOException {
        startServer();
    }

    /**
     * Start the server.
     *
     * @return the created {@link WebServer} instance
     * @throws IOException if there are problems reading logging properties
     */
    static WebServer startServer() throws IOException {
        // load logging configuration
        setupLogging();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        SendingService sendingService = new SendingService(config);

        WebServer server = WebServer.builder(createRouting(sendingService))
                .config(config.get("server"))
                .build();

        server.start()
                .thenAccept(ws -> {
                    System.out.println(
                            "WEB server is up! http://localhost:" + ws.port());
                    ws.whenShutdown().thenRun(()
                            -> {
                        // Stop messaging properly
                        sendingService.shutdown();
                        System.out.println("WEB server is DOWN. Good bye!");
                    });
                })
                .exceptionally(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                });

        // Server threads are not daemon. No need to block. Just react.
        return server;
    }

    /**
     * Creates new {@link Routing}.
     *
     * @param config configuration of this server
     * @return routing configured with JSON support, a health check, and a service
     */
    private static Routing createRouting(SendingService sendingService) {

        return Routing.builder()
                // register static content support (on "/")
                .register(StaticContentSupport.builder("/WEB").welcomeFileName("index.html"))
                // register rest endpoint for sending to Kafka
                .register("/rest/messages", sendingService)
                // register WebSocket endpoint to push messages coming from Kafka to client
                .register("/ws",
                        TyrusSupport.builder().register(
                                ServerEndpointConfig.Builder.create(
                                        WebSocketEndpoint.class, "/messages")
                                        .build())
                                .build())
                .build();
    }

    /**
     * Configure logging from logging.properties file.
     */
    private static void setupLogging() throws IOException {
        try (InputStream is = Main.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        }
    }
}
