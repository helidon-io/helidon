/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.util.concurrent.TimeUnit;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.logging.common.HelidonMdc;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Main class of the example, runnable from command line.
 */
public final class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final java.util.logging.Logger JUL_LOGGER = java.util.logging.Logger.getLogger(Main.class.getName());

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

        WebServer.builder()
                .host("localhost")
                .routing(Routing.builder()
                                 .get("/", (req, res) -> {
                                     HelidonMdc.set("name", String.valueOf(req.requestId()));
                                     LOGGER.debug("Debug message to show runtime reloading works");
                                     LOGGER.info("Running in webserver, id:");
                                     res.send("Hello")
                                             .forSingle(ignored -> LOGGER.debug("Response sent"));
                                 })
                                 .build())
                .port(8080)
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);
    }

    private static void setupLogging() {
        String location = System.getProperty("logback.configurationFile");
        location = (location == null) ? "logback-runtime.xml" : location;
        // we cannot use anything that starts threads at build time, must re-configure here
        resetLogging(location);

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
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
        JUL_LOGGER.info("Using JUL logger");

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
