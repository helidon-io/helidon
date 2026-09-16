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

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.PeerInfo;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamException;
import io.helidon.quic.stream.QuicStreamReader;
import io.helidon.quic.stream.QuicStreamWriter;

final class QuicApplicationSession implements QuicSession {
    private final QuicConnection delegate;
    private final Duration defaultStreamOpenTimeout;
    private final String applicationProtocol;
    private final QuicVersion quicVersion;
    private final CompletionStage<QuicSessionTermination> terminationStage;
    private final ReentrantLock acceptOperationLock = new ReentrantLock();
    private final ReentrantLock acceptStateLock = new ReentrantLock();

    private MinimalFuture<QuicReceiverStream> pendingAccept;

    QuicApplicationSession(QuicConnection delegate, Duration defaultStreamOpenTimeout) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.defaultStreamOpenTimeout = Objects.requireNonNull(defaultStreamOpenTimeout, "defaultStreamOpenTimeout");
        this.applicationProtocol = delegate.applicationProtocol()
                .orElseThrow(() -> new QuicException("QUIC handshake completed without an application protocol"));
        this.quicVersion = Objects.requireNonNull(delegate.quicVersion(), "negotiated QUIC version");
        CompletionStage<QuicTermination> delegateTermination =
                Objects.requireNonNull(delegate.whenTerminated(), "termination stage");
        this.terminationStage = mapStage(delegateTermination, ApplicationTermination::new);
        delegateTermination.whenComplete((termination, failure) -> failPendingAccept(termination, failure));
    }

    static Throwable publicFailure(Throwable failure) {
        Throwable unwrapped = Objects.requireNonNull(failure, "failure");
        while ((unwrapped instanceof CompletionException || unwrapped instanceof ExecutionException)
                && unwrapped.getCause() != null) {
            unwrapped = unwrapped.getCause();
        }
        if (unwrapped instanceof QuicStreamException streamFailure) {
            return new QuicStreamTerminationException(
                    streamFailure.streamId(),
                    switch (streamFailure.kind()) {
                        case CLOSED -> QuicStreamTerminationException.Kind.CLOSED;
                        case RESET_LOCALLY -> QuicStreamTerminationException.Kind.RESET_LOCALLY;
                        case RESET_BY_PEER -> QuicStreamTerminationException.Kind.RESET_BY_PEER;
                        case STOP_SENDING -> QuicStreamTerminationException.Kind.STOP_SENDING;
                    },
                    streamFailure.errorCode(),
                    failureMessage(streamFailure),
                    streamFailure);
        }
        if (unwrapped instanceof QuicException
                || unwrapped instanceof CancellationException
                || unwrapped instanceof Error) {
            return unwrapped;
        }
        return new QuicException(failureMessage(unwrapped), unwrapped);
    }

    static RuntimeException runtimeFailure(RuntimeException failure) {
        Throwable mapped = publicFailure(failure);
        return mapped instanceof RuntimeException runtimeException
                ? runtimeException
                : new QuicException(failureMessage(mapped), mapped);
    }

    @Override
    public String applicationProtocol() {
        return applicationProtocol;
    }

    @Override
    public QuicVersion quicVersion() {
        return quicVersion;
    }

    @Override
    public QuicBidirectionalStream openBidirectionalStream() {
        return openBidirectionalStream(defaultStreamOpenTimeout);
    }

    @Override
    public QuicBidirectionalStream openBidirectionalStream(Duration streamCreditTimeout) {
        QuicPublicApiSupport.positiveNanosDuration(streamCreditTimeout, "streamCreditTimeout");
        var opened = delegate.openNewLocalBidiStream(streamCreditTimeout);
        var stream = QuicBlockingSupport.await(opened,
                                               streamCreditTimeout,
                                               () -> {
                                                   if (opened.isDone() && !opened.isCompletedExceptionally()) {
                                                       QuicConnectionImpl.closeUnclaimedLocalStream(opened.resultNow());
                                                   }
                                               },
                                               "QUIC bidirectional stream open");
        return new ApplicationBidiStream(stream);
    }

    @Override
    public QuicSendStream openUnidirectionalStream() {
        return openUnidirectionalStream(defaultStreamOpenTimeout);
    }

    @Override
    public QuicSendStream openUnidirectionalStream(Duration streamCreditTimeout) {
        QuicPublicApiSupport.positiveNanosDuration(streamCreditTimeout, "streamCreditTimeout");
        var opened = delegate.openNewLocalUniStream(streamCreditTimeout);
        var stream = QuicBlockingSupport.await(opened,
                                               streamCreditTimeout,
                                               () -> {
                                                   if (opened.isDone() && !opened.isCompletedExceptionally()) {
                                                       QuicConnectionImpl.closeUnclaimedLocalStream(opened.resultNow());
                                                   }
                                               },
                                               "QUIC unidirectional stream open");
        return new ApplicationSendStream(stream);
    }

    @Override
    public QuicReceiveStream acceptStream() {
        if (!acceptOperationLock.tryLock()) {
            throw new IllegalStateException("A QUIC remote stream accept is already pending");
        }
        MinimalFuture<QuicReceiverStream> accepted = MinimalFuture.create();
        QuicRemoteStreamRegistration registration = null;
        try {
            acceptStateLock.lock();
            try {
                if (pendingAccept != null) {
                    throw new IllegalStateException("A QUIC remote stream accept is already pending");
                }
                pendingAccept = accepted;
            } finally {
                acceptStateLock.unlock();
            }
            registration = delegate.addRemoteStreamListener(accepted::complete);
            var stream = QuicBlockingSupport.await(accepted,
                                                   true,
                                                   () -> { },
                                                   "QUIC remote stream accept");
            return wrapReceiveStream(stream);
        } catch (QuicException failure) {
            if (accepted.isDone() && !accepted.isCompletedExceptionally()) {
                try {
                    closeUnclaimedRemoteStream(accepted.resultNow());
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        } finally {
            if (registration != null) {
                registration.close();
            }
            acceptStateLock.lock();
            try {
                if (pendingAccept == accepted) {
                    pendingAccept = null;
                }
            } finally {
                acceptStateLock.unlock();
            }
            acceptOperationLock.unlock();
        }
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public Optional<QuicSessionTermination> termination() {
        return delegate.termination().map(ApplicationTermination::new);
    }

    @Override
    public CompletionStage<QuicSessionTermination> whenTerminated() {
        return terminationStage;
    }

    @Override
    public QuicSession close(long applicationErrorCode) {
        delegate.terminate(QuicCloseCommand.application(
                QuicPublicApiSupport.applicationErrorCode(applicationErrorCode)));
        return this;
    }

    @Override
    public QuicSession close(long applicationErrorCode, String peerDetail) {
        Objects.requireNonNull(peerDetail, "peerDetail");
        delegate.terminate(QuicCloseCommand.application(
                        QuicPublicApiSupport.applicationErrorCode(applicationErrorCode))
                                   .withPeerDetail(peerDetail));
        return this;
    }

    @Override
    public PeerInfo remotePeer() {
        return delegate.remotePeer();
    }

    @Override
    public PeerInfo localPeer() {
        return delegate.localPeer();
    }

    @Override
    public boolean isSecure() {
        return delegate.isSecure();
    }

    @Override
    public String socketId() {
        return delegate.socketId();
    }

    @Override
    public String childSocketId() {
        return delegate.childSocketId();
    }

    private static void closeUnclaimedRemoteStream(QuicReceiverStream stream) {
        try {
            if (stream instanceof QuicSenderStream sender) {
                sender.reset(0);
            }
        } finally {
            stream.requestStopSending(0);
        }
    }

    private static QuicReceiveStream wrapReceiveStream(QuicReceiverStream stream) {
        if (stream instanceof QuicBidiStream bidiStream) {
            return new ApplicationBidiStream(bidiStream);
        }
        return new ApplicationReceiveStream(stream);
    }

    private static <T, R> CompletionStage<R> mapStage(CompletionStage<T> source,
                                                       Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mapper, "mapper");
        MinimalFuture<R> result = MinimalFuture.create();
        source.whenComplete((value, failure) -> {
            if (failure != null) {
                result.completeExceptionally(publicFailure(failure));
                return;
            }
            try {
                result.complete(Objects.requireNonNull(mapper.apply(value), "mapped QUIC result"));
            } catch (Throwable mappingFailure) {
                result.completeExceptionally(publicFailure(mappingFailure));
            }
        });
        return result.minimalCompletionStage();
    }

    private static void lockInterruptibly(ReentrantLock lock, String operation) {
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QuicException(operation + " interrupted", e);
        }
    }

    private static String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }

    private void failPendingAccept(QuicTermination termination, Throwable failure) {
        MinimalFuture<QuicReceiverStream> accepted;
        acceptStateLock.lock();
        try {
            accepted = pendingAccept;
            pendingAccept = null;
        } finally {
            acceptStateLock.unlock();
        }
        if (accepted == null) {
            return;
        }
        if (failure == null) {
            accepted.completeExceptionally(termination.closeCause());
        } else {
            accepted.completeExceptionally(publicFailure(failure));
        }
    }

    private abstract static class ApplicationStream implements QuicStream {
        private final io.helidon.quic.stream.QuicStream delegate;

        private ApplicationStream(io.helidon.quic.stream.QuicStream delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public final long streamId() {
            return delegate.streamId();
        }

        @Override
        public final boolean clientInitiated() {
            return delegate.isClientInitiated();
        }

        @Override
        public final boolean locallyInitiated() {
            return delegate.isLocalInitiated();
        }

        @Override
        public final boolean bidirectional() {
            return delegate.isBidirectional();
        }
    }

    private static final class ApplicationReceiveStream extends ApplicationStream implements QuicReceiveStream {
        private final ApplicationReader reader;

        private ApplicationReceiveStream(QuicReceiverStream delegate) {
            super(delegate);
            this.reader = new ApplicationReader(delegate);
        }

        @Override
        public Optional<BufferData> read() {
            return reader.read();
        }

        @Override
        public void stopReading(long applicationErrorCode) {
            reader.stopReading(applicationErrorCode);
        }
    }

    private static final class ApplicationSendStream extends ApplicationStream implements QuicSendStream {
        private final ApplicationWriter writer;

        private ApplicationSendStream(QuicSenderStream delegate) {
            super(delegate);
            this.writer = new ApplicationWriter(delegate);
        }

        @Override
        public void write(BufferData data) {
            writer.write(data, false);
        }

        @Override
        public void writeFinal(BufferData data) {
            writer.write(data, true);
        }

        @Override
        public void finish() {
            writer.write(BufferData.empty(), true);
        }

        @Override
        public void reset(long applicationErrorCode) {
            writer.reset(applicationErrorCode);
        }

        @Override
        public boolean stopSendingReceived() {
            return writer.stopSendingReceived();
        }

        @Override
        public CompletionStage<Long> whenStopSendingReceived() {
            return writer.whenStopSendingReceived();
        }
    }

    private static final class ApplicationBidiStream extends ApplicationStream implements QuicBidirectionalStream {
        private final ApplicationReader reader;
        private final ApplicationWriter writer;

        private ApplicationBidiStream(QuicBidiStream delegate) {
            super(delegate);
            this.reader = new ApplicationReader(delegate);
            this.writer = new ApplicationWriter(delegate);
        }

        @Override
        public Optional<BufferData> read() {
            return reader.read();
        }

        @Override
        public void stopReading(long applicationErrorCode) {
            reader.stopReading(applicationErrorCode);
        }

        @Override
        public void write(BufferData data) {
            writer.write(data, false);
        }

        @Override
        public void writeFinal(BufferData data) {
            writer.write(data, true);
        }

        @Override
        public void finish() {
            writer.write(BufferData.empty(), true);
        }

        @Override
        public void reset(long applicationErrorCode) {
            writer.reset(applicationErrorCode);
        }

        @Override
        public boolean stopSendingReceived() {
            return writer.stopSendingReceived();
        }

        @Override
        public CompletionStage<Long> whenStopSendingReceived() {
            return writer.whenStopSendingReceived();
        }
    }

    private static final class ApplicationReader {
        private final QuicReceiverStream stream;
        private final ReentrantLock operationLock = new ReentrantLock();
        private final ReentrantLock stateLock = new ReentrantLock();
        private final Condition readCompleted = stateLock.newCondition();
        private final SequentialScheduler scheduler;

        private QuicStreamReader reader;
        private boolean readPending;
        private boolean outcomeReady;
        private Optional<BufferData> completedValue;
        private Throwable completedFailure;

        private ApplicationReader(QuicReceiverStream stream) {
            this.stream = Objects.requireNonNull(stream, "stream");
            this.scheduler = SequentialScheduler.lockingScheduler(this::drain);
        }

        private Optional<BufferData> read() {
            lockInterruptibly(operationLock, "QUIC stream read");
            try {
                QuicStreamReader connectedReader = null;
                boolean start = false;
                stateLock.lock();
                try {
                    if (!outcomeReady) {
                        if (reader == null) {
                            reader = stream.connectReader(scheduler);
                        }
                        connectedReader = reader;
                        start = !connectedReader.started();
                        readPending = true;
                    }
                } catch (RuntimeException failure) {
                    readPending = false;
                    throw runtimeFailure(failure);
                } finally {
                    stateLock.unlock();
                }
                if (connectedReader != null) {
                    try {
                        if (start) {
                            connectedReader.start();
                        } else {
                            scheduler.runOrSchedule();
                        }
                    } catch (Throwable failure) {
                        completeReadFailure(failure);
                    }
                }

                try {
                    stateLock.lockInterruptibly();
                } catch (InterruptedException e) {
                    stateLock.lock();
                    try {
                        readPending = false;
                    } finally {
                        stateLock.unlock();
                    }
                    Thread.currentThread().interrupt();
                    throw new QuicException("QUIC stream read interrupted", e);
                }
                try {
                    while (!outcomeReady) {
                        try {
                            readCompleted.await();
                        } catch (InterruptedException e) {
                            readPending = false;
                            Thread.currentThread().interrupt();
                            throw new QuicException("QUIC stream read interrupted", e);
                        }
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        throw new QuicException("QUIC stream read interrupted", new InterruptedException());
                    }
                    Optional<BufferData> result = completedValue;
                    Throwable failure = completedFailure;
                    completedValue = null;
                    completedFailure = null;
                    outcomeReady = false;
                    if (failure instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    if (failure instanceof Error error) {
                        throw error;
                    }
                    if (failure != null) {
                        throw new QuicException(failureMessage(failure), failure);
                    }
                    return result;
                } finally {
                    stateLock.unlock();
                }
            } finally {
                operationLock.unlock();
            }
        }

        private void stopReading(long applicationErrorCode) {
            try {
                stream.requestStopSending(QuicPublicApiSupport.applicationErrorCode(applicationErrorCode));
            } catch (RuntimeException failure) {
                throw runtimeFailure(failure);
            }
        }

        private void drain() {
            stateLock.lock();
            try {
                if (!readPending || outcomeReady) {
                    return;
                }
                try {
                    Optional<BufferData> next = reader.poll();
                    if (next.isEmpty()) {
                        return;
                    }
                    BufferData buffer = next.orElseThrow();
                    this.completedValue = buffer == QuicStreamReader.EOF
                            ? Optional.empty()
                            : Optional.of(buffer);
                } catch (Throwable failure) {
                    completedFailure = publicFailure(failure);
                }
                readPending = false;
                outcomeReady = true;
                readCompleted.signalAll();
            } finally {
                stateLock.unlock();
            }
        }

        private void completeReadFailure(Throwable failure) {
            stateLock.lock();
            try {
                if (readPending && !outcomeReady) {
                    readPending = false;
                    completedFailure = publicFailure(failure);
                    outcomeReady = true;
                    readCompleted.signalAll();
                }
            } finally {
                stateLock.unlock();
            }
        }
    }

    private static final class ApplicationWriter {
        private final QuicSenderStream stream;
        private final CompletionStage<Long> stopSendingStage;
        private final ReentrantLock operationLock = new ReentrantLock();

        private QuicStreamWriter writer;
        private CompletionStage<Void> pendingWrite;

        private ApplicationWriter(QuicSenderStream stream) {
            this.stream = Objects.requireNonNull(stream, "stream");
            this.stopSendingStage = mapStage(stream.whenStopSendingReceived(), Function.identity());
        }

        private void write(BufferData data, boolean last) {
            Objects.requireNonNull(data, "data");
            lockInterruptibly(operationLock, "QUIC stream write");
            try {
                if (pendingWrite != null) {
                    QuicBlockingSupport.await(pendingWrite,
                                              false,
                                              () -> { },
                                              "previous QUIC stream write");
                    pendingWrite = null;
                }
                try {
                    pendingWrite = writer().scheduleForWritingAndGetDispatchCompletion(data, last);
                } catch (RuntimeException failure) {
                    throw runtimeFailure(failure);
                }
                QuicBlockingSupport.await(pendingWrite, false, () -> { }, "QUIC stream write");
                pendingWrite = null;
            } finally {
                operationLock.unlock();
            }
        }

        private void reset(long applicationErrorCode) {
            try {
                stream.reset(QuicPublicApiSupport.applicationErrorCode(applicationErrorCode));
            } catch (RuntimeException failure) {
                throw runtimeFailure(failure);
            }
        }

        private boolean stopSendingReceived() {
            try {
                return stream.stopSendingReceived();
            } catch (RuntimeException failure) {
                throw runtimeFailure(failure);
            }
        }

        private CompletionStage<Long> whenStopSendingReceived() {
            return stopSendingStage;
        }

        private QuicStreamWriter writer() {
            if (writer == null) {
                writer = stream.connectWriter(SequentialScheduler.create(completer -> completer.complete()));
            }
            return writer;
        }
    }

    private static final class ApplicationTermination implements QuicSessionTermination {
        private final QuicTermination delegate;

        private ApplicationTermination(QuicTermination delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public Origin origin() {
            return switch (delegate.origin()) {
                case LOCAL -> Origin.LOCAL;
                case PEER -> Origin.PEER;
            };
        }

        @Override
        public Kind kind() {
            return switch (delegate.kind()) {
                case CONNECTION_CLOSE -> Kind.CONNECTION_CLOSE;
                case SILENT -> Kind.SILENT;
                case STATELESS_RESET -> Kind.STATELESS_RESET;
            };
        }

        @Override
        public Layer layer() {
            return switch (delegate.layer()) {
                case TRANSPORT -> Layer.TRANSPORT;
                case APPLICATION -> Layer.APPLICATION;
            };
        }

        @Override
        public OptionalLong errorCode() {
            return delegate.errorCode();
        }

        @Override
        public OptionalLong streamId() {
            return delegate.streamId();
        }

        @Override
        public Optional<String> outgoingDetail() {
            return delegate.outgoingDetail();
        }

        @Override
        public Optional<String> peerReason() {
            return delegate.peerReason();
        }

        @Override
        public String diagnostic() {
            return delegate.logMessage();
        }
    }
}
