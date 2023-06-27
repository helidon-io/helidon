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

package io.helidon.examples.micrometer.se;

import io.helidon.config.Config;
import io.helidon.integrations.micrometer.MicrometerFeature;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

/**
 * Simple Hello World rest application.
 */
public final class Main {

    static final String PERSONALIZED_GETS_COUNTER_NAME = "personalizedGets";
    static final String ALL_GETS_TIMER_NAME = "allGets";

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
    public static void main(final String[] args) {
        startServer();
    }

    /**
     * Start the server.
     */
    static WebServer startServer() {

        // load logging configuration
        LogConfig.configureRuntime();

        // By default, this will pick up application.yaml from the classpath
        Config config = Config.create();

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(r -> setupRouting(r, config))
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/greet");
        return server;
    }

    /**
     * Setup routing.
     *
     * @param routing routing builder
     * @param config  config
     */
    static void setupRouting(HttpRouting.Builder routing, Config config) {
        MicrometerFeature micrometerSupport = MicrometerFeature.create(config);
        Counter personalizedGetCounter = micrometerSupport.registry()
                .counter(PERSONALIZED_GETS_COUNTER_NAME);
        Timer getTimer = Timer.builder(ALL_GETS_TIMER_NAME)
                .publishPercentileHistogram()
                .register(micrometerSupport.registry());

        GreetService greetService = new GreetService(config, getTimer, personalizedGetCounter);

        routing.register("/greet", greetService)
                .addFeature(micrometerSupport);
    }
}
