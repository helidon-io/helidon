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

package io.helidon.nima.webserver;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

import io.helidon.common.Version;
import io.helidon.common.config.ConfigException;
import io.helidon.common.context.Context;
import io.helidon.common.features.HelidonFeatures;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.Startable;
import io.helidon.inject.configdriven.api.ConfigDriven;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webserver.http.DirectHandlers;
import io.helidon.nima.webserver.http.HttpFeature;
import io.helidon.nima.webserver.http.HttpRouting;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

@ConfigDriven(value = WebServerConfigBlueprint.class, activateByDefault = true)
class LoomServer implements WebServer, Startable {
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
        if (sockets.containsKey(DEFAULT_SOCKET_NAME)) {
            throw new ConfigException("Configuration of default socket MUST be done on server config directly, not as a separate"
                                              + " socket with " + DEFAULT_SOCKET_NAME + " name.");
        }
        sockets.put(DEFAULT_SOCKET_NAME, serverConfig);

        // for each socket name, use the default router by default, override if customized in builder
        Optional<HttpRouting> routing = serverConfig.routing();
        List<Routing> routings = serverConfig.routings();

        Router.Builder routerBuilder = Router.builder();
        routings.forEach(routerBuilder::addRouting);
        routing.ifPresent(routerBuilder::addRouting);
        if (routing.isEmpty() && routings.isEmpty()) {
            routerBuilder.addRouting(HttpRouting.create());
        }

        Map<String, ServerListener> listenerMap = new HashMap<>();
        sockets.forEach((name, socketConfig) -> {
            listenerMap.put(name,
                            new ServerListener(name,
                                               socketConfig,
                                               routerBuilder.build(),
                                               context,
                                               serverConfig.mediaContext().orElseGet(MediaContext::create),
                                               serverConfig.contentEncoding().orElseGet(ContentEncodingContext::create),
                                               serverConfig.directHandlers().orElseGet(DirectHandlers::create)));
        });

        listeners = Map.copyOf(listenerMap);
    }

    // based on Injection services
    @Inject
    LoomServer(WebServerConfig serverConfig, List<ServiceProvider<HttpFeature>> features) {
        this(addFeatures(serverConfig, features));
    }

    @Override
    public WebServerConfig prototype() {
        return serverConfig;
    }

    @Override
    public void startService() {
        start();
    }

    @Override
    public WebServer start() {
        HelidonFeatures.flavor(HelidonFlavor.NIMA);
        HelidonFeatures.print(HelidonFlavor.NIMA, Version.VERSION, false);

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
    @PreDestroy
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

    private static WebServerConfig addFeatures(WebServerConfig serverConfig, List<ServiceProvider<HttpFeature>> features) {
        WebServerConfig.Builder newBuilder = WebServerConfig.builder(serverConfig);

        List<HttpFeature> defaultSocket = new ArrayList<>();
        Map<String, List<HttpFeature>> customSockets = new LinkedHashMap<>();
        Map<String, List<HttpFeature>> missingSockets = new LinkedHashMap<>();

        for (ServiceProvider<HttpFeature> featureProvider : features) {
            HttpFeature feature = featureProvider.get();
            String socket = feature.socket();
            if (DEFAULT_SOCKET_NAME.equals(socket)) {
                defaultSocket.add(feature);
            } else {
                if (serverConfig.sockets().containsKey(socket)) {
                    customSockets.computeIfAbsent(socket, it -> new ArrayList<>())
                            .add(feature);
                } else {
                    if (feature.socketRequired()) {
                        missingSockets.computeIfAbsent(socket, it -> new ArrayList<>())
                                .add(feature);
                    } else {
                        defaultSocket.add(feature);
                    }
                }
            }
        }

        if (!missingSockets.isEmpty()) {
            throw new IllegalArgumentException("Server is configured with the following sockets: "
                                                       + serverConfig.sockets().keySet()
                                                       + ", yet there are services that require other sockets. Map of socket "
                                                       + "names to features: "
                                                       + missingSockets);
        }
        newBuilder.routing(routing -> {
           defaultSocket.forEach(routing::addFeature);
        });
        customSockets.forEach((socketName, featureList) -> {
            // we have validated the socket names exist, so we are fine to just use it
            ListenerConfig.Builder newSocketBuilder = ListenerConfig.builder(newBuilder.sockets().get(socketName));
            newSocketBuilder.routing(routing -> {
                featureList.forEach(routing::addFeature);
            });
            newBuilder.putSocket(socketName, newSocketBuilder.build());
        });

        return newBuilder.buildPrototype();
    }

    private void stopIt() {
        // We may be in a shutdown hook and new threads may not be created
        for (ServerListener listener : listeners.values()) {
            listener.stop();
        }

        running.set(false);

        LOGGER.log(System.Logger.Level.INFO, "Níma server stopped all channels.");
        deregisterShutdownHook();
    }

    private void startIt() {
        long now = System.currentTimeMillis();
        boolean result = parallel("start", ServerListener::start);
        if (!result) {
            LOGGER.log(System.Logger.Level.ERROR, "Níma server failed to start, shutting down");
            parallel("stop", ServerListener::stop);
            if (startFutures != null) {
                startFutures.forEach(future -> future.future().cancel(true));
            }
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
        }, "nima-shutdown-hook");

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
