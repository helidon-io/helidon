/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.nima.basic;

import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

/**
 * As simple as possible with a fixed port.
 */
public class BasicMain {
    private BasicMain() {
    }

    /**
     * Main method.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        /*
         This would be the simplest possible server
         We do not use it, as we want testability
         */
        /*
        WebServer.builder()
                .port(8080)
                .routing(router -> router.get("/*", (req, res) -> res.send("Níma Works!")))
                .start();
         */
        WebServer.builder()
                .port(8080)
                .routing(BasicMain::routing)
                .build()
                .start();
    }

    /**
     * Set up HTTP routing.
     * This method is used from both unit and integration tests.
     *
     * @param router HTTP routing builder to configure routes for this service
     */
    static void routing(HttpRouting.Builder router) {
        router.get("/*", (req, res) -> res.send("Níma Works!"));
    }
}
