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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PlainSocket;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.socket.TlsSocket;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webserver.http.DirectHandlers;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.TRACE;

class ServerListener {
    private static final System.Logger LOGGER = System.getLogger(ServerListener.class.getName());

    private static final long EXECUTOR_SHUTDOWN_MILLIS = 500L;

    private final ConnectionProviders connectionProviders;
    private final String socketName;
    private final ListenerConfiguration listenerConfig;
    private final Router router;
    private final HelidonTaskExecutor readerExecutor;
    private final ExecutorService sharedExecutor;
    private final Thread serverThread;
    private final DirectHandlers simpleHandlers;
    private final CompletableFuture<Void> closeFuture;
    private final SocketOptions connectionOptions;
    private final InetSocketAddress configuredAddress;

    private final ServerContext serverContext;

    private volatile boolean running;
    private volatile int connectedPort;
    private volatile ServerSocket serverSocket;

    ServerListener(ServerContext serverContext,
                   List<ServerConnectionSelector> connectionProviders,
                   String socketName,
                   ListenerConfiguration listenerConfig,
                   Router router,
                   DirectHandlers simpleHandlers,
                   boolean inheritThreadLocals) {

        this.serverContext = serverContext;
        this.connectionProviders = ConnectionProviders.create(connectionProviders);
        this.socketName = socketName;
        this.listenerConfig = listenerConfig;
        this.router = router;
        this.connectionOptions = listenerConfig.connectionOptions();

        this.serverThread = Thread.ofPlatform()
                .allowSetThreadLocals(true)
                .inheritInheritableThreadLocals(true)
                .daemon(false)
                .name("server-" + socketName + "-listener")
                .unstarted(this::listen);
        this.simpleHandlers = simpleHandlers;
        this.readerExecutor = ThreadPerTaskExecutor.create(Thread.ofVirtual()
                                                                         .allowSetThreadLocals(true)
                                                                         .inheritInheritableThreadLocals(inheritThreadLocals)
                                                                         .factory());

        this.sharedExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                                                                         .allowSetThreadLocals(true)
                                                                         .inheritInheritableThreadLocals(inheritThreadLocals)
                                                                         .factory());

        this.closeFuture = new CompletableFuture<>();

        int port = listenerConfig.port();
        if (port < 1) {
            port = 0;
        }
        this.configuredAddress = new InetSocketAddress(listenerConfig.address(), port);
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
            readerExecutor.terminate(EXECUTOR_SHUTDOWN_MILLIS, TimeUnit.MILLISECONDS);
            if (!readerExecutor.isTerminated()) {
                LOGGER.log(DEBUG, "Some tasks in reader executor did not terminate gracefully");
                readerExecutor.forceTerminate();
            }

            // Shutdown shared executor
            try {
                sharedExecutor.shutdown();
                boolean done = sharedExecutor.awaitTermination(EXECUTOR_SHUTDOWN_MILLIS, TimeUnit.MILLISECONDS);
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

    void start() {
        router.beforeStart();

        try {
            Tls tls = listenerConfig.hasTls() ? listenerConfig.tls() : null;
            SSLServerSocket sslServerSocket = listenerConfig.hasTls() ? tls.createServerSocket() : null;
            serverSocket = listenerConfig.hasTls() ? sslServerSocket : new ServerSocket();
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
            if (listenerConfig.hasTls()) {
                format = "[%s] https://%s:%s bound for socket '%s'";
            } else {
                format = "[%s] http://%s:%s bound for socket '%s'";
            }
            LOGGER.log(INFO, String.format(format,
                                           serverChannelId,
                                           inetAddress.getHostAddress(),
                                           connectedPort,
                                           socketName));

            if (listenerConfig.writeQueueLength() <= 1) {
                LOGGER.log(System.Logger.Level.INFO, "[" + serverChannelId + "] direct writes");
            } else {
                LOGGER.log(System.Logger.Level.INFO,
                           "[" + serverChannelId + "] async writes, queue length: " + listenerConfig.writeQueueLength());
            }

            if (LOGGER.isLoggable(TRACE)) {
                if (listenerConfig.hasTls()) {
                    debugTls(serverChannelId, listenerConfig.tls());
                }
            }
        }

        serverThread.start();
    }

    boolean hasTls() {
        return listenerConfig.hasTls();
    }

    void reloadTls(Tls tls) {
        if (!listenerConfig.hasTls()) {
            throw new IllegalArgumentException("TLS is not enabled on the socket " + socketName
                                                            + " and therefore cannot be reloaded");
        }
        if (!tls.enabled()) {
            throw new UnsupportedOperationException("TLS cannot be disabled by reloading on the socket " + socketName);
        }
        listenerConfig.tls().reload(tls);
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
                // if accept fails itself, we consider it end of story, the listener is broken
                Socket socket = serverSocket.accept();

                try {
                    ConnectionHandler handler;
                    String channelId = "0x" + HexFormat.of().toHexDigits(System.identityHashCode(socket));
                    connectionOptions.configureSocket(socket);

                    HelidonSocket helidonSocket;
                    if (listenerConfig.hasTls()) {
                        SSLSocket sslSocket = (SSLSocket) socket;
                        sslSocket.setHandshakeApplicationProtocolSelector(
                                (sslEngine, list) -> {
                                    for (String protocolId : list) {
                                        if (connectionProviders.supportedApplicationProtocols()
                                                .contains(protocolId)) {
                                            return protocolId;
                                        }
                                    }
                                    return null;
                                });
                        sslSocket.startHandshake();
                        helidonSocket = TlsSocket.server(sslSocket, channelId, serverChannelId);
                    } else {
                        helidonSocket = PlainSocket.server(socket, channelId, serverChannelId);
                    }

                    handler = new ConnectionHandler(serverContext,
                                                    connectionProviders,
                                                    sharedExecutor,
                                                    serverChannelId,
                                                    channelId,
                                                    helidonSocket,
                                                    router,
                                                    listenerConfig.writeQueueLength(),
                                                    listenerConfig.maxPayloadSize(),
                                                    simpleHandlers);

                    readerExecutor.execute(handler);
                } catch (RejectedExecutionException e) {
                    LOGGER.log(ERROR, "Executor rejected handler for new connection");
                } catch (Exception e) {
                    // we may get an SSL handshake errors, which should only fail one socket, not the listener
                    LOGGER.log(TRACE, "Failed to handle accepted socket", e);
                }
            } catch (SocketException e) {
                if (!e.getMessage().contains("Socket closed")) {
                    e.printStackTrace();
                }
                if (running) {
                    stop();
                }
            } catch (Throwable e) {
                e.printStackTrace();
                if (running) {
                    stop();
                }
            }
        }

        LOGGER.log(INFO, String.format("[%s] %s socket closed.", serverChannelId, socketName));
        closeFuture.complete(null);
    }
}
