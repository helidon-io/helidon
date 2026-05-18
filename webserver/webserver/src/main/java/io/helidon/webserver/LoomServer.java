/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.Main;
import io.helidon.common.SerializationConfig;
import io.helidon.common.Version;
import io.helidon.common.Weights;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.features.HelidonFeatures;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.resumable.Resumable;
import io.helidon.common.resumable.ResumableSupport;
import io.helidon.common.tls.Tls;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.service.registry.Service;
import io.helidon.spi.HelidonShutdownHandler;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.spi.ServerFeature;

@Service.Singleton
class LoomServer implements WebServer, Resumable {
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

    private volatile HelidonShutdownHandler shutdownHandler;
    private volatile List<ListenerFuture> startFutures;
    private volatile boolean alreadyStarted = false;

    @Service.Inject
    LoomServer(WebServerService service) {
        // only for service registry
        this(WebServerConfig.builder()
                     .update(service::updateServerBuilder)
                     .buildPrototype());
    }

    LoomServer(WebServerConfig serverConfig) {
        this.registerShutdownHook = serverConfig.shutdownHook();
        this.context = serverConfig.serverContext()
                .orElseGet(() -> Context.builder()
                        .id("web-" + WEBSERVER_COUNTER.getAndIncrement())
                        .update(it -> Contexts.context().ifPresent(it::parent))
                        .build());
        this.serverConfig = serverConfig;
        this.executorService = ExecutorsFactory.newLoomServerVirtualThreadPerTaskExecutor();

        Map<String, ListenerConfig> sockets = new HashMap<>(serverConfig.sockets());
        sockets.put(DEFAULT_SOCKET_NAME, serverConfig);

        // features ordered by weight
        List<ServerFeature> features = new ArrayList<>(serverConfig.features());
        Weights.sort(features);

        ServerFeatureContextImpl featureContext = ServerFeatureContextImpl.create(serverConfig);
        for (ServerFeature feature : features) {
            featureContext.setUpFeature(feature);
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

        validateRoutingsHaveNamedListener(serverConfig, listenerMap.keySet());

        listeners = Map.copyOf(listenerMap);
        ResumableSupport.get().register(this);
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
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Webserver start was interrupted", e);
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
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Webserver stop was interrupted", e);
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

    @Override
    public void suspend() {
        try {
            lifecycleLock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during snapshot checkpoint.", e);
        }
        try {
            if (running.get()) {
                try {
                    for (ServerListener listener : listeners.values()) {
                        listener.suspend();
                    }
                } catch (RuntimeException | Error e) {
                    stopAfterResumableFailure(e);
                    throw e;
                }
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    @Override
    public void resume() {
        long now = System.currentTimeMillis();
        try {
            lifecycleLock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during snapshot restore.", e);
        }
        try {
            if (running.get()) {
                try {
                    for (ServerListener listener : listeners.values()) {
                        listener.resume();
                    }
                } catch (RuntimeException | Error e) {
                    stopAfterResumableFailure(e);
                    throw e;
                }
            }
        } finally {
            lifecycleLock.unlock();
        }
        now = System.currentTimeMillis() - now;
        LOGGER.log(System.Logger.Level.INFO, "Restored all channels in "
                + now + " milliseconds. "
                + ResumableSupport.get().uptimeSinceResume() + " milliseconds since JVM snapshot restore. "
                + "Java " + Runtime.version());
    }

    // Intended for tests that need listener state without stack walking.
    Thread.State listenerThreadState(String socketName) {
        ServerListener listener = listeners.get(socketName);
        return listener == null ? null : listener.serverThreadState();
    }

    private static void validateRoutingsHaveNamedListener(WebServerConfig serverConfig, Set<String> namedListeners) {
        var routingNames = serverConfig.namedRoutings()
                .keySet();

        List<String> invalidRoutingNames = new ArrayList<>();

        for (String routingName : routingNames) {
            if (!namedListeners.contains(routingName)) {
                invalidRoutingNames.add(routingName);
            }
        }

        if (!invalidRoutingNames.isEmpty()) {
            String message = "Listener not found for named routing(s): \""
                    + String.join(", ", invalidRoutingNames)
                    + "\", configured listener(s): \""
                    + String.join(", ", namedListeners) + "\"";

            if (serverConfig.ignoreInvalidNamedRouting()) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    LOGGER.log(System.Logger.Level.DEBUG, message);
                }
            } else {
                throw new IllegalStateException(message);
            }
        }
    }

    private void stopIt() {
        Throwable failure = stopListeners();
        running.set(false);
        LOGGER.log(System.Logger.Level.INFO, "Helidon WebServer stopped all channels.");
        try {
            deregisterShutdownHook();
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }
        LifecycleFailures.throwIfFailed(failure, "Failed to stop Helidon WebServer");
    }

    private Throwable stopAfterLifecycleFailure(Throwable failure) {
        Throwable result = LifecycleFailures.add(null, failure);
        try {
            stopIt();
        } catch (RuntimeException | Error e) {
            result = LifecycleFailures.add(result, e);
        }
        return result;
    }

    private void startIt() {
        long now = System.currentTimeMillis();
        // make sure we do not allow runtime without JEP-290 enforcement
        SerializationConfig.configureRuntime();
        Throwable failure = startListeners();
        if (failure != null) {
            LOGGER.log(System.Logger.Level.ERROR, "Helidon WebServer failed to start, shutting down");
            cancelStartFutures();
            failure = LifecycleFailures.add(failure, stopListeners());
            running.set(false);
            LifecycleFailures.throwIfFailedAsIllegalState(failure, "Failed to start Helidon WebServer");
        }
        try {
            if (registerShutdownHook) {
                registerShutdownHook();
            }
            fireAfterStart();
        } catch (RuntimeException | Error e) {
            Throwable startupFailure = stopAfterLifecycleFailure(e);
            LifecycleFailures.throwIfFailedAsIllegalState(startupFailure, "Failed to start Helidon WebServer");
        }
        now = System.currentTimeMillis() - now;
        // JVM uptime or since restore
        long uptime = ResumableSupport.get().uptime();

        LOGGER.log(System.Logger.Level.INFO, "Started all channels in "
                + now + " milliseconds. "
                + uptime + " milliseconds since JVM startup. "
                + "Java " + Runtime.version());

        if ("!".equals(System.getProperty(EXIT_ON_STARTED_KEY))) {
            LOGGER.log(System.Logger.Level.INFO, String.format("Exiting, -D%s set.", EXIT_ON_STARTED_KEY));
            // we need to run the system exit on a different thread, to correctly finish whatever was happening on main
            // all shutdown hooks run on that thread
            var ctx = Contexts.context().orElseGet(Contexts::globalContext);
            Thread.ofPlatform()
                    .daemon(false)
                    .name("Helidon system exit thread")
                    .start(() -> {
                        Contexts.runInContext(ctx, () -> System.exit(0));
                    });
        }
    }

    private void fireAfterStart() {
        listeners.values().forEach(l -> l.router().afterStart(this));
    }

    private void registerShutdownHook() {
        this.shutdownHandler = new ServerShutdownHandler(listeners, startFutures, running, context.id());
        Main.addShutdownHandler(this.shutdownHandler);
    }

    private void deregisterShutdownHook() {
        if (shutdownHandler != null) {
            Main.removeShutdownHandler(shutdownHandler);
            shutdownHandler = null;
        }
    }

    private Throwable startListeners() {
        Throwable failure = null;
        boolean interrupted = false;
        AtomicBoolean cancelled = new AtomicBoolean();
        ExecutorCompletionService<ListenerFuture> completedStarts = new ExecutorCompletionService<>(executorService);

        List<ListenerFuture> futures = new LinkedList<>();

        for (ServerListener listener : listeners.values()) {
            ListenerFuture listenerFuture = new ListenerFuture(listener, cancelled);
            listenerFuture.future = completedStarts.submit(() -> {
                Contexts.runInContext(context, () -> {
                    Thread currentThread = Thread.currentThread();
                    listenerFuture.thread.set(currentThread);
                    currentThread.setName("start " + listener);
                    listener.start(cancelled::get);
                });
                return listenerFuture;
            });
            futures.add(listenerFuture);
        }
        this.startFutures = futures;
        int remaining = futures.size();
        Future<ListenerFuture> failedFuture = null;
        while (remaining > 0) {
            try {
                Future<ListenerFuture> future = completedStarts.take();
                remaining--;
                try {
                    future.get();
                } catch (CancellationException _) {
                    // Cancelled by another listener startup failure.
                } catch (ExecutionException e) {
                    ListenerFuture listenerFuture = listenerFuture(futures, future);
                    LOGGER.log(System.Logger.Level.ERROR, "Failed to start listener: "
                            + listenerFuture.listener.configuredAddress(), e);
                    failure = LifecycleFailures.add(failure, e.getCause() == null ? e : e.getCause());
                    cancelled.set(true);
                    cancelStartFutures(futures);
                    failedFuture = future;
                    break;
                }
            } catch (InterruptedException e) {
                LOGGER.log(System.Logger.Level.ERROR, "Failed to start listener, interrupted", e);
                failure = LifecycleFailures.add(failure,
                                                new IllegalStateException("Interrupted while waiting for listener startup", e));
                cancelled.set(true);
                cancelStartFutures(futures);
                interrupted = true;
                break;
            }
        }
        if (failure != null) {
            failure = awaitStartFutures(futures, failedFuture, failure);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return failure;
    }

    private static ListenerFuture listenerFuture(List<ListenerFuture> futures, Future<ListenerFuture> future) {
        for (ListenerFuture listenerFuture : futures) {
            if (listenerFuture.future == future) {
                return listenerFuture;
            }
        }
        throw new IllegalStateException("Unknown listener startup future");
    }

    private Throwable awaitStartFutures(List<ListenerFuture> futures, Future<ListenerFuture> failedFuture, Throwable failure) {
        boolean interrupted = false;
        for (ListenerFuture listenerFuture : futures) {
            Future<ListenerFuture> future = listenerFuture.future;
            if (future == failedFuture) {
                continue;
            }
            boolean done = false;
            while (!done) {
                try {
                    future.get();
                    done = true;
                } catch (InterruptedException e) {
                    interrupted = true;
                    failure = LifecycleFailures.add(failure,
                                                    new IllegalStateException("Interrupted while waiting for listener "
                                                                                      + listenerFuture.listener, e));
                } catch (CancellationException _) {
                    done = true;
                } catch (ExecutionException e) {
                    failure = LifecycleFailures.add(failure, e.getCause() == null ? e : e.getCause());
                    done = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return failure;
    }

    private void cancelStartFutures() {
        List<ListenerFuture> localStartFutures = startFutures;
        if (localStartFutures != null) {
            cancelStartFutures(localStartFutures);
        }
    }

    private static void cancelStartFutures(List<ListenerFuture> futures) {
        for (ListenerFuture listenerFuture : futures) {
            listenerFuture.cancelled.set(true);
            Future<ListenerFuture> future = listenerFuture.future;
            if (future.isDone()) {
                continue;
            }
            Thread thread = listenerFuture.thread.get();
            if (thread == null) {
                if (!future.cancel(false)) {
                    thread = listenerFuture.thread.get();
                    if (thread != null) {
                        thread.interrupt();
                    }
                }
            } else {
                thread.interrupt();
            }
        }
    }

    private Throwable stopListeners() {
        Throwable failure = null;
        // We may be in a shutdown hook and new threads may not be created
        for (ServerListener listener : listeners.values()) {
            try {
                listener.stop();
            } catch (RuntimeException | Error e) {
                failure = LifecycleFailures.add(failure, e);
            }
        }
        return failure;
    }

    private void stopAfterResumableFailure(Throwable failure) {
        try {
            stopIt();
        } catch (RuntimeException | Error e) {
            if (failure != e) {
                failure.addSuppressed(e);
            }
        }
    }

    private static final class ListenerFuture {
        private final ServerListener listener;
        private final AtomicBoolean cancelled;
        private final AtomicReference<Thread> thread = new AtomicReference<>();
        private Future<ListenerFuture> future;

        private ListenerFuture(ServerListener listener, AtomicBoolean cancelled) {
            this.listener = listener;
            this.cancelled = cancelled;
        }
    }

    private static final class ServerShutdownHandler implements HelidonShutdownHandler {
        private final Map<String, ServerListener> listeners;
        private final List<ListenerFuture> startFutures;
        private final AtomicBoolean running;
        private final String id;

        private ServerShutdownHandler(Map<String, ServerListener> listeners,
                                      List<ListenerFuture> startFutures,
                                      AtomicBoolean running,
                                      String id) {
            this.listeners = listeners;
            this.startFutures = startFutures;
            this.running = running;
            this.id = id;
        }

        @Override
        public void shutdown() {
            Throwable failure = null;
            for (ServerListener listener : listeners.values()) {
                try {
                    listener.stop();
                } catch (RuntimeException | Error e) {
                    failure = LifecycleFailures.add(failure, e);
                }
            }
            if (startFutures != null) {
                cancelStartFutures(startFutures);
            }

            running.set(false);
            if (failure != null) {
                LOGGER.log(System.Logger.Level.ERROR, "Failed to stop Helidon WebServer from shutdown hook", failure);
            }
        }

        @Override
        public String toString() {
            return "WebServer shutdown handler for id: " + id;
        }
    }
}
