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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.Version;
import io.helidon.common.context.Context;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webserver.http.DirectHandlers;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;

class LoomServer implements WebServer {
    private static final System.Logger LOGGER = System.getLogger(LoomServer.class.getName());
    private static final String EXIT_ON_STARTED_KEY = "exit.on.started";

    private final Map<String, ServerListener> listeners;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Lock lifecycleLock = new ReentrantLock();
    private final ExecutorService executorService;
    private final Context context;
    private final boolean registerShutdownHook;

    private volatile Thread shutdownHook;
    private volatile List<ListenerFuture> startFutures;
    private volatile boolean alreadyStarted = false;

    LoomServer(Builder builder, DirectHandlers directHandlers) {
        this.registerShutdownHook = builder.shutdownHook();
        this.context = builder.context();

        List<ServerConnectionSelector> connectionProviders = builder.connectionProviders();

        Map<String, Router> routers = builder.routers();
        Map<String, ListenerConfiguration.Builder> sockets = builder.socketBuilders();

        Set<String> socketNames = new HashSet<>(routers.keySet());
        socketNames.addAll(sockets.keySet());

        Map<String, ServerListener> listeners = new HashMap<>(socketNames.size());

        Router defaultRouter = routers.get(DEFAULT_SOCKET_NAME);
        if (defaultRouter == null) {
            defaultRouter = Router.empty();
        }

        boolean inheritThreadLocals = builder.inheritThreadLocals();

        for (String socketName : socketNames) {
            Router router = routers.get(socketName);
            if (router == null) {
                router = defaultRouter;
            }
            ListenerConfiguration.Builder socketBuilder = sockets.get(socketName);
            if (socketBuilder == null) {
                socketBuilder = ListenerConfiguration.builder(socketName);
            }

            // defaults for each listener
            MediaContext mediaContext = builder.mediaContext();
            ContentEncodingContext contentEncodingContext = builder.contentEncodingContext();

            ListenerConfiguration socketConfig = socketBuilder
                    .defaultMediaContext(mediaContext)
                    .defaultContentEncodingContext(contentEncodingContext)
                    .defaultDirectHandlers(directHandlers)
                    .parentContext(context)
                    .build();

            listeners.put(socketName,
                          new ServerListener(connectionProviders,
                                             socketName,
                                             socketConfig,
                                             router,
                                             inheritThreadLocals));
        }

        this.listeners = Map.copyOf(listeners);
        this.executorService = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                                                                          .allowSetThreadLocals(true)
                                                                          .inheritInheritableThreadLocals(inheritThreadLocals)
                                                                          .factory());
    }

    @Override
    public WebServer start() {
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

        LOGGER.log(System.Logger.Level.INFO, "Helidon Níma " + Version.VERSION);
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
