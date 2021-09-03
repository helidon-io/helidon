/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.examples.abac;

import io.helidon.config.Config;
import io.helidon.microprofile.server.Server;

/**
 * Jersey example for Attribute based access control.
 */
public final class AbacJerseyMain {
    private static Server server;

    private AbacJerseyMain() {
    }

    /**
     * Main method of example. No arguments required, no configuration required.
     *
     * @param args empty is OK
     * @throws Throwable if server fails to start
     */
    public static void main(String[] args) throws Throwable {
        server = startIt();
    }

    static Server startIt() {
        Config config = Config.create();

        Server server = Server.builder()
                .config(config)
                .port(8080)
                .build()
                .start();

        System.out.printf("Started server on localhost:%d%n", server.port());
        System.out.println();
        System.out.println("***********************");
        System.out.println("** Endpoints:        **");
        System.out.println("***********************");
        System.out.println("Using declarative authorization (ABAC):");
        System.out.printf("  http://localhost:%1$d/rest/attributes%n", server.port());
        System.out.println("Using explicit authorization (ABAC):");
        System.out.printf("  http://localhost:%1$d/rest/explicit%n", server.port());

        return server;
    }
}
