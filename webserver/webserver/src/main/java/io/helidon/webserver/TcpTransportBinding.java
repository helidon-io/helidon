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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLParameters;

import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.Limit.InitializationContext;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.task.HelidonTaskExecutor;
import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.metrics.api.Tag;
import io.helidon.webserver.spi.PortTransportBinding;
import io.helidon.webserver.spi.TlsTransportBinding;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

final class TcpTransportBinding implements PortTransportBinding, TlsTransportBinding {
    static final String TYPE = "tcp";

    private static final System.Logger LOGGER = System.getLogger(TcpTransportBinding.class.getName());

    private final TransportBindingContext listenerContext;
    private final String name;
    private final String socketName;
    private final ListenerConfig listenerConfig;
    private final SocketAddress configuredAddress;
    private final SocketOptions connectionOptions;
    private final ConnectionProviders connectionProviders;
    private final Tls tls;
    private final VirtualHostRegistry virtualHosts;
    private final HelidonTaskExecutor readerExecutor;
    private final Runnable startIdleTimeoutHandler;
    private final Limit connectionLimit;
    private final Limit requestLimit;
    private final Set<ConnectionHandler> connectionHandlers = ConcurrentHashMap.newKeySet();

    private volatile boolean running;
    private volatile boolean inCheckpoint;
    private volatile int connectedPort = -1;
    private volatile ServerSocketChannel serverSocket;
    private volatile Thread serverThread;
    private volatile CompletableFuture<Void> closeFuture;

    TcpTransportBinding(TransportBindingContext listenerContext,
                        String name,
                        ListenerConfig listenerConfig,
                        SocketAddress configuredAddress,
                        SocketOptions connectionOptions,
                        ConnectionProviders connectionProviders,
                        Tls tls,
                        VirtualHostRegistry virtualHosts,
                        HelidonTaskExecutor readerExecutor,
                        Limit requestLimit,
                        Runnable startIdleTimeoutHandler) {
        this.listenerContext = listenerContext;
        this.name = Objects.requireNonNull(name, "name");
        this.socketName = listenerContext.name();
        this.listenerConfig = listenerConfig;
        this.configuredAddress = configuredAddress;
        this.connectionOptions = connectionOptions;
        this.connectionProviders = connectionProviders;
        this.tls = tls;
        this.virtualHosts = virtualHosts;
        this.readerExecutor = readerExecutor;
        this.requestLimit = requestLimit;
        this.startIdleTimeoutHandler = startIdleTimeoutHandler;
        this.connectionLimit = connectionLimit(listenerConfig);
        this.connectionLimit.init(limitContext(socketName));
        initServerThread();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String configuredEndpoint() {
        return String.valueOf(configuredAddress);
    }

    @Override
    public int port() {
        return connectedPort;
    }

    @Override
    public boolean hasTls() {
        return tls.enabled();
    }

    @Override
    public Security security() {
        return tls.enabled() ? Security.TLS : Security.UNPROTECTED;
    }

    @Override
    public void reloadTls(TlsMaterial material) {
        Objects.requireNonNull(material, "material");
        if (!tls.enabled()) {
            throw new IllegalArgumentException("TLS is not enabled on the socket " + socketName
                                                       + " and therefore cannot be reloaded");
        }
        tls.reload(material);
    }

    @Override
    public void reloadVirtualHostTls(TlsMaterial material, String configuredHost) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(configuredHost, "configuredHost");
        virtualHosts.reloadTls(material, configuredHost);
    }

    @Override
    public boolean supportsListenerVirtualHosts() {
        return virtualHosts.enabled();
    }

    @Override
    public void start() {
        SocketAddress bindAddress = bindAddress();
        try {
            if (bindAddress instanceof UnixDomainSocketAddress) {
                serverSocket = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            } else {
                serverSocket = ServerSocketChannel.open();
            }
            if (tls.enabled()) {
                // basic validation of the configuration
                tls.newEngine();
                virtualHosts.validateTls();
            }
            listenerConfig.configureSocket(serverSocket);

            serverSocket.bind(bindAddress, listenerConfig.backlog());
            this.connectedPort = serverSocket.getLocalAddress() instanceof InetSocketAddress ias ? ias.getPort() : -1;

            String serverChannelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(serverSocket));

            running = true;
            startIdleTimeoutHandler.run();
            logStarted(serverChannelId);

            serverThread.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start server", e);
        }
    }

    private SocketAddress bindAddress() {
        if (configuredAddress instanceof InetSocketAddress inetSocketAddress && inetSocketAddress.getPort() == 0) {
            OptionalInt boundPort = listenerContext.boundPort();
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
        stopRequested();
        Throwable failure = null;
        ShutdownResult result = ShutdownResult.GRACEFUL;
        failure = LifecycleFailures.add(failure, closeServerSocketForStop());
        failure = LifecycleFailures.add(failure, closeOpenConnections(false));
        failure = LifecycleFailures.add(failure, awaitClose());
        if (!awaitConnectionHandlers(gracefulPeriod)) {
            result = ShutdownResult.FORCED;
            failure = LifecycleFailures.add(failure, closeOpenConnections(true));
        }
        LifecycleFailures.throwIfFailed(failure, "Failed to stop TCP transport binding " + name);
        return result;
    }

    @Override
    public void suspend() {
        inCheckpoint = true;
        // Checkpoint suspend is expected to stop the listener thread. The connection-limit wait path
        // converts interrupts into a rejected outcome, so clear the loop condition first to avoid
        // re-entering the wait after the interrupt is consumed.
        running = false;
        closeServerSocketForSuspend();
    }

    @Override
    public void resume() {
        initServerThread();
        start();
        inCheckpoint = false;
    }

    Thread.State serverThreadState() {
        Thread localServerThread = serverThread;
        return localServerThread == null ? null : localServerThread.getState();
    }

    boolean running() {
        return running;
    }

    boolean inCheckpoint() {
        return inCheckpoint;
    }

    void stopRequested() {
        running = false;
        inCheckpoint = false;
    }

    void clearAfterSuspend() {
        serverThread = null;
        closeFuture = null;
    }

    Throwable closeServerSocketForStop() {
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

    Throwable closeOpenConnections(boolean interrupt) {
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

    Throwable awaitClose() {
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

    private boolean awaitConnectionHandlers(Duration gracefulPeriod) {
        long stopAtNanos = stopAtNanos(gracefulPeriod);
        while (!connectionHandlers.isEmpty()) {
            long remainingNanos = stopAtNanos - System.nanoTime();
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

    List<ConnectionHandler> connectionHandlers() {
        List<ConnectionHandler> result = new ArrayList<>();
        addConnectionHandlersTo(result);
        return result;
    }

    void addConnectionHandlersTo(List<ConnectionHandler> result) {
        result.addAll(connectionHandlers);
    }

    private static Limit connectionLimit(ListenerConfig listenerConfig) {
        // this instance is the only one waiting on this limit, so queue of 1 is enough
        // 5 minutes is long enough not to have busy waits
        if (listenerConfig.maxTcpConnections() == -1 || listenerConfig.maxTcpConnections() == 0) {
            // unlimited, no need to queue, as we never block
            return FixedLimit.create();
        }
        return FixedLimit.builder()
                .queueLength(1)
                .queueTimeout(Duration.ofMinutes(5))
                .permits(listenerConfig.maxTcpConnections())
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

    private void closeServerSocketForSuspend() {
        ServerSocketChannel localServerSocket = serverSocket;
        if (localServerSocket == null) {
            return;
        }
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
            LOGGER.log(INFO, "Exception thrown on socket close", e);
        } finally {
            serverSocket = null;
            connectedPort = -1;
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
                    ConnectionHandler handler = new ConnectionHandler(listenerContext,
                                                                      token,
                                                                      requestLimit,
                                                                      connectionProviders,
                                                                      socket,
                                                                      serverChannelId,
                                                                      listenerContext.router(),
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
                        readerExecutor.execute(handler);
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
        listenerContext.fatalBindingFailure(this, cause);
    }
}
