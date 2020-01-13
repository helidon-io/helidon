/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.example.helloworld.explicit;

import java.util.logging.Logger;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import io.helidon.microprofile.server.Server;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

/**
 * Explicit example.
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger("io.helidon.microprofile.startup");

    private Main() {
    }

    /**
     * Starts server and initializes CDI container manually.
     *
     * @param args command line arguments (ignored)
     */
    public static void main(String[] args) {
        LOGGER.finest("Main method");
        SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        LOGGER.finest("Weld instance");
        SeContainer cdiContainer = initializer.initialize();
        LOGGER.finest("Weld initialized");

        Server server = Server.builder()
                .addApplication(HelloWorldApplication.class)
                // using a customized helidon config instance (in this case the default...)
                .config(ConfigProviderResolver.instance()
                                .getBuilder()
                                .build())
                .host("localhost")
                // use a random free port
                .port(0)
                .build();

        server.start();

        String endpoint = "http://" + server.host() + ":" + server.port();
        System.out.println("Started application on     " + endpoint + "/helloworld");
        System.out.println("Metrics available on       " + endpoint + "/metrics");
        System.out.println("Heatlh checks available on " + endpoint + "/health");

        // the easiest possible explicit way to start an application:
        // Server.create(HelloWorldApplication.class).start();
    }
}
