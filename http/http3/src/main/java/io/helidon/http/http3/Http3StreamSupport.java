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

package io.helidon.http.http3;

import java.io.Serial;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.quic.QuicConnectionException;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamException;
import io.helidon.quic.stream.QuicStreamReader;
import io.helidon.quic.stream.QuicStreamWriter;

import static io.helidon.common.buffers.BufferData.EMPTY_BYTES;

/**
 * Helpers for reading and writing HTTP/3 stream frames on top of QUIC streams.
 */
@Api.Internal
public final class Http3StreamSupport {
    private static final Object DATA_AVAILABLE = new Object();
    private static final Object READER_CLOSED = new Object();
    private static final int MAX_FRAME_HEADER_SIZE = 16;
    private static final int MAX_RAW_DATA_CHUNK_SIZE = 8 * 1024;
    private static final Runnable NO_OP = () -> { };
    private static final SequentialScheduler NO_OP_SCHEDULER = scheduler(NO_OP);
    private static final Http3FrameListener NO_OP_FRAME_LISTENER = Http3FrameListener.create(List.of());

    private Http3StreamSupport() {
    }

    /**
     * Connect a stream writer suitable for HTTP/3 frame writes.
     *
     * @param stream sender stream
     * @param context socket context
     * @return connected stream writer
     */
    public static QuicStreamWriter connectWriter(QuicSenderStream stream, SocketContext context) {
        return connectWriter(stream, context, NO_OP_FRAME_LISTENER);
    }

    /**
     * Connect a stream writer suitable for HTTP/3 frame writes.
     *
     * @param stream sender stream
     * @param context socket context
     * @param frameListener frame listener
     * @return connected stream writer
     */
    public static QuicStreamWriter connectWriter(QuicSenderStream stream,
                                                 SocketContext context,
                                                 Http3FrameListener frameListener) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(frameListener, "frameListener");
        QuicStreamWriter writer = stream.connectWriter(scheduler(NO_OP));
        if (frameListener == NO_OP_FRAME_LISTENER) {
            return writer;
        }
        return new LoggingWriter(writer, stream.streamId(), context, frameListener);
    }

    /**
     * Connect a stream writer suitable for HTTP/3 writes on a unidirectional control or QPACK stream.
     *
     * @param stream sender stream
     * @param context socket context
     * @param streamType HTTP/3 unidirectional stream type
     * @return connected stream writer
     */
    public static QuicStreamWriter connectWriter(QuicSenderStream stream,
                                                 SocketContext context,
                                                 Http3StreamType streamType) {
        return connectWriter(stream, context, streamType, NO_OP_FRAME_LISTENER);
    }

    /**
     * Connect a stream writer suitable for HTTP/3 writes on a unidirectional control or QPACK stream.
     *
     * @param stream sender stream
     * @param context socket context
     * @param streamType HTTP/3 unidirectional stream type
     * @param frameListener frame listener
     * @return connected stream writer
     */
    public static QuicStreamWriter connectWriter(QuicSenderStream stream,
                                                 SocketContext context,
                                                 Http3StreamType streamType,
                                                 Http3FrameListener frameListener) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(streamType, "streamType");
        Objects.requireNonNull(frameListener, "frameListener");
        QuicStreamWriter writer = stream.connectWriter(scheduler(NO_OP));
        if (frameListener == NO_OP_FRAME_LISTENER) {
            return writer;
        }
        return new LoggingWriter(writer, stream.streamId(), context, streamType, frameListener);
    }

    /**
     * Write the provided bytes to a QUIC sender stream.
     *
     * @param stream sender stream
     * @param bytes bytes to write
     * @param last whether this is the final write on the stream
     * @param context socket context
     * @throws QuicStreamException if the stream has been reset or its output is already closed
     * @throws QuicConnectionException if the connection terminates before the write is accepted
     * @throws IllegalStateException if the stream already has a connected writer or final stream data was already submitted
     */
    public static void writeAll(QuicSenderStream stream,
                                byte[] bytes,
                                boolean last,
                                SocketContext context) {
        writeAll(stream, bytes, last, context, NO_OP_FRAME_LISTENER);
    }

    /**
     * Write the provided bytes to a QUIC sender stream.
     *
     * @param stream sender stream
     * @param bytes bytes to write
     * @param last whether this is the final write on the stream
     * @param context socket context
     * @param frameListener frame listener
     * @throws QuicStreamException if the stream has been reset or its output is already closed
     * @throws QuicConnectionException if the connection terminates before the write is accepted
     * @throws IllegalStateException if the stream already has a connected writer or final stream data was already submitted
     */
    public static void writeAll(QuicSenderStream stream,
                                byte[] bytes,
                                boolean last,
                                SocketContext context,
                                Http3FrameListener frameListener) {
        Objects.requireNonNull(bytes, "bytes");
        connectWriter(stream, context, frameListener).scheduleForWriting(BufferData.create(bytes), last);
    }

    /**
     * Write the provided bytes to a QUIC sender stream.
     *
     * @param stream sender stream
     * @param bytes bytes to write
     * @param last whether this is the final write on the stream
     * @param context socket context
     * @param streamType HTTP/3 unidirectional stream type
     * @throws QuicStreamException if the stream has been reset or its output is already closed
     * @throws QuicConnectionException if the connection terminates before the write is accepted
     * @throws IllegalStateException if the stream already has a connected writer or final stream data was already submitted
     */
    public static void writeAll(QuicSenderStream stream,
                                byte[] bytes,
                                boolean last,
                                SocketContext context,
                                Http3StreamType streamType) {
        writeAll(stream, bytes, last, context, streamType, NO_OP_FRAME_LISTENER);
    }

    /**
     * Write the provided bytes to a QUIC sender stream.
     *
     * @param stream sender stream
     * @param bytes bytes to write
     * @param last whether this is the final write on the stream
     * @param context socket context
     * @param streamType HTTP/3 unidirectional stream type
     * @param frameListener frame listener
     * @throws QuicStreamException if the stream has been reset or its output is already closed
     * @throws QuicConnectionException if the connection terminates before the write is accepted
     * @throws IllegalStateException if the stream already has a connected writer or final stream data was already submitted
     */
    public static void writeAll(QuicSenderStream stream,
                                byte[] bytes,
                                boolean last,
                                SocketContext context,
                                Http3StreamType streamType,
                                Http3FrameListener frameListener) {
        Objects.requireNonNull(bytes, "bytes");
        connectWriter(stream, context, streamType, frameListener).scheduleForWriting(BufferData.create(bytes), last);
    }

    private static Http3ProtocolException truncatedFrame() {
        return Http3ProtocolException.connectionError(Http3ErrorCode.FRAME_ERROR,
                                                      "Unexpected end of HTTP/3 frame");
    }

    private static byte[] copy(BufferData buffer, int offset, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) buffer.get(offset + i);
        }
        return bytes;
    }

    private static SequentialScheduler scheduler(Runnable mainLoop) {
        return SequentialScheduler.create(new SequentialScheduler.CompleteRestartableTask() {
            @Override
            protected void run() {
                mainLoop.run();
            }
        });
    }

    static final class StreamInput {
        private final QuicReceiverStream stream;
        private final QuicStreamReader reader;
        private final BlockingQueue<Object> signals = new ArrayBlockingQueue<>(1);
        private final ReentrantLock queueLock = new ReentrantLock();
        private final Http3MessageReader.ReadOptions readOptions;

        private volatile boolean closed;
        private boolean endOfStream;
        private BufferData current;

        StreamInput(QuicReceiverStream stream,
                    Runnable resetAction,
                    Http3MessageReader.ReadOptions readOptions) {
            this.stream = stream;
            this.readOptions = Objects.requireNonNull(readOptions, "readOptions");
            readOptions.onReadTimeoutActivation(() -> offerIfOpen(DATA_AVAILABLE));
            SequentialScheduler scheduler = scheduler(() -> {
                if (stream.receivingState().isReset()) {
                    resetAction.run();
                }
                offerIfOpen(DATA_AVAILABLE);
            });
            this.reader = stream.connectReader(scheduler);
            try {
                this.reader.start();
            } catch (RuntimeException | Error e) {
                try {
                    stream.disconnectReader(reader);
                } catch (RuntimeException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
                throw e;
            }
        }

        int readByte() {
            if (!ensureCurrent()) {
                return -1;
            }
            queueLock.lock();
            try {
                if (closed) {
                    throw new StreamInputException("HTTP/3 stream reader is closed.");
                }
                int result = current.read() & 0xFF;
                if (current.available() == 0) {
                    current = null;
                }
                return result;
            } finally {
                queueLock.unlock();
            }
        }

        byte[] readBytes(int length) {
            if (length == 0) {
                return EMPTY_BYTES;
            }
            byte[] bytes = new byte[length];
            int offset = 0;
            while (offset < length) {
                if (!ensureCurrent()) {
                    throw truncatedFrame();
                }
                queueLock.lock();
                try {
                    if (closed) {
                        throw new StreamInputException("HTTP/3 stream reader is closed.");
                    }
                    int toRead = Math.min(length - offset, current.available());
                    current.read(bytes, offset, toRead);
                    offset += toRead;
                    if (current.available() == 0) {
                        current = null;
                    }
                } finally {
                    queueLock.unlock();
                }
            }
            return bytes;
        }

        BufferData readBuffer(int length) {
            if (length == 0) {
                return BufferData.empty();
            }
            if (!ensureCurrent()) {
                throw truncatedFrame();
            }
            queueLock.lock();
            try {
                if (closed) {
                    throw new StreamInputException("HTTP/3 stream reader is closed.");
                }
                int toRead = Math.min(length, current.available());
                BufferData result = BufferData.readOnlySlice(current, toRead);
                if (current.available() == 0) {
                    current = null;
                }
                return result;
            } finally {
                queueLock.unlock();
            }
        }

        int discardBuffer(int length) {
            if (length == 0) {
                return 0;
            }
            if (!ensureCurrent()) {
                throw truncatedFrame();
            }
            queueLock.lock();
            try {
                if (closed) {
                    throw new StreamInputException("HTTP/3 stream reader is closed.");
                }
                int toDiscard = Math.min(length, current.available());
                current.skip(toDiscard);
                if (current.available() == 0) {
                    current = null;
                }
                return toDiscard;
            } finally {
                queueLock.unlock();
            }
        }

        boolean endOfStreamReady() {
            queueLock.lock();
            try {
                if (current != null && current.available() > 0) {
                    return false;
                }
                if (endOfStream) {
                    return true;
                }
                if (closed) {
                    return false;
                }
                try {
                    return reader.peek().orElse(null) == QuicStreamReader.EOF;
                } catch (IllegalStateException e) {
                    return false;
                }
            } finally {
                queueLock.unlock();
            }
        }

        void close() {
            queueLock.lock();
            try {
                if (closed) {
                    return;
                }
                boolean eofReady = endOfStreamReady();
                if (eofReady && !endOfStream) {
                    try {
                        eofReady = reader.poll().orElse(null) == QuicStreamReader.EOF;
                    } catch (IllegalStateException e) {
                        eofReady = false;
                    }
                }
                closed = true;
                endOfStream = eofReady;
                current = null;
                signals.clear();
                signals.add(READER_CLOSED);
            } finally {
                queueLock.unlock();
            }
            try {
                stream.disconnectReader(reader);
            } catch (IllegalStateException _) {
                // Reader is already disconnected.
            }
        }

        boolean closed() {
            return closed;
        }

        private void offerIfOpen(Object value) {
            queueLock.lock();
            try {
                if (!closed) {
                    if (!signals.offer(value)) {
                        // A pending notification already guarantees another poll.
                        return;
                    }
                }
            } finally {
                queueLock.unlock();
            }
        }

        private boolean ensureCurrent() {
            for (;;) {
                queueLock.lock();
                try {
                    if (closed) {
                        throw new StreamInputException("HTTP/3 stream reader is closed.");
                    }
                    if (current != null && current.available() > 0) {
                        return true;
                    }
                    current = null;
                    if (endOfStream) {
                        return false;
                    }
                    Optional<BufferData> next = reader.poll();
                    if (next.isPresent()) {
                        BufferData buffer = next.orElseThrow();
                        if (buffer == QuicStreamReader.EOF) {
                            endOfStream = true;
                            return false;
                        }
                        if (buffer.available() > 0) {
                            current = buffer;
                            return true;
                        }
                    }
                } finally {
                    queueLock.unlock();
                }

                try {
                    Object signal;
                    if (readOptions.readTimeoutActive()) {
                        Duration timeout = readOptions.readTimeout();
                        signal = signals.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
                        if (signal == null) {
                            throw new UncheckedIOException(new Http3ReadTimeoutException(
                                    "No data received on HTTP/3 stream " + stream.streamId()
                                            + " within the timeout " + timeout));
                        }
                    } else {
                        signal = signals.take();
                    }
                    if (signal == READER_CLOSED) {
                        throw new StreamInputException("HTTP/3 stream reader is closed.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new StreamInputException("Interrupted while reading HTTP/3 stream", e);
                }
            }
        }
    }

    static final class StreamInputException extends IllegalStateException {
        @Serial
        private static final long serialVersionUID = 1L;

        private StreamInputException(String message) {
            super(message);
        }

        private StreamInputException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class LoggingWriter extends QuicStreamWriter {
        private final QuicStreamWriter delegate;
        private final long streamId;
        private final SocketContext context;
        private final Optional<Http3StreamType> streamType;
        private final Http3FrameListener frameListener;
        private final FrameLogState frameLogState;
        private final byte[] encodedStreamType = new byte[Long.BYTES];

        private boolean preambleExpected;
        private int streamTypeExpectedLength;
        private int streamTypeLength;

        private LoggingWriter(QuicStreamWriter delegate,
                              long streamId,
                              SocketContext context,
                              Http3FrameListener frameListener) {
            super(NO_OP_SCHEDULER);
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.streamId = streamId;
            this.context = Objects.requireNonNull(context, "context");
            this.streamType = Optional.empty();
            this.frameListener = Objects.requireNonNull(frameListener, "frameListener");
            this.frameLogState = new FrameLogState(streamId, context, frameListener);
        }

        private LoggingWriter(QuicStreamWriter delegate,
                              long streamId,
                              SocketContext context,
                              Http3StreamType streamType,
                              Http3FrameListener frameListener) {
            super(NO_OP_SCHEDULER);
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.streamId = streamId;
            this.context = Objects.requireNonNull(context, "context");
            this.streamType = Optional.of(Objects.requireNonNull(streamType, "streamType"));
            this.frameListener = Objects.requireNonNull(frameListener, "frameListener");
            this.frameLogState = new FrameLogState(streamId, context, frameListener);
            this.preambleExpected = true;
        }

        @Override
        public QuicSenderStream.SendingStreamState sendingState() {
            return delegate.sendingState();
        }

        @Override
        public void scheduleForWriting(BufferData buffer, boolean last) {
            logBuffer(buffer);
            delegate.scheduleForWriting(buffer, last);
        }

        @Override
        public CompletableFuture<Void> scheduleForWritingAndGetDispatchCompletion(BufferData buffer, boolean last) {
            logBuffer(buffer);
            return delegate.scheduleForWritingAndGetDispatchCompletion(buffer, last);
        }

        @Override
        public void queueForWriting(BufferData buffer) {
            logBuffer(buffer);
            delegate.queueForWriting(buffer);
        }

        @Override
        public long credit() {
            return delegate.credit();
        }

        @Override
        public void reset(long errorCode) {
            delegate.reset(errorCode);
        }

        @Override
        public Optional<QuicSenderStream> stream() {
            return delegate.stream();
        }

        @Override
        public boolean connected() {
            return delegate.connected();
        }

        private void logBuffer(BufferData buffer) {
            int available = buffer.available();
            if (available == 0) {
                return;
            }

            int offset = 0;
            if (preambleExpected) {
                offset = logStreamType(buffer, available);
            }
            if (offset == available) {
                return;
            }

            if (streamType.isEmpty()) {
                frameLogState.accept(buffer, offset, available - offset);
                return;
            }

            Http3StreamType resolvedStreamType = streamType.orElseThrow();
            if (resolvedStreamType == Http3StreamType.CONTROL) {
                frameLogState.accept(buffer, offset, available - offset);
            } else {
                logStreamData(buffer,
                              offset,
                              available - offset,
                              resolvedStreamType + " stream data");
            }
        }

        private int logStreamType(BufferData buffer, int available) {
            int offset = 0;
            while (offset < available && preambleExpected) {
                int next = buffer.get(offset++) & 0xFF;
                encodedStreamType[streamTypeLength++] = (byte) next;
                if (streamTypeExpectedLength == 0) {
                    streamTypeExpectedLength = 1 << (next >>> 6);
                }
                if (streamTypeLength == streamTypeExpectedLength) {
                    preambleExpected = false;
                    Http3StreamType resolvedStreamType = streamType.orElseThrow();
                    if (frameListener.enabled()) {
                        frameListener.streamType(context, streamId, resolvedStreamType, streamTypeLength);
                    }
                    if (frameListener.rawDataEnabled()) {
                        byte[] data = new byte[streamTypeLength];
                        System.arraycopy(encodedStreamType, 0, data, 0, streamTypeLength);
                        frameListener.rawStreamData(context, streamId, "stream type data", data);
                    }
                }
            }
            return offset;
        }

        private void logStreamData(BufferData buffer, int offset, int length, String label) {
            if (frameListener.enabled()) {
                frameListener.streamData(context, streamId, label, length);
            }
            if (!frameListener.rawDataEnabled()) {
                return;
            }
            int end = offset + length;
            while (offset < end) {
                int chunkSize = Math.min(end - offset, MAX_RAW_DATA_CHUNK_SIZE);
                frameListener.rawStreamData(context, streamId, label, copy(buffer, offset, chunkSize));
                offset += chunkSize;
            }
        }
    }

    private static final class FrameLogState {
        private static final int STATE_TYPE = 0;
        private static final int STATE_LENGTH = 1;
        private static final int STATE_PAYLOAD = 2;

        private final long streamId;
        private final SocketContext context;
        private final Http3FrameListener frameListener;
        private final byte[] encodedHeader = new byte[MAX_FRAME_HEADER_SIZE];

        private int state = STATE_TYPE;
        private int headerLength;
        private int varIntExpectedLength;
        private int varIntLength;
        private long varIntValue;
        private long frameType;
        private long payloadRemaining;

        private FrameLogState(long streamId, SocketContext context, Http3FrameListener frameListener) {
            this.streamId = streamId;
            this.context = context;
            this.frameListener = frameListener;
        }

        private void accept(BufferData buffer, int offset, int length) {
            int end = offset + length;
            while (offset < end) {
                if (state == STATE_PAYLOAD) {
                    int chunkSize = (int) Math.min(payloadRemaining, end - offset);
                    if (frameListener.rawDataEnabled()) {
                        int rawEnd = offset + chunkSize;
                        while (offset < rawEnd) {
                            int rawChunkSize = Math.min(rawEnd - offset, MAX_RAW_DATA_CHUNK_SIZE);
                            boolean last = payloadRemaining == rawChunkSize;
                            if (frameListener.enabled()) {
                                frameListener.frameData(context, streamId, rawChunkSize, last);
                            }
                            frameListener.rawFrameData(context,
                                                       streamId,
                                                       copy(buffer, offset, rawChunkSize),
                                                       last);
                            offset += rawChunkSize;
                            payloadRemaining -= rawChunkSize;
                        }
                    } else {
                        boolean last = payloadRemaining == chunkSize;
                        if (frameListener.enabled()) {
                            frameListener.frameData(context, streamId, chunkSize, last);
                        }
                        offset += chunkSize;
                        payloadRemaining -= chunkSize;
                    }
                    if (payloadRemaining == 0) {
                        resetFrame();
                    }
                    continue;
                }

                int next = buffer.get(offset++) & 0xFF;
                encodedHeader[headerLength++] = (byte) next;
                if (!readVarIntByte(next)) {
                    continue;
                }
                if (state == STATE_TYPE) {
                    frameType = varIntValue;
                    state = STATE_LENGTH;
                    resetVarInt();
                    continue;
                }

                payloadRemaining = varIntValue;
                if (frameListener.enabled()) {
                    frameListener.frameHeader(context, streamId, frameType, payloadRemaining, headerLength);
                }
                if (frameListener.rawDataEnabled()) {
                    byte[] header = new byte[headerLength];
                    System.arraycopy(encodedHeader, 0, header, 0, headerLength);
                    frameListener.rawFrameHeader(context, streamId, header);
                }
                if (payloadRemaining == 0) {
                    if (frameListener.enabled()) {
                        frameListener.frameData(context, streamId, 0, true);
                    }
                    if (frameListener.rawDataEnabled()) {
                        frameListener.rawFrameData(context, streamId, EMPTY_BYTES, true);
                    }
                    resetFrame();
                } else {
                    state = STATE_PAYLOAD;
                    resetVarInt();
                }
            }
        }

        private boolean readVarIntByte(int next) {
            if (varIntExpectedLength == 0) {
                varIntExpectedLength = 1 << (next >>> 6);
                varIntValue = next & 0x3F;
            } else {
                varIntValue = (varIntValue << Byte.SIZE) | next;
            }
            return ++varIntLength == varIntExpectedLength;
        }

        private void resetVarInt() {
            varIntExpectedLength = 0;
            varIntLength = 0;
            varIntValue = 0;
        }

        private void resetFrame() {
            state = STATE_TYPE;
            headerLength = 0;
            payloadRemaining = 0;
            resetVarInt();
        }
    }

}
