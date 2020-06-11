/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.common;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Logging configuration utility.
 * Methods are invoked by Helidon on startup, so you do not need to explicitly configure
 * Java Util logging as long as a file {@code logging.properties} is on the classpath or
 * in the current directory, or you configure logging explicitly using System properties.
 * Both {@value #SYS_PROP_LOGGING_CLASS} and {@value #SYS_PROP_LOGGING_FILE} are
 * honored.
 * If you wish to configure the logging system differently, just do not include the file and/or
 * system properties, or set system property {@value #SYS_PROP_DISABLE_CONFIG} to {@code true}.
 */
public final class LogConfig {
    private static final String LOGGING_FILE = "logging.properties";
    private static final String SYS_PROP_DISABLE_CONFIG = "io.helidon.logging.config.disabled";
    private static final String SYS_PROP_LOGGING_CLASS = "java.util.logging.config.class";
    private static final String SYS_PROP_LOGGING_FILE = "java.util.logging.config.file";

    static {
        configureLogging("initialization");
    }

    private LogConfig() {
    }

    /**
     * Reconfigures logging with runtime configuration if within a native image.
     * See GraalVM native image support in Helidon.
     */
    public static void configureRuntime() {
        if (NativeImageHelper.isRuntime()) {
            configureLogging("runtime");
        }
    }

    // when is either `initialization` or `runtime`
    // when building native image, the `initialization` is called
    // when running it, the `runtime` is called
    // when outside of native-image, only `initialization` is called
    private static void configureLogging(String when) {
        try {
            doConfigureLogging(when);
        } catch (IOException e) {
            System.err.println("Failed to configure logging");
            e.printStackTrace();
        }
    }

    private static void doConfigureLogging(String when) throws IOException {
        String disableConfigProperty = System.getProperty(SYS_PROP_DISABLE_CONFIG);
        if (Boolean.parseBoolean(disableConfigProperty)) {
            // we are explicitly request to disable this feature
            return;
        }
        String configClass = System.getProperty(SYS_PROP_LOGGING_CLASS);
        String configPath = System.getProperty(SYS_PROP_LOGGING_FILE);
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

        Logger.getLogger(LogConfig.class.getName()).info("Logging at " + when + " configured using " + source);
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
            InputStream resourceStream = LogConfig.class.getResourceAsStream("/" + LOGGING_FILE);
            if (resourceStream != null) {
                logConfigStream = new BufferedInputStream(resourceStream);
                source = "classpath: /" + LOGGING_FILE;
            } else {
                return source;
            }
        }

        try {
            LogManager.getLogManager().readConfiguration(logConfigStream);
        } finally {
            logConfigStream.close();
        }

        return source;
    }

    /**
     * This method is for internal use, to correctly load logging configuration at AOT build time.
     */
    public static void initClass() {
        // DO NOT DELETE THIS METHOD
        // we need to ensure class initialization for native image by invoking this method
    }
}
