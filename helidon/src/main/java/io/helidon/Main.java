/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon;

import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.logging.common.LogConfig;
import io.helidon.spi.HelidonStartupProvider;

/**
 * Main entry point for any Helidon application.
 * {@link java.util.ServiceLoader} is used to discover the correct {@link io.helidon.spi.HelidonStartupProvider}
 * to start the application (probably either Helidon Injection based application, or a CDI based application).
 * <p>
 * The default option is to start Helidon injection based application.
 */
public class Main {
    static {
        LogConfig.initClass();
    }

    private Main() {
    }

    /**
     * Start Helidon.
     * This method is required to start directly from a command line.
     *
     * @param args arguments of the application
     */
    public static void main(String[] args) {
        // we always initialize logging
        LogConfig.configureRuntime();

        // this should only be called once in a lifetime of the server, so no need to optimize
        var services = HelidonServiceLoader.create(ServiceLoader.load(HelidonStartupProvider.class))
                .asList();

        if (services.isEmpty()) {
            throw new IllegalStateException("Helidon Main class can only be called if a startup provider is available. "
                                                    + "Please use either Helidon Injection, or Helidon MicroProfile "
                                                    + "(or a custom extension). If neither is available, you should use "
                                                    + "your own Main class.");
        }
        services.get(0).start(args);
    }
}
