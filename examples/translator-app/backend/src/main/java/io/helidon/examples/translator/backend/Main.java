/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.examples.translator.backend;

import java.io.IOException;
import java.util.concurrent.CompletionStage;
import java.util.logging.LogManager;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Translator application backend main class.
 */
public class Main {

    private Main() {
    }

    /**
     * Start the server.
     * @return the created {@link WebServer} instance
     * @throws IOException if there are problems reading logging properties
     */
    public static CompletionStage<WebServer> startBackendServer() throws IOException {
        // configure logging in order to not have the standard JVM defaults
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));

        Config config = Config.builder()
                .sources(ConfigSources.environmentVariables())
                .build();

        WebServer webServer = WebServer.builder(
                Routing.builder()
                        .register(new TranslatorBackendService()))
                .port(9080)
                .tracer(TracerBuilder.create(config.get("tracing"))
                                .serviceName("helidon-webserver-translator-backend")
                                .build())
                .build();

        return webServer.start()
                .thenApply(ws -> {
                    System.out.println(
                            "WEB server is up! http://localhost:" + ws.port());
                    ws.whenShutdown().thenRun(()
                                                      -> System.out.println("WEB server is DOWN. Good bye!"));
                    return ws;
                }).exceptionally(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                });
    }

    /**
     * The main method of Translator backend.
     *
     * @param args command-line args, currently ignored.
     * @throws Exception in case of an error
     */
    public static void main(String[] args) throws Exception {
        startBackendServer();
    }
}
