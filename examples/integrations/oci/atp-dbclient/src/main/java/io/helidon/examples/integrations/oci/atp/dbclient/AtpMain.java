/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.oci.atp.dbclient;

import java.io.IOException;

import io.helidon.logging.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

/**
 * Main class of the example.
 * This example sets up a web server to serve REST API to retrieve ATP wallet.
 */
public final class AtpMain {

    private static Config config;

    /**
     * Cannot be instantiated.
     */
    private AtpMain() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) throws IOException {
        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        config = Config.create();

        WebServer server = WebServer.builder()
                .routing(AtpMain::routing)
                .port(config.get("server.port").asInt().orElse(8080))
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port());
    }

    /**
     * Updates HTTP Routing.
     */
    static void routing(HttpRouting.Builder routing) {
        AtpService atpService = new AtpService(config);
        routing.register("/atp", atpService);
    }
}
