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
package io.helidon.guides.mp.restfulwebservice;

// tag::javaImports[]
import java.io.IOException;
import java.util.logging.LogManager;
// end::javaImports[]
// tag::helidonMPImports[]
import io.helidon.microprofile.server.Server;
// end::helidonMPImports[]
/**
 *
 */
public class Main {

    /**
     * Cannot be instantiated.
     */
    private Main() { }

    /**
     * Application main entry point.
     * @param args command line arguments
     * @throws IOException if there are problems reading logging properties
     */
    // tag::main[]
    public static void main(final String[] args) throws IOException {
        setupLogging();

        Server server = startServer();

        System.out.println("http://localhost:" + server.port() + "/greet");
    }
    // end::main[]

    /**
     * Start the server.
     * @return the created {@link Server} instance
     */
    // tag::startServer[]
    static Server startServer() {
        // Server will automatically pick up configuration from
        // microprofile-config.properties
        // and Application classes annotated as @ApplicationScoped
        return Server.create().start(); // <1>
    }
    // end::startServer[]

    /**
     * Configure logging from logging.properties file.
     */
    // tag::setupLogging[]
    private static void setupLogging() throws IOException {
        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                Main.class.getResourceAsStream("/logging.properties")); // <1>
    }
    // end::setupLogging[]

}
