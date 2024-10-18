/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.NoopSemaphore;
import io.helidon.common.context.Context;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.task.HelidonTaskExecutor;
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
    private final Thread serverThread;
    private final DirectHandlers directHandlers;
    private final CompletableFuture<Void> closeFuture;
    private final Tls tls;
    private final SocketOptions connectionOptions;
    private final InetSocketAddress configuredAddress;
    private final Duration gracePeriod;

    private final MediaContext mediaContext;
    private final ContentEncodingContext contentEncodingContext;
    private final Context context;
    private final Semaphore connectionSemaphore;
    private final Limit requestLimit;
    private final Map<String, ServerConnection> activeConnections = new ConcurrentHashMap<>();

    private volatile boolean running;
    private volatile int connectedPort;
    private volatile ServerSocket serverSocket;

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

        this.connectionSemaphore = listenerConfig.maxTcpConnections() == -1
                ? NoopSemaphore.INSTANCE
                : new Semaphore(listenerConfig.maxTcpConnections());

        if (listenerConfig.maxConcurrentRequests() == -1) {
            this.requestLimit = listenerConfig.concurrencyLimit()
                    .orElseGet(FixedLimit::create); // unlimited unless configured
        } else {
            this.requestLimit = FixedLimit.builder()
                    .permits(listenerConfig.maxConcurrentRequests())
                    .build();
        }

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

        this.serverThread = Thread.ofPlatform()
                .inheritInheritableThreadLocals(true)
                .daemon(false)
                .name("server-" + socketName + "-listener")
                .unstarted(this::listen);

        // to read requests and execute tasks
        this.readerExecutor = ExecutorsFactory.newServerListenerReaderExecutor();

        // to do anything else (writers etc.)
        this.sharedExecutor = ExecutorsFactory.newServerListenerSharedExecutor();

        this.closeFuture = new CompletableFuture<>();

        int port = listenerConfig.port();
        if (port < 1) {
            port = 0;
        }
        this.configuredAddress = new InetSocketAddress(listenerConfig.address(), port);
        this.router = router;

        // handle idle connection timeout
        IdleTimeoutHandler ith = new IdleTimeoutHandler(idleConnectionTimer,
                                                        listenerConfig,
                                                        this::activeConnections);
        ith.start();
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

    InetSocketAddress configuredAddress() {
        return configuredAddress;
    }

    void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            // Stop listening for connections
            serverSocket.close();

            // Shutdown reader executor
            readerExecutor.terminate(gracePeriod.toMillis(), TimeUnit.MILLISECONDS);
            if (!readerExecutor.isTerminated()) {
                LOGGER.log(DEBUG, "Some tasks in reader executor did not terminate gracefully");
                readerExecutor.forceTerminate();
            }

            // Shutdown shared executor
            try {
                sharedExecutor.shutdown();
                boolean done = sharedExecutor.awaitTermination(gracePeriod.toMillis(), TimeUnit.MILLISECONDS);
                if (!done) {
                    List<Runnable> running = sharedExecutor.shutdownNow();
                    if (!running.isEmpty()) {
                        LOGGER.log(DEBUG, running.size() + " tasks in shared executor did not terminate gracefully");
                    }
                }
            } catch (InterruptedException e) {
                // falls through
            }

        } catch (IOException e) {
            LOGGER.log(INFO, "Exception thrown on socket close", e);
        }
        serverThread.interrupt();
        closeFuture.join();
        router.afterStop();
    }

    @SuppressWarnings("resource")
    void start() {
        router.beforeStart();

        try {
            SSLServerSocket sslServerSocket = tls.enabled() ? tls.createServerSocket() : null;
            serverSocket = tls.enabled() ? sslServerSocket : new ServerSocket();
            listenerConfig.configureSocket(serverSocket);

            serverSocket.bind(configuredAddress, listenerConfig.backlog());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start server", e);
        }

        String serverChannelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(serverSocket));

        running = true;

        InetAddress inetAddress = serverSocket.getInetAddress();
        this.connectedPort = serverSocket.getLocalPort();

        if (LOGGER.isLoggable(INFO)) {
            String format;
            if (tls.enabled()) {
                format = "[%s] https://%s:%s bound for socket '%s'";
            } else {
                format = "[%s] http://%s:%s bound for socket '%s'";
            }
            LOGGER.log(INFO, String.format(format,
                                           serverChannelId,
                                           inetAddress.getHostAddress(),
                                           connectedPort,
                                           socketName));

            if (LOGGER.isLoggable(TRACE)) {
                if (listenerConfig.writeQueueLength() <= 1) {
                    LOGGER.log(System.Logger.Level.TRACE, "[" + serverChannelId + "] direct writes");
                } else {
                    LOGGER.log(System.Logger.Level.TRACE,
                               "[" + serverChannelId + "] async writes, queue length: " + listenerConfig.writeQueueLength());
                }
                if (tls.enabled()) {
                    debugTls(serverChannelId, tls);
                }
            }
        }

        serverThread.start();
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

    private void listen() {
        String serverChannelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(serverSocket));

        while (running) {
            try {
                // this must be done before we accept, and the semaphore must be released when connection is finished
                connectionSemaphore.acquire();
                // if accept fails itself, we consider it end of story, the listener is broken
                Socket socket = serverSocket.accept();

                try {
                    connectionOptions.configureSocket(socket);
                    ConnectionHandler handler = new ConnectionHandler(this,
                                                    connectionSemaphore,
                                                    requestLimit,
                                                    connectionProviders,
                                                    activeConnections,
                                                    socket,
                                                    serverChannelId,
                                                    router,
                                                    tls);
                    readerExecutor.execute(handler);
                } catch (RejectedExecutionException e) {
                    LOGGER.log(ERROR, "Executor rejected handler for new connection");

                    // the socket was never handled
                    try {
                        socket.close();
                    } catch (IOException ex) {
                        LOGGER.log(TRACE, "Failed to close socket that was rejected for execution", e);
                    }

                    // we never started the handler, so we must release the semaphore here
                    connectionSemaphore.release();
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
                    connectionSemaphore.release();
                }
            } catch (SocketException e) {
                if (!e.getMessage().contains("Socket closed")) {
                    LOGGER.log(ERROR, "Got a socket exception while listening, this server socket is terminating now", e);
                }
                if (running) {
                    stop();
                }
            } catch (Throwable e) {
                LOGGER.log(ERROR, "Got a throwable while listening, this server socket is terminating now", e);
                if (running) {
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
}
