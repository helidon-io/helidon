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

package io.helidon.examples.logging.logback.aot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.logging.common.HelidonMdc;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Main class of the example, runnable from command line.
 */
public final class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final System.Logger SYSTEM_LOGGER = System.getLogger(Main.class.getName());

    private Main() {
    }

    /**
     * Starts the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        // use slf4j for JUL as well
        setupLogging();

        // the Helidon context is used to propagate MDC across threads
        // if running within Helidon WebServer, you do not need to runInContext, as that is already
        // done by the webserver
        Contexts.runInContext(Context.create(), Main::logging);

        WebServer server = WebServer.builder()
                .port(8080)
                .routing(Main::routing)
                .build()
                .start();
    }

    private static void routing(HttpRouting.Builder routing) {
        routing.get("/", (req, res) -> {
            HelidonMdc.set("name", String.valueOf(req.id()));
            LOGGER.debug("Debug message to show runtime reloading works");
            LOGGER.info("Running in webserver, id:");
            res.send("Hello");
            LOGGER.debug("Response sent");
        });
    }

    private static void setupLogging() {
        String location = System.getProperty("logback.configurationFile");
        location = (location == null) ? "logback-runtime.xml" : location;
        // we cannot use anything that starts threads at build time, must re-configure here
        resetLogging(location);
    }

    private static void resetLogging(String location) {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory instanceof LoggerContext) {
            resetLogging(location, (LoggerContext) loggerFactory);
        } else {
            LOGGER.warn("Expecting a logback implementation, but got " + loggerFactory.getClass().getName());
        }
    }

    private static void resetLogging(String location, LoggerContext loggerFactory) {
        JoranConfigurator configurator = new JoranConfigurator();

        configurator.setContext(loggerFactory);
        loggerFactory.reset();

        try {
            configurator.doConfigure(location);

            Logger instance = LoggerFactory.getLogger(Main.class);
            instance.info("Runtime logging configured from file \"{}\".", location);
            StatusPrinter.print(loggerFactory);
        } catch (JoranException e) {
            LOGGER.warn("Failed to reload logging from " + location, e);
            e.printStackTrace();
        }
    }

    private static void logging() {
        HelidonMdc.set("name", "startup");
        LOGGER.info("Starting up");
        SYSTEM_LOGGER.log(System.Logger.Level.INFO, "Using System logger");

        // now let's see propagation across executor service boundary, we can also use Log4j's ThreadContext
        MDC.put("name", "propagated");
        // wrap executor so it supports Helidon context, this is done for all built-in executors in Helidon
        ExecutorService es = Contexts.wrap(Executors.newSingleThreadExecutor());

        Future<?> submit = es.submit(Main::log);
        try {
            submit.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        es.shutdown();
    }

    private static void log() {
        LOGGER.info("Running on another thread");
    }
}
