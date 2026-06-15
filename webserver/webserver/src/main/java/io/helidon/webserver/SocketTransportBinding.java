/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.util.Objects;
import java.util.OptionalInt;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLParameters;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.Limit.InitializationContext;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.task.HelidonTaskExecutor;
import io.helidon.common.tls.Tls;
import io.helidon.metrics.api.Tag;
import io.helidon.webserver.spi.ProtocolConfig;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.spi.ServerConnectionSelectorProvider;
import io.helidon.webserver.spi.TransportBinding;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

abstract class SocketTransportBinding implements TransportBinding {
    private static final System.Logger LOGGER = System.getLogger(SocketTransportBinding.class.getName());
    @SuppressWarnings("rawtypes")
    private static final LazyValue<List<ServerConnectionSelectorProvider>> SELECTOR_PROVIDERS = LazyValue.create(() ->
            HelidonServiceLoader.create(ServiceLoader.load(ServerConnectionSelectorProvider.class)).asList());

    private final TransportBindingContext transportContext;
    private final String type;
    private final String name;
    private final String socketName;
    private final ListenerConfig listenerConfig;
    private final Timer idleConnectionTimer;
    private final SocketAddress configuredAddress;
    private final SocketOptions connectionOptions;
    private final ConnectionProviders connectionProviders;
    private final Tls tls;
    private final VirtualHostRegistry virtualHosts;
    private final Limit connectionLimit;
    private final Limit requestLimit;
    private final Set<ConnectionHandler> connectionHandlers = ConcurrentHashMap.newKeySet();
    private final Lock idleTimeoutLock = new ReentrantLock();

    private volatile HelidonTaskExecutor readerExecutor;
    private volatile IdleTimeoutHandler idleTimeoutHandler;
    private volatile boolean running;
    private volatile boolean inCheckpoint;
    private volatile int connectedPort = -1;
    private volatile ServerSocketChannel serverSocket;
    private volatile Thread serverThread;
    private volatile CompletableFuture<Void> closeFuture;

    SocketTransportBinding(TransportBindingContext transportContext,
                           String type,
                           String name,
                           SocketAddress configuredAddress) {
        this.transportContext = Objects.requireNonNull(transportContext, "transportContext");
        this.type = Objects.requireNonNull(type, "type");
        this.name = Objects.requireNonNull(name, "name");
        ListenerContext listenerContext = Objects.requireNonNull(transportContext.listenerContext(), "listenerContext");
        this.listenerConfig = listenerContext.config();
        this.socketName = listenerConfig.name();
        this.idleConnectionTimer = transportContext.timer();
        this.configuredAddress = Objects.requireNonNull(configuredAddress, "configuredAddress");
        this.connectionOptions = listenerConfig.connectionOptions();
        ProtocolConfigs protocols = ProtocolConfigs.create(listenerConfig.protocols()
                                                                   .stream()
                                                                   .filter(config -> supportsTransportBinding(type, config))
                                                                   .toList());
        this.connectionProviders = ConnectionProviders.create(connectionSelectors(socketName, listenerConfig, protocols));
        this.tls = listenerConfig.tls().orElseGet(() -> Tls.builder().enabled(false).build());
        this.virtualHosts = VirtualHostRegistry.create(socketName, listenerConfig, tls);
        this.requestLimit = transportContext.requestLimit();
        this.connectionLimit = connectionLimit(listenerConfig);
        this.connectionLimit.init(limitContext(socketName));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<ServerConnectionSelector> connectionSelectors(String socketName,
                                                                      ListenerConfig listenerConfig,
                                                                      ProtocolConfigs protocols) {
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
        return selectors;
    }

    private static boolean supportsTransportBinding(String type, ProtocolConfig config) {
        Set<String> transportBindingTypes = config.transportBindingTypes();
        return transportBindingTypes.isEmpty() || transportBindingTypes.contains(type);
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String configuredEndpoint() {
        return String.valueOf(configuredAddress);
    }

    protected int connectedPort() {
        return connectedPort;
    }

    @Override
    public Security security() {
        return tls.enabled() ? Security.TLS : Security.UNPROTECTED;
    }

    @Override
    public void start() {
        if (configuredAddress instanceof UnixDomainSocketAddress && !listenerConfig.useNio()) {
            throw new IllegalArgumentException("UDS transport binding " + name + " on listener " + socketName
                                                       + " requires use-nio=true");
        }
        if (readerExecutor == null) {
            readerExecutor = ExecutorsFactory.newServerListenerReaderExecutor();
        }
        initServerThread();
        SocketAddress bindAddress = bindAddress();
        try {
            boolean unixDomainSocket = bindAddress instanceof UnixDomainSocketAddress;
            if (unixDomainSocket) {
                serverSocket = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            } else {
                serverSocket = ServerSocketChannel.open();
            }
            if (tls.enabled()) {
                // basic validation of the configuration
                tls.newEngine();
                virtualHosts.validateTls();
            }
            if (!unixDomainSocket) {
                listenerConfig.configureSocket(serverSocket);
            }

            serverSocket.bind(bindAddress, listenerConfig.backlog());
            this.connectedPort = serverSocket.getLocalAddress() instanceof InetSocketAddress ias ? ias.getPort() : -1;

            String serverChannelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(serverSocket));

            running = true;
            startIdleTimeoutHandler();
            logStarted(serverChannelId);

            serverThread.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start server", e);
        }
    }

    private SocketAddress bindAddress() {
        if (configuredAddress instanceof InetSocketAddress inetSocketAddress && inetSocketAddress.getPort() == 0) {
            OptionalInt boundPort = transportContext.boundPort();
            if (boundPort.isPresent() && boundPort.getAsInt() > 0) {
                return withPort(inetSocketAddress, boundPort.getAsInt());
            }
        }
        return configuredAddress;
    }

    private static InetSocketAddress withPort(InetSocketAddress address, int port) {
        if (address.isUnresolved()) {
            return InetSocketAddress.createUnresolved(address.getHostString(), port);
        }
        return new InetSocketAddress(address.getAddress(), port);
    }

    @Override
    public ShutdownResult stop(Duration gracefulPeriod) {
        Objects.requireNonNull(gracefulPeriod, "gracefulPeriod");
        long stopAtNanos = stopAtNanos(gracefulPeriod);
        stopRequested();
        Throwable failure = null;
        ShutdownResult result = ShutdownResult.GRACEFUL;
        IdleTimeoutHandler cancelledIdleTimeoutHandler = cancelIdleTimeoutHandler();
        failure = LifecycleFailures.add(failure, closeServerSocketForStop());
        failure = LifecycleFailures.add(failure, closeOpenConnections(false));
        failure = LifecycleFailures.add(failure, awaitClose());
        if (!awaitConnectionHandlers(stopAtNanos)) {
            result = ShutdownResult.FORCED;
            failure = LifecycleFailures.add(failure, closeOpenConnections(true));
        }
        failure = LifecycleFailures.add(failure, purgeCancelledIdleTimeoutHandler(cancelledIdleTimeoutHandler));
        failure = LifecycleFailures.add(failure, awaitIdleTimeoutHandler(cancelledIdleTimeoutHandler));
        try {
            shutdownReaderExecutor(remainingNanos(stopAtNanos));
        } catch (RuntimeException | Error e) {
            failure = LifecycleFailures.add(failure, e);
        }
        LifecycleFailures.throwIfFailed(failure, "Failed to stop " + type + " transport binding " + name);
        return result;
    }

    @Override
    public void suspend() {
        inCheckpoint = true;
        // Checkpoint suspend is expected to stop the listener thread. The connection-limit wait path
        // converts interrupts into a rejected outcome, so clear the loop condition first to avoid
        // re-entering the wait after the interrupt is consumed.
        running = false;
        IdleTimeoutHandler cancelledIdleTimeoutHandler = cancelIdleTimeoutHandler();
        Throwable failure = closeServerSocketForSuspend();
        // Stop handling any new requests on all accepted and active connections.
        failure = LifecycleFailures.add(failure, closeOpenConnections(false));
        // Interrupt and close any accepted and active connections.
        failure = LifecycleFailures.add(failure, closeOpenConnections(true));
        failure = LifecycleFailures.add(failure, awaitClose());
        failure = LifecycleFailures.add(failure, purgeCancelledIdleTimeoutHandler(cancelledIdleTimeoutHandler));
        failure = LifecycleFailures.add(failure, awaitIdleTimeoutHandler(cancelledIdleTimeoutHandler));
        clearAfterSuspend();
        throwIfCheckpointSuspendFailed(failure);
    }

    @Override
    public void resume() {
        start();
        inCheckpoint = false;
    }

    private void stopRequested() {
        running = false;
        inCheckpoint = false;
    }

    private void clearAfterSuspend() {
        serverThread = null;
        closeFuture = null;
    }

    private Throwable closeServerSocketForStop() {
        ServerSocketChannel localServerSocket = serverSocket;
        if (localServerSocket == null) {
            return null;
        }
        Throwable failure = null;
        boolean bound = serverSocketBound(localServerSocket, "Failed to check server socket binding before stop");
        try {
            localServerSocket.close();
        } catch (IOException e) {
            failure = LifecycleFailures.add(failure, new UncheckedIOException("Failed to close server socket", e));
        } finally {
            serverSocket = null;
            connectedPort = -1;
        }
        if (bound) {
            deleteUnixSocketFile();
        }
        return failure;
    }

    private Throwable closeOpenConnections(boolean interrupt) {
        Throwable failure = null;
        for (ConnectionHandler handler : connectionHandlers()) {
            try {
                handler.close(interrupt);
            } catch (RuntimeException | Error e) {
                failure = LifecycleFailures.add(failure, e);
            }
        }
        return failure;
    }

    private Throwable awaitClose() {
        Throwable failure = null;
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

    private boolean awaitConnectionHandlers(long stopAtNanos) {
        while (!connectionHandlers.isEmpty()) {
            long remainingNanos = remainingNanos(stopAtNanos);
            if (remainingNanos <= 0) {
                return false;
            }
            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, TimeUnit.MILLISECONDS.toNanos(100)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private void shutdownReaderExecutor(long timeoutNanos) {
        HelidonTaskExecutor localReaderExecutor = readerExecutor;
        if (localReaderExecutor == null) {
            return;
        }
        Throwable failure = null;
        localReaderExecutor.terminate(timeoutNanos, TimeUnit.NANOSECONDS);
        if (Thread.currentThread().isInterrupted()) {
            failure = LifecycleFailures.add(failure,
                                            new IllegalStateException("Interrupted while shutting down "
                                                                              + type
                                                                              + " reader executor "
                                                                              + "for " + socketName));
        }
        if (!localReaderExecutor.isTerminated()) {
            LOGGER.log(DEBUG, "Some tasks in " + type + " reader executor did not terminate gracefully");
            try {
                localReaderExecutor.forceTerminate();
            } catch (RuntimeException | Error e) {
                failure = LifecycleFailures.add(failure, e);
            }
        }
        readerExecutor = null;
        LifecycleFailures.throwIfFailed(failure, "Failed to shut down " + type + " reader executor for " + socketName);
    }

    private void startIdleTimeoutHandler() {
        idleTimeoutLock.lock();
        try {
            if (idleTimeoutHandler != null) {
                return;
            }
            IdleTimeoutHandler handler = new IdleTimeoutHandler(idleConnectionTimer,
                                                                listenerConfig,
                                                                this::connectionHandlers);
            handler.start();
            idleTimeoutHandler = handler;
        } finally {
            idleTimeoutLock.unlock();
        }
    }

    private IdleTimeoutHandler cancelIdleTimeoutHandler() {
        idleTimeoutLock.lock();
        try {
            IdleTimeoutHandler handler = idleTimeoutHandler;
            if (handler == null) {
                return null;
            }
            idleTimeoutHandler = null;
            handler.cancelOnly();
            return handler;
        } finally {
            idleTimeoutLock.unlock();
        }
    }

    private Throwable purgeCancelledIdleTimeoutHandler(IdleTimeoutHandler handler) {
        if (handler != null) {
            try {
                idleConnectionTimer.purge();
            } catch (RuntimeException | Error e) {
                return e;
            }
        }
        return null;
    }

    private static Throwable awaitIdleTimeoutHandler(IdleTimeoutHandler handler) {
        if (handler != null) {
            try {
                handler.awaitFinished();
            } catch (RuntimeException | Error e) {
                return e;
            }
        }
        return null;
    }

    private void throwIfCheckpointSuspendFailed(Throwable failure) {
        if (failure == null) {
            return;
        }
        Throwable unwrapped = LifecycleFailures.unwrap(failure);
        if (unwrapped instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (unwrapped instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Failed to suspend " + type + " transport binding for checkpoint", unwrapped);
    }

    private static long stopAtNanos(Duration gracefulPeriod) {
        long timeoutNanos = timeoutNanos(gracefulPeriod);
        long now = System.nanoTime();
        long stopAtNanos = now + timeoutNanos;
        return stopAtNanos < now ? Long.MAX_VALUE : stopAtNanos;
    }

    private static long timeoutNanos(Duration gracefulPeriod) {
        if (gracefulPeriod.isNegative()) {
            return 0;
        }
        try {
            return gracefulPeriod.toNanos();
        } catch (ArithmeticException _) {
            return Long.MAX_VALUE;
        }
    }

    private static long remainingNanos(long stopAtNanos) {
        if (stopAtNanos == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, stopAtNanos - System.nanoTime());
    }

    private List<ConnectionHandler> connectionHandlers() {
        List<ConnectionHandler> result = new ArrayList<>();
        result.addAll(connectionHandlers);
        return result;
    }

    private static Limit connectionLimit(ListenerConfig listenerConfig) {
        // this instance is the only one waiting on this limit, so queue of 1 is enough
        // 5 minutes is long enough not to have busy waits
        if (listenerConfig.maxConnections() == -1 || listenerConfig.maxConnections() == 0) {
            // unlimited, no need to queue, as we never block
            return FixedLimit.create();
        }
        return FixedLimit.builder()
                .queueLength(1)
                .queueTimeout(Duration.ofMinutes(5))
                .permits(listenerConfig.maxConnections())
                .build();
    }

    private static InitializationContext limitContext(String socketName) {
        if (WebServer.DEFAULT_SOCKET_NAME.equals(socketName)) {
            return InitializationContext.create(socketName);
        }
        return InitializationContext.create(socketName, List.of(Tag.create("socketName", socketName)));
    }

    private void initServerThread() {
        this.closeFuture = new CompletableFuture<>();
        this.serverThread = Thread.ofPlatform()
                .inheritInheritableThreadLocals(true)
                .daemon(false)
                .name("server-" + socketName + "-listener")
                .unstarted(this::listen);
    }

    private Throwable closeServerSocketForSuspend() {
        ServerSocketChannel localServerSocket = serverSocket;
        if (localServerSocket == null) {
            return null;
        }
        Throwable failure = null;
        try {
            localServerSocket.close();
            if (configuredAddress instanceof UnixDomainSocketAddress udsa) {
                try {
                    // UNIX socket files are created automatically, but they are not deleted when the channel is closed
                    Files.deleteIfExists(udsa.getPath());
                } catch (IOException e) {
                    LOGGER.log(WARNING, "Failed to delete UNIX socket file " + udsa.getPath().toAbsolutePath(), e);
                }
            }
        } catch (IOException e) {
            failure = LifecycleFailures.add(failure, new UncheckedIOException("Failed to close server socket", e));
        } finally {
            serverSocket = null;
            connectedPort = -1;
        }
        return failure;
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

    private void logStarted(String serverChannelId) {
        if (!LOGGER.isLoggable(INFO)) {
            return;
        }
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
                LOGGER.log(TRACE, "[" + serverChannelId + "] direct writes");
            } else {
                LOGGER.log(TRACE,
                           "[" + serverChannelId + "] async writes, queue length: "
                                   + listenerConfig.writeQueueLength());
            }
            if (tls.enabled()) {
                debugTls(serverChannelId, tls);
            }
        }
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

    private void listen() {
        ServerSocketChannel localServerSocket = serverSocket;
        String serverChannelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(localServerSocket));

        while (running) {
            try {
                // this must be done before we accept, and the semaphore must be released when connection is finished
                var outcome = connectionLimit.tryAcquireOutcome(true);
                if (outcome.disposition() == LimitAlgorithm.Outcome.Disposition.ACCEPTED) {
                    LimitAlgorithm.Token token = ((LimitAlgorithm.Outcome.Accepted) outcome).token();

                    // if accept fails itself, we consider it end of story, the listener is broken
                    localServerSocket = serverSocket;
                    if (localServerSocket == null) {
                        token.ignore();
                        break;
                    }
                    SocketChannel socket = localServerSocket.accept();
                    ConnectionHandler handler = new ConnectionHandler(transportContext.listenerContext(),
                                                                      token,
                                                                      requestLimit,
                                                                      connectionProviders,
                                                                      socket,
                                                                      serverChannelId,
                                                                      transportContext.router(),
                                                                      tls,
                                                                      virtualHosts,
                                                                      connectionHandlers::remove);
                    connectionHandlers.add(handler);

                    try {
                        if (!running) {
                            connectionHandlers.remove(handler);
                            closeAcceptedSocket(socket, null);
                            token.ignore();
                            continue;
                        }
                        connectionOptions.configureSocket(socket);
                        if (!running) {
                            connectionHandlers.remove(handler);
                            closeAcceptedSocket(socket, null);
                            token.ignore();
                            continue;
                        }
                        HelidonTaskExecutor localReaderExecutor = readerExecutor;
                        if (localReaderExecutor == null) {
                            throw new RejectedExecutionException(type + " reader executor is not available");
                        }
                        localReaderExecutor.execute(handler);
                    } catch (RejectedExecutionException e) {
                        connectionHandlers.remove(handler);
                        LOGGER.log(ERROR, "Executor rejected handler for new connection", e);
                        closeAcceptedSocket(socket, e);

                        // we never started the handler, so we must release the semaphore here
                        token.dropped();
                    } catch (Exception e) {
                        connectionHandlers.remove(handler);
                        // we may get an SSL handshake errors, which should only fail one socket, not the listener
                        LOGGER.log(TRACE, "Failed to handle accepted socket", e);
                        closeAcceptedSocket(socket, e);

                        // we never started the handler, so we must release the semaphore here
                        token.ignore();
                    }
                }
            } catch (AsynchronousCloseException e) {
                if (inCheckpoint) {
                    break;
                } else if (running) {
                    fatalBindingFailure(e);
                }
            } catch (SocketException e) {
                if (!e.getMessage().contains("Socket closed")) {
                    LOGGER.log(ERROR, "Got a socket exception while listening, this server socket is terminating now", e);
                }
                if (inCheckpoint) {
                    break;
                } else if (running) {
                    fatalBindingFailure(e);
                }
            } catch (Throwable e) {
                LOGGER.log(ERROR, "Got a throwable while listening, this server socket is terminating now", e);
                if (inCheckpoint) {
                    break;
                } else if (running) {
                    fatalBindingFailure(e);
                }
            }
        }

        LOGGER.log(INFO, String.format("[%s] %s socket closed.", serverChannelId, socketName));
        CompletableFuture<Void> localCloseFuture = closeFuture;
        if (localCloseFuture != null) {
            localCloseFuture.complete(null);
        }
    }

    private static void closeAcceptedSocket(SocketChannel socket, Throwable cause) {
        // the socket was never handled
        try {
            socket.close();
        } catch (IOException e) {
            if (cause != null && cause != e) {
                e.addSuppressed(cause);
            }
            LOGGER.log(TRACE, "Failed to close socket that was not handled", e);
        }
    }

    private void fatalBindingFailure(Throwable cause) {
        stopRequested();
        transportContext.fatalBindingFailure(this, cause);
    }
}
