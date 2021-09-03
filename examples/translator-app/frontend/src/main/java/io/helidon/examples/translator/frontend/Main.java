/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.examples.translator.frontend;

import io.helidon.common.LogConfig;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Translator application frontend main class.
 */
public class Main {

    private Main() {
    }

    /**
     * Start the server.
     * @return the created {@link WebServer} instance
     */
    public static Single<WebServer> startFrontendServer() {
        // configure logging in order to not have the standard JVM defaults
        LogConfig.configureRuntime();

        Config config = Config.builder()
                .sources(ConfigSources.environmentVariables())
                .build();

        WebServer webServer = WebServer.builder(
                Routing.builder()
                        .register(new TranslatorFrontendService(
                                config.get("backend.host").asString().orElse("localhost"),
                                9080)))
                .port(8080)
                .tracer(TracerBuilder.create(config.get("tracing"))
                                .serviceName("helidon-webserver-translator-frontend")
                                .registerGlobal(false)
                                .build())
                .build();

        return webServer.start()
                .peek(ws -> {
                    System.out.println(
                            "WEB server is up! http://localhost:" + ws.port());
                    ws.whenShutdown().thenRun(()
                                                      -> System.out.println("WEB server is DOWN. Good bye!"));
                }).onError(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                });
    }

    /**
     * The main method of Translator frontend.
     *
     * @param args command-line args, currently ignored.
     */
    public static void main(String[] args) {
        startFrontendServer();
    }
}
