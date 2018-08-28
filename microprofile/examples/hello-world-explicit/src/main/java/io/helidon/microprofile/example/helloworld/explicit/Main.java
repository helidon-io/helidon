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

package io.helidon.microprofile.example.helloworld.explicit;

import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.microprofile.config.MpConfig;
import io.helidon.microprofile.server.Server;

import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;

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
        Weld weld = new Weld();
        LOGGER.finest("Weld instance");
        WeldContainer cdiContainer = weld.initialize();
        LOGGER.finest("Weld initialized");

        Server server = Server.builder()
                .addApplication(HelloWorldApplication.class)
                .cdiContainer(cdiContainer)
                // using a customized helidon config instance (in this case the default...)
                .config(MpConfig.builder().config(Config.create()).build())
                .host("localhost")
                // use a random free port
                .port(0)
                .build();

        server.start();

        System.out.println("Started application on http://" + server.getHost() + ":" + server.getPort() + "/helloworld");

        // the easiest possible explicit way to start an application:
        // Server.create(HelloWorldApplication.class).start();
    }
}
