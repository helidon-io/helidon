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

package io.helidon.examples.webserver.observe;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

/**
 * Register observe support with all available observers and NO security.
 * Some observers may disclose secret or private information and should be protected, use with care (and security).
 */
public class ObserveMain {
    private ObserveMain() {
    }

    /**
     * Main method.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        // load logging
        LogConfig.configureRuntime();

        Config config = Config.create();

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(ObserveMain::routing)
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port());
    }

    /**
     * Set up HTTP routing.
     * This method is used from tests as well.
     *
     * @param router HTTP routing builder
     */
    static void routing(HttpRouting.Builder router) {
        router.get("/", (req, res) -> res.send("WebServer Works!"));
    }
}
