/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.metrics.exemplar;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.tracing.TracingFeature;

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
    public static void main(String[] args) {
        WebServerConfig.Builder builder = WebServer.builder();
        setup(builder);
        WebServer server = builder.build().start();
        System.out.println("WEB server is up! http://localhost:" + server.port() + "/greet");
    }

    /**
     * Set up the server.
     *
     * @param server server builder
     */
    static void setup(WebServerConfig.Builder server) {
        // load logging configuration
        LogConfig.configureRuntime();

        // By default, this will pick up application.yaml from the classpath
        Config config = Config.create();
        Config.global(config);

        server.routing(Main::routing)
                .config(config.get("server"));

    }

    /**
     * Setup routing.
     *
     * @param routing routing builder
     */
    static void routing(HttpRouting.Builder routing) {
        Config config = Config.global();
        Tracer tracer = TracerBuilder.create(config.get("tracing")).build();
        routing.addFeature(TracingFeature.create(tracer))
                .register("/greet", new GreetService());
    }
}
