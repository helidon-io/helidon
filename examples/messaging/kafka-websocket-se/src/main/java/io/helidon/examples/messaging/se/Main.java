/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.messaging.se;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.staticcontent.StaticContentService;
import io.helidon.webserver.websocket.WsRouting;

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
     */
    public static void main(final String[] args) {
        startServer();
    }

    /**
     * Start the server.
     *
     * @return the created {@link WebServer} instance
     */
    static WebServer startServer() {
        // load logging configuration
        LogConfig.configureRuntime();

        // By default, this will pick up application.yaml from the classpath
        Config config = Config.create();

        SendingService sendingService = new SendingService(config);

        WebServer server =
                WebServer.builder()
                        .routing(r -> r
                                // register static content support (on "/")
                                .register(StaticContentService.builder("/WEB")
                                        .welcomeFileName("index.html")
                                        .build())
                                // register rest endpoint for sending to Kafka
                                .register("/rest/messages", sendingService))
                        // register WebSocket endpoint to push messages coming from Kafka to client
                        .addRouting(WsRouting.builder()
                                .endpoint("/ws/messages", new WebSocketEndpoint()))
                        .config(config.get("server"))
                        .build()
                        .start();

        System.out.println("WEB server is up! http://localhost:" + server.port());
        Runtime.getRuntime().addShutdownHook(new Thread(sendingService::shutdown));

        // Server threads are not daemon. No need to block. Just react.
        return server;
    }
}
