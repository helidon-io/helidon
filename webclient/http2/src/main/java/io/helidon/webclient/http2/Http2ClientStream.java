/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http2;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameListener;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2LoggingFrameListener;
import io.helidon.http.http2.Http2Ping;
import io.helidon.http.http2.Http2Priority;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2Stream;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.StreamFlowControl;
import io.helidon.http.http2.WindowSize;
import io.helidon.webclient.api.ReleasableResource;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * Represents an HTTP/2 client stream.
 */
public class Http2ClientStream implements Http2Stream, ReleasableResource {

    private static final System.Logger LOGGER = System.getLogger(Http2ClientStream.class.getName());
    private static final Set<Http2StreamState> NON_CANCELABLE = Set.of(Http2StreamState.CLOSED, Http2StreamState.IDLE);
    // Http2Headers.create(...) clones the supplied basis headers, so one shared empty basis is sufficient.
    private static final Http2Headers EMPTY_INBOUND_HEADER_DECODE_BASIS = Http2Headers.create(WritableHeaders.create());

    private final Http2ClientConnection connection;
    private final Http2Settings serverSettings;
    private final SocketContext ctx;
    private final Duration timeout;
    private final Http2ClientConfig http2ClientConfig;
    private final LockingStreamIdSequence streamIdSeq;
    private final Http2FrameListener sendListener = new Http2LoggingFrameListener("cl-send");
    private final Http2FrameListener recvListener = new Http2LoggingFrameListener("cl-recv");
    private final Http2Settings settings = Http2Settings.create();
    private final AtomicBoolean reservationReleased = new AtomicBoolean();
    private final ReentrantLock inboundStateLock = new ReentrantLock();
    private final Condition inboundStateChanged = inboundStateLock.newCondition();
    private final CompletableFuture<Headers> trailers = new CompletableFuture<>();
    private boolean closed;

    private Http2StreamState state = Http2StreamState.IDLE;
    private ReadState readState = ReadState.INIT;
    private Http2Headers currentHeaders;
    // accessed from stream thread an connection thread
    private volatile StreamFlowControl flowControl;
    private boolean hasEntity;
    private boolean inboundEndQueued;

    // streamId and buffer can only be created when we are locked in the stream id sequence
    private int streamId;
    private StreamBuffer buffer;

    protected Http2ClientStream(Http2ClientConnection connection,
                                Http2Settings serverSettings,
                                SocketContext ctx,
                                Http2StreamConfig http2StreamConfig,
                                Http2ClientConfig http2ClientConfig,
                                LockingStreamIdSequence streamIdSeq) {
        this.connection = connection;
        this.serverSettings = serverSettings;
        this.ctx = ctx;
        this.timeout = http2StreamConfig.readTimeout();
        this.http2ClientConfig = http2ClientConfig;
        this.streamIdSeq = streamIdSeq;
    }

    @Override
    public int streamId() {
        return streamId;
    }

    @Override
    public Http2StreamState streamState() {
        return state;
    }

    @Override
    public void headers(Http2Headers headers, boolean endOfStream) {
        inboundStateLock.lock();
        try {
            headersLocked(headers, endOfStream);
            inboundStateChanged.signalAll();
        } finally {
            inboundStateLock.unlock();
        }
    }

    @Override
    public boolean rstStream(Http2RstStream rstStream) {
        if (state == Http2StreamState.IDLE) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                     "Received RST_STREAM for stream "
                                             + streamId + " in IDLE state");
        }
        updateState(Http2StreamState.checkAndGetState(this.state,
                                                      Http2FrameType.RST_STREAM,
                                                      false,
                                                      false,
                                                      false));

        throw new RuntimeException("Reset of " + streamId + " stream received!");
    }

    @Override
    public void windowUpdate(Http2WindowUpdate windowUpdate) {
        this.state = Http2StreamState.checkAndGetState(this.state,
                                                       Http2FrameType.WINDOW_UPDATE,
                                                       false,
                                                       false,
                                                       false);

        int increment = windowUpdate.windowSizeIncrement();

        //6.9/2
        if (increment == 0) {
            Http2RstStream frame = new Http2RstStream(Http2ErrorCode.PROTOCOL);
            connection.writer().write(frame.toFrameData(serverSettings, streamId, Http2Flag.NoFlags.create()));
        }
        //6.9.1/3
        if (flowControl.outbound().incrementStreamWindowSize(increment) > WindowSize.MAX_WIN_SIZE) {
            Http2RstStream frame = new Http2RstStream(Http2ErrorCode.FLOW_CONTROL);
            connection.writer().write(frame.toFrameData(serverSettings, streamId, Http2Flag.NoFlags.create()));
        }

        flowControl()
                .outbound()
                .incrementStreamWindowSize(increment);
    }

    @Override
    public void data(Http2FrameHeader header, BufferData data, boolean endOfStream) {
        updateState(Http2StreamState.checkAndGetState(this.state, header.type(), false, endOfStream, false));
        readState = readState.check(endOfStream ? ReadState.END : ReadState.DATA);
        flowControl.inbound().incrementWindowSize(header.length());
    }

    @Override
    public void priority(Http2Priority http2Priority) {
    }

    @Override
    public StreamFlowControl flowControl() {
        return flowControl;
    }

    @Override
    public void closeResource() {
        close();
    }

    void trailers(Http2Headers headers, boolean endOfStream) {
        inboundStateLock.lock();
        try {
            trailersLocked(headers, endOfStream);
            inboundStateChanged.signalAll();
        } finally {
            inboundStateLock.unlock();
        }
    }

    /**
     * Future that shall be completed once trailers are received.
     *
     * @return the completable future
     */
    public CompletableFuture<Headers> trailers() {
        return trailers;
    }

    /**
     * Determines if an entity is expected. Set to {@code false} when an EOS flag
     * is received.
     *
     * @return {@code true} if entity expected, {@code false} otherwise.
     */
    public boolean hasEntity() {
        inboundStateLock.lock();
        try {
            return hasEntity;
        } finally {
            inboundStateLock.unlock();
        }
    }

    /**
     * Cancels this client stream.
     */
    public void cancel() {
        if (NON_CANCELABLE.contains(state)) {
            return;
        }
        Http2RstStream rstStream = new Http2RstStream(Http2ErrorCode.CANCEL);
        Http2FrameData frameData = rstStream.toFrameData(settings, streamId, Http2Flag.NoFlags.create());
        sendListener.frameHeader(ctx, streamId, frameData.header());
        sendListener.frame(ctx, streamId, rstStream);
        try {
            write(frameData, false);
        } catch (UncheckedIOException e) {
            // we consider this to be a marker that the connection is already close
            ctx.log(LOGGER, DEBUG, "Exception during stream cancel", e);
        }
    }

    /**
     * Closes the stream and releases its reserved peer-concurrency slot.
     * Waiting callers are signaled so response-header waits do not block forever
     * after local cancellation or connection shutdown.
     */
    public void close() {
        inboundStateLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            inboundStateChanged.signalAll();
        } finally {
            inboundStateLock.unlock();
        }

        if (streamId != 0) {
            connection.removeStream(streamId);
        }
        // A slot is reserved before request HEADERS are written, so every close must release it.
        releaseReservation();
    }

    /**
     * Push data or header frame in to stream buffer.
     *
     * @param frameData data or header frame
     */
    void push(Http2FrameData frameData) {
        if (LOGGER.isLoggable(DEBUG)) {
            ctx.log(LOGGER, DEBUG, "%d: received frame of type %s, pushing to buffer", streamId, frameData.header().type());
        }

        buffer.push(frameData);
    }

    boolean prepareInboundData(Http2FrameData frameData) {
        int flags = frameData.header().flags();
        boolean endOfStream = (flags & Http2Flag.END_OF_STREAM) == Http2Flag.END_OF_STREAM;

        inboundStateLock.lock();
        try {
            if (closed) {
                return false;
            }
            if (inboundEndQueued || readState != ReadState.DATA) {
                throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                         "Received DATA frame in invalid response read state " + readState);
            }
            Http2StreamState nextState = Http2StreamState.checkAndGetState(state,
                                                                           frameData.header().type(),
                                                                           false,
                                                                           endOfStream,
                                                                           false);
            if (endOfStream) {
                inboundEndQueued = true;
                if (nextState == Http2StreamState.CLOSED || state == Http2StreamState.HALF_CLOSED_LOCAL) {
                    releaseReservation();
                }
            }
            return true;
        } finally {
            inboundStateLock.unlock();
        }
    }

    /**
     * Push decoded trailers into the stream buffer behind any earlier DATA frames.
     *
     * @param headers decoded trailer headers
     * @param endOfStream whether the trailer block ended the stream
     */
    void pushTrailers(Http2Headers headers, boolean endOfStream) {
        if (inboundEndQueued) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                     "Received trailers after inbound stream end was queued");
        }
        if (!endOfStream) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                     "Received trailers without END_STREAM");
        }
        Http2StreamState nextState = Http2StreamState.checkAndGetState(state,
                                                                       Http2FrameType.HEADERS,
                                                                       false,
                                                                       true,
                                                                       true);
        inboundEndQueued = true;
        if (nextState == Http2StreamState.CLOSED || state == Http2StreamState.HALF_CLOSED_LOCAL) {
            releaseReservation();
        }
        buffer.pushTrailers(headers, endOfStream);
    }

    BufferData read(int i) {
        return read();
    }

    /**
     * Reads a buffer data from the stream.
     *
     * @return the buffer data
     */
    public BufferData read() {
        while (expectsEntityData()) {
            Http2FrameData frameData = readOne(timeout);
            if (frameData != null) {
                return frameData.data();
            }
        }
        return BufferData.empty();
    }

    Status waitFor100Continue() {
        Duration readContinueTimeout = http2ClientConfig.readContinueTimeout();
        inboundStateLock.lock();
        try {
            boolean expected100Continue = readState == ReadState.CONTINUE_100_HEADERS;
            long remainingNanos = readContinueTimeout.toNanos();
            while (readState == ReadState.CONTINUE_100_HEADERS && !closed) {
                if (remainingNanos <= 0) {
                    // Timeout, continue as if it was received.
                    readState = readState.check(ReadState.HEADERS);
                    LOGGER.log(DEBUG, "Server didn't respond within 100 Continue timeout in "
                            + readContinueTimeout
                            + ", sending data.");
                    return Status.CONTINUE_100;
                }
                remainingNanos = inboundStateChanged.awaitNanos(remainingNanos);
            }
            if (expected100Continue && currentHeaders != null) {
                return currentHeaders.status();
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for 100 Continue response", e);
        } finally {
            inboundStateLock.unlock();
        }
    }

    /**
     * Writes HTTP2 headers to the stream.
     *
     * @param http2Headers the headers
     * @param endOfStream  end of stream marker
     */
    public void writeHeaders(Http2Headers http2Headers, boolean endOfStream) {
        inboundStateLock.lock();
        try {
            updateState(Http2StreamState.checkAndGetState(this.state, Http2FrameType.HEADERS, true, endOfStream, true));
            this.readState = readState.check(http2Headers.httpHeaders().containsToken(HeaderValues.EXPECT_100)
                                                     ? ReadState.CONTINUE_100_HEADERS
                                                     : ReadState.HEADERS);
            inboundStateChanged.signalAll();
        } finally {
            inboundStateLock.unlock();
        }
        Http2Flag.HeaderFlags flags;
        if (endOfStream) {
            flags = Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM);
        } else {
            flags = Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS);
        }

        boolean success = false;
        try {
            // Keep ascending streamId order among concurrent streams
            // §5.1.1 - The identifier of a newly established stream MUST be numerically
            //          greater than all streams that the initiating endpoint has opened or reserved.
            this.streamId = streamIdSeq.lockAndNext();
            this.connection.updateLastStreamId(streamId);
            this.buffer = new StreamBuffer(this, streamId);

            this.flowControl = connection.flowControl().createStreamFlowControl(
                    streamId,
                    WindowSize.DEFAULT_WIN_SIZE,
                    WindowSize.DEFAULT_MAX_FRAME_SIZE);
            // this must be done after we create the flow control, as it may be used from another thread
            this.connection.addStream(streamId, this);

            sendListener.headers(ctx, streamId, http2Headers);
            // First call to the server-starting stream, needs to be increasing sequence of odd numbers
            connection.writer().writeHeaders(http2Headers, streamId, flags, flowControl.outbound());
            success = true;
        } finally {
            if (!success) {
                // Undo stream registration and the reserved concurrency slot if the open/write path fails.
                close();
            }
            streamIdSeq.unlock();
        }
    }

    /**
     * Writes a buffer data into the stream.
     *
     * @param entityBytes buffer data
     * @param endOfStream end of stream marker
     */
    public void writeData(BufferData entityBytes, boolean endOfStream) {
        Http2FrameHeader frameHeader = Http2FrameHeader.create(entityBytes.available(),
                                                               Http2FrameTypes.DATA,
                                                               Http2Flag.DataFlags.create(endOfStream
                                                                                                  ? Http2Flag.END_OF_STREAM
                                                                                                  : 0),
                                                               streamId);
        Http2FrameData frameData = new Http2FrameData(frameHeader, entityBytes);
        write(frameData, frameData.header().flags(Http2FrameTypes.DATA).endOfStream());
    }

    /**
     * Sends PING frame to server. Can be used to check if connection is healthy.
     */
    public void sendPing() {
        connection.writer().write(Http2Ping.create().toFrameData());
    }

    /**
     * Reads headers from this stream.
     *
     * @return the headers
     */
    public Http2Headers readHeaders() {
        inboundStateLock.lock();
        try {
            long remainingNanos = timeout.toNanos();
            while (readState == ReadState.HEADERS && !closed) {
                if (remainingNanos <= 0) {
                    throw new StreamTimeoutException(this, streamId, timeout);
                }
                remainingNanos = inboundStateChanged.awaitNanos(remainingNanos);
            }
            if (currentHeaders == null && closed) {
                throw new IllegalStateException("Stream closed while waiting for response headers");
            }
            return currentHeaders;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for response headers", e);
        } finally {
            inboundStateLock.unlock();
        }
    }

    /**
     * Returns the socket context associated with the stream.
     *
     * @return the socket context
     */
    public SocketContext ctx() {
        return ctx;
    }

    /**
     * Reads an HTTP2 frame from the stream.
     *
     * @param pollTimeout timeout
     * @return the data frame
     */
    public Http2FrameData readOne(Duration pollTimeout) {
        Object inboundItem = buffer.poll(pollTimeout);

        if (inboundItem != null) {
            if (inboundItem instanceof StreamBuffer.InboundTrailers inboundTrailers) {
                inboundStateLock.lock();
                try {
                    trailersLocked(inboundTrailers.trailers(), inboundTrailers.endOfStream());
                    inboundStateChanged.signalAll();
                } finally {
                    inboundStateLock.unlock();
                }
                return null;
            }

            Http2FrameData frameData = (Http2FrameData) inboundItem;
            recvListener.frameHeader(ctx, streamId, frameData.header());
            recvListener.frame(ctx, streamId, frameData.data());

            switch (frameData.header().type()) {
            case DATA:
                int flags = frameData.header().flags();
                boolean endOfStream = (flags & Http2Flag.END_OF_STREAM) == Http2Flag.END_OF_STREAM;
                data(frameData.header(), frameData.data(), endOfStream);
                return frameData;
            default:
                LOGGER.log(DEBUG, "Dropping frame " + frameData.header() + " expected header or data.");
            }
        }
        return null;
    }

    /**
     * Returns the base headers to use when decoding the next inbound header block.
     * Final response headers after a {@code 100 Continue} must merge the previously
     * received informational headers, while the first informational block and trailers
     * always start from a fresh header set.
     *
     * @return base headers for the next inbound decode
     */
    Http2Headers inboundHeaderDecodeBasis() {
        inboundStateLock.lock();
        try {
            if (readState == ReadState.HEADERS && currentHeaders != null) {
                return currentHeaders;
            }
            return EMPTY_INBOUND_HEADER_DECODE_BASIS;
        } finally {
            inboundStateLock.unlock();
        }
    }

    /**
     * Applies a fully decoded inbound header block received on the connection thread.
     * The block is routed according to the current inbound read state:
     * informational headers, final response headers, or trailers.
     *
     * @param headers decoded headers
     * @param endOfStream whether the decoded block also ended the stream
     */
    void inboundHeaders(Http2Headers headers, boolean endOfStream) {
        inboundStateLock.lock();
        try {
            // A locally closed stream must not publish late headers, but the connection
            // thread still decodes them to keep HPACK state aligned for other streams.
            if (closed) {
                return;
            }
            switch (readState) {
            case CONTINUE_100_HEADERS -> continue100Locked(headers, endOfStream);
            case HEADERS -> headersLocked(headers, endOfStream);
            case DATA, TRAILERS -> pushTrailers(headers, endOfStream);
            default -> throw new IllegalStateException("Client is in wrong read state " + readState.name());
            }
            inboundStateChanged.signalAll();
        } finally {
            inboundStateLock.unlock();
        }
    }

    /**
     * Determines whether the caller should keep polling for inbound {@code DATA}
     * frames. Once final headers or trailers mark the response complete, reads
     * stop even if no explicit empty data frame is received.
     *
     * @return {@code true} when entity data is still expected
     */
    private boolean expectsEntityData() {
        inboundStateLock.lock();
        try {
            return state == Http2StreamState.HALF_CLOSED_LOCAL && readState != ReadState.END && hasEntity;
        } finally {
            inboundStateLock.unlock();
        }
    }

    /**
     * Applies the final non-trailer response headers and advances the inbound
     * stream state to either body reads or end-of-stream.
     *
     * @param headers decoded response headers
     * @param endOfStream whether the headers also closed the remote side
     */
    private void headersLocked(Http2Headers headers, boolean endOfStream) {
        Http2StreamState nextState =
                Http2StreamState.checkAndGetState(this.state, Http2FrameType.HEADERS, false, endOfStream, true);
        if (endOfStream && (nextState == Http2StreamState.CLOSED || state == Http2StreamState.HALF_CLOSED_LOCAL)) {
            releaseReservation();
        }
        updateState(nextState);
        readState = readState.check(endOfStream ? ReadState.END : ReadState.DATA);
        this.currentHeaders = headers;
        this.hasEntity = !endOfStream;
    }

    /**
     * Applies informational headers received while the caller is still waiting
     * on {@code Expect: 100-continue}. A final non-100 response can also arrive
     * in this state, so this method must handle both cases.
     *
     * @param headers decoded informational or final headers
     * @param endOfStream whether the headers also closed the remote side
     */
    private void continue100Locked(Http2Headers headers, boolean endOfStream) {
        // No stream state check as 100 continues are an exception.
        this.currentHeaders = headers;
        if (endOfStream) {
            if (state == Http2StreamState.HALF_CLOSED_LOCAL) {
                releaseReservation();
            }
            readState = readState.check(ReadState.END);
        } else if (headers.status() == Status.CONTINUE_100) {
            // After 100 continue normal headers are expected.
            readState = readState.check(ReadState.HEADERS);
        } else {
            // Some headers already came, but not 100 continue.
            readState = readState.check(ReadState.DATA);
        }
        this.hasEntity = !endOfStream;
    }

    /**
     * Publishes inbound trailers and completes the trailers future once the
     * remote side finishes the response.
     *
     * @param headers decoded trailer headers
     * @param endOfStream trailers must always close the remote side
     */
    private void trailersLocked(Http2Headers headers, boolean endOfStream) {
        Http2StreamState nextState =
                Http2StreamState.checkAndGetState(this.state, Http2FrameType.HEADERS, false, endOfStream, true);
        if (endOfStream && (nextState == Http2StreamState.CLOSED || state == Http2StreamState.HALF_CLOSED_LOCAL)) {
            releaseReservation();
        }
        updateState(nextState);
        readState = readState.check(ReadState.END);
        hasEntity = false;
        trailers.complete(headers.httpHeaders());
    }

    private void write(Http2FrameData frameData, boolean endOfStream) {
        updateState(Http2StreamState.checkAndGetState(this.state,
                                                      frameData.header().type(),
                                                      true,
                                                      endOfStream,
                                                      false));
        connection.writer().writeData(frameData,
                                      flowControl().outbound());
    }

    private void updateState(Http2StreamState newState) {
        this.state = newState;
        if (newState == Http2StreamState.CLOSED) {
            releaseReservation();
        }
    }

    private void releaseReservation() {
        if (reservationReleased.compareAndSet(false, true)) {
            connection.releaseReservedStream();
        }
    }

    enum ReadState {
        END,
        TRAILERS(END),
        DATA(TRAILERS, END),
        HEADERS(DATA, TRAILERS, END),
        CONTINUE_100_HEADERS(HEADERS, DATA, END),
        INIT(CONTINUE_100_HEADERS, HEADERS);

        private final Set<ReadState> allowedTransitions;

        ReadState(ReadState... allowedTransitions) {
            this.allowedTransitions = Set.of(allowedTransitions);
        }

        ReadState check(ReadState newState) {
            if (this == newState || allowedTransitions.contains(newState)) {
                return newState;
            }
            throw new IllegalStateException("Transition from " + this + " to " + newState + " is not allowed!");
        }
    }
}
