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
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.buffers.BufferData;
import io.helidon.quic.OrderedFlow.StreamDataFlow;
import io.helidon.quic.QuicConnectionImpl;
import io.helidon.quic.QuicConnectionImpl.ReassemblyBudget;
import io.helidon.quic.QuicTLSEngine;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.frame.ConnectionCloseFrame;
import io.helidon.quic.frame.ResetStreamFrame;
import io.helidon.quic.frame.StreamDataBlockedFrame;
import io.helidon.quic.frame.StreamFrame;

import static io.helidon.quic.frame.QuicFrame.MAX_VL_INTEGER;
import static io.helidon.quic.stream.QuicReceiverStream.ReceivingStreamState.DATA_READ;
import static io.helidon.quic.stream.QuicReceiverStream.ReceivingStreamState.DATA_RECVD;
import static io.helidon.quic.stream.QuicReceiverStream.ReceivingStreamState.RECV;
import static io.helidon.quic.stream.QuicReceiverStream.ReceivingStreamState.RESET_READ;
import static io.helidon.quic.stream.QuicReceiverStream.ReceivingStreamState.RESET_RECVD;
import static io.helidon.quic.stream.QuicReceiverStream.ReceivingStreamState.SIZE_KNOWN;

/**
 * A class that implements the receiver part of a quic stream.
 */
final class QuicReceiverStreamImpl extends AbstractQuicStream implements QuicReceiverStream {
    private static final long MIN_BUFFER_SIZE = 16L << 10;
    private static final int SMALL_FRAGMENT_LIMIT = 400;
    private static final int SMALL_FRAGMENT_BUFFER_SIZE = 16 << 10;
    private static final int SMALL_FRAGMENT_INITIAL_BUFFER_SIZE = 512;
    private static final System.Logger LOGGER = System.getLogger(QuicReceiverStreamImpl.class.getName());

    private final int maxSmallFragments;
    // The dataFlow reorders incoming stream frames and removes duplicates.
    // It contains frames that cannot be delivered yet because they are not
    // at the expected offset.
    private final ReassemblyBudget reassemblyBudget;
    private final StreamDataFlow dataFlow;
    // The orderedQueue contains frames that can be delivered to the application now.
    // They are inserted in the queue in order.
    // The application consumes this queue through QuicStreamReader.poll().
    private final ConcurrentLinkedQueue<BufferData> orderedQueue = new ConcurrentLinkedQueue<>();
    // Desired buffer size; used when updating maxStreamData
    private final long desiredBufferSize;
    private final ReentrantLock receiveLock = new ReentrantLock();
    private final ReentrantLock processedLock = new ReentrantLock();
    // Maximum stream data
    private volatile long maxStreamData;
    // how much data has been processed on this stream.
    // This is data that was poll'ed from orderedQueue or dropped after stream reset.
    private volatile long processed;
    // how much data has been delivered to orderedQueue or the pending small-fragment buffer.
    // This doesn't take into account frames that may be stored in the dataFlow.
    private volatile long received;
    // Contiguous small frames are copied into one bounded chunk so packetization cannot
    // amplify delivery-queue nodes. Access to the array is guarded by receiveLock.
    private byte[] smallFragmentBuffer;
    private volatile int smallFragmentBufferLength;
    // maximum of offset+length across all received frames
    private volatile long maxReceivedData;
    // the size of the stream, when known. Defaults to 0 when unknown.
    private volatile long knownSize;
    // the connected reader
    private volatile QuicStreamReaderImpl reader;
    // eof when the last payload has been polled by the application
    private volatile boolean eof;
    // the state of the receiving stream
    private volatile ReceivingStreamState receivingState;
    private volatile boolean requestedStopSending;
    private volatile long errorCode;

    QuicReceiverStreamImpl(QuicConnectionImpl connection, long streamId, int maxSmallFragments) {
        super(connection, validateStreamId(connection, streamId));
        errorCode = -1;
        receivingState = ReceivingStreamState.RECV;
        this.maxSmallFragments = maxSmallFragments;
        reassemblyBudget = connection.newReassemblyBudget();
        dataFlow = StreamDataFlow.create(reassemblyBudget, streamId);
        long bufsize = connection.quicConfig().initialMaxStreamData();
        desiredBufferSize = Math.clamp(bufsize, MIN_BUFFER_SIZE, MAX_VL_INTEGER);
    }

    @Override
    public StreamState state() {
        return receivingState();
    }

    @Override
    public ReceivingStreamState receivingState() {
        return receivingState;
    }

    @Override
    public QuicStreamReader connectReader(SequentialScheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        var reader = this.reader;
        if (reader == null) {
            reader = new QuicStreamReaderImpl(scheduler);
            if (Handles.READER.compareAndSet(this, null, reader)) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "reader connected");
                }
                return reader;
            }
        }
        throw new IllegalStateException("reader already connected");
    }

    @Override
    public void disconnectReader(QuicStreamReader reader) {
        Objects.requireNonNull(reader, "reader");
        var previous = this.reader;
        if (reader == previous) {
            if (Handles.READER.compareAndSet(this, reader, null)) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "reader disconnected");
                }
                return;
            }
        }
        throw new IllegalStateException("reader not connected");
    }

    @Override
    public boolean isStopSendingRequested() {
        return requestedStopSending;
    }

    @Override
    public void requestStopSending(long errorCode) {
        if (Handles.STOP_SENDING.compareAndSet(this, false, true)) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "requestedStopSending: true");
            }
            receiveLock.lock();
            ReceivingStreamState state = receivingState;
            try {
                try {
                    errorCode(errorCode);
                    switch (state) {
                    case RECV, SIZE_KNOWN -> {
                        connection().scheduleStopSendingFrame(streamId(), errorCode);
                    }
                    default -> {
                        // otherwise do nothing
                    }
                    }
                } finally {
                    // RFC-9000, section 3.5: "If an application is no longer interested in the data it is
                    // receiving on a stream, it can abort reading the stream and specify an application
                    // error code."
                    // So it implies that the application isn't anymore interested in receiving the data
                    // that has been buffered in the stream, so we drop all buffered data on this stream
                    increaseProcessedData(maxReceivedData);
                    if (state != RECV && state != DATA_READ) {
                        // we know the final size; we can remove the stream
                        if (switchReceivingState(RESET_READ)) {
                            eof = false;
                        }
                    }
                    discardBufferedData();
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG,
                                  "Dropped all buffered frames after STOP_SENDING was requested with error code 0x%s",
                                  Long.toHexString(errorCode));
                    }
                }
            } finally {
                QuicStreamReaderImpl reader = this.reader;
                receiveLock.unlock();
                if (reader != null) {
                    reader.wakeup();
                }
            }
        }
    }

    @Override
    public long dataReceived() {
        return received;
    }

    @Override
    public long maxStreamData() {
        return maxStreamData;
    }

    @Override
    public boolean isDone() {
        return switch (receivingState()) {
            case DATA_READ, DATA_RECVD, RESET_READ, RESET_RECVD ->
                // everything received from peer
                    true;
            default ->
                // the stream is only half closed
                    false;
        };
    }

    @Override
    public long rcvErrorCode() {
        return errorCode;
    }

    /**
     * Receives a QuicFrame from the remote peer.
     *
     * @param streamFrame the frame received
     */
    public void processIncomingFrame(StreamFrame streamFrame)
            throws QuicTransportException {
        // RFC-9000, section 3.5: "STREAM frames received after sending a STOP_SENDING frame
        // are still counted toward connection and stream flow control, even though these
        // frames can be discarded upon receipt."
        // so we do the necessary data size checks before checking if we sent a "STOP_SENDING"
        // frame
        QuicStreamReaderImpl readerToWake = null;
        receiveLock.lock();
        try {
            checkUpdateState(streamFrame);
            ReceivingStreamState state = receivingState;
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "receivingState: " + state);
            }
            long knownSize = this.knownSize;
            // RESET was read or received: drop the frame.
            if (state == RESET_READ || state == RESET_RECVD) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Dropping frame since state is %s", state);
                }
                return;
            }
            if (requestedStopSending) {
                // drop the frame
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG,
                              "Dropping frame received after a STOP_SENDING frame was sent");
                }
                increaseProcessedData(maxReceivedData);
                if (state != RECV) {
                    // we know the final size; we can remove the stream
                    switchReceivingState(RESET_READ);
                }
                return;
            }

            streamFrame = streamFrame.withOwnedPayload();
            dataFlow.receiveAvailable(streamFrame);
            var received = this.received;
            boolean needWakeup = false;
            while (dataFlow.hasAvailable()) {
                var frame = dataFlow.takeAvailable();
                // Check again because requestStopSending can set the volatile flag before
                // it acquires receiveLock and discards the buffered data.
                if (requestedStopSending) {
                    return;
                }
                received += frame.dataLength();
                this.received = received;
                offer(frame);
                needWakeup = true;
                dataFlow.pollAvailable();
            }
            if (state == SIZE_KNOWN && received == knownSize) {
                if (switchReceivingState(DATA_RECVD)) {
                    offerEof();
                    needWakeup = true;
                }
            }
            if (needWakeup) {
                readerToWake = this.reader;
            } else {
                int numFrames = dataFlow.size();
                long numBytes = dataFlow.buffered();
                if (numFrames > maxSmallFragments && numBytes / numFrames < SMALL_FRAGMENT_LIMIT) {
                    // The peer sent a large number of small fragments
                    // that follow a gap and can't be immediately released to the reader;
                    // we need to buffer them, and the memory overhead is unreasonably high.
                    throw new QuicTransportException("Excessive stream fragmentation",
                                                     QuicTLSEngine.KeySpace.ONE_RTT, streamFrame.frameType(),
                                                     QuicTransportErrors.INTERNAL_ERROR,
                                                     streamId());
                }
            }
        } finally {
            receiveLock.unlock();
        }
        if (readerToWake != null) {
            readerToWake.wakeup();
        }
    }

    /**
     * Updates the value of MAX_STREAM_DATA for this stream.
     *
     * @param newMaxStreamData new MAX_STREAM_DATA value
     */
    public void updateMaxStreamData(long newMaxStreamData) {
        long maxStreamData = this.maxStreamData;
        boolean updated = false;
        while (maxStreamData < newMaxStreamData) {
            updated = Handles.MAX_STREAM_DATA.compareAndSet(this, maxStreamData, newMaxStreamData);
            if (updated) {
                break;
            }
            maxStreamData = this.maxStreamData;
        }
        if (updated) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "updateMaxStreamData: max stream data updated from %s to %s",
                          maxStreamData, newMaxStreamData);
            }
        }
    }

    /**
     * Receives a QuicFrame from the remote peer.
     *
     * @param resetStreamFrame the frame received
     */
    void processIncomingResetFrame(ResetStreamFrame resetStreamFrame)
            throws QuicTransportException {
        receiveLock.lock();
        try {
            checkUpdateState(resetStreamFrame);
            if (requestedStopSending) {
                increaseProcessedData(knownSize);
                switchReceivingState(RESET_READ);
            }
        } finally {
            QuicStreamReaderImpl reader = this.reader;
            try {
                // make sure the state is switched to reset received.
                // even if we're closing the connection
                switchReceivingState(RESET_RECVD);
                discardBufferedData();
            } finally {
                receiveLock.unlock();
                if (reader != null) {
                    reader.wakeup();
                }
            }
        }
    }

    void processIncomingFrame(StreamDataBlockedFrame streamDataBlocked) {
        receiveLock.lock();
        try {
            long peerBlockedOn = streamDataBlocked.maxStreamData();
            long currentLimit = this.maxStreamData;
            if (peerBlockedOn > currentLimit) {
                // shouldn't have happened. ignore and don't increase the limit.
                return;
            }
            // the peer has stated that the stream is blocked due to flow control limit that we have
            // imposed and has requested for increasing the limit. we approve that request
            // and increase the limit only if the amount of received data that we have received and
            // processed on this stream is more than 1/4 of the credit window.
            if (!requestedStopSending
                    && currentLimit - processed < (desiredBufferSize - desiredBufferSize / 4)) {
                demand(desiredBufferSize);
            } else {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "ignoring STREAM_DATA_BLOCKED frame %s,"
                            + " since current limit %d is large enough", streamDataBlocked, currentLimit);
                }
            }
        } finally {
            receiveLock.unlock();
        }
    }

    /**
     * Called when the connection is closed.
     *
     * @param termination selected connection termination
     */
    void terminate(QuicTermination termination) {
        termination.errorCode().ifPresent(this::errorCode);
        QuicStreamReaderImpl reader;
        receiveLock.lock();
        try {
            discardBufferedData();
            reader = this.reader;
        } finally {
            receiveLock.unlock();
        }
        if (reader != null) {
            reader.wakeup();
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
        if (connection.isClientConnection() == QuicStreams.isClientInitiated(streamId)) {
            throw new IllegalArgumentException("A locally initiated stream can't be read-only");
        }
        return streamId;
    }

    /**
     * Sends a {@link ConnectionCloseFrame} due to MAX_STREAM_DATA exceeded
     * for the stream.
     *
     * @param streamFrame the stream frame that caused the excess
     * @param maxData     the value of MAX_STREAM_DATA which was exceeded
     */
    private static QuicTransportException streamControlOverflow(StreamFrame streamFrame, long maxData)
            throws QuicTransportException {
        String reason = "Stream max data exceeded: offset=%s, length=%s, max stream data=%s"
                .formatted(streamFrame.offset(), streamFrame.dataLength(), maxData);
        throw new QuicTransportException(reason,
                                         QuicTLSEngine.KeySpace.ONE_RTT,
                                         streamFrame.typeField(),
                                         QuicTransportErrors.FLOW_CONTROL_ERROR,
                                         streamFrame.streamId());
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

    private void log(System.Logger.Level level, String message, Throwable throwable) {
        connection().log(LOGGER, level, "%d: %s", throwable, streamId(), message);
    }

    private void demand(long additional) {
        var processed = this.processed;
        var maxStreamData = maxStreamData();

        long newMax = Math.clamp(processed + additional, maxStreamData, MAX_VL_INTEGER);
        if (newMax > maxStreamData) {
            connection().requestSendMaxStreamData(streamId(), newMax);
            updateMaxStreamData(newMax);
        }
    }

    /**
     * Checks for error conditions:
     * - max stream data errors
     * - max data errors
     * - final size errors
     * If everything checks OK, updates counters and returns, otherwise throws.
     *
     * @param streamFrame received stream frame
     * @throws QuicTransportException if frame is invalid
     * @implNote This method may update counters before throwing. This is OK
     *        because we do not expect to use them again in this case.
     */
    private void checkUpdateState(StreamFrame streamFrame) throws QuicTransportException {
        long offset = streamFrame.offset();
        long length = streamFrame.dataLength();

        // check maxStreamData
        long maxData = maxStreamData;
        long size;
        try {
            size = Math.addExact(offset, length);
        } catch (ArithmeticException x) {
            // should not happen
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "offset + length exceeds max value", x);
            }
            throw streamControlOverflow(streamFrame, Long.MAX_VALUE);
        }
        if (size > maxData) {
            throw streamControlOverflow(streamFrame, maxData);
        }
        ReceivingStreamState state = receivingState;
        // check finalSize if known
        long knownSize = this.knownSize;
        if (state != RECV && size > knownSize) {
            String reason = "Stream final size exceeded: offset=%s, length=%s, final size=%s"
                    .formatted(streamFrame.offset(), streamFrame.dataLength(), knownSize);
            throw new QuicTransportException(reason,
                                             QuicTLSEngine.KeySpace.ONE_RTT,
                                             streamFrame.typeField(),
                                             QuicTransportErrors.FINAL_SIZE_ERROR,
                                             streamId());
        }
        // check maxData
        updateMaxReceivedData(size, streamFrame.typeField());
        if (streamFrame.isLast()) {
            // check max received data, throw if we have data beyond the (new) EOF
            if (size < maxReceivedData) {
                String reason = "Stream truncated: offset=%s, length=%s, max received=%s"
                        .formatted(streamFrame.offset(), streamFrame.dataLength(), maxReceivedData);
                throw new QuicTransportException(reason,
                                                 QuicTLSEngine.KeySpace.ONE_RTT,
                                                 streamFrame.typeField(),
                                                 QuicTransportErrors.FINAL_SIZE_ERROR,
                                                 streamId());
            }
            if (state == RECV && switchReceivingState(SIZE_KNOWN)) {
                this.knownSize = size;
            } else {
                if (size != knownSize) {
                    String reason = "Stream final size changed: offset=%s, length=%s, final size=%s"
                            .formatted(streamFrame.offset(), streamFrame.dataLength(), knownSize);
                    throw new QuicTransportException(reason,
                                                     QuicTLSEngine.KeySpace.ONE_RTT,
                                                     streamFrame.typeField(),
                                                     QuicTransportErrors.FINAL_SIZE_ERROR,
                                                     streamId());
                }
            }
        }
    }

    /**
     * Checks for error conditions:
     * - max stream data errors
     * - max data errors
     * - final size errors
     * If everything checks OK, updates counters and returns, otherwise throws.
     *
     * @param resetStreamFrame received reset stream frame
     * @throws QuicTransportException if frame is invalid
     * @implNote This method may update counters before throwing. This is OK
     *        because we do not expect to use them again in this case.
     */
    private void checkUpdateState(ResetStreamFrame resetStreamFrame) throws QuicTransportException {
        // check maxStreamData
        long maxData = maxStreamData;
        long size = resetStreamFrame.finalSize();
        long errorCode = resetStreamFrame.errorCode();
        errorCode(errorCode);
        if (size > maxData) {
            String reason = "Stream max data exceeded: finalSize=%s, max stream data=%s"
                    .formatted(size, maxData);
            throw new QuicTransportException(reason,
                                             QuicTLSEngine.KeySpace.ONE_RTT,
                                             resetStreamFrame.typeField(),
                                             QuicTransportErrors.FLOW_CONTROL_ERROR,
                                             streamId());
        }
        ReceivingStreamState state = receivingState;
        updateMaxReceivedData(size, resetStreamFrame.typeField());
        // check max received data, throw if we have data beyond the (new) EOF
        if (size < maxReceivedData) {
            String reason = "Stream truncated: finalSize=%s, max received=%s"
                    .formatted(size, maxReceivedData);
            throw new QuicTransportException(reason,
                                             QuicTLSEngine.KeySpace.ONE_RTT,
                                             resetStreamFrame.typeField(),
                                             QuicTransportErrors.FINAL_SIZE_ERROR,
                                             streamId());
        }
        if (state == RECV && switchReceivingState(RESET_RECVD)) {
            this.knownSize = size;
        } else {
            if (state == SIZE_KNOWN) {
                switchReceivingState(RESET_RECVD);
            }
            if (size != knownSize) {
                String reason = "Stream final size changed: new finalSize=%s, old final size=%s"
                        .formatted(size, knownSize);
                throw new QuicTransportException(reason,
                                                 QuicTLSEngine.KeySpace.ONE_RTT,
                                                 resetStreamFrame.typeField(),
                                                 QuicTransportErrors.FINAL_SIZE_ERROR,
                                                 streamId());
            }
        }
    }

    private void offer(StreamFrame frame) {
        int length = frame.dataLength();
        if (length < SMALL_FRAGMENT_LIMIT) {
            int currentLength = smallFragmentBufferLength;
            if (currentLength + length > SMALL_FRAGMENT_BUFFER_SIZE) {
                flushSmallFragments();
                currentLength = 0;
            }
            int requiredLength = currentLength + length;
            byte[] buffer = smallFragmentBuffer;
            if (buffer == null) {
                buffer = new byte[Math.max(SMALL_FRAGMENT_INITIAL_BUFFER_SIZE, requiredLength)];
                smallFragmentBuffer = buffer;
            } else if (requiredLength > buffer.length) {
                int newLength = Math.min(SMALL_FRAGMENT_BUFFER_SIZE,
                                         Math.max(requiredLength, buffer.length << 1));
                buffer = Arrays.copyOf(buffer, newLength);
                smallFragmentBuffer = buffer;
            }
            frame.payload().get(buffer, currentLength, length);
            smallFragmentBufferLength = requiredLength;
            if (requiredLength == SMALL_FRAGMENT_BUFFER_SIZE) {
                flushSmallFragments();
            }
            return;
        }
        flushSmallFragments();
        orderedQueue.add(frame.ownedPayloadData());
    }

    private void flushSmallFragments() {
        int length = smallFragmentBufferLength;
        if (length == 0) {
            return;
        }
        byte[] buffer = smallFragmentBuffer;
        if (length != buffer.length) {
            buffer = Arrays.copyOf(buffer, length);
        }
        smallFragmentBuffer = null;
        smallFragmentBufferLength = 0;
        orderedQueue.add(BufferData.createReadOnly(buffer, 0, length));
    }

    private void discardBufferedData() {
        reassemblyBudget.close();
        dataFlow.clear();
        smallFragmentBuffer = null;
        smallFragmentBufferLength = 0;
        orderedQueue.clear();
    }

    private void offerEof() {
        flushSmallFragments();
        orderedQueue.add(QuicStreamReader.EOF);
    }

    /**
     * Update the {@code maxReceivedData} value, and return the amount
     * by which {@code maxReceivedData} was increased. This method is a
     * no-op and returns 0 if {@code maxReceivedData >= newMax}.
     *
     * @param newMax    the new max offset - typically obtained
     *                 by adding the length of a frame to its
     *                 offset
     * @param frameType type of frame received
     * @throws QuicTransportException if flow control was violated
     */
    private void updateMaxReceivedData(long newMax, long frameType) throws QuicTransportException {
        var max = this.maxReceivedData;
        while (max < newMax) {
            if (Handles.MAX_RECEIVED_DATA.compareAndSet(this, max, newMax)) {
                // report accepted data to connection flow control,
                // and update the amount of data received in the
                // connection. This will also check whether connection
                // flow control is exceeded, and throw in
                // this case
                connection().increaseReceivedData(newMax - max, frameType);
                return;
            }
            max = this.maxReceivedData;
        }
    }

    /**
     * Notifies the connection about received data that is no longer buffered.
     */
    private void increaseProcessedDataBy(int diff) {
        if (diff <= 0) {
            return;
        }
        processedLock.lock();
        try {
            if (requestedStopSending) {
                // once we request stop sending, updates are handled by increaseProcessedData
                return;
            }
            processed += diff;
        } finally {
            processedLock.unlock();
        }
        connection().increaseProcessedData(diff);
    }

    /**
     * Notifies the connection about received data that is no longer buffered.
     */
    private void increaseProcessedData(long newProcessed) {
        long diff;
        processedLock.lock();
        try {
            if (newProcessed > processed) {
                diff = newProcessed - processed;
                processed = newProcessed;
            } else {
                diff = 0;
            }
        } finally {
            processedLock.unlock();
        }
        if (diff > 0) {
            connection().increaseProcessedData(diff);
        }
    }

    /**
     * Called when a state change is needed.
     *
     * @param newState the new state.
     */
    private boolean switchReceivingState(ReceivingStreamState newState) {
        ReceivingStreamState oldState = receivingState;
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "switchReceivingState %s -> %s",
                      oldState, newState);
        }
        boolean switched = switch (newState) {
            case SIZE_KNOWN -> markSizeKnown();
            case DATA_RECVD -> markDataRecvd();
            case RESET_RECVD -> markResetRecvd();
            case RESET_READ -> markResetRead();
            case DATA_READ -> markDataRead();
            default -> throw new UnsupportedOperationException("switch state to " + newState.text());
        };
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            if (switched) {
                log(System.Logger.Level.DEBUG, "switched receiving state from %s to %s", oldState, newState);
            } else {
                log(System.Logger.Level.DEBUG, "receiving state not switched; state is %s", receivingState);
            }
        }

        if (switched && newState.isTerminal()) {
            notifyTerminalState(newState);
        }

        return switched;
    }

    private void notifyTerminalState(ReceivingStreamState state) {
        connection().notifyTerminalState(streamId(), state);
    }

    // DATA_RECV is reached when the last frame is received,
    // and there's no gap
    private boolean markDataRecvd() {
        boolean done;
        boolean switched = false;
        ReceivingStreamState oldState;
        do {
            oldState = receivingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case RECV, SIZE_KNOWN -> {
                    switched = Handles.RECEIVING_STATE.compareAndSet(this,
                                                                      oldState, DATA_RECVD);
                    yield switched;
                }
                case DATA_RECVD, DATA_READ, RESET_RECVD, RESET_READ -> true;
            };
        } while (!done);
        return switched;
    }

    // SIZE_KNOWN is reached when a stream frame with the FIN bit is received
    private boolean markSizeKnown() {
        boolean done;
        boolean switched = false;
        ReceivingStreamState oldState;
        do {
            oldState = receivingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case RECV -> {
                    switched = Handles.RECEIVING_STATE.compareAndSet(this,
                                                                      oldState, SIZE_KNOWN);
                    yield switched;
                }
                case DATA_RECVD, DATA_READ, SIZE_KNOWN, RESET_RECVD, RESET_READ -> true;
            };
        } while (!done);
        return switched;
    }

    // RESET_RECV is reached when a RESET_STREAM frame is received
    private boolean markResetRecvd() {
        boolean done;
        boolean switched = false;
        ReceivingStreamState oldState;
        do {
            oldState = receivingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case RECV, SIZE_KNOWN -> {
                    switched = Handles.RECEIVING_STATE.compareAndSet(this,
                                                                      oldState, RESET_RECVD);
                    yield switched;
                }
                case DATA_RECVD, DATA_READ, RESET_RECVD, RESET_READ -> true;
            };
        } while (!done);
        return switched;
    }

    // Called when the consumer has polled the last data
    // DATA_READ is a terminal state
    private boolean markDataRead() {
        boolean done;
        boolean switched = false;
        ReceivingStreamState oldState;
        do {
            oldState = receivingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case SIZE_KNOWN, DATA_RECVD, RESET_RECVD -> {
                    switched = Handles.RECEIVING_STATE.compareAndSet(this,
                                                                      oldState, DATA_READ);
                    yield switched;
                }
                case RESET_READ, DATA_READ -> true;
                default -> throw new IllegalStateException("%s: %s -> %s"
                                                                   .formatted(streamId(), oldState.text(), DATA_READ.text()));
            };
        } while (!done);
        return switched;
    }

    // Called when the consumer has read the reset
    // RESET_READ is a terminal state
    private boolean markResetRead() {
        boolean done;
        boolean switched = false;
        ReceivingStreamState oldState;
        do {
            oldState = receivingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case SIZE_KNOWN, DATA_RECVD, RESET_RECVD -> {
                    switched = Handles.RECEIVING_STATE.compareAndSet(this,
                                                                      oldState, RESET_READ);
                    yield switched;
                }
                case RESET_READ, DATA_READ -> true;
                default -> throw new IllegalStateException("%s: %s -> %s"
                                                                   .formatted(streamId(), oldState.text(), RESET_READ.text()));
            };
        } while (!done);
        return switched;
    }

    private void errorCode(long code) {
        Handles.ERROR_CODE.compareAndSet(this, -1, code);
    }

    private static final class Handles {
        static final VarHandle READER;
        static final VarHandle RECEIVING_STATE;
        static final VarHandle MAX_STREAM_DATA;
        static final VarHandle MAX_RECEIVED_DATA;
        static final VarHandle STOP_SENDING;
        static final VarHandle ERROR_CODE;

        static {
            try {
                var lookup = MethodHandles.lookup();
                RECEIVING_STATE = lookup.findVarHandle(QuicReceiverStreamImpl.class,
                                                       "receivingState", ReceivingStreamState.class);
                READER = lookup.findVarHandle(QuicReceiverStreamImpl.class,
                                              "reader", QuicStreamReaderImpl.class);
                MAX_STREAM_DATA = lookup.findVarHandle(QuicReceiverStreamImpl.class,
                                                       "maxStreamData", long.class);
                MAX_RECEIVED_DATA = lookup.findVarHandle(QuicReceiverStreamImpl.class,
                                                         "maxReceivedData", long.class);
                STOP_SENDING = lookup.findVarHandle(QuicReceiverStreamImpl.class,
                                                    "requestedStopSending", boolean.class);
                ERROR_CODE = lookup.findVarHandle(QuicReceiverStreamImpl.class,
                                                  "errorCode", long.class);
            } catch (Exception x) {
                throw new ExceptionInInitializerError(x);
            }
        }
    }

    // private implementation of a QuicStreamReader for this stream
    private final class QuicStreamReaderImpl extends QuicStreamReader {

        static final int STARTED = 1;
        static final int PENDING = 2;
        private final ReentrantLock stateLock = new ReentrantLock();
        // Volatile keeps the fast-path checks visible across threads.
        // Synchronized blocks still guard the transitions before STARTED is set.
        private volatile int state;

        QuicStreamReaderImpl(SequentialScheduler scheduler) {
            super(scheduler);
        }

        @Override
        public ReceivingStreamState receivingState() {
            checkConnected();
            return QuicReceiverStreamImpl.this.receivingState();
        }

        @Override
        public Optional<BufferData> poll() {
            checkConnected();
            var buffer = orderedQueue.poll();
            if (buffer == null && smallFragmentBufferLength != 0) {
                receiveLock.lock();
                try {
                    buffer = orderedQueue.poll();
                    if (buffer == null && smallFragmentBufferLength != 0) {
                        flushSmallFragments();
                        buffer = orderedQueue.poll();
                    }
                } finally {
                    receiveLock.unlock();
                }
            }
            if (buffer == null) {
                if (eof) {
                    return Optional.of(EOF);
                }
                var state = receivingState;
                if (state == RESET_RECVD) {
                    increaseProcessedData(knownSize);
                }
                checkReset();
                // unfulfilled = maxStreamData - received;
                // if we have received more than 1/4 of the buffer, update maxStreamData
                if (!requestedStopSending && unfulfilled() < desiredBufferSize - desiredBufferSize / 4) {
                    demand(desiredBufferSize);
                }
                return Optional.empty();
            }

            if (requestedStopSending) {
                // check reset again
                checkReset();
                return Optional.empty();
            }
            increaseProcessedDataBy(buffer.available());
            if (buffer == EOF) {
                eof = true;
                switchReceivingState(DATA_READ);
                return Optional.of(EOF);
            }
            // if the amount of received data that has been processed on this stream is
            // more than 1/4 of the credit window then send a MaxStreamData frame.
            if (!requestedStopSending && maxStreamData - processed < desiredBufferSize - desiredBufferSize / 4) {
                demand(desiredBufferSize);
            }
            return Optional.of(buffer);
        }

        @Override
        public Optional<BufferData> peek() {
            checkConnected();
            var buffer = orderedQueue.peek();
            if (buffer == null && smallFragmentBufferLength != 0) {
                receiveLock.lock();
                try {
                    buffer = orderedQueue.peek();
                    if (buffer == null && smallFragmentBufferLength != 0) {
                        flushSmallFragments();
                        buffer = orderedQueue.peek();
                    }
                } finally {
                    receiveLock.unlock();
                }
            }
            if (buffer == null) {
                checkReset();
                return eof ? Optional.of(EOF) : Optional.empty();
            }
            return Optional.of(buffer);
        }

        @Override
        public Optional<QuicReceiverStream> stream() {
            var stream = QuicReceiverStreamImpl.this;
            var reader = stream.reader;
            return reader == this ? Optional.of(stream) : Optional.empty();
        }

        @Override
        public boolean connected() {
            var reader = QuicReceiverStreamImpl.this.reader;
            return reader == this;
        }

        @Override
        public boolean started() {
            int currentState = this.state;
            if ((currentState & STARTED) == STARTED) {
                return true;
            }
            stateLock.lock();
            try {
                currentState = this.state;
                return (currentState & STARTED) == STARTED;
            } finally {
                stateLock.unlock();
            }
        }

        @Override
        public void start() {
            // Run the scheduler if woken up before starting
            int currentState = this.state;
            if ((currentState & STARTED) == 0) {
                boolean wakeup = false;
                stateLock.lock();
                try {
                    currentState = this.state;
                    if ((currentState & STARTED) == 0) {
                        wakeup = wakeupOnStart(currentState);
                        this.state = STARTED;
                    }
                } finally {
                    stateLock.unlock();
                }
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "reader started (wakeup: %s)", wakeup);
                }
                if (wakeup
                        || !orderedQueue.isEmpty()
                        || smallFragmentBufferLength != 0
                        || receivingState != RECV
                        || requestedStopSending
                        || connection().termination().isPresent()) {
                    wakeup();
                }
            }
        }

        void wakeup() {
            // Only run the scheduler after the reader is started.
            int currentState = this.state;
            boolean notStarted = (currentState & STARTED) == 0;
            boolean pending = false;
            if (notStarted) {
                stateLock.lock();
                try {
                    currentState = this.state;
                    notStarted = (currentState & STARTED) == 0;
                    if (notStarted) {
                        currentState |= PENDING;
                        this.state = currentState;
                        pending = true;
                    }
                } finally {
                    stateLock.unlock();
                }
            }
            if (notStarted) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "reader not started (pending: %s)", pending);
                }
                return;
            }
            scheduler().runOrSchedule(connection().quicInstance().executor());
        }

        /**
         * Checks whether the stream was reset and throws an exception if
         * yes.
         *
         * @throws QuicStreamException if the stream is reset
         */
        private void checkReset() {
            var state = receivingState;
            if (requestedStopSending) {
                throw QuicStreamException.closed(streamId());
            }
            if (state == RESET_READ || state == RESET_RECVD) {
                if (state == RESET_RECVD) {
                    switchReceivingState(RESET_READ);
                }
                if (requestedStopSending) {
                    throw QuicStreamException.closed(streamId());
                } else {
                    throw QuicStreamException.resetByPeer(streamId(), errorCode);
                }
            }
            checkOpened();
        }

        private long unfulfilled() {
            // These counters are volatile and this method is used for threshold checks,
            // so an approximate concurrent snapshot is enough.
            var max = maxStreamData;
            var rcved = received;
            return max - rcved;
        }

        private boolean wakeupOnStart(int state) {
            return (state & PENDING) != 0
                    || !orderedQueue.isEmpty()
                    || smallFragmentBufferLength != 0
                    || receivingState != RECV
                    || requestedStopSending
                    || connection().termination().isPresent();
        }

        private void checkConnected() {
            if (!connected()) {
                throw new IllegalStateException("reader not connected");
            }
        }
    }

}
