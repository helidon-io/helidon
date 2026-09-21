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

package io.helidon.webserver.http3;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import io.helidon.http.HttpLogConfig;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3LoggingFrameListener;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3Settings;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicTermination;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.TransportBindingContext;
import io.helidon.webserver.quic.spi.HttpQuicSubProtocolRuntime;
import io.helidon.webserver.spi.TransportBinding;

import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_3;

final class Http3QuicRuntime implements HttpQuicSubProtocolRuntime, Http3ServerConnection.StreamLifecycle {
    private static final System.Logger LOGGER = System.getLogger(Http3QuicRuntime.class.getName());
    private static final Http3FrameListener NO_OP_FRAME_LISTENER = Http3FrameListener.create(List.of());

    private final String listenerName;
    private final TransportBindingContext bindingContext;
    private final Http3Config config;
    private final Http3Handler handler;
    private final Http3Settings localSettings;
    private final Duration streamOpenTimeout;
    private final Duration requestReadTimeout;
    private final Http3FrameListener receiveFrameListener;
    private final Http3FrameListener sendFrameListener;
    private final Executor requestExecutor;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final ReentrantLock stateLock = new ReentrantLock();
    private final Map<QuicConnection, Http3ServerConnection> connections = new ConcurrentHashMap<>();

    private volatile boolean draining;
    private long activeStreams;
    private CompletableFuture<Void> drainCompletion;

    Http3QuicRuntime(TransportBindingContext context, Http3Config config) {
        this.listenerName = context.listenerContext().config().name();
        this.bindingContext = context;
        this.config = config;
        HttpLogConfig log = config.log();
        this.receiveFrameListener = log.receiveLog()
                ? Http3LoggingFrameListener.create(log, "recv")
                : NO_OP_FRAME_LISTENER;
        this.sendFrameListener = log.sendLog()
                ? Http3LoggingFrameListener.create(log, "send")
                : NO_OP_FRAME_LISTENER;
        this.handler = new Http3RoutingHandler(context,
                                               config.maxBufferedEntitySize().toBytes(),
                                               config.validatePath());
        this.localSettings = Http3Settings.createConfigured(config.maxFieldSectionSize(),
                                                            config.qpackMaxTableCapacity(),
                                                            config.qpackBlockedStreams());
        this.streamOpenTimeout = Http3RuntimeSupport.DEFAULT_STREAM_OPEN_TIMEOUT;
        this.requestReadTimeout = context.listenerContext().config().connectionOptions().readTimeout();
        this.requestExecutor = context.listenerContext().executor();
    }

    @Override
    public List<String> alpnIds() {
        return List.of(Http3Protocol.ALPN);
    }

    @Override
    public long minimumPeerUniStreams() {
        return Http3Protocol.MINIMUM_PEER_UNI_STREAMS;
    }

    @Override
    public LongFunction<String> applicationErrors() {
        return Http3RuntimeSupport.applicationErrors();
    }

    @Override
    public boolean isNormalTermination(QuicTermination termination) {
        Objects.requireNonNull(termination, "termination");
        if (termination.kind() == QuicTermination.Kind.CONNECTION_CLOSE
                && termination.layer() == QuicTermination.Layer.APPLICATION) {
            return termination.cause().isEmpty()
                    && termination.errorCode().orElse(-1) == Http3ErrorCode.NO_ERROR.code();
        }
        return HttpQuicSubProtocolRuntime.super.isNormalTermination(termination);
    }

    @Override
    public void accept(QuicConnection connection, ConnectionObservation observation) {
        accept(connection, observation, Optional.empty());
    }

    @Override
    public void accept(QuicConnection connection,
                       ConnectionObservation observation,
                       SniContext sniContext) {
        accept(connection, observation, Optional.of(Objects.requireNonNull(sniContext, "sniContext")));
    }

    @Override
    public TransportBinding.ShutdownResult closeGracefully(Duration gracePeriod) {
        Objects.requireNonNull(gracePeriod, "gracePeriod");
        if (!shutdownStarted.compareAndSet(false, true)) {
            return TransportBinding.ShutdownResult.GRACEFUL;
        }
        long graceNanos;
        try {
            graceNanos = gracePeriod.isNegative() ? 0 : gracePeriod.toNanos();
        } catch (ArithmeticException e) {
            graceNanos = Long.MAX_VALUE;
        }
        long drainStarted = System.nanoTime();

        List<Http3ServerConnection> currentConnections;
        CompletableFuture<Void> currentDrainCompletion;
        long currentActiveStreams;
        stateLock.lock();
        try {
            draining = true;
            currentConnections = List.copyOf(connections.values());
            currentConnections.forEach(Http3ServerConnection::markDraining);
            currentDrainCompletion = activeStreams == 0 ? CompletableFuture.completedFuture(null) : new CompletableFuture<>();
            drainCompletion = currentDrainCompletion;
            currentActiveStreams = activeStreams;
        } finally {
            stateLock.unlock();
        }
        logDebug(() -> "state=drain-start connections=%d activeStreams=%d gracePeriod=%s"
                .formatted(currentConnections.size(), currentActiveStreams, gracePeriod));

        List<CompletableFuture<Void>> drainGoAwayDispatches = currentConnections.stream()
                .map(Http3ServerConnection::sendDrainGoAway)
                .toList();
        TransportBinding.ShutdownResult result = awaitCompletion(currentDrainCompletion,
                                                                 drainStarted,
                                                                 graceNanos,
                                                                 "drain")
                ? TransportBinding.ShutdownResult.GRACEFUL
                : TransportBinding.ShutdownResult.FORCED;
        logDebug(() -> "state=drain-complete connections=%d activeStreams=%d"
                .formatted(connections.size(), activeStreamCount()));
        List<Http3ServerConnection> closingConnections = List.of();
        try {
            List<CompletableFuture<Void>> finalGoAwayDispatches = List.copyOf(connections.values())
                    .stream()
                    .map(Http3ServerConnection::sendFinalGoAway)
                    .toList();
            List<CompletableFuture<Void>> allGoAwayDispatches = new ArrayList<>(drainGoAwayDispatches);
            allGoAwayDispatches.addAll(finalGoAwayDispatches);
            CompletableFuture<Void> finalDispatch =
                    CompletableFuture.allOf(allGoAwayDispatches.toArray(CompletableFuture[]::new));
            if (!finalDispatch.isDone()) {
                long remainingNanos = remainingNanos(drainStarted, graceNanos);
                if (remainingNanos == 0) {
                    throw new TimeoutException("HTTP/3 graceful-close deadline expired before final GOAWAY dispatch");
                }
                finalDispatch.get(remainingNanos, TimeUnit.NANOSECONDS);
            } else {
                finalDispatch.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = TransportBinding.ShutdownResult.FORCED;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            result = TransportBinding.ShutdownResult.FORCED;
            logDebug(() -> "state=final-goaway-dispatch-ended cause=%s"
                    .formatted(Http3RuntimeSupport.throwableSummary(Http3RuntimeSupport.unwrap(e))));
        } finally {
            closingConnections = closeNow();
        }
        CompletableFuture<Void> connectionCleanup = CompletableFuture.allOf(
                closingConnections.stream()
                        .map(Http3ServerConnection::whenTerminated)
                        .map(stage -> stage.toCompletableFuture())
                        .toArray(CompletableFuture[]::new));
        if (!awaitCompletion(connectionCleanup, drainStarted, graceNanos, "connection-cleanup")) {
            result = TransportBinding.ShutdownResult.FORCED;
        }
        return result;
    }

    @Override
    public void close() {
        if (shutdownStarted.compareAndSet(false, true)) {
            logDebug(() -> "state=server-close-immediate connections=%d".formatted(connections.size()));
            closeNow();
        }
    }

    @Override
    public boolean requestStarted(Http3ServerConnection connection, Http3ServerStream stream) {
        stateLock.lock();
        try {
            if (draining || shutdownStarted.get() || connection.rejectsStream(stream.streamId())) {
                return false;
            }
            activeStreams++;
            return true;
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public void requestCompleted(Http3ServerConnection connection, Http3ServerStream stream) {
        CompletableFuture<Void> localDrainCompletion = null;
        stateLock.lock();
        try {
            if (activeStreams == 0) {
                LOGGER.log(System.Logger.Level.ERROR,
                           "HTTP/3 stream completed without active stream accounting: connectionId={0}, streamId={1}",
                           connection.connection().childSocketId(),
                           stream.streamId());
                return;
            }
            activeStreams--;
            if (draining && activeStreams == 0 && drainCompletion != null) {
                localDrainCompletion = drainCompletion;
            }
        } finally {
            stateLock.unlock();
        }
        if (localDrainCompletion != null) {
            localDrainCompletion.complete(null);
        }
    }

    private static long remainingNanos(long started, long budgetNanos) {
        long elapsed = Math.max(0, System.nanoTime() - started);
        return elapsed >= budgetNanos ? 0 : budgetNanos - elapsed;
    }

    private void accept(QuicConnection connection,
                        ConnectionObservation observation,
                        Optional<SniContext> sniContext) {
        Http3ServerConnection serverConnection = new Http3ServerConnection(bindingContext,
                                                                           connection,
                                                                           observation,
                                                                           sniContext,
                                                                           localSettings,
                                                                           handler,
                                                                           receiveFrameListener,
                                                                           sendFrameListener,
                                                                           streamOpenTimeout,
                                                                           requestReadTimeout,
                                                                           config,
                                                                           this,
                                                                           requestExecutor);
        boolean accepted;
        stateLock.lock();
        try {
            accepted = !shutdownStarted.get()
                    && !draining
                    && connections.putIfAbsent(connection, serverConnection) == null;
        } finally {
            stateLock.unlock();
        }
        if (!accepted) {
            connection.log(LOGGER,
                           System.Logger.Level.DEBUG,
                           "state=connection-rejected connectionId=%s peer=%s reason=server-closing",
                           connection.childSocketId(),
                           connection.remotePeer().address());
            serverConnection.close();
            return;
        }

        connection.log(LOGGER,
                       System.Logger.Level.DEBUG,
                       "state=connection-accepted connectionId=%s peer=%s",
                       connection.childSocketId(),
                       connection.remotePeer().address());
        observation.protocolSelected(PROTOCOL_HTTP_3);
        connection.whenTerminated()
                .whenComplete((cause, throwable) -> {
                    StreamOutcome streamOutcome = cause != null
                            && (isNormalTermination(cause)
                                    || cause.kind() == QuicTermination.Kind.STATELESS_RESET)
                            ? StreamOutcome.CANCELLED
                            : StreamOutcome.ERROR;
                    serverConnection.transportTerminated(cause == null ? throwable : cause.closeCause(),
                                                         throwable,
                                                         streamOutcome);
                    serverConnection.whenTerminated().whenComplete((_, cleanupFailure) -> {
                        connections.remove(connection, serverConnection);
                        if (cleanupFailure != null) {
                            logDebug(() -> "state=connection-cleanup-failed connectionId=%s cause=%s"
                                    .formatted(connection.childSocketId(),
                                               Http3RuntimeSupport.throwableSummary(cleanupFailure)));
                        }
                    });
                });
        serverConnection.start();
    }

    private boolean awaitCompletion(CompletableFuture<Void> completion,
                                    long drainStarted,
                                    long graceNanos,
                                    String action) {
        if (!completion.isDone()) {
            long remainingNanos = remainingNanos(drainStarted, graceNanos);
            if (remainingNanos == 0) {
                return false;
            }
        }
        try {
            if (completion.isDone()) {
                completion.get();
            } else {
                completion.get(remainingNanos(drainStarted, graceNanos), TimeUnit.NANOSECONDS);
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            logDebug(() -> "state=%s-wait-ended cause=%s"
                    .formatted(action,
                               Http3RuntimeSupport.throwableSummary(Http3RuntimeSupport.unwrap(e))));
            return false;
        }
    }

    private List<Http3ServerConnection> closeNow() {
        List<Http3ServerConnection> currentConnections;
        stateLock.lock();
        try {
            currentConnections = List.copyOf(connections.values());
        } finally {
            stateLock.unlock();
        }
        logDebug(() -> "state=server-close-now connections=%d".formatted(currentConnections.size()));
        currentConnections.forEach(Http3ServerConnection::close);
        return currentConnections;
    }

    private long activeStreamCount() {
        stateLock.lock();
        try {
            return activeStreams;
        } finally {
            stateLock.unlock();
        }
    }

    private void logDebug(Supplier<String> messageSupplier) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                       () -> listenerName == null || listenerName.isBlank()
                               ? messageSupplier.get()
                               : "[" + listenerName + "] " + messageSupplier.get());
        }
    }

}
