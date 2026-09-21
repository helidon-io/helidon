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

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.http3.Http3ControlStreamListener;
import io.helidon.http.http3.Http3ControlStreamSupport;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3GoAway;
import io.helidon.http.http3.Http3PeerCriticalStreams;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3ProtocolException;
import io.helidon.http.http3.Http3QpackContext;
import io.helidon.http.http3.Http3Settings;
import io.helidon.http.http3.Http3StreamObservation;
import io.helidon.http.http3.Http3StreamSupport;
import io.helidon.http.http3.Http3StreamType;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicRemoteStreamRegistration;
import io.helidon.quic.QuicStreamLimitException;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamWriter;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.TransportBindingContext;

final class Http3ServerConnection implements Http3ControlStreamListener {
    private static final System.Logger LOGGER = System.getLogger(Http3ServerConnection.class.getName());

    private final QuicConnection connection;
    private final ConnectionObservation transportObservation;
    private final Http3Settings localSettings;
    private final Http3QpackContext qpackContext;
    private final Http3Handler handler;
    private final Http3FrameListener receiveFrameListener;
    private final Http3FrameListener sendFrameListener;
    private final Duration streamOpenTimeout;
    private final Duration requestReadTimeout;
    private final Http3Config config;
    private final Limit requestLimit;
    private final StreamLifecycle streamLifecycle;
    private final Executor requestExecutor;
    private final Http3ConnectionContext context;
    private final Map<Long, Http3ServerStream> streams = new ConcurrentHashMap<>();
    private final Map<Long, StreamTask> streamTasks = new ConcurrentHashMap<>();
    private final Map<Long, Http3StreamObservation> observations = new ConcurrentHashMap<>();
    private final CompletableFuture<Void> initialized = new CompletableFuture<>();
    private final Http3PeerCriticalStreams peerCriticalStreams = Http3PeerCriticalStreams.create();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean transportCleanupObserved = new AtomicBoolean();
    private final AtomicReference<Lifecycle> lifecycle = new AtomicReference<>(Lifecycle.OPEN);
    private final AtomicReference<Throwable> cleanupFailure = new AtomicReference<>();
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();
    private final AtomicReference<Http3GoAway> localGoAway = new AtomicReference<>();
    private final AtomicReference<Http3GoAway> peerGoAway = new AtomicReference<>();
    private final AtomicInteger nextRequestId = new AtomicInteger();
    private final AtomicLong nextUnprocessedStreamId = new AtomicLong();
    private final ReentrantLock streamRegistrationLock = new ReentrantLock();
    private final ReentrantLock controlWriteLock = new ReentrantLock();

    private volatile QuicSenderStream controlStream;
    private volatile QuicStreamWriter controlStreamWriter;
    private CompletableFuture<Void> controlWriteTail = CompletableFuture.completedFuture(null);
    private CompletableFuture<Void> localGoAwayDispatch = CompletableFuture.completedFuture(null);
    private volatile QuicRemoteStreamRegistration remoteStreamRegistration;
    private volatile Http3Settings peerSettings;

    Http3ServerConnection(TransportBindingContext bindingContext,
                          QuicConnection connection,
                          ConnectionObservation transportObservation,
                          Optional<SniContext> sniContext,
                          Http3Settings localSettings,
                          Http3Handler handler,
                          Http3FrameListener receiveFrameListener,
                          Http3FrameListener sendFrameListener,
                          Duration streamOpenTimeout,
                          Duration requestReadTimeout,
                          Http3Config config,
                          StreamLifecycle streamLifecycle,
                          Executor requestExecutor) {
        Objects.requireNonNull(bindingContext, "bindingContext");
        this.connection = connection;
        this.transportObservation = Objects.requireNonNull(transportObservation, "transportObservation");
        this.localSettings = localSettings;
        this.handler = handler;
        this.receiveFrameListener = receiveFrameListener;
        this.sendFrameListener = sendFrameListener;
        this.streamOpenTimeout = streamOpenTimeout;
        this.requestReadTimeout = Objects.requireNonNull(requestReadTimeout, "requestReadTimeout");
        this.config = config;
        this.requestLimit = Objects.requireNonNull(bindingContext.requestLimit(), "bindingContext.requestLimit()");
        this.streamLifecycle = Objects.requireNonNull(streamLifecycle, "streamLifecycle");
        this.requestExecutor = Objects.requireNonNull(requestExecutor, "requestExecutor");
        this.qpackContext = Http3QpackContext.create(localSettings.qpackMaxTableCapacity(),
                                                     localSettings.qpackBlockedStreams(),
                                                     config.maxHeadersSize(),
                                                     this::fail);
        this.context = new Http3ConnectionContext(bindingContext, connection, sniContext);
    }

    void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        streamRegistrationLock.lock();
        try {
            if (closing()) {
                return;
            }
            QuicRemoteStreamRegistration registration = connection.addRemoteStreamListener(this::acceptRemoteStream);
            if (closing()) {
                registration.close();
                return;
            }
            remoteStreamRegistration = registration;
        } finally {
            streamRegistrationLock.unlock();
        }
        if (closing()) {
            return;
        }
        openAndPrimeControlStream()
                .thenCompose(_ -> openAndPrimeQpackEncoderStream())
                .thenCompose(_ -> openAndPrimeQpackDecoderStream())
                .whenComplete((_, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = Http3RuntimeSupport.unwrap(throwable);
                        fail(cause instanceof QuicStreamLimitException
                                     ? Http3ProtocolException.connectionError(
                                             Http3ErrorCode.STREAM_CREATION_ERROR,
                                             "HTTP/3 local critical streams cannot be created",
                                             cause)
                                     : cause);
                        return;
                    }
                    boolean completed;
                    streamRegistrationLock.lock();
                    try {
                        completed = lifecycle.get() == Lifecycle.OPEN && initialized.complete(null);
                    } finally {
                        streamRegistrationLock.unlock();
                    }
                    if (completed) {
                        logDebug(() -> "state=connection-ready peer=%s local=%s"
                                .formatted(connection.remotePeer().address(), connection.localPeer().address()));
                    }
                });
    }

    QuicConnection connection() {
        return connection;
    }

    Http3ConnectionContext context() {
        return context;
    }

    Http3QpackContext qpackContext() {
        return qpackContext;
    }

    CompletableFuture<Void> initialized() {
        return initialized;
    }

    boolean isDraining() {
        return lifecycle.get() == Lifecycle.DRAINING;
    }

    boolean rejectsStream(long streamId) {
        if (lifecycle.get() != Lifecycle.OPEN) {
            return true;
        }
        Http3GoAway goAway = localGoAway.get();
        return goAway != null && goAway.rejectsStream(streamId);
    }

    void markDraining() {
        lifecycle.compareAndSet(Lifecycle.OPEN, Lifecycle.DRAINING);
    }

    CompletableFuture<Void> sendDrainGoAway() {
        if (localGoAway.get() != null) {
            return localGoAwayDispatch;
        }
        try {
            return sendGoAway(Http3GoAway.requestStream(Http3Protocol.MAX_CLIENT_BIDIRECTIONAL_STREAM_ID),
                              "drain-start");
        } catch (IllegalStateException e) {
            logDebug(() -> "state=goaway-skip reason=drain-start current=%s"
                    .formatted(goAwaySummary(localGoAway.get())));
            return localGoAwayDispatch;
        }
    }

    CompletableFuture<Void> sendFinalGoAway() {
        if (!connection.isOpen()) {
            return CompletableFuture.completedFuture(null);
        }
        controlWriteLock.lock();
        try {
            long identifier = nextUnprocessedStreamId.get();
            Http3GoAway current = localGoAway.get();
            if (current != null) {
                identifier = Math.min(identifier, current.identifier());
            }
            return sendGoAway(Http3GoAway.requestStream(identifier), "drain-final");
        } finally {
            controlWriteLock.unlock();
        }
    }

    CompletableFuture<Void> sendGoAway(Http3GoAway goAway, String reason) {
        if (goAway.type() != Http3GoAway.Type.REQUEST_STREAM_ID) {
            throw new IllegalArgumentException("Server HTTP/3 GOAWAY requires a request stream identifier.");
        }
        CompletableFuture<Void> dispatch;
        controlWriteLock.lock();
        try {
            Http3GoAway current = localGoAway.get();
            if (current != null && !goAway.isValidSuccessorOf(current)) {
                throw new IllegalStateException("HTTP/3 GOAWAY identifier increased from "
                                                        + current.identifier()
                                                        + " to "
                                                        + goAway.identifier());
            }
            if (current != null && goAway.identifier() == current.identifier()) {
                return localGoAwayDispatch;
            }
            localGoAway.set(goAway);
            byte[] frame = Http3Protocol.goAwayFrame(goAway);
            controlWriteTail = controlWriteTail.thenCompose(_ -> initialized.thenCompose(_ -> {
                try {
                    return controlStreamWriter.scheduleForWritingAndGetDispatchCompletion(BufferData.create(frame), false);
                } catch (RuntimeException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }));
            localGoAwayDispatch = controlWriteTail;
            dispatch = controlWriteTail;
        } finally {
            controlWriteLock.unlock();
        }
        logStreamDebug(goAway.identifier(), () -> "state=goaway-send reason=%s".formatted(reason));
        dispatch.whenComplete((_, throwable) -> {
            if (throwable != null) {
                fail(throwable);
            }
        });
        return dispatch;
    }

    void fail(Throwable throwable) {
        fail(OptionalLong.empty(), throwable);
    }

    void fail(long streamId, Throwable throwable) {
        fail(OptionalLong.of(streamId), throwable);
    }

    void close() {
        IllegalStateException cause = new IllegalStateException("HTTP/3 server is closing");
        if (!closeChildren(cause, StreamOutcome.CANCELLED)) {
            return;
        }
        logDebug(() -> "state=connection-close-local activeStreams=%d".formatted(streams.size()));
        connection.terminate(QuicCloseCommand.application(Http3ErrorCode.NO_ERROR.code()));
    }

    void transportTerminated(Throwable throwable) {
        transportTerminated(throwable, null, StreamOutcome.ERROR);
    }

    void transportTerminated(Throwable throwable, Throwable transportCleanupFailure) {
        transportTerminated(throwable, transportCleanupFailure, StreamOutcome.ERROR);
    }

    void transportTerminated(Throwable throwable,
                             Throwable transportCleanupFailure,
                             StreamOutcome streamOutcome) {
        Throwable cause = throwable == null ? new ClosedChannelException() : throwable;
        if (transportCleanupFailure != null) {
            recordCleanupFailure(cause, transportCleanupFailure);
        }
        closeChildren(cause, transportCleanupFailure == null ? streamOutcome : StreamOutcome.ERROR);
        transportCleanupObserved.set(true);
        completeClosed();
        logConnectionClosed(throwable);
    }

    CompletionStage<Void> whenTerminated() {
        return terminated.minimalCompletionStage();
    }

    @Override
    public void onSettings(Http3Settings settings) {
        if (peerSettings != null) {
            throw Http3ProtocolException.connectionError(Http3ErrorCode.SETTINGS_ERROR,
                                                         "Peer HTTP/3 settings are already installed");
        }
        peerSettings = settings;
        qpackContext.peerSettings(settings.qpackMaxTableCapacity(), settings.qpackBlockedStreams());
    }

    @Override
    public void onGoAway(Http3GoAway goAway) {
        if (goAway.type() != Http3GoAway.Type.PUSH_ID) {
            throw Http3ProtocolException.connectionError(Http3ErrorCode.ID_ERROR,
                                                         "Client GOAWAY must contain a push identifier");
        }
        for (;;) {
            Http3GoAway current = peerGoAway.get();
            if (current != null && !goAway.isValidSuccessorOf(current)) {
                throw Http3ProtocolException.connectionError(Http3ErrorCode.ID_ERROR,
                                                             "Peer GOAWAY identifier increased from "
                                                                     + current.identifier()
                                                                     + " to "
                                                                     + goAway.identifier());
            }
            if (current != null && current.identifier() == goAway.identifier()) {
                return;
            }
            if (peerGoAway.compareAndSet(current, goAway)) {
                return;
            }
        }
    }

    void streamClosed(Http3ServerStream stream, Throwable streamCleanupFailure) {
        if (streamCleanupFailure != null) {
            recordCleanupFailure(streamCleanupFailure);
        }
        if (streams.remove(stream.streamId(), stream)) {
            try {
                streamLifecycle.requestCompleted(this, stream);
            } catch (RuntimeException | Error failure) {
                recordCleanupFailure(failure);
                throw failure;
            } finally {
                completeClosed();
            }
        }
    }

    void logRequestRejected(long streamId) {
        logStreamDebug(streamId,
                       () -> "state=request-rejected draining=%s goAwayStreamId=%s"
                               .formatted(isDraining(), goAwaySummary(localGoAway.get())));
    }

    void logRequestCancelled(long streamId) {
        logStreamDebug(streamId, () -> "state=request-cancelled");
    }

    void logRequestFailure(long streamId, String action, Throwable throwable) {
        logStreamDebug(streamId,
                       () -> "state=request-failure action=%s cause=%s"
                               .formatted(action, Http3RuntimeSupport.throwableSummary(throwable)));
    }

    void executeRequestCompletion(Runnable completion) {
        requestExecutor.execute(completion);
    }

    private static void addSuppressed(Throwable cause, Throwable cleanupFailure) {
        if (cause != cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        }
    }

    private static String goAwaySummary(Http3GoAway goAway) {
        return goAway == null ? "none" : "0x" + Long.toHexString(goAway.identifier());
    }

    private static void updateMax(AtomicLong target, long candidate) {
        for (;;) {
            long current = target.get();
            if (candidate <= current || target.compareAndSet(current, candidate)) {
                return;
            }
        }
    }

    private void fail(OptionalLong streamId, Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        Optional<Http3ProtocolException> protocolException = Http3ProtocolException.find(cause);
        if (protocolException.filter(it -> it.scope() == Http3ProtocolException.Scope.STREAM).isPresent()) {
            throw new IllegalArgumentException("Stream-scoped HTTP/3 signal reached the connection owner", cause);
        }
        if (!closeChildren(cause, StreamOutcome.ERROR)) {
            return;
        }
        logDebug(() -> "state=connection-fail cause=%s"
                .formatted(Http3RuntimeSupport.throwableSummary(cause)));
        Http3ErrorCode errorCode = protocolException.map(Http3ProtocolException::errorCode)
                .orElse(Http3ErrorCode.INTERNAL_ERROR);
        QuicCloseCommand command = QuicCloseCommand.application(errorCode.code(), cause);
        if (streamId.isPresent()) {
            command = command.withStream(streamId.orElseThrow());
        }
        if (config.sendErrorDetails() && cause.getMessage() != null) {
            command = command.withPeerDetail(cause.getMessage());
        }
        connection.terminate(command);
    }

    private boolean acceptRemoteStream(QuicReceiverStream stream) {
        try {
            if (stream instanceof QuicBidiStream bidiStream) {
                acceptRequestStream(bidiStream);
            } else {
                Http3StreamObservation observation = Http3ControlStreamSupport.observe(stream,
                                                                                        qpackContext,
                                                                                        peerCriticalStreams,
                                                                                        connection,
                                                                                        receiveFrameListener,
                                                                                        this);
                boolean registered;
                streamRegistrationLock.lock();
                try {
                    registered = !closing() && observations.putIfAbsent(stream.streamId(), observation) == null;
                } finally {
                    streamRegistrationLock.unlock();
                }
                if (!registered) {
                    observation.close();
                    return true;
                }
                observation.completion().whenComplete((_, throwable) -> {
                    observations.remove(stream.streamId(), observation);
                    if (throwable != null && !closing() && connection.isOpen()) {
                        fail(throwable);
                    }
                });
            }
        } catch (Throwable t) {
            fail(t);
        }
        return true;
    }

    private void acceptRequestStream(QuicBidiStream stream) {
        StreamObservation streamObservation = transportObservation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE);
        Http3ServerStream serverStream;
        try {
            serverStream = new Http3ServerStream(this,
                                                 stream,
                                                 nextRequestId.incrementAndGet(),
                                                 handler,
                                                 localSettings,
                                                 requestReadTimeout,
                                                 receiveFrameListener,
                                                 sendFrameListener,
                                                 config,
                                                 requestLimit,
                                                 streamObservation);
        } catch (RuntimeException | Error failure) {
            streamObservation.close(StreamOutcome.ERROR);
            throw failure;
        }
        if (!registerRequest(serverStream)) {
            serverStream.reject();
            return;
        }
        Http3ServerStream.RequestAdmission admission;
        try {
            admission = serverStream.admitRequest();
        } catch (RuntimeException | Error failure) {
            serverStream.requestTaskCompleted();
            throw failure;
        }
        if (admission == Http3ServerStream.RequestAdmission.REJECTED) {
            serverStream.reject();
            return;
        }
        if (admission == Http3ServerStream.RequestAdmission.CLOSED) {
            serverStream.connectionClosed(new IllegalStateException("HTTP/3 connection closed during request admission"),
                                          StreamOutcome.CANCELLED);
            serverStream.requestTaskCompleted();
            return;
        }

        StreamTask task = null;
        boolean submit = false;
        try {
            streamRegistrationLock.lock();
            try {
                if (!closing() && streams.get(serverStream.streamId()) == serverStream) {
                    task = new StreamTask(serverStream);
                    submit = streamTasks.putIfAbsent(serverStream.streamId(), task) == null;
                }
            } finally {
                streamRegistrationLock.unlock();
            }
        } catch (RuntimeException | Error failure) {
            serverStream.requestTaskCompleted();
            throw failure;
        }
        if (!submit) {
            serverStream.connectionClosed(new IllegalStateException("HTTP/3 connection closed before request dispatch"),
                                          StreamOutcome.CANCELLED);
            serverStream.requestTaskCompleted();
            return;
        }
        StreamTask submittedTask = Objects.requireNonNull(task, "request stream task");
        try {
            requestExecutor.execute(submittedTask);
        } catch (RuntimeException | Error failure) {
            submittedTask.cancel(false);
            try {
                serverStream.reject();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            fail(failure);
        }
    }

    private boolean registerRequest(Http3ServerStream stream) {
        streamRegistrationLock.lock();
        try {
            if (rejectsStream(stream.streamId()) || streams.containsKey(stream.streamId())) {
                return false;
            }
            if (!streamLifecycle.requestStarted(this, stream)) {
                return false;
            }
            boolean added = false;
            boolean registered = false;
            try {
                added = streams.putIfAbsent(stream.streamId(), stream) == null;
                if (!added) {
                    return false;
                }
                stream.activate();
                acceptedRequestStream(stream.streamId());
                registered = true;
                return true;
            } finally {
                if (!registered) {
                    if (added) {
                        streams.remove(stream.streamId(), stream);
                    }
                    streamLifecycle.requestCompleted(this, stream);
                }
            }
        } finally {
            streamRegistrationLock.unlock();
        }
    }

    private CompletableFuture<Http3ServerConnection> openAndPrimeControlStream() {
        return connection.openNewLocalUniStream(streamOpenTimeout)
                .thenApply(stream -> {
                    QuicStreamWriter writer = Http3StreamSupport.connectWriter(stream,
                                                                               connection,
                                                                               Http3StreamType.CONTROL,
                                                                               sendFrameListener);
                    writer.scheduleForWriting(BufferData.create(Http3Protocol.controlStreamPreamble(localSettings)), false);
                    controlWriteLock.lock();
                    try {
                        if (controlStream != null) {
                            throw new IllegalStateException("HTTP/3 local control stream is already initialized");
                        }
                        controlStream = stream;
                        controlStreamWriter = writer;
                    } finally {
                        controlWriteLock.unlock();
                    }
                    addLocalCriticalStream(stream, Http3StreamType.CONTROL);
                    return this;
                });
    }

    private CompletableFuture<PrimedUniStream> openAndPrimeUniStream(byte[] bytes, Http3StreamType streamType) {
        return connection.openNewLocalUniStream(streamOpenTimeout)
                .thenApply(stream -> {
                    QuicStreamWriter writer = Http3StreamSupport.connectWriter(stream,
                                                                              connection,
                                                                              streamType,
                                                                              sendFrameListener);
                    writer.scheduleForWriting(BufferData.create(bytes), false);
                    return new PrimedUniStream(stream, writer);
                });
    }

    private CompletableFuture<Http3ServerConnection> openAndPrimeQpackEncoderStream() {
        return openAndPrimeUniStream(Http3Protocol.qpackUniStreamPreamble(Http3StreamType.QPACK_ENCODER),
                                     Http3StreamType.QPACK_ENCODER)
                .thenApply(primedStream -> {
                    addLocalCriticalStream(primedStream.stream(), Http3StreamType.QPACK_ENCODER);
                    qpackContext.encoderInstructionsSender(bytes -> primedStream.writer()
                            .scheduleForWriting(BufferData.create(bytes), false));
                    return this;
                });
    }

    private CompletableFuture<Http3ServerConnection> openAndPrimeQpackDecoderStream() {
        return openAndPrimeUniStream(Http3Protocol.qpackUniStreamPreamble(Http3StreamType.QPACK_DECODER),
                                     Http3StreamType.QPACK_DECODER)
                .thenApply(primedStream -> {
                    addLocalCriticalStream(primedStream.stream(), Http3StreamType.QPACK_DECODER);
                    qpackContext.decoderInstructionsSender(bytes -> primedStream.writer()
                            .scheduleForWriting(BufferData.create(bytes), false));
                    return this;
                });
    }

    private void addLocalCriticalStream(QuicSenderStream stream, Http3StreamType streamType) {
        streamRegistrationLock.lock();
        try {
            if (closing()) {
                return;
            }
        } finally {
            streamRegistrationLock.unlock();
        }
        stream.futureSendingCompletion().whenComplete((state, throwable) -> {
            if (closing() || !connection.isOpen()) {
                return;
            }
            Throwable cause = throwable == null
                    ? new IllegalStateException("HTTP/3 local critical stream entered terminal state " + state)
                    : throwable;
            Http3ProtocolException failure = Http3ProtocolException.connectionError(
                    Http3ErrorCode.CLOSED_CRITICAL_STREAM,
                    "HTTP/3 local critical stream closed: " + streamType,
                    cause);
            if (streamType == Http3StreamType.QPACK_ENCODER || streamType == Http3StreamType.QPACK_DECODER) {
                qpackContext.instructionStreamFailed(streamType, failure);
            } else {
                fail(failure);
            }
        });
    }

    private boolean closeChildren(Throwable cause, StreamOutcome streamOutcome) {
        List<Http3ServerStream> currentStreams;
        List<StreamTask> currentTasks;
        List<Http3StreamObservation> currentObservations;
        QuicRemoteStreamRegistration registration;
        streamRegistrationLock.lock();
        try {
            Lifecycle current = lifecycle.get();
            if (current == Lifecycle.CLOSING || current == Lifecycle.CLOSED) {
                return false;
            }
            lifecycle.set(Lifecycle.CLOSING);
            currentStreams = List.copyOf(streams.values());
            currentTasks = List.copyOf(streamTasks.values());
            currentObservations = List.copyOf(observations.values());
            observations.clear();
            registration = remoteStreamRegistration;
            remoteStreamRegistration = null;
        } finally {
            streamRegistrationLock.unlock();
        }
        try {
            if (registration != null) {
                registration.close();
            }
        } catch (Throwable cleanupFailure) {
            recordCleanupFailure(cause, cleanupFailure);
        }
        for (Http3StreamObservation observation : currentObservations) {
            try {
                observation.close();
            } catch (Throwable cleanupFailure) {
                recordCleanupFailure(cause, cleanupFailure);
            }
        }
        try {
            qpackContext.close(cause);
        } catch (Throwable cleanupFailure) {
            recordCleanupFailure(cause, cleanupFailure);
        }
        try {
            peerCriticalStreams.close(cause);
        } catch (Throwable cleanupFailure) {
            recordCleanupFailure(cause, cleanupFailure);
        }
        for (Http3ServerStream stream : currentStreams) {
            try {
                stream.connectionClosed(cause, streamOutcome);
            } catch (Throwable cleanupFailure) {
                recordCleanupFailure(cause, cleanupFailure);
            }
        }
        initialized.completeExceptionally(cause);
        for (StreamTask task : currentTasks) {
            task.cancelAfterClose();
        }
        completeClosed();
        return true;
    }

    private boolean closing() {
        Lifecycle current = lifecycle.get();
        return current == Lifecycle.CLOSING || current == Lifecycle.CLOSED;
    }

    private void completeClosed() {
        if (transportCleanupObserved.get()
                && streamTasks.isEmpty()
                && streams.isEmpty()
                && lifecycle.compareAndSet(Lifecycle.CLOSING, Lifecycle.CLOSED)) {
            Throwable failure = cleanupFailure.get();
            if (failure == null) {
                terminated.complete(null);
            } else {
                terminated.completeExceptionally(failure);
            }
        }
    }

    private void recordCleanupFailure(Throwable cause, Throwable failure) {
        addSuppressed(cause, failure);
        recordCleanupFailure(failure);
    }

    private void recordCleanupFailure(Throwable failure) {
        Throwable current = cleanupFailure.get();
        while (current == null && !cleanupFailure.compareAndSet(null, failure)) {
            current = cleanupFailure.get();
        }
        if (current != null && current != failure) {
            current.addSuppressed(failure);
        }
    }

    private void acceptedRequestStream(long streamId) {
        updateMax(nextUnprocessedStreamId, streamId + 4);
    }

    private void logConnectionClosed(Throwable throwable) {
        logDebug(() -> "state=connection-close draining=%s goAwayStreamId=%s peerSettings=%s cause=%s"
                .formatted(isDraining(),
                           goAwaySummary(localGoAway.get()),
                           peerSettings != null,
                           Http3RuntimeSupport.throwableSummary(throwable)));
    }

    private void logDebug(Supplier<String> messageSupplier) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            connection.log(LOGGER, System.Logger.Level.DEBUG, "%s", messageSupplier.get());
        }
    }

    private void logStreamDebug(long streamId, Supplier<String> messageSupplier) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            connection.log(LOGGER, System.Logger.Level.DEBUG, "%d: %s", streamId, messageSupplier.get());
        }
    }

    private enum Lifecycle {
        OPEN,
        DRAINING,
        CLOSING,
        CLOSED
    }

    interface StreamLifecycle {
        boolean requestStarted(Http3ServerConnection connection, Http3ServerStream stream);

        void requestCompleted(Http3ServerConnection connection, Http3ServerStream stream);
    }

    private record PrimedUniStream(QuicSenderStream stream, QuicStreamWriter writer) {
    }

    private final class StreamTask extends FutureTask<Void> {
        private final Http3ServerStream serverStream;
        private final long streamId;
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile Thread runner;

        private StreamTask(Http3ServerStream stream) {
            super(stream, null);
            this.serverStream = stream;
            this.streamId = stream.streamId();
        }

        @Override
        public void run() {
            if (finished.get() || !running.compareAndSet(false, true)) {
                return;
            }
            runner = Thread.currentThread();
            try {
                super.run();
            } finally {
                runner = null;
                completeTask();
            }
        }

        @Override
        protected void done() {
            if (!running.get()) {
                completeTask();
            }
        }

        private void completeTask() {
            if (finished.compareAndSet(false, true)) {
                serverStream.requestTaskCompleted();
                streamTasks.remove(streamId, this);
                completeClosed();
            }
        }

        private void cancelAfterClose() {
            cancel(runner != Thread.currentThread());
        }
    }
}
