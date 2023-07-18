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

package io.helidon.tests.integration.webclient;

import io.helidon.config.Config;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.nima.webserver.context.ContextFeature;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.tracing.TracingFeature;
import io.helidon.security.integration.nima.SecurityFeature;

import io.helidon.tracing.opentracing.OpenTracing;
import io.opentracing.Tracer;

/**
 * The application main class.
 */
public final class Main {

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    public static void main(String[] args) {
        startServer();
    }

    /**
     * Start the server.
     *
     * @return the created {@link WebServer} instance
     */
    static WebServer startServer() {
        return startServer(null);
    }

    /**
     * Start the server.
     *
     * @param tracer tracer, may be {@code null}
     * @return the created {@link WebServer} instance
     */
    static WebServer startServer(Tracer tracer) {
        WebServerConfig.Builder builder = WebServer.builder();
        setup(builder, tracer);
        WebServer server = builder.build().start();
        System.out.println("WEB server is up! http://localhost:" + server.port() + "/greet");
        return server;
    }

    /**
     * Set up the server.
     *
     * @param builder server builder
     * @param tracer  tracer, may be {@code null}
     */
    static void setup(WebServerConfig.Builder builder, Tracer tracer) {
        Config config = Config.create();
        builder.config(config.get("server"));
        builder.routing(routing -> routing(routing, config, tracer));
    }

    /**
     * Set up routing.
     *
     * @param config configuration of this server
     * @param tracer tracer, may be {@code null}
     */
    static void routing(HttpRouting.Builder routing, Config config, Tracer tracer) {
        GreetService greetService = new GreetService(config);
        routing.addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.create(config.get("security")))
                .register("/greet", greetService);
        if (tracer != null) {
            routing.addFeature(TracingFeature.create(OpenTracing.create(tracer)));
        }
    }
}
