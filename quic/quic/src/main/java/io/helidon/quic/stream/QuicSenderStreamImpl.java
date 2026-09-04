/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

package io.helidon.quic.stream;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.quic.QuicConnectionImpl;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.SequentialScheduler;

/**
 * A class that implements the sending part of a quic stream.
 */
@Api.Internal
public final class QuicSenderStreamImpl extends AbstractQuicStream implements QuicSenderStream {
    private static final System.Logger LOGGER = System.getLogger(QuicSenderStreamImpl.class.getName());

    private final StreamWriterQueueImpl queue;
    private final CompletableFuture<SendingStreamState> sendingCompletion = new CompletableFuture<>();
    private final CompletableFuture<Long> stopSending = new CompletableFuture<>();
    private final ReentrantLock resetPublicationLock = new ReentrantLock();
    private volatile SendingStreamState sendingState;
    private volatile QuicStreamWriterImpl writer;
    private volatile long errorCode;
    private volatile boolean stopSendingReceived;
    private volatile boolean readyForSending;

    QuicSenderStreamImpl(QuicConnectionImpl connection, long streamId, int streamBufferSize) {
        super(connection, validateStreamId(connection, streamId));
        queue = new StreamWriterQueueImpl(streamBufferSize);
        errorCode = -1;
        sendingState = SendingStreamState.READY;
    }

    @Override
    public SendingStreamState sendingState() {
        return sendingState;
    }

    @Override
    public QuicStreamWriter connectWriter(SequentialScheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        var writer = this.writer;
        if (writer == null) {
            writer = new QuicStreamWriterImpl(scheduler);
            if (Handles.WRITER.compareAndSet(this, null, writer)) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "writer connected");
                }
                return writer;
            }
        }
        throw new IllegalStateException("writer already connected");
    }

    @Override
    public void disconnectWriter(QuicStreamWriter writer) {
        Objects.requireNonNull(writer, "writer");
        var previous = this.writer;
        if (writer == previous) {
            if (Handles.WRITER.compareAndSet(this, writer, null)) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "writer disconnected");
                }
                return;
            }
        }
        throw new IllegalStateException("writer not connected");
    }

    @Override
    public void reset(long errorCode) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "Resetting due to %s",
                      connection().appErrorToString(errorCode));
        }
        connection().runWithStreamDispatchLock(() -> {
            resetPublicationLock.lock();
            try {
                SendingStreamState state = sendingState;
                if (state.isReset() || state.isTerminal()) {
                    return;
                }
                if (switchSendingState(SendingStreamState.RESET_SENT)) {
                    errorCode(errorCode);
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "Requesting to send RESET_STREAM(errorCode=%d)",
                                  errorCode);
                    }
                    try {
                        if (connection().isOpen()) {
                            connection().requestResetStream(streamId(), errorCode);
                        }
                    } finally {
                        queue.markReset(errorCode);
                    }
                }
            } finally {
                resetPublicationLock.unlock();
            }
        });
    }

    @Override
    public long sndErrorCode() {
        resetPublicationLock.lock();
        try {
            return errorCode;
        } finally {
            resetPublicationLock.unlock();
        }
    }

    @Override
    public boolean stopSendingReceived() {
        return stopSendingReceived;
    }

    @Override
    public CompletionStage<Long> whenStopSendingReceived() {
        return stopSending.minimalCompletionStage();
    }

    @Override
    public CompletableFuture<SendingStreamState> futureSendingCompletion() {
        return sendingCompletion;
    }

    @Override
    public long dataSent() {
        // returns the amount of data that has been submitted for
        // sending downstream. This will be the amount of data that
        // has been consumed by the downstream consumer.
        return queue.bytesConsumed();
    }

    /**
     * Called to set the max stream data for this stream.
     *
     * @param newLimit the proposed new max stream data
     * @return the new limit that has been finalized for max stream data.
     *        This new limit may or may not have been increased to the proposed {@code newLimit}.
     * <p>Note: as per RFC 9000, any value less than the current
     *        max stream data is ignored
     */
    public long updateMaxStreamData(long newLimit) {
        return queue.updateMaxStreamData(newLimit);
    }

    /**
     * Called by {@link QuicConnectionStreams} after a RESET_STREAM frame
     * has been sent.
     */
    public void resetSent() {
        queue.markReset(errorCode);
        queue.close(QuicStreamException.resetLocally(streamId(), errorCode));
    }

    /**
     * Called when the packet containing the RESET_STREAM frame for this
     * stream has been acknowledged.
     *
     * @param finalSize the final size acknowledged
     * @return true if the state was switched to RESET_RECVD as a result
     *        of this method invocation
     */
    public boolean resetAcknowledged(long finalSize) {
        long queueSize = queue.bytesConsumed();
        if (switchSendingState(SendingStreamState.RESET_RECVD)) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "Reset received: final: %d, processed: %d",
                          finalSize, queueSize);
            }
            if (finalSize != queueSize) {
                if (LOGGER.isLoggable(System.Logger.Level.ERROR)) {
                    log(System.Logger.Level.ERROR,
                              "Acknowledged reset has wrong size: acked: %d, expected: %d",
                              finalSize,
                              queueSize);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Called when the packet containing the final STREAM frame for this
     * stream has been acknowledged.
     *
     * @param finalSize the final size acknowledged
     * @return true if the state was switched to DATA_RECVD as a result
     *        of this method invocation
     */
    public boolean dataAcknowledged(long finalSize) {
        queue.markDispatched(finalSize, true);
        long queueSize = queue.bytesConsumed();
        if (switchSendingState(SendingStreamState.DATA_RECVD)) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "Last data received: final: %d, processed: %d",
                          finalSize, queueSize);
            }
            if (finalSize != queueSize) {
                if (LOGGER.isLoggable(System.Logger.Level.ERROR)) {
                    log(System.Logger.Level.ERROR,
                              "Acknowledged data has wrong size: acked: %d, expected: %d",
                              finalSize,
                              queueSize);
                }
            }
        }
        return false;
    }

    /**
     * Called when a STOP_SENDING frame is received from the peer.
     *
     * @param errorCode the error code
     */
    public void stopSendingReceived(long errorCode) {
        errorCode(errorCode);
        stopSendingReceived = true;
        try {
            if (queue.stopSending(errorCode)) {
                try {
                    if (connection().isOpen()) {
                        reset(errorCode);
                    }
                } finally {
                    QuicStreamWriterImpl writer = this.writer;
                    if (writer != null) {
                        writer.wakeupWriter();
                    }
                }
            }
        } finally {
            stopSending.complete(errorCode);
        }
    }

    /**
     * Returns the number of bytes that are available for sending, subject to flow control.
     *
     * @return number of bytes that are available for sending, subject to flow control
     * @implSpec This method does not return more than what flow control for this
     *        stream would allow at the time the method is called.
     * @implNote If the sender part is not finished initializing the default
     *        implementation of this method will return 0.
     */
    public long available() {
        return queue.readyToSend();
    }

    /**
     * Whether the sending is blocked due to flow control.
     *
     * @return {@code true} if sending is blocked due to flow control
     */
    public boolean isBlocked() {
        return queue.consumerBlocked();
    }

    /**
     * Returns the size of this stream, if known.
     *
     * @return size of this stream, if known
     * @implSpec This method returns {@code -1} if the size of the stream is not
     *        known.
     */
    public long streamSize() {
        return queue.streamSize();
    }

    /**
     * Polls at most {@code maxBytes} from the {@link StreamWriterQueue} of
     * this stream. The semantics are equivalent to that of {@link
     * StreamWriterQueue#poll(int)}
     *
     * @param maxBytes the maximum number of bytes to poll for sending
     * @return a ByteBuffer containing at most {@code maxBytes} remaining
     *        bytes.
     */
    public ByteBuffer poll(int maxBytes) {
        return queue.poll(maxBytes);
    }

    boolean hasPendingZeroLengthEndOfStream() {
        return queue.hasPendingZeroLengthEndOfStream();
    }

    boolean markReadyForSending() {
        return Handles.READY_FOR_SENDING.compareAndSet(this, false, true);
    }

    void clearReadyForSending() {
        Handles.READY_FOR_SENDING.setRelease(this, false);
    }

    void markDispatched(long offset, boolean endOfStream) {
        queue.markDispatched(offset, endOfStream);
    }

    @Override
    public boolean isDone() {
        return switch (sendingState()) {
            case DATA_RECVD, RESET_RECVD ->
                // everything acknowledged
                    true;
            default ->
                // the stream is only half closed
                    false;
        };
    }

    @Override
    public StreamState state() {
        return sendingState();
    }

    private void log(System.Logger.Level level, String format, Object... arguments) {
        if (!LOGGER.isLoggable(level)) {
            return;
        }
        if (arguments.length == 0) {
            connection().log(LOGGER, level, "%d: %s", streamId(), format);
            return;
        }
        Object[] actualArguments = new Object[arguments.length + 1];
        actualArguments[0] = streamId();
        System.arraycopy(arguments, 0, actualArguments, 1, arguments.length);
        connection().log(LOGGER, level, "%d: " + format, actualArguments);
    }

    private void logDebug(String format, Object... arguments) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, format, arguments);
        }
    }

    /**
     * Called when the connection is closed locally.
     *
     * @param termination selected connection termination
     */
    void terminate(QuicTermination termination) {
        termination.errorCode().ifPresent(this::errorCode);
        queue.close(termination.closeCause());
        sendingCompletion.completeExceptionally(termination.closeCause());
        stopSending.completeExceptionally(termination.closeCause());
        QuicStreamWriterImpl writer = this.writer;
        if (writer != null) {
            writer.wakeupWriter();
        }
    }

    void checkOpened() {
        Optional<QuicTermination> termination = connection().termination();
        if (termination.isEmpty()) {
            return;
        }
        throw termination.orElseThrow().closeCause();
    }

    private static long validateStreamId(QuicConnectionImpl connection, long streamId) {
        if (QuicStreams.isBidirectional(streamId)) {
            return streamId;
        }
        if (connection.isClientConnection() != QuicStreams.isClientInitiated(streamId)) {
            throw new IllegalArgumentException("A remotely initiated stream can't be write-only");
        }
        return streamId;
    }

    /**
     * Called when some data is submitted (or offered) by the
     * producer. If the stream is in the READY state, this will
     * switch the sending state to SEND.
     *
     * @param last whether there will be no further data submitted
     *            by the producer.
     * @return the state before switching to SEND.
     * @implNote The parameter {@code last} is ignored at this stage.
     *        {@link #switchSendingState(SendingStreamState)
     *        switchSendingState(SendingStreamState.DATA_SENT)} will be called
     *        later on when the last piece of data has been pushed downstream.
     */
    private SendingStreamState sending(boolean last) {
        SendingStreamState state = sendingState;
        if (state == SendingStreamState.READY) {
            switchSendingState(SendingStreamState.SEND);
        }
        return state;
    }

    /**
     * Called when the StreamWriterQueue implementation notifies of
     * a state change.
     *
     * @param newState the new state, according to the StreamWriterQueue.
     */
    private boolean switchSendingState(SendingStreamState newState) {
        SendingStreamState oldState = sendingState;
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "switchSendingState %s -> %s",
                      oldState, newState);
        }
        boolean switched = switch (newState) {
            case SEND -> markSending();
            case DATA_SENT -> markDataSent();
            case DATA_RECVD -> markDataRecvd();
            case RESET_SENT -> markResetSent();
            case RESET_RECVD -> markResetRecvd();
            default -> throw new UnsupportedOperationException("switch state to " + newState.text());
        };
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            if (switched) {
                log(System.Logger.Level.DEBUG, "switched sending state from %s to %s", oldState, newState);
            } else {
                log(System.Logger.Level.DEBUG, "sending state not switched; state is %s", sendingState);
            }
        }

        if (switched && newState.isTerminal()) {
            notifyTerminalState(newState);
        }

        return switched;
    }

    private void notifyTerminalState(SendingStreamState state) {
        sendingCompletion.complete(state);
        connection().notifyTerminalState(streamId(), state);
    }

    // SEND can only be set from the READY state
    private boolean markSending() {
        boolean done;
        boolean switched = false;
        SendingStreamState oldState;
        do {
            oldState = sendingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case READY -> {
                    switched = Handles.SENDING_STATE.compareAndSet(this,
                                                                    oldState, SendingStreamState.SEND);
                    yield switched;
                }
                case SEND, RESET_RECVD, RESET_SENT -> true;
                // there should be no further submission of data after DATA_SENT
                case DATA_SENT, DATA_RECVD -> throw new IllegalStateException(oldState.text());
            };
        } while (!done);
        return switched;
    }

    // DATA_SENT can only be set from the SEND state
    private boolean markDataSent() {
        boolean done;
        boolean switched = false;
        SendingStreamState oldState;
        do {
            oldState = sendingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case SEND -> {
                    switched = Handles.SENDING_STATE.compareAndSet(this,
                                                                    oldState, SendingStreamState.DATA_SENT);
                    yield switched;
                }
                case DATA_SENT, RESET_RECVD, RESET_SENT, DATA_RECVD -> true;
                case READY -> throw new IllegalStateException(oldState.text());
            };
        } while (!done);
        return switched;
    }

    // Reset can only be set in the READY, SEND, or DATA_SENT state
    private boolean markResetSent() {
        boolean done;
        boolean switched = false;
        SendingStreamState oldState;
        do {
            oldState = sendingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case READY, SEND, DATA_SENT -> {
                    switched = Handles.SENDING_STATE.compareAndSet(this,
                                                                    oldState, SendingStreamState.RESET_SENT);
                    yield switched;
                }
                case RESET_RECVD, RESET_SENT, DATA_RECVD -> true;
            };
        } while (!done);
        return switched;
    }

    // Called when the packet containing the last frame is acknowledged
    // DATA_RECVD is a terminal state
    private boolean markDataRecvd() {
        boolean done;
        boolean switched = false;
        SendingStreamState oldState;
        do {
            oldState = sendingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case DATA_SENT, RESET_SENT -> {
                    switched = Handles.SENDING_STATE.compareAndSet(this,
                                                                    oldState, SendingStreamState.DATA_RECVD);
                    yield switched;
                }
                case RESET_RECVD, DATA_RECVD -> true;
                default -> throw new IllegalStateException("%s: %s -> %s"
                                                                   .formatted(streamId(),
                                                                              oldState.text(),
                                                                              SendingStreamState.RESET_RECVD.text()));
            };
        } while (!done);
        return switched;
    }

    // Called when the packet containing the reset frame is acknowledged
    // RESET_RECVD is a terminal state
    private boolean markResetRecvd() {
        boolean done;
        boolean switched = false;
        SendingStreamState oldState;
        do {
            oldState = sendingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case DATA_SENT, RESET_SENT -> {
                    switched = Handles.SENDING_STATE.compareAndSet(this,
                                                                    oldState, SendingStreamState.RESET_RECVD);
                    yield switched;
                }
                case RESET_RECVD, DATA_RECVD -> true;
                default -> throw new IllegalStateException("%s: %s -> %s"
                                                                   .formatted(streamId(),
                                                                              oldState.text(),
                                                                              SendingStreamState.RESET_RECVD.text()));
            };
        } while (!done);
        return switched;
    }

    private void errorCode(long code) {
        Handles.ERROR_CODE.compareAndSet(this, -1, code);
    }

    // Some VarHandles to implement CAS semantics on top of plain
    // volatile fields in this class.
    private static class Handles {
        static final VarHandle SENDING_STATE;
        static final VarHandle WRITER;
        static final VarHandle ERROR_CODE;
        static final VarHandle READY_FOR_SENDING;

        static {
            Lookup lookup = MethodHandles.lookup();
            try {
                SENDING_STATE = lookup.findVarHandle(QuicSenderStreamImpl.class,
                                                     "sendingState", SendingStreamState.class);
                WRITER = lookup.findVarHandle(QuicSenderStreamImpl.class,
                                              "writer", QuicStreamWriterImpl.class);
                ERROR_CODE = lookup.findVarHandle(QuicSenderStreamImpl.class,
                                                  "errorCode", long.class);
                READY_FOR_SENDING = lookup.findVarHandle(QuicSenderStreamImpl.class,
                                                         "readyForSending", boolean.class);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    /**
     * A concrete implementation of the {@link StreamWriterQueue} for this
     * stream.
     */
    private final class StreamWriterQueueImpl extends StreamWriterQueue {
        private StreamWriterQueueImpl(int streamBufferSize) {
            super(streamBufferSize);
        }

        @Override
        protected void wakeupProducer() {
            // The scheduler is provided by the producer
            // to wakeup and run the producer's write loop.
            var writer = QuicSenderStreamImpl.this.writer;
            if (writer != null) {
                writer.wakeupWriter();
            }
        }

        @Override
        protected void logDebug(String format, Object... arguments) {
            QuicSenderStreamImpl.this.logDebug(format, arguments);
        }

        @Override
        protected void wakeupConsumer() {
            // Notify the connection impl that either the data is available
            // for writing or the stream is blocked and the peer needs to be
            // made aware. The connection should
            // eventually call QuicSenderStreamImpl::poll to
            // get the data available for writing and package it
            // in a StreamFrame or notice that the stream is blocked and send a
            // STREAM_DATA_BLOCKED frame.
            connection().streamDataAvailableForSending(QuicSenderStreamImpl.this);
        }

        @Override
        protected void switchState(SendingStreamState state) {
            // called to indicate a change in the stream state.
            // at the moment the only expected value is DATA_SENT
            switchSendingState(state);
        }

        @Override
        protected long streamId() {
            return QuicSenderStreamImpl.this.streamId();
        }
    }

    /**
     * The stream internal implementation of a QuicStreamWriter.
     * Most of the logic is implemented in the StreamWriterQueue,
     * which is subclassed here to provide an implementation of its
     * few abstract methods.
     */
    private class QuicStreamWriterImpl extends QuicStreamWriter {
        QuicStreamWriterImpl(SequentialScheduler scheduler) {
            super(scheduler);
        }

        @Override
        public SendingStreamState sendingState() {
            checkConnected();
            return QuicSenderStreamImpl.this.sendingState();
        }

        @Override
        public void scheduleForWriting(BufferData buffer, boolean last) {
            Objects.requireNonNull(buffer, "buffer");
            checkConnected();
            SendingStreamState state = sending(last);
            switch (state) {
            // this isn't atomic but it doesn't really matter since reset
            // will be handled by the same thread that polls.
            case READY, SEND -> {
                // allow a last empty buffer to be submitted even
                // if the connection is closed. That can help
                // unblock the consumer side.
                if (buffer != QuicStreamReader.EOF || !last) {
                    checkOpened();
                }
                queue.submit(byteBuffer(buffer), last);
            }
            case RESET_SENT, RESET_RECVD -> throw streamResetException();
            case DATA_SENT, DATA_RECVD -> throw streamClosedException();
            default -> throw new IllegalStateException("Unknown sending state: " + state);
            }
        }

        @Override
        public CompletableFuture<Void> scheduleForWritingAndGetDispatchCompletion(BufferData buffer, boolean last) {
            Objects.requireNonNull(buffer, "buffer");
            checkConnected();
            SendingStreamState state = sending(last);
            return switch (state) {
            // this isn't atomic but it doesn't really matter since reset
            // will be handled by the same thread that polls.
            case READY, SEND -> {
                // allow a last empty buffer to be submitted even
                // if the connection is closed. That can help
                // unblock the consumer side.
                if (buffer != QuicStreamReader.EOF || !last) {
                    checkOpened();
                }
                yield queue.submitAndGetDispatchCompletion(byteBuffer(buffer), last);
            }
            case RESET_SENT, RESET_RECVD -> throw streamResetException();
            case DATA_SENT, DATA_RECVD -> throw streamClosedException();
            default -> throw new IllegalStateException("Unknown sending state: " + state);
            };
        }

        @Override
        public void queueForWriting(BufferData buffer) {
            Objects.requireNonNull(buffer, "buffer");
            checkConnected();
            SendingStreamState state = sending(false);
            switch (state) {
            // this isn't atomic but it doesn't really matter since reset
            // will be handled by the same thread that polls.
            case READY, SEND -> {
                checkOpened();
                queue.queue(byteBuffer(buffer));
            }
            case RESET_SENT, RESET_RECVD -> throw streamResetException();
            case DATA_SENT, DATA_RECVD -> throw streamClosedException();
            default -> throw new IllegalStateException("Unknown sending state: " + state);
            }
        }

        @Override
        public long credit() {
            checkConnected();
            // how much data the producer can send before
            // reaching the flow control limit. Could be
            // negative if the limit has been reached already.
            return queue.producerCredit();
        }

        @Override
        public void reset(long errorCode) {
            checkConnected();
            QuicSenderStreamImpl.this.reset(errorCode);
        }

        @Override
        public Optional<QuicSenderStream> stream() {
            var stream = QuicSenderStreamImpl.this;
            var writer = stream.writer;
            return writer == this ? Optional.of(stream) : Optional.empty();
        }

        @Override
        public boolean connected() {
            var writer = QuicSenderStreamImpl.this.writer;
            return writer == this;
        }

        void wakeupWriter() {
            scheduler().runOrSchedule(connection().quicInstance().executor());
        }

        /**
         * Compose an exception to throw if data is submitted after the
         * stream was reset.
         *
         * @return a new stream exception
         */
        QuicStreamException streamResetException() {
            long resetByPeer = queue.resetByPeer();
            if (resetByPeer < 0) {
                return QuicStreamException.stopSending(streamId(), -resetByPeer - 1);
            } else {
                return QuicStreamException.resetLocally(streamId(), sndErrorCode());
            }
        }

        /**
         * Compose an exception to throw if data is submitted after the
         * the final data has been sent.
         *
         * @return a new stream exception
         */
        QuicStreamException streamClosedException() {
            return QuicStreamException.closed(streamId());
        }

        private ByteBuffer byteBuffer(BufferData buffer) {
            if (buffer == QuicStreamReader.EOF || buffer.available() == 0) {
                return ByteBuffer.allocate(0);
            }
            return ByteBuffer.wrap(buffer.readBytes());
        }

        private void checkConnected() {
            if (!connected()) {
                throw new IllegalStateException("writer not connected");
            }
        }
    }

}
