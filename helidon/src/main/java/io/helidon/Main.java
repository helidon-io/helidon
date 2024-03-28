/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.SerializationConfig;
import io.helidon.common.Weights;
import io.helidon.logging.common.LogConfig;
import io.helidon.spi.HelidonShutdownHandler;
import io.helidon.spi.HelidonStartupProvider;

/**
 * Main entry point for any Helidon application.
 * {@link java.util.ServiceLoader} is used to discover the correct {@link io.helidon.spi.HelidonStartupProvider}
 * to start the application (probably either Helidon Injection based application, or a CDI based application).
 * <p>
 * The default option is to start Helidon injection based application.
 */
public class Main {
    private static final Set<HelidonShutdownHandler> SHUTDOWN_HANDLERS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ReentrantLock SHUTDOWN_HANDLER_LOCK = new ReentrantLock();
    private static final AtomicBoolean SHUTDOWN_HOOK_ADDED = new AtomicBoolean();

    static {
        LogConfig.initClass();
    }

    private Main() {
    }

    /**
     * Start Helidon.
     * This method is required to start directly from a command line.
     *
     * @param args arguments of the application
     */
    public static void main(String[] args) {
        // we always initialize logging
        LogConfig.configureRuntime();
        // and make sure JEP-290 is enforced (deserialization)
        SerializationConfig.configureRuntime();

        // this should only be called once in a lifetime of the server, so no need to optimize
        var services = HelidonServiceLoader.create(ServiceLoader.load(HelidonStartupProvider.class))
                .asList();

        if (services.isEmpty()) {
            throw new IllegalStateException("Helidon Main class can only be called if a startup provider is available. "
                                                    + "Please use either Helidon Injection, or Helidon MicroProfile "
                                                    + "(or a custom extension). If neither is used, you should define "
                                                    + "your own Main class, usually configured as 'mainClass' property in "
                                                    + "your pom.xml.");
        }

        addShutdownHook();

        services.getFirst().start(args);
    }

    /**
     * Add shutdown handler to the list of handlers to be executed on shutdown.
     * <p>
     * On shutdown, the handlers are executed in {@link io.helidon.common.Weight Weighted} order, starting with
     * the highest weight.
     *
     * @param handler to execute
     */
    public static void addShutdownHandler(HelidonShutdownHandler handler) {
        // need to make sure we have a shutdown hook, if started through a custom main class, it
        // would not be added, so add it here (will only be added once)
        addShutdownHook();

        SHUTDOWN_HANDLER_LOCK.lock();
        try {
            SHUTDOWN_HANDLERS.add(handler);
        } finally {
            SHUTDOWN_HANDLER_LOCK.unlock();
        }
    }

    /**
     * Remove a shutdown handler from the list of handlers.
     *
     * @param handler handler to remove, must be the same instance registered with
     *                {@link #addShutdownHandler(io.helidon.spi.HelidonShutdownHandler)}
     */
    public static void removeShutdownHandler(HelidonShutdownHandler handler) {
        SHUTDOWN_HANDLER_LOCK.lock();
        try {
            SHUTDOWN_HANDLERS.remove(handler);
        } finally {
            SHUTDOWN_HANDLER_LOCK.unlock();
        }
    }

    private static void addShutdownHook() {
        if (SHUTDOWN_HOOK_ADDED.compareAndSet(false, true)) {
            Thread shutdownHook = Thread.ofPlatform()
                    .daemon(false)
                    .name("helidon-shutdown-thread")
                    .unstarted(Main::shutdown);

            Runtime.getRuntime()
                    .addShutdownHook(shutdownHook);

            // we also need to keep the logging system active until the shutdown hook completes
            // this introduces a hard dependency on JUL, as we cannot abstract this easily away
            // this is to workaround https://bugs.openjdk.java.net/browse/JDK-8161253
            keepLoggingActive(shutdownHook);
        }
    }

    private static void shutdown() {
        SHUTDOWN_HANDLER_LOCK.lock();
        try {
            System.Logger logger = System.getLogger(Main.class.getName());

            logger.log(Level.INFO, "Shutdown requested by JVM shutting down");
            List<HelidonShutdownHandler> handlers = new ArrayList<>(SHUTDOWN_HANDLERS);

            handlers.sort(Weights.weightComparator());

            for (HelidonShutdownHandler handler : handlers) {
                try {
                    if (logger.isLoggable(Level.TRACE)) {
                        logger.log(Level.TRACE, "Calling shutdown handler: " + handler);
                    }
                    handler.shutdown();
                } catch (Exception e) {
                    logger.log(Level.ERROR, "Failed when calling shutdown handler: " + handler);
                }
            }

            SHUTDOWN_HANDLERS.clear();
            SHUTDOWN_HOOK_ADDED.set(false);

            logger.log(Level.INFO, "Shutdown finished");
        } finally {
            SHUTDOWN_HANDLER_LOCK.unlock();
        }
    }

    private static void keepLoggingActive(Thread shutdownHook) {
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        Handler[] handlers = rootLogger.getHandlers();

        List<Handler> newHandlers = new ArrayList<>();

        boolean added = false;
        for (Handler handler : handlers) {
            if (handler instanceof KeepLoggingActiveHandler) {
                // we want to replace it with our current shutdown hook
                newHandlers.add(new KeepLoggingActiveHandler(shutdownHook));
                added = true;
            } else {
                newHandlers.add(handler);
            }
        }
        if (!added) {
            // out handler must be first, so other handlers are not closed before we finish shutdown hook
            newHandlers.addFirst(new KeepLoggingActiveHandler(shutdownHook));
        }

        for (Handler handler : handlers) {
            rootLogger.removeHandler(handler);
        }
        for (Handler newHandler : newHandlers) {
            rootLogger.addHandler(newHandler);
        }
    }

    private static final class KeepLoggingActiveHandler extends Handler {
        private final Thread shutdownHook;

        private KeepLoggingActiveHandler(Thread shutdownHook) {
            this.shutdownHook = shutdownHook;
        }

        @Override
        public void publish(LogRecord record) {
            // noop
        }

        @Override
        public void flush() {
            // noop
        }

        @Override
        public void close() {
            try {
                shutdownHook.join();
            } catch (InterruptedException ignored) {
            }
        }
    }
}
