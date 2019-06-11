/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.server;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Start a Helidon microprofile server that collects JAX-RS resources from
 * configuration or from classpath.
 * <p>
 * Uses {@code logging.properties} to configure Java logging unless a configuration is defined through
 * a Java system property. The file is expected either in the directory the application was started, or on
 * the classpath.
 */
public final class Main {
    private static final String LOGGING_FILE = "logging.properties";
    private static int port = 0;

    private Main() {
    }

    /**
     * Main method to start server. The server will collection JAX-RS application automatically (through
     * CDI extension - just annotate it with {@link javax.enterprise.context.ApplicationScoped}).
     *
     * @param args command line arguments, currently ignored
     */
    @SuppressWarnings("CallToPrintStackTrace")
    public static void main(String[] args) {
        try {
            configureLogging();
        } catch (IOException e) {
            System.err.println("Failed to configure logging");
            e.printStackTrace();
        }

        Server server = Server.create();
        server.start();
        port = server.port();
    }

    private static void configureLogging() throws IOException {
        String configClass = System.getProperty("java.util.logging.config.class");
        String configPath = System.getProperty("java.util.logging.config.file");
        String source;

        if (configClass != null) {
            source = "class: " + configClass;
        } else if (configPath != null) {
            Path path = Paths.get(configPath);
            source = path.toAbsolutePath().toString();
        } else {
            // we want to configure logging ourselves
            source = findAndConfigureLogging();
        }

        Logger.getLogger(Main.class.getName()).info("Logging configured using " + source);
    }

    private static String findAndConfigureLogging() throws IOException {
        String source = "defaults";

        // Let's try to find a logging.properties
        // first as a file in the current working directory
        InputStream logConfigStream;

        Path path = Paths.get("").resolve(LOGGING_FILE);

        if (Files.exists(path)) {
            logConfigStream = new BufferedInputStream(Files.newInputStream(path));
            source = "file: " + path.toAbsolutePath();
        } else {
            // second look for classpath (only the first one)
            InputStream resourceStream = Main.class.getResourceAsStream("/" + LOGGING_FILE);
            if (null != resourceStream) {
                logConfigStream = new BufferedInputStream(resourceStream);
                source = "classpath: /" + LOGGING_FILE;
            } else {
              logConfigStream = null;
            }
        }
        if (null != logConfigStream) {
            try {
                LogManager.getLogManager().readConfiguration(logConfigStream);
            } finally {
                logConfigStream.close();
            }
        }

        return source;
    }

    /**
     * Once the server is started (e.g. the main method finished), the
     * server port can be obtained with this method.
     * This method will return a reasonable value only if the
     * server is started through {@link #main(String[])} method.
     * Otherwise use {@link Server#port()}.
     *
     * @return port the server started on
     */
    public static int serverPort() {
        return port;
    }
}
