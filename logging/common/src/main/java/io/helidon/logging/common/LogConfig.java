/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.NativeImageHelper;
import io.helidon.logging.common.spi.LoggingProvider;

/**
 * Logging configuration utility.
 * This utility looks for logging provider and configures it either for initialization or for runtime.
 * <p>
 * Initialization is done automatically at GraalVM native image build (through Helidon feature). Runtime initialization
 * is done automatically when using a container (such as Helidon MP), for Helidon SE (and programmatic API) it is recommended
 * to invoke {@link #configureRuntime()} as the first thing in your application, so all logs are correctly handled.
 */
public final class LogConfig {
    private static final AtomicBoolean RUNTIME_CONFIGURED = new AtomicBoolean();
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
     * Reconfigures logging with runtime configuration if within a native image.
     * See GraalVM native image support in Helidon.
     */
    public static void configureRuntime() {
        if (NativeImageHelper.isRuntime() && RUNTIME_CONFIGURED.compareAndSet(false, true)) {
            // only do this once
            PROVIDER.runTime();
        }
    }

    /**
     * This method is for internal use, to correctly load logging configuration at AOT build time.
     */
    public static void initClass() {
        // DO NOT DELETE THIS METHOD
        // we need to ensure class initialization for native image by invoking this method
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
