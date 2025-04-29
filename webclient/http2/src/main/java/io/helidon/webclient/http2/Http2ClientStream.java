/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
import io.helidon.http.http2.Http2HuffmanDecoder;
import io.helidon.http.http2.Http2LoggingFrameListener;
import io.helidon.http.http2.Http2Ping;
import io.helidon.http.http2.Http2Priority;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2Stream;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.StreamFlowControl;
import io.helidon.http.http2.WindowSize;
import io.helidon.webclient.api.ReleasableResource;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * Represents an HTTP2 client stream. This class is not intended to be used by
 * applications, it is only public internally within Helidon.
 */
public class Http2ClientStream implements Http2Stream, ReleasableResource {

    private static final System.Logger LOGGER = System.getLogger(Http2ClientStream.class.getName());
    private static final Set<Http2StreamState> NON_CANCELABLE = Set.of(Http2StreamState.CLOSED, Http2StreamState.IDLE);
    private static final Http2FrameData HTTP2_PING = Http2Ping.create().toFrameData();

    private final Http2ClientConnection connection;
    private final Http2Settings serverSettings;
    private final SocketContext ctx;
    private final Duration timeout;
    private final Http2ClientConfig http2ClientConfig;
    private final LockingStreamIdSequence streamIdSeq;
    private final Http2FrameListener sendListener = new Http2LoggingFrameListener("cl-send");
    private final Http2FrameListener recvListener = new Http2LoggingFrameListener("cl-recv");
    private final Http2Settings settings = Http2Settings.create();
    private final List<Http2FrameData> continuationData = new ArrayList<>();
    private final CompletableFuture<Headers> trailers = new CompletableFuture<>();

    private Http2StreamState state = Http2StreamState.IDLE;
    private ReadState readState = ReadState.INIT;
    private Http2Headers currentHeaders;
    // accessed from stream thread an connection thread
    private volatile StreamFlowControl flowControl;
    private boolean hasEntity;

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
        this.state = Http2StreamState.checkAndGetState(this.state, Http2FrameType.HEADERS, false, endOfStream, true);
        readState = readState.check(endOfStream ? ReadState.END : ReadState.DATA);
        this.currentHeaders = headers;
        this.hasEntity = !endOfStream;
    }

    @Override
    public boolean rstStream(Http2RstStream rstStream) {
        if (state == Http2StreamState.IDLE) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                     "Received RST_STREAM for stream "
                                             + streamId + " in IDLE state");
        }
        this.state = Http2StreamState.checkAndGetState(this.state,
                                                       Http2FrameType.RST_STREAM,
                                                       false,
                                                       false,
                                                       false);

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
        this.state = Http2StreamState.checkAndGetState(this.state, header.type(), false, endOfStream, false);
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
        state = Http2StreamState.checkAndGetState(this.state, Http2FrameType.HEADERS, false, endOfStream, true);
        readState = readState.check(ReadState.END);
        trailers.complete(headers.httpHeaders());
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
        return hasEntity;
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
     * Removes the stream from underlying connection.
     */
    public void close() {
        connection.removeStream(streamId);
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

    BufferData read(int i) {
        return read();
    }

    /**
     * Reads a buffer data from the stream.
     *
     * @return the buffer data
     */
    public BufferData read() {
        while (state == Http2StreamState.HALF_CLOSED_LOCAL && readState != ReadState.END && hasEntity) {
            Http2FrameData frameData = readOne(timeout);
            if (frameData != null) {
                return frameData.data();
            }
        }
        return BufferData.empty();
    }

    Status waitFor100Continue() {
        Duration readContinueTimeout = http2ClientConfig.readContinueTimeout();
        boolean expected100Continue = readState == ReadState.CONTINUE_100_HEADERS;
        try {
            while (readState == ReadState.CONTINUE_100_HEADERS) {
                readOne(readContinueTimeout);
            }
        } catch (StreamTimeoutException ignored) {
            // Timeout, continue as if it was received
            readState = readState.check(ReadState.HEADERS);
            LOGGER.log(DEBUG, "Server didn't respond within 100 Continue timeout in "
                    + readContinueTimeout
                    + ", sending data.");
            return Status.CONTINUE_100;
        }
        if (expected100Continue && currentHeaders != null) {
            return currentHeaders.status();
        }
        return null;
    }

    /**
     * Writes HTTP2 headers to the stream.
     *
     * @param http2Headers the headers
     * @param endOfStream end of stream marker
     */
    public void writeHeaders(Http2Headers http2Headers, boolean endOfStream) {
        this.state = Http2StreamState.checkAndGetState(this.state, Http2FrameType.HEADERS, true, endOfStream, true);
        this.readState = readState.check(http2Headers.httpHeaders().contains(HeaderValues.EXPECT_100)
                                                 ? ReadState.CONTINUE_100_HEADERS
                                                 : ReadState.HEADERS);
        Http2Flag.HeaderFlags flags;
        if (endOfStream) {
            flags = Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM);
        } else {
            flags = Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS);
        }

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
        } finally {
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
        splitAndWrite(frameData);
    }

    /**
     * Sends PING frame to server. Can be used to check if connection is healthy.
     */
    public void sendPing() {
        connection.writer().write(HTTP2_PING);
    }

    /**
     * Reads headers from this stream.
     *
     * @return the headers
     */
    public Http2Headers readHeaders() {
        while (readState == ReadState.HEADERS) {
            Http2FrameData frameData = readOne(timeout);
            if (frameData != null) {
                throw new IllegalStateException("Unexpected frame type " + frameData.header() + ", HEADERS are expected.");
            }
        }
        return currentHeaders;
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
        Http2FrameData frameData = buffer.poll(pollTimeout);

        if (frameData != null) {

            recvListener.frameHeader(ctx, streamId, frameData.header());
            recvListener.frame(ctx, streamId, frameData.data());

            int flags = frameData.header().flags();
            boolean endOfStream = (flags & Http2Flag.END_OF_STREAM) == Http2Flag.END_OF_STREAM;
            boolean endOfHeaders = (flags & Http2Flag.END_OF_HEADERS) == Http2Flag.END_OF_HEADERS;

            switch (frameData.header().type()) {
            case DATA:
                data(frameData.header(), frameData.data(), endOfStream);
                return frameData;

            case HEADERS, CONTINUATION:
                continuationData.add(frameData);

                // (HEADERS[100-continue] CONTINUATION*)*
                // ^------- endOfHeaders
                // HEADERS+
                // CONTINUATION*
                // ^------- endOfHeaders
                // DATA*
                // (HEADERS[trailers] CONTINUATION*)+
                // ^------- endOfHeaders
                if (endOfHeaders) {
                    var requestHuffman = Http2HuffmanDecoder.create();

                    //  HTTP/1.1 100 Continue            HEADERS
                    //  Extension-Field: bar       ==>     - END_STREAM
                    //                                     + END_HEADERS
                    //                                       :status = 100
                    //                                       extension-field = bar
                    //
                    //  HTTP/1.1 200 OK                  HEADERS
                    //  Content-Type: image/jpeg   ==>     - END_STREAM
                    //  Transfer-Encoding: chunked         + END_HEADERS
                    //  Trailer: Foo                         :status = 200
                    //                                       content-type = image/jpeg
                    //  123                                  trailer = Foo
                    //  {binary data}
                    //  0                                DATA
                    //  Foo: bar                           - END_STREAM
                    //                                   {binary data}
                    //
                    //                                   HEADERS
                    //                                     + END_STREAM
                    //                                     + END_HEADERS
                    //                                       foo = bar
                    switch (readState) {
                    case CONTINUE_100_HEADERS -> {
                        Http2Headers http2Headers = readHeaders(requestHuffman, false);
                        // Clear out for headers
                        continuationData.clear();
                        this.continue100(http2Headers, endOfStream);
                    }
                    case HEADERS -> {
                        // Add extension headers from 100 Continue
                        Http2Headers http2Headers = readHeaders(requestHuffman, true);
                        // Clear out for trailers
                        continuationData.clear();
                        this.headers(http2Headers, endOfStream);
                    }
                    case DATA, TRAILERS -> {
                        Http2Headers http2Headers = readHeaders(requestHuffman, false);
                        this.trailers(http2Headers, endOfStream);
                    }
                    default -> throw new IllegalStateException("Client is in wrong read state " + readState.name());
                    }
                }
                break;
            default:
                LOGGER.log(DEBUG, "Dropping frame " + frameData.header() + " expected header or data.");
            }
        }
        return null;
    }

    private void continue100(Http2Headers headers, boolean endOfStream) {
        // no stream state check as 100 continues are an exception
        this.currentHeaders = headers;
        if (endOfStream) {
            readState = readState.check(ReadState.END);
        } else if (headers.status() == Status.CONTINUE_100) {
            // After 100 continue normal headers are expected
            readState = readState.check(ReadState.HEADERS);
        } else {
            // Some headers already came, but not 100 continue
            readState = readState.check(ReadState.DATA);
        }
        this.hasEntity = !endOfStream;
    }

    private Http2Headers readHeaders(Http2HuffmanDecoder decoder, boolean mergeWithPrevious) {
        Http2Headers http2Headers = Http2Headers.create(this,
                                                        connection.getInboundDynamicTable(),
                                                        decoder,
                                                        mergeWithPrevious && currentHeaders != null
                                                                ? currentHeaders
                                                                : Http2Headers.create(WritableHeaders.create()),
                                                        continuationData.toArray(new Http2FrameData[0]));
        recvListener.headers(ctx, streamId, http2Headers);
        return http2Headers;
    }

    private void splitAndWrite(Http2FrameData frameData) {
        int maxFrameSize = this.serverSettings.value(Http2Setting.MAX_FRAME_SIZE).intValue();

        // Split to frames if bigger than max frame size
        Http2FrameData[] frames = frameData.split(maxFrameSize);
        for (Http2FrameData frame : frames) {
            write(frame, frame.header().flags(Http2FrameTypes.DATA).endOfStream());
        }
    }

    private void write(Http2FrameData frameData, boolean endOfStream) {
        this.state = Http2StreamState.checkAndGetState(this.state,
                                                       frameData.header().type(),
                                                       true,
                                                       endOfStream,
                                                       false);
        connection.writer().writeData(frameData,
                                      flowControl().outbound());
    }

    enum ReadState {
        END,
        TRAILERS(END),
        DATA(TRAILERS, END),
        HEADERS(DATA, TRAILERS, END),
        CONTINUE_100_HEADERS(HEADERS, DATA, END),
        INIT(CONTINUE_100_HEADERS, HEADERS);

        private final Set<ReadState> allowedTransitions;

        ReadState(ReadState... allowedTransitions){
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
