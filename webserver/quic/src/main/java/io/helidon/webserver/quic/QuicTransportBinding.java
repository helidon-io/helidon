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

package io.helidon.webserver.quic;

import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongFunction;

import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.tls.Tls;
import io.helidon.config.ConfigException;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicServerRuntime;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.webserver.HttpTransportObserverSupport;
import io.helidon.webserver.ListenerTlsContext;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.TransportBindingContext;
import io.helidon.webserver.quic.QuicSubProtocolConfigSupport.ResolvedRuntime;
import io.helidon.webserver.quic.spi.HttpQuicSubProtocolRuntime;
import io.helidon.webserver.quic.spi.QuicSubProtocolConfig;
import io.helidon.webserver.quic.spi.QuicSubProtocolProvider;
import io.helidon.webserver.quic.spi.QuicSubProtocolRuntime;
import io.helidon.webserver.spi.PortTransportBinding;

final class QuicTransportBinding implements PortTransportBinding {
    private final TransportBindingContext context;
    private final String listenerName;
    private final QuicTransportConfig bindingConfig;
    private final List<QuicSubProtocolConfig> protocolConfigs;
    @SuppressWarnings("rawtypes")
    private final List<QuicSubProtocolProvider> protocolProviders;
    private final ListenerTlsContext listenerTls;
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    private volatile RuntimeState server;
    private volatile int connectedPort = -1;

    QuicTransportBinding(TransportBindingContext context,
                         QuicTransportConfig bindingConfig,
                         List<QuicSubProtocolConfig> protocolConfigs,
                         @SuppressWarnings("rawtypes") List<QuicSubProtocolProvider> protocolProviders) {
        this.context = Objects.requireNonNull(context, "context");
        this.listenerName = context.listenerContext().config().name();
        this.bindingConfig = Objects.requireNonNull(bindingConfig, "bindingConfig");
        this.protocolConfigs = List.copyOf(protocolConfigs);
        this.protocolProviders = List.copyOf(protocolProviders);
        this.listenerTls = Objects.requireNonNull(context.listenerTls(), "listenerTls");
    }

    @Override
    public String type() {
        return QuicTransportBindingTypes.QUIC;
    }

    @Override
    public boolean holdsIdleConnectionPermit() {
        return false;
    }

    @Override
    public String configuredEndpoint() {
        return inetEndpoint(configuredInetSocketAddress());
    }

    @Override
    public int port() {
        return connectedPort;
    }

    @Override
    public Security security() {
        return Security.TLS;
    }

    @Override
    public void start() {
        lifecycleLock.lock();
        try {
            if (server != null) {
                return;
            }
            try {
                startServer();
            } catch (UncheckedIOException e) {
                UncheckedIOException failure = new UncheckedIOException(
                        "Failed to start QUIC listener for socket " + listenerName + ": " + e.getMessage(),
                        e.getCause());
                for (Throwable suppressed : e.getSuppressed()) {
                    failure.addSuppressed(suppressed);
                }
                throw failure;
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    @Override
    public ShutdownResult stop(Duration gracefulPeriod) {
        Objects.requireNonNull(gracefulPeriod, "gracefulPeriod");
        RuntimeState localServer;
        lifecycleLock.lock();
        try {
            localServer = server;
            server = null;
            connectedPort = -1;
        } finally {
            lifecycleLock.unlock();
        }
        if (localServer == null) {
            return ShutdownResult.GRACEFUL;
        }
        return localServer.closeGracefully(gracefulPeriod);
    }

    @Override
    public void suspend() {
        RuntimeState localServer;
        lifecycleLock.lock();
        try {
            localServer = server;
            server = null;
            connectedPort = -1;
        } finally {
            lifecycleLock.unlock();
        }
        if (localServer != null) {
            localServer.close();
        }
    }

    @Override
    public void resume() {
        start();
    }

    private static int normalizePort(int port) {
        return port < 1 ? 0 : port;
    }

    private static String inetEndpoint(InetSocketAddress address) {
        String host = address.getHostString();
        if (host.indexOf(':') >= 0 && !host.startsWith("[") && !host.endsWith("]")) {
            host = "[" + host + "]";
        }
        return host + ":" + normalizePort(address.getPort());
    }

    private static String protocolId(QuicSubProtocolConfig config) {
        return config.type() + "(" + config.name() + ")";
    }

    private void startServer() {
        RuntimeState created = null;
        List<ResolvedRuntime> runtimes = List.of();
        try {
            Tls currentTls = listenerTls.tls();
            if (!currentTls.enabled()) {
                throw new IllegalStateException("QUIC transport binding on listener " + listenerName
                                                        + " requires listener TLS");
            }
            if (listenerTls.virtualHostsEnabled()) {
                listenerTls.validateVirtualHosts();
            }
            runtimes = QuicSubProtocolConfigSupport.createRuntimes(context, protocolConfigs, protocolProviders);
            validateMinimumPeerUniStreams(runtimes, bindingConfig.quic());
            created = new RuntimeState(this,
                                       listenerName,
                                       context,
                                       configuredInetSocketAddress(),
                                       currentTls,
                                       bindingConfig.quic(),
                                       bindingConfig.retryEnabled(),
                                       bindingConfig.handshakeTimeout(),
                                       bindingConfig.maxPendingHandshakes(),
                                       bindingConfig.alpnPreference(),
                                       runtimes);

            InetSocketAddress localAddress = created.start();
            server = created;
            connectedPort = localAddress.getPort();
        } catch (RuntimeException | Error e) {
            if (created != null) {
                try {
                    created.close();
                } catch (RuntimeException | Error cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            } else {
                for (int i = runtimes.size() - 1; i >= 0; i--) {
                    try {
                        runtimes.get(i).runtime().close();
                    } catch (RuntimeException | Error cleanupFailure) {
                        e.addSuppressed(cleanupFailure);
                    }
                }
            }
            throw e;
        }
    }

    private void validateMinimumPeerUniStreams(List<ResolvedRuntime> runtimes, QuicConfig quicConfig) {
        for (ResolvedRuntime runtime : runtimes) {
            long runtimeMinimum = runtime.runtime().minimumPeerUniStreams();
            if (runtimeMinimum < 0) {
                throw new ConfigException("Listener " + listenerName + " QUIC protocol "
                                                  + protocolId(runtime.config())
                                                  + " reports negative minimumPeerUniStreams requirement "
                                                  + runtimeMinimum);
            }
            if (quicConfig.maxUniStreams() < runtimeMinimum) {
                throw new ConfigException("Listener " + listenerName + " QUIC protocol "
                                                  + protocolId(runtime.config())
                                                  + " requires QuicConfig.maxUniStreams to be at least "
                                                  + runtimeMinimum + ", but the binding configures "
                                                  + quicConfig.maxUniStreams());
            }
        }
    }

    private InetSocketAddress configuredInetSocketAddress() {
        SocketAddress configuredAddress = context.configuredAddress();
        if (!(configuredAddress instanceof InetSocketAddress inetSocketAddress)) {
            throw new ConfigException("QUIC transport binding cannot use non-internet listener address "
                                              + configuredAddress);
        }
        if (inetSocketAddress.getPort() > 0) {
            return inetSocketAddress;
        }
        int port = context.boundPort().orElse(0);
        if (port == inetSocketAddress.getPort()) {
            return inetSocketAddress;
        }
        if (inetSocketAddress.isUnresolved()) {
            return InetSocketAddress.createUnresolved(inetSocketAddress.getHostString(), port);
        }
        return new InetSocketAddress(inetSocketAddress.getAddress(), port);
    }

    private static final class RuntimeState implements AutoCloseable {
        private final QuicTransportBinding binding;
        private final String listenerName;
        private final TransportBindingContext context;
        private final QuicServerRuntime quicServer;
        private final QuicServerObserver quicServerObserver;
        private final List<ResolvedRuntime> runtimes;
        private final SequencedMap<String, ResolvedRuntime> runtimesByAlpn;
        private final Map<QuicConnection, SniContext> sniContexts = new ConcurrentHashMap<>();
        private final AtomicBoolean shutdownStarted = new AtomicBoolean();
        private final AtomicBoolean fatalFailureReported = new AtomicBoolean();
        private final QuicAcceptLoop acceptLoop;

        private RuntimeState(QuicTransportBinding binding,
                             String listenerName,
                             TransportBindingContext context,
                             InetSocketAddress bindAddress,
                             Tls tls,
                             QuicConfig quicConfig,
                             boolean retryEnabled,
                             Duration handshakeTimeout,
                             int maxPendingHandshakes,
                             List<String> alpnPreference,
                             List<ResolvedRuntime> runtimes) {
            this.binding = binding;
            this.listenerName = listenerName;
            this.context = context;
            this.runtimes = List.copyOf(runtimes);
            this.runtimesByAlpn =
                    QuicSubProtocolConfigSupport.resolveAlpns(listenerName, this.runtimes, alpnPreference);
            HttpTransportObserver observer = this.runtimes.stream()
                    .map(ResolvedRuntime::runtime)
                    .allMatch(HttpQuicSubProtocolRuntime.class::isInstance)
                    ? HttpTransportObserverSupport.observer(context.listenerContext())
                    : HttpTransportObserver.noop();
            this.quicServerObserver = new QuicServerObserver(
                    observer,
                    (connection, termination) -> connection.applicationProtocol()
                            .map(runtimesByAlpn::get)
                            .map(ResolvedRuntime::runtime)
                            .filter(HttpQuicSubProtocolRuntime.class::isInstance)
                            .map(HttpQuicSubProtocolRuntime.class::cast)
                            .map(runtime -> runtime.isNormalTermination(termination))
                            .orElseGet(() -> HttpQuicSubProtocolRuntime.defaultNormalTermination(termination)));
            Limit connectionLimit = context.connectionLimit();
            QuicServerRuntime.Builder serverBuilder = QuicServerRuntime.builder()
                    .serverId(listenerName)
                    .executor(context.listenerContext().executor())
                    .bindAddress(bindAddress)
                    .tls(tls)
                    .applicationProtocols(List.copyOf(this.runtimesByAlpn.sequencedKeySet()))
                    .applicationErrors(defaultApplicationErrors(this.runtimes))
                    .quicConfig(quicConfig)
                    .retryEnabled(retryEnabled)
                    .handshakeTimeout(handshakeTimeout)
                    .maxPendingHandshakes(maxPendingHandshakes)
                    .observer(quicServerObserver)
                    .connectionAdmission(() -> {
                        LimitAlgorithm.Outcome outcome = connectionLimit.tryAcquireOutcome(false);
                        if (outcome instanceof LimitAlgorithm.Outcome.Accepted accepted) {
                            LimitAlgorithm.Token token = accepted.token();
                            return QuicServerRuntime.ConnectionPermit.accepted(token::ignore, token::success);
                        }
                        return QuicServerRuntime.ConnectionPermit.rejected();
                    });
            serverBuilder.tlsSelector(new QuicServerRuntime.TlsSelector() {
                @Override
                public Tls select(QuicConnection connection, String requestedServerName) {
                    try {
                        return retainSelection(connection, binding.listenerTls.select(requestedServerName));
                    } catch (ListenerTlsContext.RejectedSniException e) {
                        throw new QuicServerRuntime.RejectedTlsSelectionException(
                                e.getMessage(),
                                e.sendUnrecognizedNameAlert(),
                                e);
                    }
                }

                @Override
                public Tls selectWithoutSni(QuicConnection connection) {
                    try {
                        return retainSelection(connection, binding.listenerTls.selectWithoutSni());
                    } catch (ListenerTlsContext.RejectedSniException e) {
                        throw new QuicServerRuntime.RejectedTlsSelectionException(
                                e.getMessage(),
                                e.sendUnrecognizedNameAlert(),
                                e);
                    }
                }
            });
            this.quicServer = serverBuilder.build();
            this.acceptLoop = new QuicAcceptLoop(quicServer::accept,
                                                 this::dispatch,
                                                 this::discard,
                                                 this::acceptFailed,
                                                 context.listenerContext().executor());
        }

        @Override
        public void close() {
            if (!shutdownStarted.compareAndSet(false, true)) {
                return;
            }
            acceptLoop.stop();

            Throwable failure = null;
            try {
                quicServer.stopAccepting();
            } catch (RuntimeException | Error e) {
                failure = collectFailure(failure, e);
            }
            for (int i = runtimes.size() - 1; i >= 0; i--) {
                try {
                    runtimes.get(i).runtime().close();
                } catch (RuntimeException | Error e) {
                    failure = collectFailure(failure, e);
                }
            }
            ConnectionOutcome closeOutcome = ConnectionOutcome.ERROR;
            try {
                quicServer.close();
                closeOutcome = ConnectionOutcome.LOCAL_CLOSE;
            } catch (RuntimeException | Error e) {
                failure = collectFailure(failure, e);
            } finally {
                sniContexts.clear();
                quicServerObserver.closeOutstanding(closeOutcome);
            }

            if (failure != null) {
                if (failure instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw (Error) failure;
            }
        }

        private static LongFunction<String> defaultApplicationErrors(List<ResolvedRuntime> runtimes) {
            if (runtimes.size() == 1) {
                return runtimes.getFirst().runtime().applicationErrors();
            }
            return QuicSubProtocolRuntime::defaultApplicationErrorToString;
        }

        private static Throwable collectFailure(Throwable current, Throwable next) {
            if (current == null) {
                return next;
            }
            current.addSuppressed(next);
            return current;
        }

        private static Throwable unwrap(Throwable throwable) {
            return throwable instanceof CompletionException completionException && completionException.getCause() != null
                    ? completionException.getCause()
                    : throwable;
        }

        private InetSocketAddress start() {
            InetSocketAddress localAddress = quicServer.localAddress();
            for (ResolvedRuntime runtime : runtimes) {
                runtime.runtime().start();
            }
            acceptLoop.start();
            return localAddress;
        }

        private void acceptFailed(Throwable throwable) {
            if (!shutdownStarted.get() && fatalFailureReported.compareAndSet(false, true)) {
                context.fatalBindingFailure(binding, unwrap(throwable));
            }
        }

        private void dispatch(QuicConnection connection) {
            if (shutdownStarted.get()) {
                discard(connection);
                return;
            }
            SniContext sniContext = sniContexts.remove(connection);
            if (sniContext == null) {
                reject(connection, "No TLS server-name selection is available for listener " + listenerName);
                return;
            }
            Optional<String> negotiatedAlpn = connection.applicationProtocol();
            if (negotiatedAlpn.isEmpty() || negotiatedAlpn.orElseThrow().isEmpty()) {
                reject(connection, "No ALPN was negotiated for listener " + listenerName);
                return;
            }
            String alpn = negotiatedAlpn.orElseThrow();

            ResolvedRuntime resolvedRuntime = runtimesByAlpn.get(alpn);
            if (resolvedRuntime == null) {
                reject(connection,
                       "No QUIC sub-protocol is configured for negotiated ALPN \"" + alpn
                               + "\" on listener " + listenerName);
                return;
            }

            try {
                quicServer.applicationErrors(connection, resolvedRuntime.runtime().applicationErrors());
                if (resolvedRuntime.runtime() instanceof HttpQuicSubProtocolRuntime httpRuntime) {
                    quicServerObserver.handoff(connection,
                                               (acceptedConnection, observation) ->
                                                       httpRuntime.accept(acceptedConnection,
                                                                          observation,
                                                                          sniContext));
                } else {
                    resolvedRuntime.runtime().accept(connection);
                }
            } catch (RuntimeException | Error e) {
                connection.terminate(QuicCloseCommand.transport(
                        e,
                        "Failed to dispatch ALPN \"" + alpn + "\" to " + protocolId(resolvedRuntime.config())));
            }
        }

        private void discard(QuicConnection connection) {
            connection.terminate(QuicCloseCommand.transport(
                    QuicTransportErrors.NO_ERROR,
                    "QUIC listener is not accepting new connections"));
        }

        private ShutdownResult closeGracefully(Duration gracePeriod) {
            if (!shutdownStarted.compareAndSet(false, true)) {
                return ShutdownResult.GRACEFUL;
            }
            acceptLoop.stop();

            long graceNanos;
            try {
                graceNanos = gracePeriod.isNegative() ? 0 : gracePeriod.toNanos();
            } catch (ArithmeticException e) {
                graceNanos = Long.MAX_VALUE;
            }
            long shutdownStartedAt = System.nanoTime();

            ShutdownResult result = ShutdownResult.GRACEFUL;
            Throwable failure = null;
            try {
                quicServer.stopAccepting();
            } catch (RuntimeException | Error e) {
                failure = collectFailure(failure, e);
            }
            for (ResolvedRuntime runtime : runtimes) {
                try {
                    long elapsed = Math.max(0, System.nanoTime() - shutdownStartedAt);
                    long remainingNanos = elapsed >= graceNanos ? 0 : graceNanos - elapsed;
                    ShutdownResult runtimeResult = runtime.runtime().closeGracefully(Duration.ofNanos(remainingNanos));
                    if (runtimeResult == ShutdownResult.FORCED) {
                        result = ShutdownResult.FORCED;
                    }
                } catch (RuntimeException | Error e) {
                    failure = collectFailure(failure, e);
                }
            }
            ConnectionOutcome closeOutcome = ConnectionOutcome.ERROR;
            try {
                long elapsed = Math.max(0, System.nanoTime() - shutdownStartedAt);
                long remainingNanos = elapsed >= graceNanos ? 0 : graceNanos - elapsed;
                boolean closed = quicServer.close(Duration.ofNanos(remainingNanos));
                closeOutcome = closed ? ConnectionOutcome.LOCAL_CLOSE : ConnectionOutcome.TIMEOUT;
                if (!closed) {
                    result = ShutdownResult.FORCED;
                }
            } catch (RuntimeException | Error e) {
                failure = collectFailure(failure, e);
            } finally {
                sniContexts.clear();
                quicServerObserver.closeOutstanding(closeOutcome);
            }
            for (ResolvedRuntime runtime : runtimes) {
                try {
                    runtime.runtime().close();
                } catch (RuntimeException | Error e) {
                    failure = collectFailure(failure, e);
                }
            }

            if (failure != null) {
                if (failure instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw (Error) failure;
            }
            return result;
        }

        private void reject(QuicConnection connection, String message) {
            connection.terminate(QuicCloseCommand.transport(new IllegalStateException(message), message));
        }

        private Tls retainSelection(QuicConnection connection, ListenerTlsContext.Selection selection) {
            SniContext selectedContext = selection.sniContext();
            SniContext previous = sniContexts.putIfAbsent(connection, selectedContext);
            if (previous != null) {
                throw new IllegalStateException("TLS was already selected for QUIC connection "
                                                        + connection.childSocketId());
            }
            connection.whenTerminated().whenComplete((_, failure) ->
                                                              sniContexts.remove(connection, selectedContext));
            return selection.tls();
        }
    }
}
