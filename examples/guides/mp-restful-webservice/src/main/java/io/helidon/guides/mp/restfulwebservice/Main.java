/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 *
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
     * Application main entry point.
     * @param args command line arguments
     * @throws IOException if there are problems reading logging properties
     */
    // tag::main[]
    public static void main(final String[] args) throws IOException {
        setupLogging();

        Server server = startServer();

        System.out.println("http://localhost:" + server.getPort() + "/greet");
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
