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

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.tls.Tls;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3GoAway;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3Settings;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicServerRuntime;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.QuicVersion;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.TransportBindingContext;

import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_3;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class RawHttp3TestServer implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(RawHttp3TestServer.class.getName());

    private final String listenerName;
    private final QuicServerRuntime quicServer;
    private final RawRuntime runtime;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();

    private RawHttp3TestServer(String listenerName,
                               QuicServerRuntime quicServer,
                               RawRuntime runtime) {
        this.listenerName = Objects.requireNonNull(listenerName, "listenerName");
        this.quicServer = Objects.requireNonNull(quicServer, "quicServer");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        acceptLoop();
    }

    static RawHttp3TestServer create(Executor executor,
                                     InetSocketAddress bindAddress,
                                     Tls tls,
                                     List<QuicVersion> availableVersions,
                                     Http3Handler handler) {
        return create("raw-http3",
                      executor,
                      bindAddress,
                      tls,
                      availableVersions,
                      0,
                      0,
                      Http3RuntimeSupport.DEFAULT_STREAM_OPEN_TIMEOUT,
                      Duration.ZERO,
                      (_, stream) -> handler.handle(stream));
    }

    static RawHttp3TestServer create(Executor executor,
                                     InetSocketAddress bindAddress,
                                     Tls tls,
                                     List<QuicVersion> availableVersions,
                                     ConnectionHandler handler) {
        return create("raw-http3",
                      executor,
                      bindAddress,
                      tls,
                      availableVersions,
                      0,
                      0,
                      Http3RuntimeSupport.DEFAULT_STREAM_OPEN_TIMEOUT,
                      Duration.ZERO,
                      handler);
    }

    static RawHttp3TestServer create(Executor executor,
                                     InetSocketAddress bindAddress,
                                     Tls tls,
                                     List<QuicVersion> availableVersions,
                                     Duration requestReadTimeout,
                                     ConnectionHandler handler) {
        return create("raw-http3",
                      executor,
                      bindAddress,
                      tls,
                      availableVersions,
                      0,
                      0,
                      Http3RuntimeSupport.DEFAULT_STREAM_OPEN_TIMEOUT,
                      requestReadTimeout,
                      handler);
    }

    static RawHttp3TestServer create(String listenerName,
                                     Executor executor,
                                     InetSocketAddress bindAddress,
                                     Tls tls,
                                     List<QuicVersion> availableVersions,
                                     long qpackMaxTableCapacity,
                                     int qpackBlockedStreams,
                                     Duration streamOpenTimeout,
                                     Http3Handler handler) {
        return create(listenerName,
                      executor,
                      bindAddress,
                      tls,
                      availableVersions,
                      qpackMaxTableCapacity,
                      qpackBlockedStreams,
                      streamOpenTimeout,
                      Duration.ZERO,
                      (_, stream) -> handler.handle(stream));
    }

    static QuicServerRuntime createQuicServer(String listenerName,
                                              Executor executor,
                                              InetSocketAddress bindAddress,
                                              Tls tls,
                                              QuicConfig quicConfig) {
        return QuicServerRuntime.builder()
                .serverId(listenerName)
                .executor(executor)
                .bindAddress(bindAddress)
                .tls(tls)
                .applicationProtocols(List.of(Http3Protocol.ALPN))
                .applicationErrors(Http3RuntimeSupport.applicationErrors())
                .quicConfig(quicConfig)
                .build();
    }

    static InputStream requestBodyInputStream(Http3ServerStream stream) {
        Function<Integer, BufferData> reader = stream.requestBodyReader(-1);
        return new InputStream() {
            private byte[] current = BufferData.EMPTY_BYTES;
            private int offset;
            private boolean endOfInput;
            private boolean closed;

            @Override
            public int read() {
                if (!ensureAvailable(1)) {
                    return -1;
                }
                return current[offset++] & 0xff;
            }

            @Override
            public int read(byte[] bytes, int destinationOffset, int length) {
                Objects.checkFromIndexSize(destinationOffset, length, bytes.length);
                if (length == 0) {
                    return 0;
                }
                if (!ensureAvailable(length)) {
                    return -1;
                }
                int count = Math.min(length, current.length - offset);
                System.arraycopy(current, offset, bytes, destinationOffset, count);
                offset += count;
                return count;
            }

            @Override
            public void close() {
                if (closed) {
                    return;
                }
                closed = true;
                endOfInput = true;
            }

            private boolean ensureAvailable(int estimate) {
                if (closed) {
                    return false;
                }
                while (offset == current.length && !endOfInput) {
                    current = reader.apply(estimate).readBytes();
                    offset = 0;
                    endOfInput = current.length == 0;
                }
                return !endOfInput;
            }
        };
    }

    InetSocketAddress localAddress() {
        return quicServer.localAddress();
    }

    void sendGoAway(QuicConnection connection, Http3GoAway goAway) {
        runtime.sendGoAway(connection, goAway);
    }

    void closeGracefully(Duration gracePeriod) {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        try {
            quicServer.stopAccepting();
            runtime.closeGracefully(gracePeriod);
        } finally {
            try {
                quicServer.close();
            } finally {
                runtime.close();
            }
        }
    }

    @Override
    public void close() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        try {
            quicServer.stopAccepting();
        } finally {
            try {
                quicServer.close();
            } finally {
                runtime.close();
            }
        }
    }

    private static RawHttp3TestServer create(String listenerName,
                                             Executor executor,
                                             InetSocketAddress bindAddress,
                                             Tls tls,
                                             List<QuicVersion> availableVersions,
                                             long qpackMaxTableCapacity,
                                             int qpackBlockedStreams,
                                             Duration streamOpenTimeout,
                                             Duration requestReadTimeout,
                                             ConnectionHandler handler) {
        RawRuntime runtime = new RawRuntime(listenerName,
                                            handler,
                                            qpackMaxTableCapacity,
                                            qpackBlockedStreams,
                                            streamOpenTimeout,
                                            requestReadTimeout,
                                            executor);
        QuicServerRuntime server = createQuicServer(listenerName,
                                                    executor,
                                                    bindAddress,
                                                    tls,
                                                    QuicConfig.builder()
                                                            .availableVersions(availableVersions)
                                                            .buildPrototype());
        return new RawHttp3TestServer(listenerName, server, runtime);
    }

    private void acceptLoop() {
        quicServer.accept().whenComplete((connection, throwable) -> {
            if (throwable == null) {
                dispatch(connection);
            } else if (!shutdownStarted.get() && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                Throwable cause = Http3RuntimeSupport.unwrap(throwable);
                LOGGER.log(System.Logger.Level.DEBUG,
                           "Failed to accept raw HTTP/3 connection on listener " + listenerName + ": " + cause,
                           cause);
            }
            if (!shutdownStarted.get() && quicServer.accepting()) {
                acceptLoop();
            }
        });
    }

    private void dispatch(QuicConnection connection) {
        try {
            runtime.accept(connection, ConnectionObservation.noop());
        } catch (RuntimeException | Error e) {
            connection.terminate(QuicCloseCommand.application(
                    Http3ErrorCode.INTERNAL_ERROR.code(),
                    e,
                    "Failed to dispatch raw HTTP/3 connection on listener " + listenerName));
        }
    }

    @FunctionalInterface
    interface ConnectionHandler {
        Optional<Http3Handler.BufferedResponse> handle(QuicConnection connection, Http3ServerStream stream);
    }

    private static final class RawRuntime implements Http3ServerConnection.StreamLifecycle, AutoCloseable {
        private static final Http3FrameListener NO_OP_FRAME_LISTENER = Http3FrameListener.create(List.of());

        private final String listenerName;
        private final TransportBindingContext bindingContext;
        private final ConnectionHandler handler;
        private final Http3Settings localSettings;
        private final Duration streamOpenTimeout;
        private final Duration requestReadTimeout;
        private final Executor requestExecutor;
        private final Http3Config config = Http3Config.create();
        private final AtomicBoolean shutdownStarted = new AtomicBoolean();
        private final ReentrantLock stateLock = new ReentrantLock();
        private final Map<QuicConnection, Http3ServerConnection> connections =
                new ConcurrentHashMap<>();

        private boolean draining;
        private long activeStreams;
        private CompletableFuture<Void> drainCompletion;

        private RawRuntime(String listenerName,
                           ConnectionHandler handler,
                           long qpackMaxTableCapacity,
                           int qpackBlockedStreams,
                           Duration streamOpenTimeout,
                           Duration requestReadTimeout,
                           Executor requestExecutor) {
            this.listenerName = listenerName;
            this.handler = Objects.requireNonNull(handler, "handler");
            this.localSettings = Http3Settings.create(qpackMaxTableCapacity, qpackBlockedStreams);
            this.streamOpenTimeout = Objects.requireNonNull(streamOpenTimeout, "streamOpenTimeout");
            this.requestReadTimeout = Objects.requireNonNull(requestReadTimeout, "requestReadTimeout");
            this.requestExecutor = Objects.requireNonNull(requestExecutor, "requestExecutor");
            this.bindingContext = bindingContext(requestExecutor);
        }

        @Override
        public void close() {
            if (shutdownStarted.compareAndSet(false, true)) {
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
                               "Raw HTTP/3 request completed without active stream accounting on listener {0}",
                               listenerName);
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

        private static boolean isNormalTermination(QuicTermination termination) {
            if (termination.kind() == QuicTermination.Kind.CONNECTION_CLOSE
                    && termination.layer() == QuicTermination.Layer.APPLICATION) {
                return termination.cause().isEmpty()
                        && termination.errorCode().orElse(-1) == Http3ErrorCode.NO_ERROR.code();
            }
            return termination.cause().isEmpty()
                    && (termination.kind() == QuicTermination.Kind.SILENT
                            || termination.errorCode().orElse(-1) == 0);
        }

        private static long remainingNanos(long started, long budgetNanos) {
            long elapsed = Math.max(0, System.nanoTime() - started);
            return elapsed >= budgetNanos ? 0 : budgetNanos - elapsed;
        }

        private static TransportBindingContext bindingContext(Executor executor) {
            TransportBindingContext context = mock(TransportBindingContext.class);
            ListenerContext listenerContext = mock(ListenerContext.class);
            when(context.listenerContext()).thenReturn(listenerContext);
            when(context.router()).thenReturn(mock(Router.class));
            when(context.requestLimit()).thenReturn(FixedLimit.create());
            if (executor instanceof ExecutorService executorService) {
                when(listenerContext.executor()).thenReturn(executorService);
            }
            return context;
        }

        private void accept(QuicConnection connection, ConnectionObservation observation) {
            Http3ServerConnection serverConnection = new Http3ServerConnection(bindingContext,
                                                                               connection,
                                                                               observation,
                                                                               Optional.empty(),
                                                                               localSettings,
                                                                               stream -> handler.handle(connection, stream),
                                                                               NO_OP_FRAME_LISTENER,
                                                                               NO_OP_FRAME_LISTENER,
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
                serverConnection.close();
                return;
            }

            observation.protocolSelected(PROTOCOL_HTTP_3);
            connection.whenTerminated()
                    .whenComplete((termination, throwable) -> {
                        StreamOutcome outcome = termination != null
                                && (isNormalTermination(termination)
                                        || termination.kind() == QuicTermination.Kind.STATELESS_RESET)
                                ? StreamOutcome.CANCELLED
                                : StreamOutcome.ERROR;
                        serverConnection.transportTerminated(termination == null ? throwable : termination.closeCause(),
                                                             throwable,
                                                             outcome);
                        serverConnection.whenTerminated()
                                .whenComplete((_, cleanupFailure) -> connections.remove(connection, serverConnection));
                    });
            serverConnection.start();
        }

        private void sendGoAway(QuicConnection connection, Http3GoAway goAway) {
            Objects.requireNonNull(goAway, "goAway");
            if (goAway.type() != Http3GoAway.Type.REQUEST_STREAM_ID) {
                throw new IllegalArgumentException("Server HTTP/3 GOAWAY requires a request stream identifier.");
            }
            Http3ServerConnection serverConnection = connections.get(connection);
            if (serverConnection == null) {
                throw new IllegalStateException("HTTP/3 connection is not managed by this server.");
            }
            serverConnection.sendGoAway(goAway, "manual");
        }

        private void closeGracefully(Duration gracePeriod) {
            if (!shutdownStarted.compareAndSet(false, true)) {
                return;
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
            stateLock.lock();
            try {
                draining = true;
                currentConnections = List.copyOf(connections.values());
                currentConnections.forEach(Http3ServerConnection::markDraining);
                currentDrainCompletion = activeStreams == 0
                        ? CompletableFuture.completedFuture(null)
                        : new CompletableFuture<>();
                drainCompletion = currentDrainCompletion;
            } finally {
                stateLock.unlock();
            }

            List<CompletableFuture<Void>> drainGoAwayDispatches = currentConnections.stream()
                    .map(Http3ServerConnection::sendDrainGoAway)
                    .toList();
            awaitCompletion(currentDrainCompletion, drainStarted, graceNanos);
            try {
                List<CompletableFuture<Void>> finalGoAwayDispatches = List.copyOf(connections.values())
                        .stream()
                        .map(Http3ServerConnection::sendFinalGoAway)
                        .toList();
                List<CompletableFuture<Void>> allGoAwayDispatches = new ArrayList<>(drainGoAwayDispatches);
                allGoAwayDispatches.addAll(finalGoAwayDispatches);
                awaitCompletion(CompletableFuture.allOf(allGoAwayDispatches.toArray(CompletableFuture[]::new)),
                                drainStarted,
                                graceNanos);
            } finally {
                closeNow();
            }
        }

        private void awaitCompletion(CompletableFuture<Void> completion, long started, long budgetNanos) {
            try {
                if (completion.isDone()) {
                    completion.join();
                    return;
                }
                long remainingNanos = remainingNanos(started, budgetNanos);
                if (remainingNanos != 0) {
                    completion.get(remainingNanos, TimeUnit.NANOSECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException | CompletionException e) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    Throwable cause = Http3RuntimeSupport.unwrap(e);
                    LOGGER.log(System.Logger.Level.DEBUG,
                               "Raw HTTP/3 server shutdown wait failed on listener " + listenerName + ": " + cause,
                               cause);
                }
            }
        }

        private void closeNow() {
            List.copyOf(connections.values()).forEach(Http3ServerConnection::close);
        }
    }

}
