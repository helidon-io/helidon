/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.serviceregistry;

import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.webserver.WebServer;

/**
 * Example of using Helidon inject registry.
 */
public class Main {
    private Main() {
    }

    /**
     * Main method.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        InjectRegistry registry = InjectRegistryManager.create()
                .registry();

        WebServer server = WebServer.builder()
                .routing(routing -> routing.get("/imperative", (req, res) -> res.send("Hello from imperative.")))
                .serviceRegistry(registry)
                .build()
                .start();

        System.out.println("Started server on http://localhost:" + server.port());
    }
}
