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
import io.helidon.nima.http.media.jsonp.JsonpSupport;
import io.helidon.nima.webserver.WebServer;

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

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        MicrometerFeature micrometerSupport = MicrometerFeature.create();
        Counter personalizedGetCounter = micrometerSupport.registry()
                .counter(PERSONALIZED_GETS_COUNTER_NAME);
        Timer getTimer = Timer.builder(ALL_GETS_TIMER_NAME)
                .publishPercentileHistogram()
                .register(micrometerSupport.registry());

        GreetService greetService = new GreetService(config, getTimer, personalizedGetCounter);

        // Get webserver config from the "server" section of application.yaml
        WebServer webServer = WebServer.builder()
                .host("localhost")
                .config(config.get("server"))
                .port(-1)
                .addMediaSupport(JsonpSupport.create(config))
                .routing(router -> router
                        .register("/greet", () -> greetService)
                        .addFeature(() -> MicrometerFeature.builder().config(config).build()))
                .build()
                .start();
        System.out.println("WEB server is up! http://localhost:" + webServer.port() + "/greet");
        return webServer;
    }
}
