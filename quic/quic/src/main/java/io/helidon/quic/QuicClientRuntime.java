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

package io.helidon.quic;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongFunction;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;

import io.helidon.common.Api;
import io.helidon.common.tls.Tls;
import io.helidon.quic.QuicEndpoint.QuicEndpointFactory;
import io.helidon.quic.packet.QuicPacket;

/**
 * One-shot owner of client-side QUIC transport resources.
 *
 * <p>The runtime owns UDP endpoints, selector or poller infrastructure, timers, connection registration, and pending
 * transport work. A configured executor is borrowed and is never closed by this runtime. Closing or aborting a runtime is
 * terminal; a closed runtime cannot create more connections.
 */
@Api.Internal
public final class QuicClientRuntime implements QuicInstance, AutoCloseable {
    static final Duration DEFAULT_INITIAL_RESPONSE_TIMEOUT;
    static final int MAX_INITIAL_TOKENS = 1024;

    private static final System.Logger LOGGER = System.getLogger(QuicClientRuntime.class.getName());
    private static final AtomicLong IDS = new AtomicLong();
    private static final AtomicLong CONNECTIONS = new AtomicLong();
    private static final int MAX_ENDPOINTS_LIMIT = 16;

    static {
        DEFAULT_INITIAL_RESPONSE_TIMEOUT = Duration.ofSeconds(30);
    }

    private final String clientId;
    private final String name;
    private final Executor executor;
    private final Tls tls;
    private final long tlsGeneration;
    private final QuicTlsConfigSnapshot tlsConfig;
    private final QuicTLSContext quicTLSContext;
    private final QuicClientTlsSessionCache tlsSessionCache;
    private final boolean ownsTlsSessionCache;
    private final QuicClientInitialTokenCache initialTokenCache;
    private final boolean ownsInitialTokenCache;
    private final List<QuicVersion> availableVersions;
    private final InetSocketAddress bindAddress;
    private final QuicConfig quicConfig;
    private final Duration initialResponseTimeout;
    private final QuicRuntimeConfig runtimeConfig;
    private final ReentrantLock lock = new ReentrantLock();
    private final QuicEndpoint[] endpoints;
    private final Map<Long, OwnedConnection> connections = new HashMap<>();
    private final Map<QuicEndpoint, Integer> endpointConnectionCounts = new HashMap<>();
    private final QuicEndpointFactory endpointFactory = QuicEndpointFactory.create();
    private final LongFunction<String> appErrorCodeToString;
    private final Observer observer;

    private int insertionPoint;
    private volatile QuicSelector<?> selector;
    private volatile boolean closed;

    private QuicClientRuntime(Builder builder) {
        Objects.requireNonNull(builder, "QUIC client runtime builder");
        if (builder.tls == null) {
            throw new IllegalStateException("No TLS set");
        }
        this.tls = builder.tls;
        this.executor = Objects.requireNonNull(builder.executor, "executor");
        this.clientId = builder.clientId == null ? "quic-client-" + IDS.incrementAndGet() : builder.clientId;
        this.name = "QuicClientRuntime(%s)".formatted(clientId);
        this.appErrorCodeToString = builder.appErrorCodeToString == null
                ? QuicInstance.super::appErrorToString
                : builder.appErrorCodeToString;
        this.observer = builder.observer == null ? connection -> { } : builder.observer;
        this.quicConfig = builder.quicConfig == null ? QuicConfig.create() : builder.quicConfig;
        this.initialResponseTimeout = builder.initialResponseTimeout == null
                ? DEFAULT_INITIAL_RESPONSE_TIMEOUT
                : builder.initialResponseTimeout;
        if (initialResponseTimeout.isNegative() || initialResponseTimeout.isZero()) {
            throw new IllegalArgumentException("initialResponseTimeout must be positive: " + initialResponseTimeout);
        }
        this.runtimeConfig = QuicRuntimeConfig.create(quicConfig);
        List<QuicVersion> configuredAvailableVersions = quicConfig.availableVersions();
        if (configuredAvailableVersions.isEmpty()) {
            throw new IllegalStateException("Need at least one available Quic version");
        }
        this.tlsConfig = QuicTlsConfigSnapshot.create(tls, runtimeConfig);
        this.tlsGeneration = tlsConfig.generation();
        QuicTLSContext baseTlsContext = QuicTLSContext.create(tlsConfig);
        this.ownsTlsSessionCache = builder.tlsSessionCache == null;
        this.tlsSessionCache = ownsTlsSessionCache
                ? QuicClientTlsSessionCache.create(builder.tls)
                : builder.tlsSessionCache;
        this.ownsInitialTokenCache = builder.initialTokenCache == null;
        this.initialTokenCache = ownsInitialTokenCache
                ? QuicClientInitialTokenCache.create()
                : builder.initialTokenCache;
        this.quicTLSContext = baseTlsContext.withClientSessionCache(tlsSessionCache.delegate());
        try {
            if (!tlsSessionCache.validFor(tls)) {
                throw new IllegalArgumentException(
                        "Borrowed QUIC TLS session cache does not belong to the configured TLS generation");
            }
            var unsupportedVersions = new ArrayList<>(configuredAvailableVersions);
            unsupportedVersions.removeAll(quicTLSContext.createEngine().supportedQuicVersions());
            if (!unsupportedVersions.isEmpty()) {
                throw new IllegalArgumentException("Requested QUIC versions not supported by TLS: "
                                                           + unsupportedVersions);
            }
            if (tls.generation() != tlsGeneration) {
                throw new IllegalStateException("TLS configuration was reloaded while creating the QUIC client runtime");
            }
        } catch (RuntimeException | Error e) {
            if (ownsTlsSessionCache) {
                tlsSessionCache.close();
            }
            if (ownsInitialTokenCache) {
                initialTokenCache.close();
            }
            throw e;
        }
        this.availableVersions = List.copyOf(configuredAvailableVersions);
        this.bindAddress = builder.bindAddress == null ? new InetSocketAddress(0) : builder.bindAddress;
        this.endpoints = new QuicEndpoint[Math.min(runtimeConfig.endpoint().maxEndpoints(), MAX_ENDPOINTS_LIMIT)];
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "created");
        }
    }

    /**
     * Creates a new runtime builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an unconnected client connection owned by this runtime.
     *
     * @param peerAddress remote transport address
     * @param tlsPeerName peer name used by TLS
     * @param tlsPeerPort peer port used by TLS
     * @param applicationProtocols application protocols to advertise through ALPN
     * @return new client connection
     * @throws IllegalStateException if this runtime is closed or its TLS configuration was reloaded
     */
    public QuicClientConnection createConnection(InetSocketAddress peerAddress,
                                                 String tlsPeerName,
                                                 int tlsPeerPort,
                                                 String[] applicationProtocols) {
        return createConnectionInternal(peerAddress, tlsPeerName, tlsPeerPort, applicationProtocols, null);
    }

    /**
     * Creates and starts a connection with a per-connection SNI override.
     *
     * @param peerAddress remote transport address
     * @param tlsPeerName peer name used by TLS
     * @param tlsPeerPort peer port used by TLS
     * @param applicationProtocols application protocols to advertise through ALPN
     * @param serverNamesOverride server names to use
     * @return new client connection
     * @throws IllegalStateException if this runtime is closed or its TLS configuration was reloaded
     */
    public QuicClientConnection createConnection(InetSocketAddress peerAddress,
                                                 String tlsPeerName,
                                                 int tlsPeerPort,
                                                 String[] applicationProtocols,
                                                 List<SNIServerName> serverNamesOverride) {
        Objects.requireNonNull(serverNamesOverride, "serverNamesOverride");
        return createConnectionInternal(peerAddress,
                                        tlsPeerName,
                                        tlsPeerPort,
                                        applicationProtocols,
                                        serverNamesOverride);
    }

    /**
     * Returns the local address used to bind newly created endpoints.
     *
     * @return local bind address
     */
    public InetSocketAddress bindAddress() {
        return bindAddress;
    }

    boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isVersionAvailable(QuicVersion quicVersion) {
        return availableVersions.contains(quicVersion);
    }

    @Override
    public List<QuicVersion> availableVersions() {
        return availableVersions;
    }

    @Override
    public boolean isClient() {
        return true;
    }

    @Override
    public String instanceId() {
        return clientId;
    }

    @Override
    public QuicTLSContext quicTlsContext() {
        return quicTLSContext;
    }

    @Override
    public SSLParameters sslParameters() {
        return tlsConfig.sslParameters();
    }

    @Override
    public QuicConfig quicConfig() {
        return quicConfig;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Executor executor() {
        return executor;
    }

    @Override
    public String appErrorToString(long code) {
        return Objects.requireNonNull(appErrorCodeToString.apply(code), "application error formatter result");
    }

    @Override
    public QuicEndpoint endpoint() {
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("QUIC client runtime is closed");
            }
            int index = insertionPoint;
            if (index >= endpoints.length) {
                index = 0;
            }
            QuicEndpoint endpoint = endpoints[index];
            if (endpoint != null) {
                int endpointConnectionCount = endpointConnectionCounts.getOrDefault(endpoint, 0);
                if (endpoints.length == 1 || endpointConnectionCount < 2) {
                    return endpoint;
                }
                for (int i = 1; i < endpoints.length; i++) {
                    int nextIndex = (index + i) % endpoints.length;
                    QuicEndpoint next = endpoints[nextIndex];
                    int nextConnectionCount = next == null ? Integer.MAX_VALUE : endpointConnectionCounts.getOrDefault(next, 0);
                    if (nextConnectionCount < endpointConnectionCount) {
                        endpoint = next;
                        index = nextIndex;
                        endpointConnectionCount = nextConnectionCount;
                    }
                }
                insertionPoint = ++index >= endpoints.length ? 0 : index;
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "selecting endpoint: %s", endpoint.name());
                }
                return endpoint;
            }

            String endpointName = "QuicEndpoint(" + clientId + "-" + index + ")";
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "adding new endpoint: %s", endpointName);
            }

            QuicSelector<?> candidateSelector = selector;
            boolean newSelector = candidateSelector == null;
            QuicEndpoint candidateEndpoint = null;
            try {
                QuicEndpoint.ChannelType channelType = runtimeConfig.endpoint().channelType();
                if (newSelector) {
                    String selectorName = "QuicSelector(" + clientId + ")";
                    candidateSelector = switch (channelType) {
                        case NON_BLOCKING_WITH_SELECTOR ->
                                QuicSelector.createQuicNioSelector(this, runtimeConfig, selectorName);
                        case BLOCKING_WITH_VIRTUAL_THREADS ->
                                QuicSelector.createQuicVirtualThreadPoller(this, runtimeConfig, selectorName);
                    };
                }
                candidateEndpoint = switch (channelType) {
                    case NON_BLOCKING_WITH_SELECTOR ->
                            endpointFactory.createSelectableEndpoint(this,
                                                                     runtimeConfig,
                                                                     endpointName,
                                                                     bindAddress,
                                                                     candidateSelector.timer());
                    case BLOCKING_WITH_VIRTUAL_THREADS ->
                            endpointFactory.createVirtualThreadedEndpoint(this,
                                                                          runtimeConfig,
                                                                          endpointName,
                                                                          bindAddress,
                                                                          candidateSelector.timer());
                };
                QuicEndpoint.registerWithSelector(candidateEndpoint, candidateSelector);
                candidateSelector.start();

                if (newSelector) {
                    selector = candidateSelector;
                }
                endpoints[index] = candidateEndpoint;
                insertionPoint = index + 1;
                return candidateEndpoint;
            } catch (RuntimeException | Error failure) {
                if (candidateEndpoint != null) {
                    try {
                        candidateEndpoint.close();
                    } catch (Throwable closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
                if (newSelector && candidateSelector != null) {
                    try {
                        candidateSelector.close();
                    } catch (Throwable closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
                throw failure;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void unmatchedQuicPacket(SocketAddress source, QuicPacket.HeadersType type, ByteBuffer buffer) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG,
                "dropping unmatched packet in buffer [%s, %d bytes, %s]",
                type,
                buffer.remaining(),
                source);
        }
    }

    @Override
    public void runtimeFailed(Throwable failure) {
        abort(failure);
    }

    @Override
    public Optional<byte[]> initialTokenFor(InetSocketAddress peerAddress) {
        return initialTokenFor(peerAddress, QuicVersion.firstFlightVersion(availableVersions));
    }

    @Override
    public Optional<byte[]> initialTokenFor(InetSocketAddress peerAddress, QuicVersion version) {
        Objects.requireNonNull(peerAddress, "peerAddress");
        Objects.requireNonNull(version, "version");
        lock.lock();
        try {
            if (closed) {
                return Optional.empty();
            }
            return initialTokenCache.consume(peerAddress, version);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void registerInitialToken(InetSocketAddress peerAddress, byte[] token) {
        registerInitialToken(peerAddress, QuicVersion.firstFlightVersion(availableVersions), token);
    }

    @Override
    public void registerInitialToken(InetSocketAddress peerAddress, QuicVersion version, byte[] token) {
        Objects.requireNonNull(peerAddress, "peerAddress");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(token, "token");
        if (token.length == 0) {
            throw new IllegalArgumentException("Empty token");
        }
        lock.lock();
        try {
            if (!closed) {
                initialTokenCache.register(peerAddress, version, token);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void registerNewToken(InetSocketAddress peerAddress, QuicVersion version, byte[] token) {
        Objects.requireNonNull(peerAddress, "peerAddress");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(token, "token");
        if (token.length == 0) {
            throw new IllegalArgumentException("Empty token");
        }
        lock.lock();
        try {
            if (!closed) {
                initialTokenCache.registerNewToken(peerAddress, version, token);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        List<QuicClientConnection> connectionSnapshot;
        QuicEndpoint[] endpointSnapshot;
        QuicSelector<?> selectorSnapshot;
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            connectionSnapshot = connections.values().stream().map(OwnedConnection::connection).toList();
            connections.clear();
            endpointConnectionCounts.clear();
            endpointSnapshot = endpoints.clone();
            Arrays.fill(endpoints, null);
            selectorSnapshot = selector;
            selector = null;
        } finally {
            lock.unlock();
        }

        if (ownsTlsSessionCache) {
            tlsSessionCache.close();
        }
        if (ownsInitialTokenCache) {
            initialTokenCache.close();
        }
        Throwable cleanupFailure = null;
        for (QuicClientConnection connection : connectionSnapshot) {
            cleanupFailure = collectFailure(
                    cleanupFailure,
                    terminate(connection,
                              QuicCloseCommand.silent("QUIC client runtime closed - no error")));
        }
        for (QuicEndpoint endpoint : endpointSnapshot) {
            if (endpoint != null) {
                try {
                    endpoint.close();
                } catch (Throwable failure) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "failed to close endpoint %s: %s", endpoint.name(), failure);
                    }
                }
            }
        }
        if (selectorSnapshot != null) {
            selectorSnapshot.close();
        }
        if (cleanupFailure != null && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "failed to clean up one or more client connections: %s", cleanupFailure);
        }
    }

    /**
     * Aborts this runtime and all resources it owns after a fatal failure.
     *
     * @param cause abort cause
     */
    public void abort(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        List<QuicClientConnection> connectionSnapshot;
        QuicEndpoint[] endpointSnapshot;
        QuicSelector<?> selectorSnapshot;
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            connectionSnapshot = connections.values().stream().map(OwnedConnection::connection).toList();
            connections.clear();
            endpointConnectionCounts.clear();
            endpointSnapshot = endpoints.clone();
            Arrays.fill(endpoints, null);
            selectorSnapshot = selector;
            selector = null;
        } finally {
            lock.unlock();
        }

        if (ownsTlsSessionCache) {
            tlsSessionCache.close();
        }
        if (ownsInitialTokenCache) {
            initialTokenCache.close();
        }
        for (QuicClientConnection connection : connectionSnapshot) {
            collectFailure(cause, terminate(connection, QuicCloseCommand.transport(cause)));
        }
        for (QuicEndpoint endpoint : endpointSnapshot) {
            if (endpoint != null) {
                try {
                    endpoint.abort(cause);
                } catch (Throwable failure) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "failed to abort endpoint %s: %s", endpoint.name(), failure);
                    }
                }
            }
        }
        if (selectorSnapshot != null) {
            selectorSnapshot.abort(cause);
            selectorSnapshot.close();
        }
    }

    private static Throwable terminate(QuicConnection connection, QuicCloseCommand command) {
        Throwable failure = null;
        try {
            if (connection.isOpen()) {
                connection.terminate(command);
            }
        } catch (Throwable terminationFailure) {
            failure = terminationFailure;
        }
        return failure;
    }

    private static Throwable collectFailure(Throwable current, Throwable next) {
        if (next == null) {
            return current;
        }
        if (current == null) {
            return next;
        }
        if (current != next) {
            current.addSuppressed(next);
        }
        return current;
    }

    private QuicClientConnection createConnectionInternal(InetSocketAddress peerAddress,
                                                          String tlsPeerName,
                                                          int tlsPeerPort,
                                                          String[] applicationProtocols,
                                                          List<SNIServerName> serverNamesOverride) {
        Objects.requireNonNull(peerAddress, "peerAddress");
        Objects.requireNonNull(tlsPeerName, "tlsPeerName");
        Objects.requireNonNull(applicationProtocols, "applicationProtocols");
        if (applicationProtocols.length == 0) {
            throw new IllegalArgumentException("at least one ALPN is needed");
        }

        SSLParameters connectionParameters = tlsConfig.sslParameters();
        connectionParameters.setApplicationProtocols(applicationProtocols);
        if (serverNamesOverride != null) {
            connectionParameters.setServerNames(serverNamesOverride);
        }
        //= https://www.rfc-editor.org/rfc/rfc9001#section-4.2
        //# Clients MUST NOT offer TLS versions older than 1.3.
        connectionParameters.setProtocols(new String[] {"TLSv1.3"});

        long connectionId;
        QuicClientConnection connection;
        OwnedConnection ownedConnection;
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("QUIC client runtime is closed");
            }
            if (tls.generation() != tlsGeneration) {
                throw new IllegalStateException("TLS configuration was reloaded; create a new QUIC client runtime");
            }
            connectionId = CONNECTIONS.incrementAndGet();
            QuicConnectionImpl.ClientConnectionCreation created = QuicConnectionImpl.create(
                    this,
                    runtimeConfig,
                    peerAddress,
                    InetSocketAddress.createUnresolved(tlsPeerName, tlsPeerPort),
                    connectionParameters,
                    initialResponseTimeout,
                    connectionId);
            connection = created.connection();
            QuicEndpoint connectionEndpoint = created.endpoint();
            ownedConnection = new OwnedConnection(connection, connectionEndpoint);
            connections.put(connectionId, ownedConnection);
            endpointConnectionCounts.merge(connectionEndpoint, 1, Integer::sum);
        } finally {
            lock.unlock();
        }
        connection.whenTerminated().whenComplete((_, throwable) -> {
            lock.lock();
            try {
                if (connections.remove(connectionId, ownedConnection)) {
                    endpointConnectionCounts.computeIfPresent(ownedConnection.endpoint(),
                                                              (endpoint, count) -> count == 1 ? null : count - 1);
                }
            } finally {
                lock.unlock();
            }
        });
        try {
            observer.connectionCreated(connection);
        } catch (Throwable observerFailure) {
            if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "[" + name + "] QUIC client observer failed during connection creation",
                           observerFailure);
            }
        }
        return connection;
    }

    private void log(System.Logger.Level level, String format, Object... arguments) {
        LOGGER.log(level,
                   () -> "[" + name + "] "
                           + (arguments.length == 0 ? format : format.formatted(arguments)));
    }

    /**
     * Observer of client connection lifecycle needed by an owning adapter.
     *
     * <p>Callbacks execute on transport or application threads and must not block. The observer is borrowed; closing the
     * runtime does not close it. Observer failures are logged and do not affect the transport connection.
     */
    @FunctionalInterface
    public interface Observer {
        /**
         * Invoked after a connection is registered with this runtime and before its handshake is started.
         *
         * @param connection semantic connection view
         */
        void connectionCreated(QuicConnection connection);
    }

    /**
     * Builder for client-side QUIC runtimes.
     */
    public static final class Builder {
        private String clientId;
        private Executor executor;
        private Tls tls;
        private QuicClientTlsSessionCache tlsSessionCache;
        private QuicClientInitialTokenCache initialTokenCache;
        private InetSocketAddress bindAddress;
        private LongFunction<String> appErrorCodeToString;
        private QuicConfig quicConfig;
        private Duration initialResponseTimeout;
        private Observer observer;

        private Builder() {
        }

        /**
         * Configures application-error descriptions used for diagnostics.
         *
         * @param errorCodeToString error-code mapper that must return a non-null description
         * @return this builder
         */
        public Builder applicationErrors(LongFunction<String> errorCodeToString) {
            this.appErrorCodeToString = Objects.requireNonNull(errorCodeToString, "errorCodeToString");
            return this;
        }

        /**
         * Configures the runtime identifier.
         *
         * @param clientId runtime identifier
         * @return this builder
         */
        public Builder clientId(String clientId) {
            this.clientId = Objects.requireNonNull(clientId, "clientId");
            return this;
        }

        /**
         * Configures client TLS.
         *
         * @param tls TLS configuration
         * @return this builder
         */
        public Builder tls(Tls tls) {
            this.tls = Objects.requireNonNull(tls, "tls");
            return this;
        }

        /**
         * Configures a borrowed client TLS session cache.
         *
         * <p>The runtime uses the cache for every connection it creates and does not close it.
         *
         * @param tlsSessionCache borrowed TLS session cache
         * @return this builder
         */
        @Api.Internal
        public Builder tlsSessionCache(QuicClientTlsSessionCache tlsSessionCache) {
            this.tlsSessionCache = Objects.requireNonNull(tlsSessionCache, "tlsSessionCache");
            return this;
        }

        /**
         * Configures a borrowed Initial-token cache shared across reconnecting runtimes.
         *
         * <p>The runtime does not close a borrowed cache.
         *
         * @param initialTokenCache borrowed Initial-token cache
         * @return this builder
         */
        @Api.Internal
        public Builder initialTokenCache(QuicClientInitialTokenCache initialTokenCache) {
            this.initialTokenCache = Objects.requireNonNull(initialTokenCache, "initialTokenCache");
            return this;
        }

        /**
         * Configures the local UDP bind address.
         *
         * @param bindAddress local bind address
         * @return this builder
         */
        public Builder bindAddress(InetSocketAddress bindAddress) {
            this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
            return this;
        }

        /**
         * Configures the borrowed executor used for transport work.
         *
         * @param executor borrowed executor
         * @return this builder
         */
        public Builder executor(Executor executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        /**
         * Configures QUIC transport behavior.
         *
         * @param quicConfig QUIC configuration
         * @return this builder
         */
        public Builder quicConfig(QuicConfig quicConfig) {
            this.quicConfig = Objects.requireNonNull(quicConfig, "quicConfig");
            return this;
        }

        /**
         * Configures the maximum wait for the peer's first Initial response. The duration must be positive.
         *
         * @param initialResponseTimeout initial-response timeout
         * @return this builder
         */
        public Builder initialResponseTimeout(Duration initialResponseTimeout) {
            this.initialResponseTimeout = Objects.requireNonNull(initialResponseTimeout, "initialResponseTimeout");
            return this;
        }

        /**
         * Configures the borrowed client connection observer.
         *
         * @param observer connection observer
         * @return this builder
         */
        public Builder observer(Observer observer) {
            this.observer = Objects.requireNonNull(observer, "observer");
            return this;
        }

        /**
         * Builds a one-shot client runtime.
         *
         * @return configured runtime
         */
        public QuicClientRuntime build() {
            return new QuicClientRuntime(this);
        }
    }

    private record OwnedConnection(QuicClientConnection connection, QuicEndpoint endpoint) {
    }
}
