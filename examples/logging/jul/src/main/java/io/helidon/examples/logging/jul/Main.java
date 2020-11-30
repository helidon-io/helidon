/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.examples.logging.jul;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.common.LogConfig;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.logging.common.HelidonMdc;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Main class of the example, runnable from command line.
 */
public final class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    /**
     * Starts the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();

        // the Helidon context is used to propagate MDC across threads
        // if running within Helidon WebServer, you do not need to runInContext, as that is already
        // done by the webserver
        Contexts.runInContext(Context.create(), Main::logging);

        WebServer.builder()
                .routing(Routing.builder()
                                 .get("/", (req, res) -> {
                                     HelidonMdc.set("name", String.valueOf(req.requestId()));
                                     LOGGER.info("Running in webserver, id:");
                                     res.send("Hello");
                                 })
                                 .build())
                .port(8080)
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);
    }

    private static void logging() {
        HelidonMdc.set("name", "startup");
        LOGGER.info("Starting up");

        // now let's see propagation across executor service boundary
        HelidonMdc.set("name", "propagated");
        // wrap executor so it supports Helidon context, this is done for all built-in executors in Helidon
        ExecutorService es = Contexts.wrap(Executors.newSingleThreadExecutor());

        Future<?> submit = es.submit(() -> {
            LOGGER.info("Running on another thread");
        });
        try {
            submit.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        es.shutdown();
    }
}
