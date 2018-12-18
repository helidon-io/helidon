/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.logging.LogManager;

import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.zipkin.ZipkinTracerBuilder;

/**
 * The ZipkinExampleMain is an app that leverages a use of Open Tracing and sends
 * the collected data to Zipkin.
 *
 * @see ZipkinTracerBuilder
 */
public final class ZipkinExampleMain {

    private ZipkinExampleMain() {
    }

    /**
     * Run the OpenTracing application.
     *
     * @param args not used
     * @throws Exception in case of an error
     */
    public static void main(String[] args) throws Exception {

        // configure logging in order to not have the standard JVM defaults
        LogManager.getLogManager().readConfiguration(ZipkinExampleMain.class.getResourceAsStream("/logging.properties"));

        WebServer webServer = WebServer.create(
                ServerConfiguration.builder()
                                   .port(8080)
                                   .tracer(ZipkinTracerBuilder.forService("demo-first").zipkinHost("zipkin")),
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
                       }));

        webServer.start()
                 .whenComplete((server, throwable) -> {
                     System.out.println("Started at http://localhost:" + server.port());
                 });
    }

}
