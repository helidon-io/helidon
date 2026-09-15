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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongFunction;

import javax.net.ssl.SSLParameters;

import io.helidon.common.Api;
import io.helidon.common.tls.Tls;
import io.helidon.quic.QuicEndpoint.QuicEndpointFactory;
import io.helidon.quic.packet.QuicPacket;

/**
 * One-shot owner of server-side QUIC transport resources.
 *
 * <p>The runtime owns its UDP endpoint, selector or poller, timer queue, logical connection registry, and pending accept
 * operations. The configured executor and observer are borrowed and are never closed by this runtime. Closing or aborting
 * a runtime is terminal.
 */
@Api.Internal
public final class QuicServerRuntime implements QuicInstance, AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(QuicServerRuntime.class.getName());
    private static final AtomicLong IDS = new AtomicLong();
    private static final AtomicLong CONNECTIONS = new AtomicLong();
    private static final ScopedValue<StopAcceptingCleanupScope> STOP_ACCEPTING_CLEANUP = ScopedValue.newInstance();
    private static final ScopedValue<UnpublishedAdmissionReleaseScope> UNPUBLISHED_ADMISSION_RELEASE =
            ScopedValue.newInstance();
    static final Duration DEFAULT_HANDSHAKE_TIMEOUT = Duration.ofSeconds(10);
    static final int DEFAULT_MAX_PENDING_HANDSHAKES = 256;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition connectionSetupChanged = lock.newCondition();
    private final ReentrantLock tlsStateLock = new ReentrantLock();
    private final AtomicReference<QuicEndpoint> closingEndpoint = new AtomicReference<>();
    private final AtomicReference<QuicSelector<?>> closingSelector = new AtomicReference<>();
    private final AtomicReference<Thread> closePreparationOwner = new AtomicReference<>();
    private final CompletableFuture<Throwable> closePreparation = new CompletableFuture<>();
    private final Queue<QuicConnectionImpl> acceptedConnections = new ArrayDeque<>();
    private final Queue<CompletableFuture<QuicConnection>> pendingAccepts = new ArrayDeque<>();
    private final Map<QuicConnectionImpl, OwnedConnection> connections = new IdentityHashMap<>();
    private final Map<Tls, TlsState> tlsStates = new IdentityHashMap<>();
    private final QuicEndpointFactory endpointFactory = QuicEndpointFactory.create();
    private final String serverId;
    private final String name;
    private final String objectId = identityTag(this);
    private final Executor executor;
    private final List<QuicVersion> availableVersions;
    private final InetSocketAddress bindAddress;
    private final QuicConfig quicConfig;
    private final QuicRuntimeConfig runtimeConfig;
    private final Tls tls;
    private final TlsSelector tlsSelector;
    private final List<String> applicationProtocols;
    private final LongFunction<String> appErrorCodeToString;
    private final Observer observer;
    private final ConnectionAdmission connectionAdmission;
    private final Duration handshakeTimeout;
    private final QuicServerHandshakeAdmission handshakeAdmission;
    private final boolean retryEnabled;
    private final QuicAddressTokenService tokenService;
    private final QuicServerIngress ingress;

    private volatile QuicSelector<?> selector;
    private volatile QuicEndpoint endpoint;
    private volatile TlsState currentTlsState;
    private volatile boolean accepting = true;
    private volatile boolean closed;
    private int initializingConnections;
    private boolean stoppingAccepting;

    private QuicServerRuntime(Builder builder) {
        Objects.requireNonNull(builder, "QUIC server runtime builder");
        if (builder.tls == null) {
            throw new IllegalStateException("No TLS set");
        }
        this.tls = builder.tls;
        this.tlsSelector = builder.tlsSelector;
        this.applicationProtocols = List.copyOf(builder.applicationProtocols);
        this.serverId = builder.serverId == null ? "quic-server-" + IDS.incrementAndGet() : builder.serverId;
        this.name = "QuicServerRuntime(%s)".formatted(serverId);
        this.appErrorCodeToString = builder.appErrorCodeToString == null
                ? QuicInstance.super::appErrorToString
                : Objects.requireNonNull(builder.appErrorCodeToString, "application error formatter");
        this.observer = builder.observer == null ? new Observer() { } : builder.observer;
        this.connectionAdmission = builder.connectionAdmission == null
                ? () -> UnlimitedConnectionPermit.INSTANCE
                : builder.connectionAdmission;
        this.handshakeTimeout = builder.handshakeTimeout;
        if (handshakeTimeout.isNegative() || handshakeTimeout.isZero()) {
            throw new IllegalArgumentException("handshakeTimeout must be positive: " + handshakeTimeout);
        }
        if (builder.maxPendingHandshakes < 1) {
            throw new IllegalArgumentException("maxPendingHandshakes must be greater than 0: "
                                                       + builder.maxPendingHandshakes);
        }
        this.handshakeAdmission = new QuicServerHandshakeAdmission(builder.maxPendingHandshakes,
                                                                   handshakeTimeout,
                                                                   this::dispatchHandshakeTimeout);
        this.quicConfig = builder.quicConfig == null ? QuicConfig.create() : builder.quicConfig;
        this.runtimeConfig = QuicRuntimeConfig.create(quicConfig);
        List<QuicVersion> configuredAvailableVersions = quicConfig.availableVersions();
        if (configuredAvailableVersions.isEmpty()) {
            throw new IllegalStateException("Need at least one available QUIC version");
        }
        this.availableVersions = List.copyOf(configuredAvailableVersions);
        this.retryEnabled = builder.retryEnabled;
        this.bindAddress = builder.bindAddress == null ? new InetSocketAddress(0) : builder.bindAddress;
        this.executor = Objects.requireNonNull(builder.executor, "executor");
        QuicAddressTokenService candidateTokenService = null;
        QuicServerIngress candidateIngress;
        try {
            tlsState();
            candidateTokenService = retryEnabled ? QuicAddressTokenService.create() : null;
            candidateIngress = new QuicServerIngress(availableVersions, retryEnabled, candidateTokenService);
        } catch (RuntimeException | Error e) {
            if (candidateTokenService != null) {
                try {
                    candidateTokenService.close();
                } catch (RuntimeException | Error closeFailure) {
                    e.addSuppressed(closeFailure);
                }
            }
            try {
                closeTlsState();
            } catch (RuntimeException | Error closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
        this.tokenService = candidateTokenService;
        this.ingress = candidateIngress;
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
     * Starts this runtime if it has not yet been started.
     *
     * @throws java.io.UncheckedIOException if the UDP endpoint or selector cannot be initialized
     * @throws IllegalStateException if this runtime has already been closed or failed
     */
    public void start() {
        QuicEndpoint candidateEndpoint = null;
        QuicSelector<?> candidateSelector = null;
        Throwable failure = null;
        List<CompletableFuture<QuicConnection>> failedAccepts = List.of();
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("QUIC server runtime is closed");
            }
            if (endpoint != null) {
                return;
            }
            try {
                QuicEndpoint.ChannelType channelType = runtimeConfig.endpoint().channelType();
                String selectorName = "QuicSelector(" + serverId + ")";
                candidateSelector = switch (channelType) {
                    case NON_BLOCKING_WITH_SELECTOR ->
                            QuicSelector.createQuicNioSelector(this, runtimeConfig, selectorName);
                    case BLOCKING_WITH_VIRTUAL_THREADS ->
                            QuicSelector.createQuicVirtualThreadPoller(this, runtimeConfig, selectorName);
                };
                String endpointName = "QuicEndpoint(" + serverId + ")";
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
                endpoint = candidateEndpoint;
                selector = candidateSelector;
                candidateEndpoint = null;
                candidateSelector = null;
            } catch (RuntimeException | Error t) {
                closePreparationOwner.set(Thread.currentThread());
                accepting = false;
                closed = true;
                closeTlsState();
                if (tokenService != null) {
                    tokenService.close();
                }
                failedAccepts = drainPendingAccepts();
                failure = t;
            }
        } finally {
            lock.unlock();
        }
        if (failure == null) {
            return;
        }
        Thread currentThread = Thread.currentThread();
        try {
            closeEndpoint(candidateEndpoint, failure);
            closeSelector(candidateSelector, failure);
            failAccepts(failedAccepts, failure);
        } finally {
            closePreparation.complete(failure);
            closePreparationOwner.compareAndSet(currentThread, null);
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw (Error) failure;
    }

    /**
     * Returns the configured local bind address.
     *
     * @return configured bind address
     */
    public InetSocketAddress bindAddress() {
        return bindAddress;
    }

    /**
     * Starts this runtime if necessary and returns the bound local address.
     *
     * @return bound local address
     * @throws java.io.UncheckedIOException if the endpoint cannot be initialized or queried
     */
    public InetSocketAddress localAddress() {
        start();
        return (InetSocketAddress) endpoint().localAddress();
    }

    /**
     * Accepts the next established QUIC connection.
     *
     * <p>Canceling the returned future cancels only that accept operation; a later accepted connection is offered to the
     * next live waiter or retained by this runtime.
     *
     * @return future completed with the next accepted connection
     */
    public CompletableFuture<QuicConnection> accept() {
        CompletableFuture<QuicConnection> result = MinimalFuture.create();
        QuicConnection accepted = null;
        IllegalStateException failure = null;
        boolean queued = false;
        lock.lock();
        try {
            if (closed) {
                failure = new IllegalStateException("QUIC server runtime is closed");
            } else if (!accepting) {
                failure = new IllegalStateException("QUIC server runtime is not accepting new connections");
            } else {
                accepted = acceptedConnections.poll();
                if (accepted == null) {
                    pendingAccepts.add(result);
                    queued = true;
                }
            }
        } finally {
            lock.unlock();
        }
        if (queued) {
            result.whenComplete((_, throwable) -> {
                if (result.isCancelled()) {
                    lock.lock();
                    try {
                        pendingAccepts.remove(result);
                    } finally {
                        lock.unlock();
                    }
                }
            });
            return result;
        }
        if (accepted != null) {
            result.complete(accepted);
        } else {
            result.completeExceptionally(failure);
        }
        return result;
    }

    /**
     * Returns whether this runtime accepts new connections.
     *
     * @return {@code true} when accepting
     */
    public boolean accepting() {
        return accepting;
    }

    boolean isClosed() {
        return closed;
    }
    /**
     * Suspends accepting new connections and rejects queued accepts.
     *
     * <p>Synchronous completion actions and connection callbacks triggered by rejection must return promptly. They must not
     * synchronously wait for another thread invoking a lifecycle operation on this runtime. A direct reentrant call to this
     * method from such a callback is an idempotent no-op.
     *
     * @throws IllegalStateException if called from an unpublished connection-permit release callback
     */
    public void stopAccepting() {
        rejectLifecycleReentry("stop accepting connections");
        if (inStopAcceptingCleanup()) {
            return;
        }
        List<CompletableFuture<QuicConnection>> accepts;
        List<QuicConnectionImpl> queuedConnections;
        lock.lock();
        try {
            while (stoppingAccepting) {
                connectionSetupChanged.awaitUninterruptibly();
            }
            if (closed || !accepting) {
                return;
            }
            accepting = false;
            stoppingAccepting = true;
            while (initializingConnections != 0 && !closed) {
                connectionSetupChanged.awaitUninterruptibly();
            }
            if (closed) {
                stoppingAccepting = false;
                connectionSetupChanged.signalAll();
                return;
            }
            accepts = drainPendingAccepts();
            queuedConnections = drainAcceptedConnections();
        } finally {
            lock.unlock();
        }
        StopAcceptingCleanupScope cleanupScope =
                new StopAcceptingCleanupScope(this, currentStopAcceptingCleanup());
        Throwable cleanupFailure = null;
        Throwable deferredAbort = null;
        RuntimeSnapshot abortSnapshot = null;
        try {
            ScopedValue.where(STOP_ACCEPTING_CLEANUP, cleanupScope)
                    .run(() -> {
                        IllegalStateException failure =
                                new IllegalStateException("QUIC server runtime is not accepting new connections");
                        try {
                            failAccepts(accepts, failure);
                        } catch (RuntimeException | Error callbackFailure) {
                            cleanupScope.cleanupFailed(callbackFailure);
                        }
                        for (QuicConnectionImpl connection : queuedConnections) {
                            cleanupScope.cleanupFailed(
                                    terminate(connection,
                                              QuicCloseCommand.transport(
                                                      QuicTransportErrors.NO_ERROR,
                                                      "QUIC server runtime is not accepting new connections")));
                        }
                    });
            cleanupFailure = cleanupScope.cleanupFailure();
            deferredAbort = cleanupScope.deferredAbort();
            if (deferredAbort != null) {
                deferredAbort = collectFailure(deferredAbort, cleanupFailure);
                cleanupFailure = null;
                abortSnapshot = seal(true);
            }
        } finally {
            lock.lock();
            try {
                stoppingAccepting = false;
                connectionSetupChanged.signalAll();
            } finally {
                lock.unlock();
            }
        }
        if (deferredAbort != null) {
            try {
                abort(deferredAbort, abortSnapshot);
            } catch (RuntimeException | Error abortFailure) {
                if (deferredAbort != abortFailure) {
                    abortFailure.addSuppressed(deferredAbort);
                }
                throw abortFailure;
            }
        }
        if (cleanupFailure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (cleanupFailure instanceof Error error) {
            throw error;
        }
        if (cleanupFailure != null) {
            throw new IllegalStateException("QUIC server stop cleanup failed", cleanupFailure);
        }
    }

    /**
     * Resumes accepting new connections on this running runtime.
     *
     * @throws IllegalStateException if this runtime has been closed or failed, or if called from an unpublished
     *                               connection-permit release callback or synchronous stop-rejection callback
     */
    public void resumeAccepting() {
        rejectLifecycleReentry("resume accepting connections");
        rejectStopAcceptingCleanupReentry("resume accepting connections");
        lock.lock();
        try {
            while (stoppingAccepting) {
                connectionSetupChanged.awaitUninterruptibly();
            }
            if (closed) {
                throw new IllegalStateException("QUIC server runtime is closed");
            }
            accepting = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Configures application-error descriptions on a connection owned by this runtime.
     *
     * @param connection owned connection
     * @param formatter application-error formatter that must return a non-null description
     */
    public void applicationErrors(QuicConnection connection, LongFunction<String> formatter) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(formatter, "formatter");
        QuicConnectionImpl implementation = null;
        lock.lock();
        try {
            OwnedConnection owned = connections.get(connection);
            if (owned != null) {
                implementation = owned.connection();
            }
        } finally {
            lock.unlock();
        }
        if (implementation == null) {
            throw new IllegalArgumentException("Connection is not owned by this QUIC server runtime");
        }
        implementation.applicationErrors(formatter);
    }

    @Override
    public Executor executor() {
        return executor;
    }

    @Override
    public QuicConfig quicConfig() {
        return quicConfig;
    }

    @Override
    public QuicEndpoint endpoint() {
        QuicEndpoint current = endpoint;
        if (current != null) {
            return current;
        }
        start();
        current = endpoint;
        if (current == null) {
            throw new IllegalStateException("QUIC server runtime endpoint is unavailable");
        }
        return current;
    }

    @Override
    public void unmatchedQuicPacket(SocketAddress source, QuicPacket.HeadersType type, ByteBuffer buffer) {
        if (!accepting || !(source instanceof InetSocketAddress peerAddress) || type != QuicPacket.HeadersType.LONG) {
            return;
        }
        QuicEndpoint currentEndpoint = endpoint;
        if (currentEndpoint == null) {
            return;
        }
        QuicServerIngress.Action action = ingress.inspect(peerAddress, currentEndpoint.idFactory(), buffer);
        if (action instanceof QuicServerIngress.Respond response) {
            try {
                currentEndpoint.sendStatelessDatagram(peerAddress, response.datagram());
            } catch (RuntimeException e) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                               "[" + logTag() + "] Failed to send stateless QUIC response", e);
                }
            }
            return;
        }
        if (!(action instanceof QuicServerIngress.Admit admit)) {
            return;
        }
        ByteBuffer packet = buffer.duplicate();
        var longHeader = admit.header();
        QuicVersion quicVersion = admit.version();
        long handshakeReservation = handshakeAdmission.tryReserve();
        if (handshakeReservation == QuicServerHandshakeAdmission.NO_RESERVATION) {
            return;
        }
        ConnectionPermit connectionPermit;
        try {
            connectionPermit =
                    Objects.requireNonNull(connectionAdmission.tryAcquire(), "QUIC server connection admission result");
            if (!connectionPermit.accepted()) {
                handshakeAdmission.releaseReservation(handshakeReservation);
                return;
            }
        } catch (Throwable t) {
            handshakeAdmission.releaseReservation(handshakeReservation);
            if (LOGGER.isLoggable(System.Logger.Level.ERROR)) {
                LOGGER.log(System.Logger.Level.ERROR,
                           "[" + logTag() + "] QUIC server connection admission failed",
                           t);
            }
            return;
        }
        var handshakePermit = handshakeAdmission.materializePermit(handshakeReservation, connectionPermit);
        boolean setupStarted;
        lock.lock();
        try {
            setupStarted = !closed && accepting && endpoint == currentEndpoint;
            if (setupStarted) {
                initializingConnections++;
            }
        } finally {
            lock.unlock();
        }
        if (!setupStarted) {
            releaseAdmission(handshakePermit);
            return;
        }
        QuicServerConnection connection = null;
        OwnedConnection ownedConnection = null;
        boolean cleanupCallbackInstalled = false;
        try {
            long connectionId = CONNECTIONS.incrementAndGet();
            TlsState tlsState = tlsState();
            AtomicReference<QuicConnection> selectingConnection = tlsSelector == null ? null : new AtomicReference<>();
            QuicTLSContext tlsContext = tlsState.quicTLSContext();
            if (selectingConnection != null) {
                tlsContext = tlsContext.withServerTlsSelector(requestedServerName -> {
                    var currentConnection = Objects.requireNonNull(selectingConnection.get(), "selectingConnection");
                    Tls selectedTls;
                    try {
                        selectedTls = requestedServerName.isPresent()
                                ? tlsSelector.select(currentConnection, requestedServerName.orElseThrow())
                                : tlsSelector.selectWithoutSni(currentConnection);
                    } catch (RejectedTlsSelectionException e) {
                        if (e.sendUnrecognizedNameAlert()) {
                            throw QuicTlsHandshakeMessages.unrecognizedName(e.getMessage(), e);
                        }
                        throw new QuicSilentTlsRejection(e);
                    }
                    TlsState selectedState = tlsState(Objects.requireNonNull(selectedTls, "selectedTls"));
                    return new QuicTlsServerSelector.Selection(selectedState.tlsConfig(),
                                                               connectionSslParameters(selectedState),
                                                               selectedState.sessionCache());
                });
            }
            connection = new QuicServerConnection(quicVersion, this, runtimeConfig, peerAddress,
                                                  connectionSslParameters(tlsState), tlsContext, connectionId);
            if (selectingConnection != null) {
                selectingConnection.set(connection);
            }
            handshakePermit.connection(connection);
            ownedConnection = new OwnedConnection(connection, handshakePermit);
            QuicServerConnection createdConnection = connection;
            OwnedConnection createdOwnedConnection = ownedConnection;
            createdConnection.cleanupComplete()
                    .whenComplete((_, failure) -> connectionTerminated(createdOwnedConnection, failure));
            cleanupCallbackInstalled = true;
            connection.initialize(longHeader.destinationId(), admit.originalDestinationId(), admit.retrySourceId(),
                                  admit.token(), admit.addressValidated());
            boolean published = false;
            lock.lock();
            try {
                if (!closed && endpoint == currentEndpoint && connection.isOpen() && ownedConnection.setupInProgress()) {
                    connections.put(connection, ownedConnection);
                    ownedConnection.completeSetup();
                    completeConnectionSetupLocked();
                    published = true;
                }
            } finally {
                lock.unlock();
            }
            if (!published) {
                connection.terminate(QuicCloseCommand.silent("QUIC server connection setup was cancelled"));
                return;
            }
            try {
                observer.connectionCreated(createdConnection);
            } catch (Throwable observerFailure) {
                observerFailed("connection creation", observerFailure);
            }
            if (!handshakePermit.arm(currentEndpoint.timer())) {
                return;
            }
            currentEndpoint.registerNewConnection(connection);
            connection.processIncoming(peerAddress, longHeader.destinationId().asReadOnlyBuffer(), type, packet);
        } catch (Throwable t) {
            if (LOGGER.isLoggable(System.Logger.Level.ERROR)) {
                LOGGER.log(System.Logger.Level.ERROR,
                           "[" + logTag() + "] Failed to accept incoming QUIC connection", t);
            }
            if (connection != null) {
                try {
                    connection.terminate(QuicCloseCommand.transport(t));
                } finally {
                    if (!cleanupCallbackInstalled) {
                        releaseUnpublishedAdmission(handshakePermit);
                        completeConnectionSetup();
                    }
                }
            } else {
                releaseUnpublishedAdmission(handshakePermit);
                completeConnectionSetup();
            }
        }
    }

    private void completeConnectionSetup() {
        lock.lock();
        try {
            completeConnectionSetupLocked();
        } finally {
            lock.unlock();
        }
    }

    private void completeConnectionSetupLocked() {
        if (initializingConnections == 0) {
            throw new IllegalStateException("No QUIC server connection setup is active");
        }
        initializingConnections--;
        if (initializingConnections == 0) {
            connectionSetupChanged.signalAll();
        }
    }

    @Override
    public void runtimeFailed(Throwable failure) {
        abortRuntime(failure);
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
        return false;
    }

    @Override
    public String instanceId() {
        return serverId;
    }

    @Override
    public QuicTLSContext quicTlsContext() {
        return tlsState().quicTLSContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return connectionSslParameters(tlsState());
    }

    @Override
    public String appErrorToString(long code) {
        return Objects.requireNonNull(appErrorCodeToString.apply(code), "application error formatter result");
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Returns the log tag for this runtime's UDP channel.
     *
     * @return runtime log tag
     */
    public String logTag() {
        QuicEndpoint currentEndpoint = endpoint;
        return currentEndpoint == null ? objectId : identityTag(currentEndpoint.channel());
    }

    /**
     * Closes this runtime.
     *
     * @throws IllegalStateException if called from an unpublished connection-permit release callback or synchronous
     *                               stop-rejection callback
     */
    @Override
    public void close() {
        rejectLifecycleReentry("close the runtime");
        rejectStopAcceptingCleanupReentry("close the runtime");
        close(Long.MAX_VALUE);
    }

    /**
     * Closes this runtime and waits up to the supplied timeout for selector termination.
     *
     * <p>A negative timeout is treated as zero. Calling this method from an unpublished connection-permit release callback
     * or synchronous stop-rejection callback returns {@code false} without changing runtime lifecycle state.
     *
     * @param timeout maximum selector-cleanup wait
     * @return {@code true} if selector termination was observed within the allotted wait;
     *         {@code false} if the wait expired during this or a concurrent close, was interrupted,
     *         was skipped to avoid a self-wait, or cleanup failed
     */
    public boolean close(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (inUnpublishedAdmissionRelease() || inStopAcceptingCleanup()) {
            return false;
        }
        if (timeout.isNegative()) {
            return close(0);
        }
        try {
            return close(timeout.toNanos());
        } catch (ArithmeticException e) {
            return close(Long.MAX_VALUE);
        }
    }

    private boolean close(long timeoutNanos) {
        long startedAt = System.nanoTime();
        RuntimeSnapshot snapshot = seal(false);
        QuicSelector<?> selectorToClose = closingSelector.get();
        if (snapshot != null) {
            Thread currentThread = Thread.currentThread();
            Throwable preparationFailure = closeRuntimeState(null);
            try {
                IllegalStateException closedFailure = new IllegalStateException("QUIC server runtime is closed");
                failAccepts(snapshot.pendingAccepts(), closedFailure);
                for (QuicConnectionImpl connection : snapshot.acceptedConnections()) {
                    preparationFailure = collectFailure(
                            preparationFailure,
                            terminate(connection,
                                      QuicCloseCommand.transport(QuicTransportErrors.NO_ERROR,
                                                                 "QUIC server runtime is not accepting new connections")));
                }
                for (OwnedConnection owned : snapshot.connections()) {
                    preparationFailure = collectFailure(
                            preparationFailure,
                            terminate(owned.connection(),
                                      QuicCloseCommand.silent("QUIC server runtime closed - no error")));
                }
                preparationFailure = closeEndpoint(snapshot.endpoint(), preparationFailure);
                if (snapshot.endpoint() == null || snapshot.endpoint().isClosed()) {
                    closingEndpoint.compareAndSet(snapshot.endpoint(), null);
                }
            } catch (RuntimeException | Error unexpectedFailure) {
                preparationFailure = collectFailure(preparationFailure, unexpectedFailure);
            } finally {
                observeConnectionTermination(snapshot.connections(), preparationFailure);
                closePreparationOwner.compareAndSet(currentThread, null);
            }
        }
        if (!closePreparation.isDone()) {
            if (closePreparationOwner.get() == Thread.currentThread()) {
                return false;
            }
            if (ConnectionTerminatorImpl.isCompletingTermination(this)) {
                if (selectorToClose != null) {
                    abortSelector(selectorToClose,
                                  new IllegalStateException("QUIC server close cannot wait for connection termination "
                                                                    + "from its completion callback"));
                }
                return false;
            }
            if (selectorToClose != null && !selectorToClose.canAwaitTermination()) {
                abortSelector(selectorToClose,
                              new IllegalStateException("QUIC server close cannot wait for its own selector"));
                return false;
            }
        }
        Throwable cleanupFailure;
        long elapsed = Math.max(0, System.nanoTime() - startedAt);
        long remainingNanos = elapsed >= timeoutNanos ? 0 : timeoutNanos - elapsed;
        try {
            cleanupFailure = timeoutNanos == Long.MAX_VALUE
                    ? closePreparation.get()
                    : closePreparation.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (selectorToClose != null) {
                abortSelector(selectorToClose,
                              new IllegalStateException("Interrupted while closing QUIC server runtime", e));
            }
            return false;
        } catch (ExecutionException e) {
            cleanupFailure = e.getCause();
        } catch (TimeoutException e) {
            if (selectorToClose != null) {
                abortSelector(selectorToClose,
                              new IllegalStateException("QUIC server cleanup did not complete before the close deadline",
                                                        e));
            }
            return false;
        }
        if (selectorToClose == null) {
            return cleanupFailure == null;
        }
        boolean selectorTerminated = true;
        Throwable selectorFailure = null;
        elapsed = Math.max(0, System.nanoTime() - startedAt);
        remainingNanos = elapsed >= timeoutNanos ? 0 : timeoutNanos - elapsed;
        try {
            selectorTerminated = selectorToClose.close(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (RuntimeException | Error closeFailure) {
            selectorTerminated = false;
            selectorFailure = closeFailure;
        }
        if (selectorTerminated) {
            closingSelector.compareAndSet(selectorToClose, null);
        } else {
            IllegalStateException timeoutFailure =
                    new IllegalStateException("QUIC server selector did not terminate before the close deadline");
            selectorFailure = collectFailure(selectorFailure, abortSelector(selectorToClose, timeoutFailure));
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            if (cleanupFailure != null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           "Failed to clean up QUIC server runtime resources",
                           cleanupFailure);
            }
            if (selectorFailure != null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           "Failed to terminate QUIC server selector within the close deadline",
                           selectorFailure);
            }
        }
        return selectorTerminated && cleanupFailure == null;
    }

    /**
     * Aborts this runtime after a fatal transport failure.
     *
     * @param failure fatal failure
     * @throws IllegalStateException if called from an unpublished connection-permit release callback or synchronous
     *                               stop-rejection callback
     */
    public void abort(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        rejectLifecycleReentry("abort the runtime");
        rejectStopAcceptingCleanupReentry("abort the runtime");
        abort(failure, seal(false));
    }

    private void abort(Throwable failure, RuntimeSnapshot snapshot) {
        QuicEndpoint endpointToAbort = snapshot == null ? closingEndpoint.get() : snapshot.endpoint();
        QuicSelector<?> selectorToAbort = snapshot == null ? closingSelector.get() : snapshot.selector();
        if (snapshot == null && endpointToAbort == null && selectorToAbort == null) {
            return;
        }
        Thread currentThread = Thread.currentThread();
        try {
            if (snapshot != null) {
                closeRuntimeState(failure);
                failAccepts(snapshot.pendingAccepts(), failure);
                for (QuicConnectionImpl queued : snapshot.acceptedConnections()) {
                    collectFailure(failure, terminate(queued, QuicCloseCommand.transport(failure)));
                }
                for (OwnedConnection owned : snapshot.connections()) {
                    collectFailure(failure, terminate(owned.connection(), QuicCloseCommand.transport(failure)));
                }
            }
            if (endpointToAbort != null) {
                try {
                    endpointToAbort.abort(failure);
                } catch (Throwable abortFailure) {
                    failure.addSuppressed(abortFailure);
                }
            }
            if (selectorToAbort != null) {
                abortSelector(selectorToAbort, failure);
                if (closeSelector(selectorToAbort, failure)) {
                    closingSelector.compareAndSet(selectorToAbort, null);
                }
            }
        } finally {
            if (endpointToAbort != null && endpointToAbort.isClosed()) {
                closingEndpoint.compareAndSet(endpointToAbort, null);
            }
            if (snapshot != null) {
                observeConnectionTermination(snapshot.connections(), failure);
                closePreparationOwner.compareAndSet(currentThread, null);
            }
        }
    }

    boolean handshakeSucceeded(QuicConnection connection) {
        OwnedConnection ownedConnection;
        QuicServerHandshakeAdmission.Establishment establishment;
        lock.lock();
        try {
            ownedConnection = connections.get(connection);
            establishment = ownedConnection == null || !connection.isOpen()
                    ? QuicServerHandshakeAdmission.Establishment.LOST
                    : ownedConnection.handshakePermit().claimEstablishment();
        } finally {
            lock.unlock();
        }
        if (establishment == QuicServerHandshakeAdmission.Establishment.EXPIRED) {
            try {
                dispatchHandshakeTimeout(ownedConnection.handshakePermit());
            } finally {
                ownedConnection.handshakePermit().completeEstablishment(establishment);
            }
            return false;
        }
        if (ownedConnection != null) {
            ownedConnection.handshakePermit().completeEstablishment(establishment);
        }
        if (establishment != QuicServerHandshakeAdmission.Establishment.ESTABLISHED) {
            return false;
        }
        try {
            observer.handshakeSucceeded(connection);
        } catch (Throwable observerFailure) {
            observerFailed("handshake completion", observerFailure);
        }
        return true;
    }

    Optional<byte[]> newToken(InetSocketAddress peerAddress, QuicVersion version) {
        if (!retryEnabled || closed) {
            return Optional.empty();
        }
        return tokenService.newToken(peerAddress, version);
    }

    void acceptedConnection(QuicConnectionImpl connection) {
        for (;;) {
            CompletableFuture<QuicConnection> waiter;
            boolean reject;
            lock.lock();
            try {
                if (closed || !accepting) {
                    waiter = null;
                    reject = true;
                } else {
                    OwnedConnection owned = connections.get(connection);
                    if (owned == null || !connection.isOpen()) {
                        waiter = null;
                        reject = true;
                    } else {
                        reject = false;
                        waiter = pendingAccepts.poll();
                        if (waiter == null) {
                            acceptedConnections.add(connection);
                            return;
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
            if (reject) {
                rejectAcceptedConnection(connection);
                return;
            }
            if (waiter.complete(connection)) {
                return;
            }
        }
    }

    private RuntimeSnapshot seal(boolean stopCleanupOwner) {
        lock.lock();
        try {
            if (closed) {
                while (stoppingAccepting && !stopCleanupOwner) {
                    connectionSetupChanged.awaitUninterruptibly();
                }
                return null;
            }
            closePreparationOwner.set(Thread.currentThread());
            accepting = false;
            closed = true;
            closingEndpoint.set(endpoint);
            closingSelector.set(selector);
            connectionSetupChanged.signalAll();
            while (initializingConnections != 0) {
                connectionSetupChanged.awaitUninterruptibly();
            }
            RuntimeSnapshot snapshot = new RuntimeSnapshot(drainPendingAccepts(),
                                                           drainAcceptedConnections(),
                                                           List.copyOf(connections.values()),
                                                           endpoint,
                                                           selector);
            connections.clear();
            endpoint = null;
            selector = null;
            while (stoppingAccepting && !stopCleanupOwner) {
                connectionSetupChanged.awaitUninterruptibly();
            }
            return snapshot;
        } finally {
            lock.unlock();
        }
    }

    private Throwable closeRuntimeState(Throwable failure) {
        try {
            closeTlsState();
        } catch (RuntimeException | Error closeFailure) {
            failure = collectFailure(failure, closeFailure);
        }
        if (tokenService != null) {
            try {
                tokenService.close();
            } catch (RuntimeException | Error closeFailure) {
                failure = collectFailure(failure, closeFailure);
            }
        }
        return failure;
    }

    private void dispatchHandshakeTimeout(QuicServerHandshakeAdmission.Permit permit) {
        QuicConnection connection = permit.takeConnection();
        if (connection == null) {
            return;
        }
        Runnable cleanup = () -> {
            TimeoutException timeout = new TimeoutException("QUIC server handshake timed out after " + handshakeTimeout);
            Throwable cleanupFailure =
                    terminate(connection, QuicCloseCommand.serverHandshakeTimeout(timeout, timeout.getMessage()));
            if (cleanupFailure != null) {
                IllegalStateException failure =
                        new IllegalStateException("QUIC server handshake timeout cleanup failed", cleanupFailure);
                try {
                    abortRuntime(failure);
                } catch (Throwable abortFailure) {
                    failure.addSuppressed(abortFailure);
                    LOGGER.log(System.Logger.Level.ERROR,
                               "[" + logTag() + "] Failed to abort after QUIC server handshake timeout cleanup failure",
                               failure);
                }
            }
        };
        try {
            executor.execute(cleanup);
        } catch (RuntimeException | Error dispatchFailure) {
            if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "[" + logTag() + "] QUIC server handshake timeout dispatch failed; cleaning up inline",
                           dispatchFailure);
            }
            cleanup.run();
        }
    }

    private void connectionTerminated(OwnedConnection ownedConnection, Throwable failure) {
        QuicServerHandshakeAdmission.Permit handshakePermit = ownedConnection.handshakePermit();
        QuicServerHandshakeAdmission.ReleaseClaim release = handshakePermit.claimRelease();
        boolean setupInProgress;
        lock.lock();
        try {
            setupInProgress = ownedConnection.setupInProgress();
            connections.remove(ownedConnection.connection(), ownedConnection);
            acceptedConnections.remove(ownedConnection.connection());
        } finally {
            lock.unlock();
        }
        if (setupInProgress) {
            if (release == null) {
                completeConnectionSetup(ownedConnection);
                abortRuntime(
                        new IllegalStateException("Unpublished QUIC server connection lost admission release ownership"));
                return;
            }
            ownedConnection.connection().routeLifecycle().whenRemoved()
                    .whenComplete((_, routeFailure) -> {
                        try {
                            completeUnpublishedAdmissionRelease(handshakePermit, release);
                        } finally {
                            completeConnectionSetup(ownedConnection);
                        }
                        Throwable cleanupFailure = collectFailure(failure, routeFailure);
                        if (cleanupFailure != null) {
                            String message = failure == null
                                    ? "QUIC server endpoint route cleanup failed"
                                    : "QUIC server connection cleanup failed";
                            abortRuntime(new IllegalStateException(message, cleanupFailure));
                        }
                    });
            return;
        }
        if (release == null) {
            return;
        }
        try {
            if (failure != null) {
                IllegalStateException cleanupFailure =
                        new IllegalStateException("QUIC server connection cleanup failed", failure);
                abortRuntime(cleanupFailure);
            }
        } finally {
            if (release.established()) {
                completeAdmissionRelease(handshakePermit, release);
            } else {
                ownedConnection.connection().routeLifecycle().whenRemoved()
                        .whenComplete((_, routeFailure) -> {
                            try {
                                if (routeFailure != null) {
                                    abortRuntime(
                                            new IllegalStateException("QUIC server endpoint route cleanup failed",
                                                                      routeFailure));
                                }
                            } finally {
                                completeAdmissionRelease(handshakePermit, release);
                            }
                        });
            }
        }
    }

    private void completeConnectionSetup(OwnedConnection ownedConnection) {
        lock.lock();
        try {
            if (ownedConnection.completeSetup()) {
                completeConnectionSetupLocked();
            }
        } finally {
            lock.unlock();
        }
    }

    private void releaseAdmission(QuicServerHandshakeAdmission.Permit permit) {
        QuicServerHandshakeAdmission.ReleaseClaim release = permit.claimRelease();
        if (release == null) {
            return;
        }
        completeAdmissionRelease(permit, release);
    }

    private void releaseUnpublishedAdmission(QuicServerHandshakeAdmission.Permit permit) {
        QuicServerHandshakeAdmission.ReleaseClaim release = permit.claimRelease();
        if (release == null) {
            return;
        }
        completeUnpublishedAdmissionRelease(permit, release);
    }

    private void completeUnpublishedAdmissionRelease(QuicServerHandshakeAdmission.Permit permit,
                                                      QuicServerHandshakeAdmission.ReleaseClaim release) {
        ScopedValue.where(UNPUBLISHED_ADMISSION_RELEASE,
                          new UnpublishedAdmissionReleaseScope(this, currentUnpublishedAdmissionRelease()))
                .run(() -> completeAdmissionRelease(permit, release));
    }

    private void completeAdmissionRelease(QuicServerHandshakeAdmission.Permit permit,
                                          QuicServerHandshakeAdmission.ReleaseClaim release) {
        try {
            permit.completeRelease(release);
        } catch (Throwable releaseFailure) {
            if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "[" + logTag() + "] QUIC server connection admission release failed",
                           releaseFailure);
            }
        }
    }

    private void rejectLifecycleReentry(String operation) {
        if (inUnpublishedAdmissionRelease()) {
            String message = "Cannot %s from an unpublished connection-permit release callback".formatted(operation);
            throw new IllegalStateException(message);
        }
    }

    private void rejectStopAcceptingCleanupReentry(String operation) {
        if (inStopAcceptingCleanup()) {
            String message = "Cannot %s from a synchronous stop-rejection callback".formatted(operation);
            throw new IllegalStateException(message);
        }
    }

    private boolean inStopAcceptingCleanup() {
        return stopAcceptingCleanupScope() != null;
    }

    private StopAcceptingCleanupScope stopAcceptingCleanupScope() {
        for (StopAcceptingCleanupScope scope = currentStopAcceptingCleanup();
                scope != null;
                scope = scope.parent()) {
            if (scope.runtime() == this) {
                return scope;
            }
        }
        return null;
    }

    private static StopAcceptingCleanupScope currentStopAcceptingCleanup() {
        return STOP_ACCEPTING_CLEANUP.isBound() ? STOP_ACCEPTING_CLEANUP.get() : null;
    }

    private void abortRuntime(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        StopAcceptingCleanupScope cleanupScope = stopAcceptingCleanupScope();
        if (cleanupScope == null) {
            abort(failure);
        } else {
            cleanupScope.deferAbort(failure);
        }
    }

    private boolean inUnpublishedAdmissionRelease() {
        for (UnpublishedAdmissionReleaseScope scope = currentUnpublishedAdmissionRelease();
                scope != null;
                scope = scope.parent()) {
            if (scope.runtime() == this) {
                return true;
            }
        }
        return false;
    }

    private static UnpublishedAdmissionReleaseScope currentUnpublishedAdmissionRelease() {
        return UNPUBLISHED_ADMISSION_RELEASE.isBound() ? UNPUBLISHED_ADMISSION_RELEASE.get() : null;
    }

    private List<CompletableFuture<QuicConnection>> drainPendingAccepts() {
        List<CompletableFuture<QuicConnection>> result = new ArrayList<>(pendingAccepts);
        pendingAccepts.clear();
        return result;
    }

    private List<QuicConnectionImpl> drainAcceptedConnections() {
        List<QuicConnectionImpl> result = new ArrayList<>(acceptedConnections);
        acceptedConnections.clear();
        return result;
    }

    private void rejectAcceptedConnection(QuicConnectionImpl connection) {
        connection.terminate(QuicCloseCommand.transport(QuicTransportErrors.NO_ERROR,
                                                        "QUIC server runtime is not accepting new connections"));
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

    private static Throwable abortSelector(QuicSelector<?> selector, Throwable failure) {
        try {
            selector.abort(failure);
        } catch (RuntimeException | Error abortFailure) {
            failure = collectFailure(failure, abortFailure);
        }
        return failure;
    }

    private void observeConnectionTermination(List<OwnedConnection> ownedConnections, Throwable initialFailure) {
        List<CompletableFuture<Throwable>> observations = new ArrayList<>(ownedConnections.size());
        Throwable observationFailure = initialFailure;
        for (OwnedConnection owned : ownedConnections) {
            try {
                observations.add(owned.connection()
                                         .whenTerminated()
                                         .handle((_, failure) -> failure)
                                         .toCompletableFuture());
            } catch (RuntimeException | Error failure) {
                observationFailure = collectFailure(observationFailure, failure);
            }
        }
        Throwable setupFailure = observationFailure;
        CompletableFuture.allOf(observations.toArray(CompletableFuture[]::new))
                .whenComplete((_, failure) -> {
                    Throwable result = collectFailure(setupFailure, failure);
                    for (CompletableFuture<Throwable> observation : observations) {
                        result = collectFailure(result, observation.getNow(null));
                    }
                    closePreparation.complete(result);
                });
    }

    private static void failAccepts(List<CompletableFuture<QuicConnection>> accepts, Throwable failure) {
        accepts.forEach(accept -> accept.completeExceptionally(failure));
    }

    private static Throwable closeEndpoint(QuicEndpoint endpoint, Throwable failure) {
        if (endpoint == null) {
            return failure;
        }
        try {
            endpoint.close();
        } catch (Throwable closeFailure) {
            if (failure == null) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    LOGGER.log(System.Logger.Level.DEBUG, "Failed to close QUIC server endpoint", closeFailure);
                }
            } else {
                failure.addSuppressed(closeFailure);
            }
            return failure == null ? closeFailure : failure;
        }
        return failure;
    }

    private static boolean closeSelector(QuicSelector<?> selector, Throwable failure) {
        if (selector == null) {
            return true;
        }
        try {
            return selector.close(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (Throwable closeFailure) {
            if (failure == null) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    LOGGER.log(System.Logger.Level.DEBUG, "Failed to close QUIC server selector", closeFailure);
                }
            } else {
                failure.addSuppressed(closeFailure);
            }
            return false;
        }
    }

    private static String identityTag(Object instance) {
        return "0x" + HexFormat.of().toHexDigits(System.identityHashCode(instance));
    }

    private void observerFailed(String phase, Throwable failure) {
        if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "[" + logTag() + "] QUIC server observer failed during " + phase,
                       failure);
        }
    }

    private TlsState tlsState() {
        long generation = tls.generation();
        TlsState current = currentTlsState;
        if (current != null && current.generation() == generation) {
            if (closed) {
                throw new IllegalStateException("QUIC server runtime is closed");
            }
            if (generation == tls.generation()) {
                return current;
            }
        }
        return tlsState(tls);
    }

    private TlsState tlsState(Tls selectedTls) {
        tlsStateLock.lock();
        try {
            while (true) {
                if (closed) {
                    throw new IllegalStateException("QUIC server runtime is closed");
                }
                long generation = selectedTls.generation();
                TlsState current = tlsStates.get(selectedTls);
                if (current != null && current.generation() == generation) {
                    return current;
                }
                TlsState candidate = createTlsState(selectedTls);
                if (candidate.generation() != selectedTls.generation()) {
                    candidate.close();
                    continue;
                }
                tlsStates.put(selectedTls, candidate);
                if (selectedTls == tls) {
                    currentTlsState = candidate;
                }
                if (current != null) {
                    current.close();
                }
                if (candidate.generation() == selectedTls.generation()) {
                    return candidate;
                }
            }
        } finally {
            tlsStateLock.unlock();
        }
    }

    private TlsState createTlsState(Tls selectedTls) {
        QuicTlsConfigSnapshot tlsConfig = QuicTlsConfigSnapshot.create(selectedTls, runtimeConfig);
        QuicTlsServerSessionCache sessionCache = new QuicTlsServerSessionCache(tlsConfig.sessionCacheSize(),
                                                                                tlsConfig.sessionTimeout());
        try {
            QuicTLSContext tlsContext = QuicTLSContext.create(tlsConfig).withServerSessionCache(sessionCache);
            var unsupported = new ArrayList<>(availableVersions);
            unsupported.removeAll(tlsContext.createEngine().supportedQuicVersions());
            if (!unsupported.isEmpty()) {
                throw new IllegalArgumentException("Requested QUIC versions not supported by TLS: " + unsupported);
            }
            return new TlsState(tlsConfig.generation(),
                                tlsContext,
                                tlsConfig,
                                sessionCache);
        } catch (RuntimeException | Error e) {
            sessionCache.close();
            throw e;
        }
    }

    private SSLParameters connectionSslParameters(TlsState tlsState) {
        SSLParameters parameters = tlsState.tlsConfig().sslParameters();
        parameters.setProtocols(new String[] {"TLSv1.3"});
        if (!applicationProtocols.isEmpty()) {
            parameters.setApplicationProtocols(applicationProtocols.toArray(String[]::new));
        }
        return parameters;
    }

    private void closeTlsState() {
        tlsStateLock.lock();
        try {
            currentTlsState = null;
            for (TlsState state : tlsStates.values()) {
                state.close();
            }
            tlsStates.clear();
        } finally {
            tlsStateLock.unlock();
        }
    }

    /**
     * Non-blocking admission hook for new server connections.
     *
     * <p>The hook runs after the runtime has validated a supported Initial packet and before it allocates connection or
     * TLS state. Implementations must return immediately. Each accepted decision must provide a distinct permit whose
     * completion callbacks are safe to invoke from a transport thread.
     */
    @FunctionalInterface
    public interface ConnectionAdmission {
        /**
         * Attempts to admit a new connection without waiting.
         *
         * @return connection permit or rejection
         */
        ConnectionPermit tryAcquire();
    }

    /**
     * One connection-admission decision.
     *
     * <p>Release callbacks execute synchronously on whichever thread completes release, which may be a transport,
     * configured-executor, or lifecycle-caller thread. They must return promptly and must not invoke, or synchronously wait
     * for another task invoking, lifecycle operations on the runtime which owns the permit. Arrange such lifecycle work after
     * the callback returns.
     */
    public interface ConnectionPermit {
        /**
         * Creates an accepted permit.
         *
         * @param releaseBeforeEstablished callback used when the connection ends before its handshake completes
         * @param releaseEstablished callback used when an established connection completes cleanup
         * @return accepted permit
         */
        static ConnectionPermit accepted(Runnable releaseBeforeEstablished, Runnable releaseEstablished) {
            return new AcceptedConnectionPermit(
                    Objects.requireNonNull(releaseBeforeEstablished, "release-before-established callback"),
                    Objects.requireNonNull(releaseEstablished, "release-established callback"));
        }

        /**
         * Returns the shared rejected decision.
         *
         * @return rejected decision
         */
        static ConnectionPermit rejected() {
            return RejectedConnectionPermit.INSTANCE;
        }

        /**
         * Returns whether the connection was admitted.
         *
         * @return {@code true} for an accepted connection
         */
        boolean accepted();

        /**
         * Releases an accepted permit which did not reach an established connection.
         */
        void releaseBeforeEstablished();

        /**
         * Releases an accepted permit for an established connection.
         */
        void releaseEstablished();
    }

    /**
     * Observer of server connection lifecycle needed by an owning adapter.
     *
     * <p>Callbacks execute on transport threads and must not block. The observer is borrowed; closing the runtime does not
     * close it.
     */
    public interface Observer {
        /**
         * Invoked after a connection is registered with this runtime and before its first packet is processed.
         *
         * @param connection semantic connection view
         */
        default void connectionCreated(QuicConnection connection) {
        }

        /**
         * Invoked after the server handshake completes successfully.
         *
         * @param connection semantic connection view
         */
        default void handshakeSucceeded(QuicConnection connection) {
        }
    }

    /**
     * Selects the TLS configuration for a server connection from the first complete ClientHello.
     *
     * <p>An implementation must return identities from a stable, bounded set of configured {@link Tls} instances. The
     * runtime owns one reload-aware TLS state and bounded session cache for each distinct identity until the runtime closes.
     */
    public interface TlsSelector {
        /**
         * Selects TLS for a normalized DNS SNI host.
         *
         * @param connection in-progress connection
         * @param requestedServerName normalized DNS SNI host
         * @return selected TLS configuration
         * @implSpec The returned identity must belong to the selector's stable, bounded configured set. An implementation
         * must not create a new {@link Tls} instance per call or per connection.
         */
        Tls select(QuicConnection connection, String requestedServerName);

        /**
         * Selects TLS when the ClientHello does not contain SNI.
         *
         * @param connection in-progress connection
         * @return selected TLS configuration
         * @implSpec The returned identity must belong to the selector's stable, bounded configured set. An implementation
         * must not create a new {@link Tls} instance per call or per connection.
         */
        Tls selectWithoutSni(QuicConnection connection);
    }

    /**
     * Explicit TLS server-name policy rejection.
     */
    public static final class RejectedTlsSelectionException extends IllegalArgumentException {
        /**
         * Whether this rejection sends the TLS server-name alert.
         */
        private final boolean sendUnrecognizedNameAlert;

        /**
         * Creates a rejection.
         *
         * @param message local diagnostic
         * @param sendUnrecognizedNameAlert whether to close with TLS {@code unrecognized_name}
         */
        public RejectedTlsSelectionException(String message, boolean sendUnrecognizedNameAlert) {
            this(message, sendUnrecognizedNameAlert, Optional.empty());
        }

        /**
         * Creates a rejection with its originating policy failure.
         *
         * @param message local diagnostic
         * @param sendUnrecognizedNameAlert whether to close with TLS {@code unrecognized_name}
         * @param cause originating policy failure
         */
        public RejectedTlsSelectionException(String message,
                                             boolean sendUnrecognizedNameAlert,
                                             Throwable cause) {
            this(message,
                 sendUnrecognizedNameAlert,
                 Optional.of(Objects.requireNonNull(cause, "cause")));
        }

        private RejectedTlsSelectionException(String message,
                                              boolean sendUnrecognizedNameAlert,
                                              Optional<Throwable> cause) {
            super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause").orElse(null));
            this.sendUnrecognizedNameAlert = sendUnrecognizedNameAlert;
        }

        /**
         * Whether to close with TLS {@code unrecognized_name}.
         *
         * @return whether to send the alert
         */
        public boolean sendUnrecognizedNameAlert() {
            return sendUnrecognizedNameAlert;
        }
    }

    /**
     * Builder for server-side QUIC runtimes.
     */
    public static final class Builder {
        private String serverId;
        private Executor executor;
        private List<String> applicationProtocols = List.of();
        private Tls tls;
        private InetSocketAddress bindAddress;
        private LongFunction<String> appErrorCodeToString;
        private QuicConfig quicConfig;
        private Observer observer;
        private ConnectionAdmission connectionAdmission;
        private TlsSelector tlsSelector;
        private Duration handshakeTimeout = DEFAULT_HANDSHAKE_TIMEOUT;
        private int maxPendingHandshakes = DEFAULT_MAX_PENDING_HANDSHAKES;
        private boolean retryEnabled;

        private Builder() {
        }

        /**
         * Configures the runtime identifier.
         *
         * @param serverId runtime identifier
         * @return this builder
         */
        public Builder serverId(String serverId) {
            this.serverId = Objects.requireNonNull(serverId, "serverId");
            return this;
        }

        /**
         * Configures server TLS.
         *
         * @param tls TLS configuration
         * @return this builder
         */
        public Builder tls(Tls tls) {
            this.tls = Objects.requireNonNull(tls, "tls");
            return this;
        }

        /**
         * Configures application protocols advertised by the server, in server preference order with the most preferred
         * protocol first. The first configured protocol also offered by the client is selected.
         *
         * @param applicationProtocols ordered application protocols
         * @return this builder
         */
        public Builder applicationProtocols(List<String> applicationProtocols) {
            this.applicationProtocols =
                    List.copyOf(Objects.requireNonNull(applicationProtocols, "applicationProtocols"));
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
         * Configures application-error descriptions used before protocol handoff.
         *
         * @param formatter error-code formatter that must return a non-null description
         * @return this builder
         */
        public Builder applicationErrors(LongFunction<String> formatter) {
            this.appErrorCodeToString = Objects.requireNonNull(formatter, "formatter");
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
         * Configures whether clients without a valid address token receive a Retry packet.
         *
         * @param retryEnabled whether Retry is enabled
         * @return this builder
         */
        public Builder retryEnabled(boolean retryEnabled) {
            this.retryEnabled = retryEnabled;
            return this;
        }

        /**
         * Configures the absolute maximum duration of an incomplete server handshake. The duration must be positive and
         * fit in signed 64-bit nanoseconds.
         *
         * @param handshakeTimeout positive server handshake timeout representable in signed 64-bit nanoseconds
         * @return this builder
         */
        public Builder handshakeTimeout(Duration handshakeTimeout) {
            this.handshakeTimeout = Objects.requireNonNull(handshakeTimeout, "handshakeTimeout");
            return this;
        }

        /**
         * Configures the maximum number of incomplete server handshakes retained by this runtime.
         *
         * @param maxPendingHandshakes positive pending-handshake limit
         * @return this builder
         */
        public Builder maxPendingHandshakes(int maxPendingHandshakes) {
            this.maxPendingHandshakes = maxPendingHandshakes;
            return this;
        }

        /**
         * Configures the borrowed server connection observer.
         *
         * @param observer connection observer
         * @return this builder
         */
        public Builder observer(Observer observer) {
            this.observer = Objects.requireNonNull(observer, "observer");
            return this;
        }

        /**
         * Configures the non-blocking server connection-admission hook.
         *
         * @param connectionAdmission connection-admission hook
         * @return this builder
         */
        public Builder connectionAdmission(ConnectionAdmission connectionAdmission) {
            this.connectionAdmission = Objects.requireNonNull(connectionAdmission, "connectionAdmission");
            return this;
        }

        /**
         * Configures server TLS selection from ClientHello SNI.
         *
         * <p>The selector must return identities from a stable, bounded set of configured {@link Tls} instances. The runtime
         * retains one reload-aware TLS state and bounded session cache per distinct identity until close.
         *
         * @param tlsSelector server TLS selector
         * @return this builder
         */
        public Builder tlsSelector(TlsSelector tlsSelector) {
            this.tlsSelector = Objects.requireNonNull(tlsSelector, "tlsSelector");
            return this;
        }

        /**
         * Builds a one-shot server runtime.
         *
         * @return configured runtime
         */
        public QuicServerRuntime build() {
            return new QuicServerRuntime(this);
        }
    }

    private record TlsState(long generation,
                            QuicTLSContext quicTLSContext,
                            QuicTlsConfigSnapshot tlsConfig,
                            QuicTlsServerSessionCache sessionCache) implements AutoCloseable {
        @Override
        public void close() {
            sessionCache.close();
        }
    }

    private record RuntimeSnapshot(List<CompletableFuture<QuicConnection>> pendingAccepts,
                                   List<QuicConnectionImpl> acceptedConnections,
                                   List<OwnedConnection> connections,
                                   QuicEndpoint endpoint,
                                   QuicSelector<?> selector) {
    }

    private static final class StopAcceptingCleanupScope {
        private final QuicServerRuntime runtime;
        private final StopAcceptingCleanupScope parent;
        private Throwable cleanupFailure;
        private Throwable deferredAbort;

        private StopAcceptingCleanupScope(QuicServerRuntime runtime,
                                          StopAcceptingCleanupScope parent) {
            this.runtime = runtime;
            this.parent = parent;
        }

        private QuicServerRuntime runtime() {
            return runtime;
        }

        private StopAcceptingCleanupScope parent() {
            return parent;
        }

        private void cleanupFailed(Throwable failure) {
            cleanupFailure = collectFailure(cleanupFailure, failure);
        }

        private Throwable cleanupFailure() {
            return cleanupFailure;
        }

        private void deferAbort(Throwable failure) {
            deferredAbort = collectFailure(deferredAbort, failure);
        }

        private Throwable deferredAbort() {
            return deferredAbort;
        }
    }

    private record UnpublishedAdmissionReleaseScope(QuicServerRuntime runtime,
                                                     UnpublishedAdmissionReleaseScope parent) {
    }

    private static final class OwnedConnection {
        private final QuicServerConnection connection;
        private final QuicServerHandshakeAdmission.Permit handshakePermit;
        private boolean setupInProgress = true;

        private OwnedConnection(QuicServerConnection connection,
                                QuicServerHandshakeAdmission.Permit handshakePermit) {
            this.connection = connection;
            this.handshakePermit = handshakePermit;
        }

        private QuicServerConnection connection() {
            return connection;
        }

        private QuicServerHandshakeAdmission.Permit handshakePermit() {
            return handshakePermit;
        }

        private boolean setupInProgress() {
            return setupInProgress;
        }

        private boolean completeSetup() {
            if (!setupInProgress) {
                return false;
            }
            setupInProgress = false;
            return true;
        }
    }

    private record AcceptedConnectionPermit(Runnable beforeEstablishedAction,
                                            Runnable establishedAction) implements ConnectionPermit {
        @Override
        public boolean accepted() {
            return true;
        }

        @Override
        public void releaseBeforeEstablished() {
            beforeEstablishedAction.run();
        }

        @Override
        public void releaseEstablished() {
            establishedAction.run();
        }
    }

    private enum RejectedConnectionPermit implements ConnectionPermit {
        INSTANCE;

        @Override
        public boolean accepted() {
            return false;
        }

        @Override
        public void releaseBeforeEstablished() {
        }

        @Override
        public void releaseEstablished() {
        }
    }

    private enum UnlimitedConnectionPermit implements ConnectionPermit {
        INSTANCE;

        @Override
        public boolean accepted() {
            return true;
        }

        @Override
        public void releaseBeforeEstablished() {
        }

        @Override
        public void releaseEstablished() {
        }
    }
}
