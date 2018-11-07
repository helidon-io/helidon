/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.guides.se.restfulwebservice;

import java.io.IOException;
import java.util.logging.LogManager;

// tag::importsHealth1[]
import io.helidon.common.http.Http;
// end::importsHealth1[]
// tag::importsStart[]
import io.helidon.config.Config;
// end::importsStart[]
// tag::importsMetrics[]
import io.helidon.metrics.MetricsSupport;
// end::importsMetrics[]
// tag::importsWebServer[]
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
// end::importsWebServer[]
// tag::importsHealth2[]
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
// end::importsHealth2[]
// tag::importsEnd[]
import io.helidon.webserver.WebServer;
import io.helidon.webserver.json.JsonSupport;
// end::importsEnd[]
// tag::importsHealth3[]
import javax.json.Json;
import javax.json.JsonObject;
// end::importsHealth3[]

/**
 * Simple Hello World rest application.
 */
public final class Main {

    // tag::greetServiceDecl[]
    private static GreetService greetService;
    // end::greetServiceDecl[]

    /**
     * Cannot be instantiated.
     */
    private Main() { }

    /**
     * Creates new {@link Routing}.
     *
     * @return the new instance
     */
    // tag::createRoutingFull[]
    // tag::createRoutingStart[]
    private static Routing createRouting() {
    // end::createRoutingStart[]
    // tag::initMetrics[]
        final MetricsSupport metrics = MetricsSupport.create(); // <1>
    // end::initMetrics[]
    // tag::createRoutingBasic[]
        greetService = new GreetService(); // <1>
        return Routing.builder()
                .register(JsonSupport.get()) // <2>
    // end::createRoutingBasic[]
    // tag::registerMetrics[]
                .register(metrics) // <1>
    // end::registerMetrics[]
    // tag::registerGreetService[]
                .register("/greet", greetService) // <3>
    // end::registerGreetService[]
    // tag::createRoutingHealth[]
                .get("/alive", Main::alive)
                .get("/ready", Main::ready)
    // end::createRoutingHealth[]
    // tag::createRoutingEnd[]
                .build();
    }
    // end::createRoutingEnd[]
    // end::createRoutingFull[]

    /**
     * Application main entry point.
     * @param args command line arguments.
     * @throws IOException if there are problems reading logging properties
     */
    public static void main(final String[] args) throws IOException {
        // tag::mainContent[]
        startServer();
        // end::mainContent[]
    }

    /**
     * Start the server.
     * @return the created {@link WebServer} instance
     * @throws IOException if there are problems reading logging properties
     */
    // tag::startServer[]
    protected static WebServer startServer() throws IOException {

        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                Main.class.getResourceAsStream("/logging.properties"));

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // Get webserver config from the "server" section of application.yaml
        ServerConfiguration serverConfig =
                ServerConfiguration.fromConfig(config.get("server")); // <1>

        WebServer server = WebServer.create(serverConfig, createRouting()); // <2>

        // Start the server and print some info.
        server.start().thenAccept(ws -> { // <3>
            System.out.println(
                    "WEB server is up! http://localhost:" + ws.port());
        });

        // Server threads are not demon. NO need to block. Just react.
        server.whenShutdown().thenRun(() // <4>
                -> System.out.println("WEB server is DOWN. Good bye!"));

        return server;
    }
    // end::startServer[]

    /**
     * Responds with a health message.
     * @param request the server request
     * @param response the server response
     */
    // tag::alive[]
    private static void alive(final ServerRequest request,
                        final ServerResponse response) {
        /*
         * Return 200 if the greeting is set to something non-null and non-empty;
         * return 500 (server error) otherwise.
         */
        String greetServiceError = greetService.checkHealth(); //<1>
        if (greetServiceError == null) {
            response
                    .status(Http.Status.OK_200) //<2>
                    .send();
        } else {
            JsonObject returnObject = Json.createObjectBuilder() //<3>
                    .add("error", greetServiceError)
                    .build();
            response
                    .status(Http.Status.INTERNAL_SERVER_ERROR_500) //<4>
                    .send(returnObject);
        }
    }
    // end::alive[]

    /**
     * Implements a very simple readiness check.
     * @param request the server request
     * @param response the server response
     */
    //tag::ready[]
    private static void ready(final ServerRequest request,
                       final ServerResponse response) {
        response
                .status(Http.Status.OK_200)
                .send();
    }
    //end::ready[]
}
