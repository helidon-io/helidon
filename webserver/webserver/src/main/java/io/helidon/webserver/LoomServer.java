/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import java.util.Timer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

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
            throw new IllegalStateException("Webserver start was interrupted");
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
        // JVM uptime or since restore
        long uptime = ResumableSupport.get().uptime();

        LOGGER.log(System.Logger.Level.INFO, "Started all channels in "
                + now + " milliseconds. "
                + uptime + " milliseconds since JVM startup. "
                + "Java " + Runtime.version());

        fireAfterStart();

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

    // return false if anything fails
    private boolean parallel(String taskName, Consumer<ServerListener> task) {
        boolean result = true;

        List<ListenerFuture> futures = new LinkedList<>();

        for (ServerListener listener : listeners.values()) {
            futures.add(new ListenerFuture(listener, executorService.submit(() -> {
                Contexts.runInContext(context, () -> {
                    Thread.currentThread().setName(taskName + " " + listener);
                    task.accept(listener);
                });
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

    @Override
    public void suspend() {
        try {
            lifecycleLock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted during snapshot checkpoint.", e);
        }
        try {
            if (running.get()) {
                for (ServerListener listener : listeners.values()) {
                    listener.suspend();
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
            throw new IllegalStateException("Interrupted during snapshot restore.", e);
        }
        try {
            if (running.get()) {
                for (ServerListener listener : listeners.values()) {
                    listener.resume();
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

    private record ListenerFuture(ServerListener listener, Future<?> future) {
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
            listeners.values().forEach(ServerListener::stop);
            if (startFutures != null) {
                startFutures.forEach(future -> future.future().cancel(true));
            }

            running.set(false);
        }

        @Override
        public String toString() {
            return "WebServer shutdown handler for id: " + id;
        }
    }
}
