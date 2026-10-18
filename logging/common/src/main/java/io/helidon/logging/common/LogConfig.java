/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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
package io.helidon.logging.common;

import java.util.List;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.logging.common.spi.LoggingProvider;

/**
 * Logging configuration utility.
 * This utility looks for a logging provider and configures it once during class initialization.
 * <p>
 * Initialization is done automatically when using a container (such as Helidon MP). For Helidon SE (and programmatic API),
 * invoke {@link #configureRuntime()} as the first thing in your application, so all logs are correctly handled.
 */
public final class LogConfig {
    private static final LoggingProvider PROVIDER;

    static {
        List<LoggingProvider> providers = HelidonServiceLoader.create(ServiceLoader.load(LoggingProvider.class))
                .asList();
        if (providers.isEmpty()) {
            System.err.println("There is no Helidon logging implementation on classpath, skipping log configuration.");
            PROVIDER = new NoOpProvider();
        } else {
            PROVIDER = providers.get(0);
        }

        PROVIDER.initialization();

        if (providers.size() > 1) {
            System.getLogger(LogConfig.class.getName())
                    .log(System.Logger.Level.TRACE, "Multiple logging providers on classpath, using the first one: "
                            + PROVIDER.getClass().getName());
        }
    }

    private LogConfig() {
    }

    /**
     * Ensures logging is configured by initializing this class.
     * Subsequent calls do not reconfigure logging.
     */
    public static void configureRuntime() {
        // Invoking this method triggers class initialization.
    }

    /**
     * Ensures logging is configured by initializing this class. Subsequent calls do not reconfigure logging.
     */
    public static void initClass() {
        // Invoking this method triggers class initialization.
    }

    private static final class NoOpProvider implements LoggingProvider {
        @Override
        public void initialization() {
        }

        @Override
        public void runTime() {
        }
    }
}
