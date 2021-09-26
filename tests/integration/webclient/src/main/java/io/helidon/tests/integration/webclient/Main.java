/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.webclient;

import java.util.concurrent.CompletionStage;

import io.helidon.config.Config;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.opentracing.Tracer;

/**
 * The application main class.
 */
public final class Main {

    static int serverPort;

    static WebServer webServer;

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    public static void main(String[] args) {
        startServer();
    }

    static CompletionStage<WebServer> startServer(Tracer tracer) {
        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // Get webserver config from the "server" section of application.yaml
        WebServer.Builder builder = WebServer.builder()
                .tracer(tracer);

        return startIt(config, builder);
    }

    /**
     * Start the server.
     *
     * @return the created {@link WebServer} instance
     */
    static CompletionStage<WebServer> startServer() {
        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // Get webserver config from the "server" section of application.yaml

        WebServer.Builder builder = WebServer.builder();

        return startIt(config, builder);
    }

    private static CompletionStage<WebServer> startIt(Config config, WebServer.Builder serverBuilder) {
        serverBuilder.config(config.get("server"));
        webServer = serverBuilder.routing(createRouting(config))
                .addMediaSupport(JsonpSupport.create())
                .build();

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        CompletionStage<WebServer> start = webServer.start();

        start.thenAccept(ws -> {
                    serverPort = ws.port();
                    System.out.println(
                            "WEB server is up! http://localhost:" + ws.port() + "/greet");
                    ws.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
                })
                .exceptionally(t -> {
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
        GreetService greetService = new GreetService(config);
        return Routing.builder()
                .register(WebSecurity.create(config.get("security")))
                .register("/greet", greetService)
                .build();
    }
}
