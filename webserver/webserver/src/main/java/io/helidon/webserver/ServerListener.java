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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.net.ssl.SSLParameters;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.context.Context;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.task.HelidonTaskExecutor;
import io.helidon.common.task.InterruptableTask;
import io.helidon.common.tls.Tls;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.spi.ProtocolConfig;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.spi.ServerConnectionSelectorProvider;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

class ServerListener implements ListenerContext {
    private static final System.Logger LOGGER = System.getLogger(ServerListener.class.getName());

    @SuppressWarnings("rawtypes")
    private static final LazyValue<List<ServerConnectionSelectorProvider>> SELECTOR_PROVIDERS = LazyValue.create(() ->
            HelidonServiceLoader.create(ServiceLoader.load(ServerConnectionSelectorProvider.class)).asList());

    private final ConnectionProviders connectionProviders;
    private final String socketName;
    private final ListenerConfig listenerConfig;
    private final Router router;
    private final HelidonTaskExecutor readerExecutor;
    private final ExecutorService sharedExecutor;
    private final DirectHandlers directHandlers;
    private final Tls tls;
    private final SocketOptions connectionOptions;
    private final SocketAddress configuredAddress;
    private final Duration gracePeriod;

    private final MediaContext mediaContext;
    private final ContentEncodingContext contentEncodingContext;
    private final Context context;
    private final Limit connectionLimit;
    private final Limit requestLimit;
    private final Map<String, ServerConnection> activeConnections = new ConcurrentHashMap<>();

    private volatile boolean running;
    private volatile boolean inCheckpoint;
    private volatile int connectedPort;
    private volatile ServerSocketChannel serverSocket;
    private volatile Thread serverThread;
    private volatile CompletableFuture<Void> closeFuture;
    private volatile Runnable beforeReaderExecutorTerminate = () -> {
    };
    private volatile Runnable beforeSharedExecutorAwait = () -> {
    };

    @SuppressWarnings("unchecked")
    ServerListener(String socketName,
                   ListenerConfig listenerConfig,
                   Router router,
                   Context serverContext,
                   Timer idleConnectionTimer,
                   MediaContext defaultMediaContext,
                   ContentEncodingContext defaultContentEncodingContext,
                   DirectHandlers defaultDirectHandlers) {

        ProtocolConfigs protocols = ProtocolConfigs.create(listenerConfig.protocols());
        List<ServerConnectionSelector> selectors = new ArrayList<>(listenerConfig.connectionSelectors());

        // for each discovered selector provider, add a selector for each configuration of that provider
        SELECTOR_PROVIDERS.get()
                .forEach(provider -> {
                    List<ProtocolConfig> configurations = protocols.config(provider.protocolType(),
                                                                           provider.protocolConfigType());
                    for (ProtocolConfig configuration : configurations) {
                        selectors.add(provider.create(socketName, configuration, protocols));
                    }
                });

        // this instance is the only one waiting on this limit, so queue of 1 is enough
        // 5 minutes is long enough not to have busy waits
        if (listenerConfig.maxTcpConnections() == -1 || listenerConfig.maxTcpConnections() == 0) {
            // unlimited, no need to queue, as we never block
            this.connectionLimit = FixedLimit.create();
        } else {
            this.connectionLimit = FixedLimit.builder()
                    .queueLength(1)
                    .queueTimeout(Duration.ofMinutes(5))
                    .permits(listenerConfig.maxTcpConnections())
                    .build();
        }

        if (listenerConfig.maxConcurrentRequests() == -1) {
            this.requestLimit = listenerConfig.concurrencyLimit()
                    .orElseGet(FixedLimit::create); // unlimited unless configured
        } else {
            this.requestLimit = FixedLimit.builder()
                    .permits(listenerConfig.maxConcurrentRequests())
                    .build();
        }

        this.connectionLimit.init(socketName);
        this.requestLimit.init(socketName);

        this.connectionProviders = ConnectionProviders.create(selectors);
        this.socketName = socketName;
        this.listenerConfig = listenerConfig;
        this.tls = listenerConfig.tls().orElseGet(() -> Tls.builder().enabled(false).build());
        this.connectionOptions = listenerConfig.connectionOptions();
        this.directHandlers = listenerConfig.directHandlers().orElse(defaultDirectHandlers);
        this.mediaContext = listenerConfig.mediaContext().orElse(defaultMediaContext);
        this.contentEncodingContext = listenerConfig.contentEncoding().orElse(defaultContentEncodingContext);
        this.context = listenerConfig.listenerContext().orElseGet(() -> Context.builder()
                .id("listener-" + socketName)
                .parent(serverContext)
                .build());
        this.gracePeriod = listenerConfig.shutdownGracePeriod();

        initServerThread();

        // to read requests and execute tasks
        this.readerExecutor = ExecutorsFactory.newServerListenerReaderExecutor();

        // to do anything else (writers etc.)
        this.sharedExecutor = ExecutorsFactory.newServerListenerSharedExecutor();

        this.configuredAddress = listenerConfig.bindAddress()
                .orElseGet(() -> {
                    int port = listenerConfig.port();
                    if (port < 1) {
                        port = 0;
                    }
                    return new InetSocketAddress(listenerConfig.address(), port);
                });

        this.router = router;

        // handle idle connection timeout
        IdleTimeoutHandler ith = new IdleTimeoutHandler(idleConnectionTimer,
                                                        listenerConfig,
                                                        this::activeConnections);
        ith.start();
    }

    private void initServerThread() {
        this.closeFuture = new CompletableFuture<>();
        this.serverThread = Thread.ofPlatform()
                .inheritInheritableThreadLocals(true)
                .daemon(false)
                .name("server-" + socketName + "-listener")
                .unstarted(this::listen);
    }

    @Override
    public MediaContext mediaContext() {
        return mediaContext;
    }

    @Override
    public ContentEncodingContext contentEncodingContext() {
        return contentEncodingContext;
    }

    @Override
    public DirectHandlers directHandlers() {
        return directHandlers;
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public ListenerConfig config() {
        return listenerConfig;
    }

    @Override
    public ExecutorService executor() {
        return sharedExecutor;
    }

    @Override
    public String toString() {
        return socketName + " (" + configuredAddress + ")";
    }

    int port() {
        return connectedPort;
    }

    Router router() {
        return router;
    }

    SocketAddress configuredAddress() {
        return configuredAddress;
    }

    void stop() {
        if (!running && !inCheckpoint) {
            return;
        }
        running = false;
        inCheckpoint = false;
        Throwable failure = stopResources();
        try {
            router.afterStop();
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }
        LifecycleFailures.throwIfFailed(failure, "Failed to stop listener " + socketName);
    }

    private Throwable stopResources() {
        Throwable failure = null;
        // Stop listening for connections
        closeServerSocketForStop();

        try {
            // Stop handling any new requests on all active connections
            failure = LifecycleFailures.add(failure, closeActiveConnections(false));
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }

        try {
            // Shutdown reader executor
            shutdownReaderExecutor();
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }

        try {
            // Shutdown shared executor
            shutdownSharedExecutor();
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }

        try {
            // Interrupt and close any active connections
            failure = LifecycleFailures.add(failure, closeActiveConnections(true));
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }

        Thread localServerThread = serverThread;
        CompletableFuture<Void> localCloseFuture = closeFuture;
        if (localServerThread != null) {
            localServerThread.interrupt();
            if (localServerThread.getState() == Thread.State.NEW && localCloseFuture != null) {
                localCloseFuture.complete(null);
            }
        }
        if (localCloseFuture != null) {
            try {
                localCloseFuture.join();
            } catch (RuntimeException | Error e) {
                failure = LifecycleFailures.add(failure, e);
            }
        }

        return failure;
    }

    private void suspendForCheckpoint() {
        try {
            // Stop listening for connections
            serverSocket.close();
            if (configuredAddress instanceof UnixDomainSocketAddress udsa) {
                try {
                    // UNIX socket files are created automatically, but they are not deleted when the channel is closed
                    Files.deleteIfExists(udsa.getPath());
                } catch (IOException e) {
                    LOGGER.log(WARNING, "Failed to delete UNIX socket file " + udsa.getPath().toAbsolutePath(), e);
                }
            }
            // Stop handling any new requests on all active connections
            activeConnections().forEach(connection -> connection.close(false));
            // Interrupt and close any active connections
            activeConnections().forEach(connection -> connection.close(true));
        } catch (IOException e) {
            LOGGER.log(INFO, "Exception thrown on socket close", e);
        }
        serverThread.interrupt();
        closeFuture.join();
    }

    void start() {
        start(() -> false);
    }

    void start(BooleanSupplier cancelled) {
        boolean lifecycleStarted = false;
        try {
            checkCancelledStartup(cancelled);
            router.beforeStart();
            lifecycleStarted = true;
            checkCancelledStartup(cancelled);
            startIt(cancelled);
        } catch (RuntimeException | Error e) {
            rollbackFailedStart(e, lifecycleStarted);
            throw e;
        }
    }

    private void startIt() {
        startIt(() -> false);
    }

    private void startIt(BooleanSupplier cancelled) {
        checkCancelledStartup(cancelled);
        try {
            if (configuredAddress instanceof UnixDomainSocketAddress) {
                serverSocket = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            } else {
                serverSocket = ServerSocketChannel.open();
            }
            if (tls.enabled()) {
                // basic validation of the configuration
                tls.newEngine();
            }
            listenerConfig.configureSocket(serverSocket);

            serverSocket.bind(configuredAddress, listenerConfig.backlog());
            checkCancelledStartup(cancelled);
            this.connectedPort = serverSocket.getLocalAddress() instanceof InetSocketAddress ias ? ias.getPort() : -1;

            String serverChannelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(serverSocket));

            running = true;

            if (LOGGER.isLoggable(INFO)) {
                if (configuredAddress instanceof InetSocketAddress inetAddress) {
                    String format;
                    if (tls.enabled()) {
                        format = "[%s] https://%s:%s bound for socket '%s'";
                    } else {
                        format = "[%s] http://%s:%s bound for socket '%s'";
                    }
                    LOGGER.log(INFO, String.format(format,
                                                   serverChannelId,
                                                   inetAddress.getHostString(),
                                                   connectedPort,
                                                   socketName));
                } else {
                    String format;
                    if (tls.enabled()) {
                        format = "[%s] %s bound for secure socket '%s'";
                    } else {
                        format = "[%s] %s bound for socket '%s'";
                    }
                    LOGGER.log(INFO, String.format(format,
                                                   serverChannelId,
                                                   configuredAddress,
                                                   socketName));
                }


                if (LOGGER.isLoggable(TRACE)) {
                    if (listenerConfig.writeQueueLength() <= 1) {
                        LOGGER.log(System.Logger.Level.TRACE, "[" + serverChannelId + "] direct writes");
                    } else {
                        LOGGER.log(System.Logger.Level.TRACE,
                                   "[" + serverChannelId + "] async writes, queue length: "
                                           + listenerConfig.writeQueueLength());
                    }
                    if (tls.enabled()) {
                        debugTls(serverChannelId, tls);
                    }
                }
            }

            serverThread.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start server", e);
        }
    }

    private void checkCancelledStartup(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new IllegalStateException("Listener startup cancelled " + socketName);
        }
    }

    boolean hasTls() {
        return tls.enabled();
    }

    void reloadTls(Tls tls) {
        if (!this.tls.enabled()) {
            throw new IllegalArgumentException("TLS is not enabled on the socket " + socketName
                                                       + " and therefore cannot be reloaded");
        }
        if (!tls.enabled()) {
            throw new UnsupportedOperationException("TLS cannot be disabled by reloading on the socket " + socketName);
        }
        this.tls.reload(tls);
    }

    private void debugTls(String serverChannelId, Tls tls) {
        SSLParameters sslParameters = tls.newEngine()
                .getSSLParameters();

        String message = "[" + serverChannelId + "] TLS configuration of socket " + socketName + '\n'
                + "Protocols: " + Arrays.toString(sslParameters.getProtocols()) + '\n'
                + "Cipher Suites: " + Arrays.toString(sslParameters.getCipherSuites()) + '\n'
                + "Endpoint identification algorithm: " + sslParameters.getEndpointIdentificationAlgorithm() + '\n'
                + "Need client auth: " + sslParameters.getNeedClientAuth() + '\n'
                + "Want client auth: " + sslParameters.getWantClientAuth();

        LOGGER.log(TRACE, message);
    }

    private void rollbackFailedStart(Throwable startupFailure, boolean lifecycleStarted) {
        running = false;
        suppressCleanupFailure(startupFailure, this::closeServerSocketOnFailure);
        suppressCleanupFailure(startupFailure, this::shutdownReaderExecutor);
        suppressCleanupFailure(startupFailure, this::shutdownSharedExecutor);
        if (lifecycleStarted) {
            suppressCleanupFailure(startupFailure, router::afterStop);
        }
    }

    private static void suppressCleanupFailure(Throwable startupFailure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error e) {
            LifecycleFailures.add(startupFailure, e);
        }
    }

    private void closeServerSocketOnFailure() {
        ServerSocketChannel localServerSocket = serverSocket;
        if (localServerSocket == null) {
            return;
        }
        boolean bound = serverSocketBound(localServerSocket, "Failed to check server socket binding after failed start");
        try {
            localServerSocket.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close server socket after failed start", e);
        } finally {
            serverSocket = null;
            connectedPort = -1;
        }
        if (bound) {
            deleteUnixSocketFile();
        }
    }

    private void closeServerSocketForStop() {
        ServerSocketChannel localServerSocket = serverSocket;
        if (localServerSocket == null) {
            return;
        }
        boolean bound = serverSocketBound(localServerSocket, "Failed to check server socket binding before stop");
        try {
            localServerSocket.close();
        } catch (IOException e) {
            LOGGER.log(INFO, "Exception thrown on socket close", e);
        } finally {
            serverSocket = null;
            connectedPort = -1;
        }
        if (bound) {
            deleteUnixSocketFile();
        }
    }

    private boolean serverSocketBound(ServerSocketChannel localServerSocket, String debugMessage) {
        try {
            return localServerSocket.getLocalAddress() != null;
        } catch (IOException e) {
            LOGGER.log(DEBUG, debugMessage, e);
            return false;
        }
    }

    private void deleteUnixSocketFile() {
        if (configuredAddress instanceof UnixDomainSocketAddress udsa) {
            try {
                Files.deleteIfExists(udsa.getPath());
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to delete UNIX socket file " + udsa.getPath().toAbsolutePath(), e);
            }
        }
    }

    private void shutdownReaderExecutor() {
        Throwable failure = null;
        try {
            beforeReaderExecutorTerminate.run();
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }
        readerExecutor.terminate(gracePeriod.toMillis(), TimeUnit.MILLISECONDS);
        if (Thread.currentThread().isInterrupted()) {
            failure = LifecycleFailures.add(failure,
                                            new IllegalStateException("Interrupted while shutting down listener reader executor "
                                                                              + "for " + socketName));
        }
        if (!readerExecutor.isTerminated()) {
            LOGGER.log(DEBUG, "Some tasks in reader executor did not terminate gracefully");
            try {
                readerExecutor.forceTerminate();
            } catch (RuntimeException | Error e) {
                failure = LifecycleFailures.add(failure, e);
            }
        }
        LifecycleFailures.throwIfFailed(failure, "Failed to shut down listener reader executor for " + socketName);
    }

    private void shutdownSharedExecutor() {
        Throwable failure = null;
        sharedExecutor.shutdown();
        try {
            beforeSharedExecutorAwait.run();
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }
        try {
            boolean done = sharedExecutor.awaitTermination(gracePeriod.toMillis(), TimeUnit.MILLISECONDS);
            if (!done) {
                forceShutdownSharedExecutor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            IllegalStateException interrupted =
                    new IllegalStateException("Interrupted while shutting down listener executor for " + socketName, e);
            try {
                forceShutdownSharedExecutor();
            } catch (RuntimeException | Error shutdownFailure) {
                interrupted.addSuppressed(shutdownFailure);
            }
            failure = LifecycleFailures.add(failure, interrupted);
        }
        LifecycleFailures.throwIfFailed(failure, "Failed to shut down listener executor for " + socketName);
    }

    private void forceShutdownSharedExecutor() {
        List<Runnable> running = sharedExecutor.shutdownNow();
        if (!running.isEmpty()) {
            LOGGER.log(DEBUG, running.size() + " tasks in shared executor did not terminate gracefully");
        }
    }

    private void listen() {
        String serverChannelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(serverSocket));

        while (running) {
            try {
                // this must be done before we accept, and the semaphore must be released when connection is finished
                var outcome = connectionLimit.tryAcquireOutcome(true);
                if (outcome.disposition() == LimitAlgorithm.Outcome.Disposition.ACCEPTED) {
                    LimitAlgorithm.Token token = ((LimitAlgorithm.Outcome.Accepted) outcome).token();

                    // if accept fails itself, we consider it end of story, the listener is broken
                    SocketChannel socket = serverSocket.accept();

                    try {
                        connectionOptions.configureSocket(socket);
                        ConnectionHandler handler = new ConnectionHandler(this,
                                                                          token,
                                                                          requestLimit,
                                                                          connectionProviders,
                                                                          activeConnections,
                                                                          socket,
                                                                          serverChannelId,
                                                                          router,
                                                                          tls);
                        readerExecutor.execute(handler);
                    } catch (RejectedExecutionException e) {
                        LOGGER.log(ERROR, "Executor rejected handler for new connection", e);

                        // the socket was never handled
                        try {
                            socket.close();
                        } catch (IOException ex) {
                            LOGGER.log(TRACE, "Failed to close socket that was rejected for execution", e);
                        }

                        // we never started the handler, so we must release the semaphore here
                        token.dropped();
                    } catch (Exception e) {
                        // we may get an SSL handshake errors, which should only fail one socket, not the listener
                        LOGGER.log(TRACE, "Failed to handle accepted socket", e);
                        // the socket was never handled
                        try {
                            socket.close();
                        } catch (IOException ex) {
                            LOGGER.log(TRACE,
                                       "Failed to close socket that failed start execution (see previous trace for reason)",
                                       e);
                        }

                        // we never started the handler, so we must release the semaphore here
                        token.ignore();
                    }
                }
            } catch (AsynchronousCloseException e) {
                if (inCheckpoint) {
                    break;
                } else if (running) {
                    stop();
                }
            } catch (SocketException e) {
                if (!e.getMessage().contains("Socket closed")) {
                    LOGGER.log(ERROR, "Got a socket exception while listening, this server socket is terminating now", e);
                }
                if (inCheckpoint) {
                    break;
                } else if (running) {
                    stop();
                }
            } catch (Throwable e) {
                LOGGER.log(ERROR, "Got a throwable while listening, this server socket is terminating now", e);
                if (inCheckpoint) {
                    break;
                } else if (running) {
                    stop();
                }
            }
        }

        LOGGER.log(INFO, String.format("[%s] %s socket closed.", serverChannelId, socketName));
        closeFuture.complete(null);
    }

    private List<ServerConnection> activeConnections() {
        return new ArrayList<>(activeConnections.values());
    }

    private Throwable closeActiveConnections(boolean interrupt) {
        Throwable failure = null;
        for (ServerConnection connection : activeConnections()) {
            try {
                connection.close(interrupt);
            } catch (RuntimeException | Error e) {
                failure = LifecycleFailures.add(failure, e);
            }
        }
        return failure;
    }

    // Intended for testing.
    Thread serverThreads() {
        return serverThread;
    }

    // Intended for testing.
    void activeConnection(String id, ServerConnection connection) {
        activeConnections.put(id, connection);
    }

    // Intended for testing.
    Future<?> readerTask(InterruptableTask<?> task) {
        return readerExecutor.execute(task);
    }

    // Intended for testing.
    void beforeReaderExecutorTerminate(Runnable beforeReaderExecutorTerminate) {
        this.beforeReaderExecutorTerminate = Objects.requireNonNull(beforeReaderExecutorTerminate);
    }

    // Intended for testing.
    void beforeSharedExecutorAwait(Runnable beforeSharedExecutorAwait) {
        this.beforeSharedExecutorAwait = Objects.requireNonNull(beforeSharedExecutorAwait);
    }

    void suspend() {
        inCheckpoint = true;
        // Checkpoint suspend is expected to stop the listener thread. The connection-limit wait path
        // converts interrupts into a rejected outcome, so clear the loop condition first to avoid
        // re-entering the wait after the interrupt is consumed.
        running = false;
        suspendForCheckpoint();
        serverThread = null;
        closeFuture = null;
    }

    void resume() {
        initServerThread();
        startIt();
        inCheckpoint = false;
    }
}
