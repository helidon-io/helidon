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

package io.helidon.webserver.examples.opentracing;

import java.io.IOException;
import java.util.logging.LogManager;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * The ZipkinExampleMain is an app that leverages a use of Open Tracing and sends
 * the collected data to Zipkin.
 *
 * @see io.helidon.tracing.TracerBuilder
 * @see io.helidon.tracing.zipkin.ZipkinTracerBuilder
 */
public final class Main {

    private Main() {
    }

    /**
     * Run the OpenTracing application.
     *
     * @param args not used
     * @throws IOException in case of an error
     */
    public static void main(String[] args) throws IOException {

        // configure logging in order to not have the standard JVM defaults
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));

        Config config = Config.builder()
                .sources(ConfigSources.environmentVariables())
                .build();

        WebServer webServer = WebServer.builder(
                Routing.builder()
                        .any((req, res) -> {
                            System.out.println("Received another request.");
                            req.next();
                        })
                        .get("/test", (req, res) -> res.send("Hello World!"))
                        .post("/hello", (req, res) -> {
                            req.content()
                                    .as(String.class)
                                    .thenAccept(s -> res.send("Hello: " + s))
                                    .exceptionally(t -> {
                                        req.next(t);
                                        return null;
                                    });
                        }))
                .port(8080)
                .tracer(TracerBuilder.create(config.get("tracing"))
                                .serviceName("demo-first")
                                .registerGlobal(true)
                                .build())
                .build();

        webServer.start()
                .whenComplete((server, throwable) -> {
                    System.out.println("Started at http://localhost:" + server.port());
                });
    }

}
