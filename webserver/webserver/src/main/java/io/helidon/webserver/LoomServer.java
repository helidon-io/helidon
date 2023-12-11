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

package io.helidon.webserver;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.SerializationConfig;
import io.helidon.common.Version;
import io.helidon.common.context.Context;
import io.helidon.common.features.HelidonFeatures;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.tls.Tls;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.spi.ServerFeature;

class LoomServer implements WebServer {
    private static final System.Logger LOGGER = System.getLogger(LoomServer.class.getName());
    private static final String EXIT_ON_STARTED_KEY = "exit.on.started";
    private static final AtomicInteger WEBSERVER_COUNTER = new AtomicInteger(1);

    private final Map<String, ServerListener> listeners;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Lock lifecycleLock = new ReentrantLock();
    private final ExecutorService executorService;
    private final Context context;
    private final WebServerConfig serverConfig;
    private final boolean registerShutdownHook;

    private volatile Thread shutdownHook;
    private volatile List<ListenerFuture> startFutures;
    private volatile boolean alreadyStarted = false;

    LoomServer(WebServerConfig serverConfig) {
        this.registerShutdownHook = serverConfig.shutdownHook();
        this.context = serverConfig.serverContext()
                .orElseGet(() -> Context.builder()
                        .id("web-" + WEBSERVER_COUNTER.getAndIncrement())
                        .build());
        this.serverConfig = serverConfig;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();

        Map<String, ListenerConfig> sockets = new HashMap<>(serverConfig.sockets());
        sockets.put(DEFAULT_SOCKET_NAME, serverConfig);

        // features ordered by weight
        List<ServerFeature> features = serverConfig.features();
        ServerFeatureContextImpl featureContext = ServerFeatureContextImpl.create(serverConfig);
        for (ServerFeature feature : features) {
            feature.setup(featureContext);
        }

        Timer idleConnectionTimer = new Timer("helidon-idle-connection-timer", true);
        Map<String, ServerListener> listenerMap = new HashMap<>();
        sockets.forEach((name, socketConfig) -> {
            Router socketRouter = featureContext.router(name);
            listenerMap.put(name,
                            new ServerListener(name,
                                               socketConfig,
                                               socketRouter,
                                               context,
                                               idleConnectionTimer,
                                               serverConfig.mediaContext().orElseGet(MediaContext::create),
                                               serverConfig.contentEncoding().orElseGet(ContentEncodingContext::create),
                                               serverConfig.directHandlers().orElseGet(DirectHandlers::create)));
        });

        listeners = Map.copyOf(listenerMap);
    }

    @Override
    public WebServerConfig prototype() {
        return serverConfig;
    }

    @Override
    public WebServer start() {
        HelidonFeatures.flavor(HelidonFlavor.SE);
        HelidonFeatures.print(HelidonFlavor.SE, Version.VERSION, false);

        try {
            lifecycleLock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
        try {
            if (running.compareAndSet(false, true)) {
                if (alreadyStarted) {
                    running.set(false);
                    throw new IllegalStateException("Server cannot be stopped and restarted, please create a new server");
                }
                alreadyStarted = true;
                startIt();
            }
        } finally {
            lifecycleLock.unlock();
        }

        return this;
    }

    @Override
    public WebServer stop() {
        try {
            lifecycleLock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
        try {
            if (running.get()) {
                stopIt();
            }
        } finally {
            lifecycleLock.unlock();
        }

        return this;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int port(String socketName) {
        if (!running.get()) {
            return -1;
        }
        ServerListener listener = listeners.get(socketName);
        return listener == null ? -1 : listener.port();
    }

    @Override
    public boolean hasTls(String socketName) {
        ServerListener listener = listeners.get(socketName);
        return listener != null && listener.hasTls();
    }

    @Override
    public void reloadTls(String socketName, Tls tls) {
        ServerListener listener = listeners.get(socketName);
        if (listener == null) {
            throw new IllegalArgumentException("Cannot reload TLS on socket " + socketName
                                                       + " since this socket does not exist");
        } else {
            listener.reloadTls(tls);
        }
    }

    @Override
    public Context context() {
        return context;
    }

    private void stopIt() {
        // We may be in a shutdown hook and new threads may not be created
        for (ServerListener listener : listeners.values()) {
            listener.stop();
        }

        running.set(false);

        LOGGER.log(System.Logger.Level.INFO, "Helidon WebServer stopped all channels.");
        deregisterShutdownHook();
    }

    private void startIt() {
        long now = System.currentTimeMillis();
        // make sure we do not allow runtime without JEP-290 enforcement
        SerializationConfig.configureRuntime();
        boolean result = parallel("start", ServerListener::start);
        if (!result) {
            LOGGER.log(System.Logger.Level.ERROR, "Helidon WebServer failed to start, shutting down");
            parallel("stop", ServerListener::stop);
            if (startFutures != null) {
                startFutures.forEach(future -> future.future().cancel(true));
            }
            running.set(false);
            return;
        }
        if (registerShutdownHook) {
            registerShutdownHook();
        }
        now = System.currentTimeMillis() - now;
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();

        LOGGER.log(System.Logger.Level.INFO, "Started all channels in "
                + now + " milliseconds. "
                + uptime + " milliseconds since JVM startup. "
                + "Java " + Runtime.version());

        if ("!".equals(System.getProperty(EXIT_ON_STARTED_KEY))) {
            LOGGER.log(System.Logger.Level.INFO, String.format("Exiting, -D%s set.", EXIT_ON_STARTED_KEY));
            System.exit(0);
        }
    }

    private void registerShutdownHook() {
        this.shutdownHook = new Thread(() -> {
            LOGGER.log(System.Logger.Level.INFO, "Shutdown requested by JVM shutting down");
            listeners.values().forEach(ServerListener::stop);
            if (startFutures != null) {
                startFutures.forEach(future -> future.future().cancel(true));
            }

            running.set(false);

            LOGGER.log(System.Logger.Level.INFO, "Shutdown finished");
        }, "webserver-shutdown-hook");

        Runtime.getRuntime().addShutdownHook(shutdownHook);
        // we also need to keep the logging system active until the shutdown hook completes
        // this introduces a hard dependency on JUL, as we cannot abstract this easily away
        // this is to workaround https://bugs.openjdk.java.net/browse/JDK-8161253
        keepLoggingActive(shutdownHook);
    }

    private void deregisterShutdownHook() {
        if (shutdownHook != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            shutdownHook = null;
        }
    }

    // return false if anything fails
    private boolean parallel(String taskName, Consumer<ServerListener> task) {
        boolean result = true;

        List<ListenerFuture> futures = new LinkedList<>();

        for (ServerListener listener : listeners.values()) {
            futures.add(new ListenerFuture(listener, executorService.submit(() -> {
                Thread.currentThread().setName(taskName + " " + listener);
                task.accept(listener);
            })));
        }
        for (ListenerFuture listenerFuture : futures) {
            try {
                listenerFuture.future().get();
            } catch (InterruptedException e) {
                LOGGER.log(System.Logger.Level.ERROR, "Failed to start listener, interrupted: "
                        + listenerFuture.listener.configuredAddress(), e);
                result = false;
            } catch (ExecutionException e) {
                LOGGER.log(System.Logger.Level.ERROR, "Failed to start listener: "
                        + listenerFuture.listener.configuredAddress(), e);
                result = false;
            }
        }
        this.startFutures = futures;
        return result;
    }

    private void keepLoggingActive(Thread shutdownHook) {
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
            newHandlers.add(0, new KeepLoggingActiveHandler(shutdownHook));
        }

        for (Handler handler : handlers) {
            rootLogger.removeHandler(handler);
        }
        for (Handler newHandler : newHandlers) {
            rootLogger.addHandler(newHandler);
        }
    }

    private record ListenerFuture(ServerListener listener, Future<?> future) {
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
