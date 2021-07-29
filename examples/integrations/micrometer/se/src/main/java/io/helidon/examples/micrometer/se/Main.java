/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.examples.micrometer.se;

import io.helidon.common.LogConfig;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.micrometer.MicrometerSupport;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

/**
 * Simple Hello World rest application.
 */
public final class Main {

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    /**
     * Application main entry point.
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        startServer();
    }

    /**
     * Start the server.
     * @return the created {@link WebServer} instance
     */
    static Single<WebServer> startServer() {

        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // Get webserver config from the "server" section of application.yaml
        WebServer server = WebServer.builder(createRouting(config))
                .config(config.get("server"))
                .addMediaSupport(JsonpSupport.create())
                .build();

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        // Server threads are not daemon. No need to block. Just react.
        return server.start()
            .peek(ws -> {
                System.out.println(
                        "WEB server is up! http://localhost:" + ws.port() + "/greet");
                ws.whenShutdown().thenRun(()
                    -> System.out.println("WEB server is DOWN. Good bye!"));
                })
            .onError(t -> {
                System.err.println("Startup failed: " + t.getMessage());
                t.printStackTrace(System.err);
            });
    }

    /**
     * Creates new {@link Routing}.
     *
     * @return routing configured with JSON support, Micrometer metrics, and the greeting service
     * @param config configuration of this server
     */
    private static Routing createRouting(Config config) {

        MicrometerSupport micrometerSupport = MicrometerSupport.create();
        Counter personalizedGetCounter = micrometerSupport.registry()
                .counter("personalizedGets");
        Timer getTimer = Timer.builder("allGets")
                .publishPercentileHistogram()
                .register(micrometerSupport.registry());

        GreetService greetService = new GreetService(config, getTimer, personalizedGetCounter);

        return Routing.builder()
                .register(micrometerSupport)                 // Micrometer support at "/micrometer"
                .register("/greet", greetService)
                .build();
    }
}
