/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.examples.webclient.standalone;

import java.util.concurrent.CompletionStage;

import io.helidon.config.Config;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * The application main class.
 */
public final class ServerMain {

    private static int serverPort = -1;

    /**
     * Cannot be instantiated.
     */
    private ServerMain() {
    }

    /**
     * WebServer starting method.
     *
     * @param args starting arguments
     */
    public static void main(String[] args) {
        startServer();
    }

    /**
     * Returns current port of the running server.
     *
     * @return server port
     */
    public static int getServerPort() {
        return serverPort;
    }

    /**
     * Start the server.
     *
     * @return the created {@link WebServer} instance
     */
    static CompletionStage<WebServer> startServer() {
        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        WebServer server = WebServer.builder(createRouting(config))
                .config(config.get("server"))
                .addMediaSupport(JsonpSupport.create())
                .build();

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        CompletionStage<WebServer> start = server.start();

        start.thenAccept(ws -> {
            serverPort = ws.port();
            System.out.println("WEB server is up! http://localhost:" + ws.port() + "/greet");
            ws.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
        }).exceptionally(t -> {
            System.err.println("Startup failed: " + t.getMessage());
            t.printStackTrace(System.err);
            return null;
        });

        // Server threads are not daemon. No need to block. Just react.
        return start;
    }

    /**
     * Creates new {@link Routing}.
     *
     * @param config configuration of this server
     * @return routing configured with JSON support, a health check, and a service
     */
    private static Routing createRouting(Config config) {
        MetricsSupport metrics = MetricsSupport.create();
        GreetService greetService = new GreetService(config);
        return Routing.builder()
                .register(metrics)
                .register("/greet", greetService)
                .build();
    }
}
