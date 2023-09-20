/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.quickstart.se;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.logging.common.LogConfig;
import io.helidon.openapi.OpenApiFeature;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;

/**
 * The application main class.
 */
public final class Main {

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        // load logging configuration
        LogConfig.configureRuntime();

        WebServer server = WebServer.builder()
                .config(GlobalConfig.config().get("server"))
                .routing(Main::routing)
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/greet");
    }

    /**
     * Updates HTTP Routing and registers observe providers.
     */
    static void routing(HttpRouting.Builder routing) {
        Config config = GlobalConfig.config();

        OpenApiFeature openApi = OpenApiFeature.create(config.get("openapi"));
        GreetService greetService = new GreetService(config.get("app"));
        routing.addFeature(openApi)
                .register("/greet", greetService)
                .addFeature(ObserveFeature.create(config.get("observe")));
    }
}
