/*
 * Copyright (c) 2017, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.opentracing;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.logging.common.LogConfig;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.tracing.TracingObserver;

/**
 * The application uses Open Tracing and sends the collected data to ZipKin.
 *
 * @see io.helidon.tracing.TracerBuilder
 * @see io.helidon.tracing.providers.zipkin.ZipkinTracerBuilder
 */
public final class Main {

    private Main() {
    }

    /**
     * Run the OpenTracing application.
     *
     * @param args not used
     */
    public static void main(String[] args) {

        // configure logging in order to not have the standard JVM defaults
        LogConfig.configureRuntime();

        WebServer server = setupServer(WebServerConfig.builder(), 9411);

        System.out.println("Started at http://localhost:" + server.port());
    }

    static WebServer setupServer(WebServerConfig.Builder builder, int port) {
        Config config = Config.builder()
                .sources(ConfigSources.create(Map.of("host", "localhost",
                        "port", "8080")))
                .build();

        Tracer tracer = TracerBuilder.create("demo-first")
                .collectorPort(port)
                .registerGlobal(true)
                .build();

        return builder
                .config(config)
                .addFeature(ObserveFeature.builder()
                        .addObserver(TracingObserver.create(tracer))
                        .build())
                .routing(routing -> routing
                        .get("/test", (req, res) -> res.send("Hello World!"))
                        .post("/hello", (req, res) -> res.send("Hello: " + req.content().as(String.class))))
                .build()
                .start();
    }
}
