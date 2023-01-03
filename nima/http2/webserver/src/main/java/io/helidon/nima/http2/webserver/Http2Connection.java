/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.http2.webserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValues;
import io.helidon.common.http.HttpPrologue;
import io.helidon.nima.http2.FlowControl;
import io.helidon.nima.http2.Http2ConnectionWriter;
import io.helidon.nima.http2.Http2ErrorCode;
import io.helidon.nima.http2.Http2Exception;
import io.helidon.nima.http2.Http2Flag;
import io.helidon.nima.http2.Http2FrameData;
import io.helidon.nima.http2.Http2FrameHeader;
import io.helidon.nima.http2.Http2FrameListener;
import io.helidon.nima.http2.Http2FrameType;
import io.helidon.nima.http2.Http2FrameTypes;
import io.helidon.nima.http2.Http2GoAway;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.http2.Http2HuffmanDecoder;
import io.helidon.nima.http2.Http2LoggingFrameListener;
import io.helidon.nima.http2.Http2Ping;
import io.helidon.nima.http2.Http2Priority;
import io.helidon.nima.http2.Http2RstStream;
import io.helidon.nima.http2.Http2Setting;
import io.helidon.nima.http2.Http2Settings;
import io.helidon.nima.http2.Http2StreamState;
import io.helidon.nima.http2.Http2Util;
import io.helidon.nima.http2.Http2WindowUpdate;
import io.helidon.nima.http2.WindowSize;
import io.helidon.nima.webserver.CloseConnectionException;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.spi.ServerConnection;

import static io.helidon.nima.http2.Http2Util.PREFACE_LENGTH;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * HTTP/2 server connection.
 * A single connection is created between a client and a server.
 * A single connection serves multiple streams.
 */
public class Http2Connection implements ServerConnection {
    static final String FULL_PROTOCOL = "HTTP/2.0";
    static final String PROTOCOL = "HTTP";
    static final String PROTOCOL_VERSION = "2.0";

    private static final System.Logger LOGGER = System.getLogger(Http2Connection.class.getName());
    private static final int FRAME_HEADER_LENGTH = 9;

    private final Map<Integer, StreamContext> streams = new HashMap<>(1000);
    private final ConnectionContext ctx;
    private final HttpRouting routing;
    private final Http2Headers.DynamicTable requestDynamicTable;
    private final Http2HuffmanDecoder requestHuffman;
    private final Http2FrameListener receiveFrameListener = // Http2FrameListener.create(List.of());
            Http2FrameListener.create(List.of(new Http2LoggingFrameListener("recv")));
    private final Http2ConnectionWriter connectionWriter;
    private final WindowSize connectionWindowSize = new WindowSize();
    private final DataReader reader;

    private Http2Settings serverSettings = Http2Settings.builder()
            .add(Http2Setting.ENABLE_PUSH, false)
            .build();
    // initial client settings, until we receive real ones
    private Http2Settings clientSettings = Http2Settings.builder()
            .build();
    private Http2FrameHeader frameHeader;
    private BufferData frameInProgress;
    private Http2Ping ping;
    private boolean expectPreface;
    private HttpPrologue upgradePrologue;
    private Http2Headers upgradeHeaders;
    private boolean returnErrorDetails = true;
    private State state = State.WRITE_SERVER_SETTINGS;
    private int continuationExpectedStreamId;
    private int lastStreamId;
    private long maxClientFrameSize = 16_384;
    private int streamInitialWindowSize = WindowSize.DEFAULT_WIN_SIZE;

    Http2Connection(ConnectionContext ctx) {
        this.ctx = ctx;

        this.connectionWriter = new Http2ConnectionWriter(ctx, ctx.dataWriter(), List.of(new Http2LoggingFrameListener("send")));
        this.requestDynamicTable = Http2Headers.DynamicTable.create(serverSettings.value(Http2Setting.HEADER_TABLE_SIZE));
        this.requestHuffman = new Http2HuffmanDecoder();
        this.routing = ctx.router().routing(HttpRouting.class, HttpRouting.empty());
        this.reader = ctx.dataReader();
    }

    @Override
    public void handle() throws InterruptedException {
        try {
            doHandle();
        } catch (Http2Exception e) {
            if (state == State.FINISHED) {
                // already handled
                return;
            }
            ctx.log(LOGGER, DEBUG, "Intentional HTTP/2 exception, code: %s, message: %s",
                    e.code(),
                    e.getMessage());

            ctx.log(LOGGER, TRACE, "Stacktrace of HTTP/2 exception", e);

            Http2GoAway frame = new Http2GoAway(0,
                                                e.code(),
                                                returnErrorDetails ? e.getMessage() : "");
            connectionWriter.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()), FlowControl.NOOP);
            state = State.FINISHED;
        } catch (CloseConnectionException | InterruptedException e) {
            throw e;
        } catch (Throwable e) {
            if (state == State.FINISHED) {
                // we have already finished this
                return;
            }
            Http2GoAway frame = new Http2GoAway(0,
                                                Http2ErrorCode.INTERNAL,
                                                returnErrorDetails ? e.getClass().getName() + ": " + e.getMessage() : "");
            connectionWriter.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()), FlowControl.NOOP);
            state = State.FINISHED;
            throw e;
        }
    }

    /**
     * Client settings, obtained from HTTP/2 upgrade request.
     *
     * @param http2Settings client settings to use
     */
    public void clientSettings(Http2Settings http2Settings) {
        this.clientSettings = http2Settings;
        if (this.clientSettings.hasValue(Http2Setting.HEADER_TABLE_SIZE)) {
            updateHeaderTableSize(clientSettings.value(Http2Setting.HEADER_TABLE_SIZE));
        }
    }

    /**
     * Connection headers from an upgrade request from HTTP/1.1.
     *
     * @param prologue prologue of the HTTP/2 request
     * @param headers  headers to use for first stream (obtained from original HTTP/1.1 request)
     */
    public void upgradeConnectionData(HttpPrologue prologue, Http2Headers headers) {
        this.upgradePrologue = prologue;
        this.upgradeHeaders = headers;
    }

    /**
     * Expect connection preface (prior knowledge).
     */
    public void expectPreface() {
        this.expectPreface = true;
    }

    @Override
    public String toString() {
        return "[" + ctx.socketId() + " " + ctx.childSocketId() + "]";
    }

    private void doHandle() throws InterruptedException {

        while (state != State.FINISHED) {
            if (expectPreface && state != State.WRITE_SERVER_SETTINGS) {
                readPreface();
                expectPreface = false;
            }
            // usually we read frame and then process it
            if (state == State.READ_FRAME) {
                try {
                    readFrame();
                } catch (DataReader.InsufficientDataAvailableException e) {
                    // no data to read -> connection is closed
                    throw new CloseConnectionException("Connection closed by client", e);
                }
            }
            switch (state) {
            case CONTINUATION -> doContinuation();
            case WRITE_SERVER_SETTINGS -> writeServerSettings();
            case WINDOW_UPDATE -> windowUpdateFrame();
            case SETTINGS -> doSettings();
            case ACK_SETTINGS -> ackSettings();
            case DATA -> dataFrame();
            case HEADERS -> doHeaders();
            case PRIORITY -> doPriority();
            case READ_PUSH_PROMISE -> throw new Http2Exception(Http2ErrorCode.REFUSED_STREAM, "Push promise not supported");
            case PING -> pingFrame();
            case SEND_PING_ACK -> writePingAck();
            case GO_AWAY ->
                // todo we may need to do graceful shutdown to process the last stream
                    goAwayFrame();
            case RST_STREAM -> rstStream();
            default -> unknownFrame();
            }
        }
    }

    private void readPreface() {
        BufferData preface = reader.readBuffer(PREFACE_LENGTH);
        byte[] bytes = new byte[PREFACE_LENGTH];
        preface.read(bytes);
        if (!Http2Util.isPreface(bytes)) {
            throw new IllegalStateException("Invalid HTTP/2 connection preface: \n" + preface.debugDataHex(true));
        }
        ctx.log(LOGGER, TRACE, "Processed preface data");
        state = State.READ_FRAME;
    }

    private void readFrame() {
        BufferData frameHeaderBuffer = reader.readBuffer(FRAME_HEADER_LENGTH);

        receiveFrameListener.frameHeader(ctx, frameHeaderBuffer);
        frameHeader = Http2FrameHeader.create(frameHeaderBuffer);
        receiveFrameListener.frameHeader(ctx, this.frameHeader);

        // https://datatracker.ietf.org/doc/html/rfc7540#section-4.1
        if (serverSettings.value(Http2Setting.MAX_FRAME_SIZE) < frameHeader.length()) {
            throw new Http2Exception(Http2ErrorCode.FRAME_SIZE, "Frame size " + frameHeader.length() + " is too big");
        }

        frameHeader.type().checkLength(frameHeader.length());

        // read the frame
        if (frameHeader.length() == 0) {
            frameInProgress = BufferData.empty();
        } else {
            frameInProgress = reader.readBuffer(frameHeader.length());
        }

        receiveFrameListener.frame(ctx, frameInProgress);
        this.state = switch (frameHeader.type()) {
            case DATA -> State.DATA;
            case HEADERS -> State.HEADERS;
            case PRIORITY -> State.PRIORITY;
            case RST_STREAM -> State.RST_STREAM;
            case SETTINGS -> State.SETTINGS;
            case PUSH_PROMISE -> State.READ_PUSH_PROMISE;
            case PING -> State.PING;
            case GO_AWAY -> State.GO_AWAY;
            case WINDOW_UPDATE -> State.WINDOW_UPDATE;
            case CONTINUATION -> State.CONTINUATION;
            case UNKNOWN -> State.UNKNOWN;
        };

        if (continuationExpectedStreamId != 0) {
            // somebody expects header data, we cannot receive anything else
            int streamId = frameHeader.streamId();

            if (state == State.CONTINUATION) {
                if (continuationExpectedStreamId != streamId) {
                    // TODO go away should send the id of the last processed stream (for retries etc.)
                    // received headers for wrong stream
                    throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Received CONTINUATION for stream " + streamId
                            + ", expected for " + continuationExpectedStreamId);
                }
            } else {
                throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Expecting CONTINUATION for stream "
                        + continuationExpectedStreamId + ", received " + state + " for " + streamId);
            }
        }
    }

    private void doContinuation() {
        Http2Flag.ContinuationFlags flags = frameHeader.flags(Http2FrameTypes.CONTINUATION);
        List<Http2FrameData> continuationData = stream(frameHeader.streamId()).contData();
        if (continuationData.isEmpty()) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Received continuation without headers.");
        }
        continuationData.add(new Http2FrameData(frameHeader, inProgressFrame()));
        if (flags.endOfHeaders()) {
            state = State.HEADERS;
        } else {
            state = State.READ_FRAME;
        }
    }

    private void writeServerSettings() {
        connectionWriter.write(serverSettings.toFrameData(serverSettings, 0, Http2Flag.SettingsFlags.create(0)),
                               FlowControl.NOOP);
        state = State.READ_FRAME;
    }

    private void windowUpdateFrame() {
        Http2WindowUpdate windowUpdate = Http2WindowUpdate.create(inProgressFrame());
        receiveFrameListener.frame(ctx, windowUpdate);
        state = State.READ_FRAME;

        boolean overflow;

        if (frameHeader.streamId() == 0) {
            // overall connection
            // todo implement
            if (windowUpdate.windowSizeIncrement() == 0) {
                Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.PROTOCOL, "Window size 0");
                connectionWriter.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()), FlowControl.NOOP);
            }
            overflow = connectionWindowSize.incrementWindowSize(windowUpdate.windowSizeIncrement());
            if (overflow) {
                Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.FLOW_CONTROL, "Window size too big. Max: ");
                connectionWriter.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()), FlowControl.NOOP);
            }
        } else {
            try {
                StreamContext stream = stream(frameHeader.streamId());
                stream.stream().windowUpdate(windowUpdate);
            } catch (Http2Exception ignored) {
                // stream closed
            }
        }
    }

    private void doSettings() {
        if (frameHeader.streamId() != 0) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Settings must use stream ID 0, but use " + frameHeader.streamId());
        }
        Http2Flag.SettingsFlags flags = frameHeader.flags(Http2FrameTypes.SETTINGS);

        if (flags.ack()) {
            state = State.READ_FRAME;
            if (frameHeader.length() > 0) {
                throw new Http2Exception(Http2ErrorCode.FRAME_SIZE, "Settings with ACK should not have payload.");
            }
        } else {
            this.clientSettings = Http2Settings.create(inProgressFrame());
            this.receiveFrameListener.frame(ctx, clientSettings);

            this.clientSettings.presentValue(Http2Setting.HEADER_TABLE_SIZE)
                    .ifPresent(this::updateHeaderTableSize);

            Optional<Long> windowSize = this.clientSettings.presentValue(Http2Setting.INITIAL_WINDOW_SIZE);
            if (windowSize.isPresent()) {
                long it = windowSize.get();

                //6.9.2/3 - legal range for the increment to the flow-control window is 1 to 2^31-1 (2,147,483,647) octets.
                if (it > WindowSize.MAX_WIN_SIZE) {
                    Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.FLOW_CONTROL, "Window " + it + " size too large");
                    connectionWriter.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()), FlowControl.NOOP);
                }

                //6.9.1/1 - changing the flow-control window for streams that are not yet active
                streamInitialWindowSize = (int) it;

                //6.9.2/1 - SETTINGS frame can alter the initial flow-control
                //   window size for streams with active flow-control windows (that is,
                //   streams in the "open" or "half-closed (remote)" state)
                for (StreamContext sctx : streams.values()) {
                    Http2StreamState streamState = sctx.stream.streamState();
                    if (streamState == Http2StreamState.OPEN || streamState == Http2StreamState.HALF_CLOSED_REMOTE) {
                        sctx.stream.flowControl().resetStreamWindowSize(it);
                    }
                }

                // Unblock frames waiting for update
                this.connectionWindowSize.triggerUpdate();
            }

            this.clientSettings.presentValue(Http2Setting.MAX_FRAME_SIZE)
                    .ifPresent(it -> this.maxClientFrameSize = it);

            // specification defines, that the frame size must be between the initial size (16384) and 2^24-1
            if (this.maxClientFrameSize < 16_384 || this.maxClientFrameSize > 16_777_215) {
                throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Frame size must be between 2^14 and 2^24-1, but is: "
                        + maxClientFrameSize);
            }

            // TODO for each
            //        Http2Setting.MAX_CONCURRENT_STREAMS;
            //        Http2Setting.MAX_HEADER_LIST_SIZE;
            state = State.ACK_SETTINGS;
        }
    }

    private void ackSettings() {
        Http2Flag.SettingsFlags flags = Http2Flag.SettingsFlags.create(Http2Flag.ACK);
        Http2FrameHeader header = Http2FrameHeader.create(0, Http2FrameTypes.SETTINGS, flags, 0);
        connectionWriter.write(new Http2FrameData(header, BufferData.empty()), FlowControl.NOOP);
        state = State.READ_FRAME;

        if (upgradeHeaders != null) {
            // initial request from outside
            Headers httpHeaders = upgradeHeaders.httpHeaders();
            boolean hasEntity = httpHeaders.contains(Header.CONTENT_LENGTH)
                    || httpHeaders.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            // we now have all information needed to execute
            Http2Stream stream = stream(1).stream();
            stream.prologue(upgradePrologue);
            stream.headers(upgradeHeaders, !hasEntity);
            upgradeHeaders = null;
            ctx.sharedExecutor()
                    .submit(new StreamRunnable(streams, stream, stream.streamId()));
        }
    }

    private void dataFrame() {
        BufferData buffer;

        StreamContext stream = stream(frameHeader.streamId());
        stream.stream().checkDataReceivable();

        // todo we need to have some information about how much data is buffered for a stream
        // to prevent OOM (use flow control!)
        if (frameHeader.flags(Http2FrameTypes.DATA).padded()) {
            BufferData frameData = inProgressFrame();
            int padLength = frameData.read();

            if (frameHeader.length() == padLength) {
                buffer = null;
            } else if (frameHeader.length() - padLength > 0) {
                buffer = BufferData.create(frameHeader.length() - padLength);
                buffer.write(frameData);
            } else {
                throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Invalid pad length");
            }
            frameData.skip(padLength);
        } else {
            buffer = inProgressFrame();
        }

        // TODO buffer now contains the actual data bytes
        stream.stream().data(frameHeader, buffer);

        state = State.READ_FRAME;
    }

    private void doHeaders() {
        int streamId = frameHeader.streamId();
        StreamContext streamContext = stream(streamId);

        streamContext.stream().checkHeadersReceivable();

        // first frame, expecting continuation
        if (frameHeader.type() == Http2FrameType.HEADERS && !frameHeader.flags(Http2FrameTypes.HEADERS).endOfHeaders()) {
            // this needs to retain the data until we receive last continuation, cannot use the same data
            streamContext.contData().clear();
            streamContext.contData().add(new Http2FrameData(frameHeader, inProgressFrame().copy()));
            streamContext.continuationHeader = frameHeader;
            this.continuationExpectedStreamId = streamId;
            this.state = State.READ_FRAME;
            return;
        }

        // we are sure this is the last frame of headers
        boolean endOfStream;
        Http2Headers headers;
        Http2Stream stream = streamContext.stream();

        if (frameHeader.type() == Http2FrameType.CONTINUATION) {
            // end of continuations with header frames
            List<Http2FrameData> frames = streamContext.contData();
            headers = Http2Headers.create(stream,
                                          requestDynamicTable,
                                          requestHuffman,
                                          frames.toArray(new Http2FrameData[0]));
            endOfStream = streamContext.continuationHeader.flags(Http2FrameTypes.HEADERS).endOfStream();
            frames.clear();
            streamContext.continuationHeader = null;
            continuationExpectedStreamId = 0;
        } else {
            endOfStream = frameHeader.flags(Http2FrameTypes.HEADERS).endOfStream();
            headers = Http2Headers.create(stream,
                                          requestDynamicTable,
                                          requestHuffman,
                                          new Http2FrameData(frameHeader, inProgressFrame()));
        }

        receiveFrameListener.headers(ctx, headers);
        headers.validateRequest();
        // todo configure path validation
        String path = headers.path();
        Http.Method method = headers.method();
        HttpPrologue httpPrologue = HttpPrologue.create(FULL_PROTOCOL,
                                                        PROTOCOL,
                                                        PROTOCOL_VERSION,
                                                        method,
                                                        path,
                                                        true);
        stream.prologue(httpPrologue);
        stream.headers(headers, endOfStream);
        state = State.READ_FRAME;

        // we now have all information needed to execute
        ctx.sharedExecutor()
                .submit(new StreamRunnable(streams, stream, stream.streamId()));
    }

    private void pingFrame() {
        if (frameHeader.streamId() != 0) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                     "Received ping for a stream " + frameHeader.streamId());
        }
        if (frameHeader.length() != 8) {
            throw new Http2Exception(Http2ErrorCode.FRAME_SIZE,
                                     "Received ping with wrong size. Should be 8 bytes, is " + frameHeader.length());
        }
        if (frameHeader.flags(Http2FrameTypes.PING).ack()) {
            // ignore - ACK for an unsent ping (we do not send pings for now)
            state = State.READ_FRAME;
        } else {
            ping = Http2Ping.create(inProgressFrame());
            receiveFrameListener.frame(ctx, ping);
            state = State.SEND_PING_ACK;
        }
    }

    private void doPriority() {
        Http2Priority http2Priority = Http2Priority.create(inProgressFrame());
        receiveFrameListener.frame(ctx, http2Priority);

        if (frameHeader.streamId() == 0) {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Priority with stream id 0");
        }

        if (http2Priority.streamId() != 0) {
            try {
                stream(http2Priority.streamId()); // dependency on another stream, may be created in idle state
            } catch (Http2Exception ignored) {
                // this stream is closed, ignore
            }
        }
        StreamContext stream;
        try {
            stream = stream(frameHeader.streamId());
        } catch (Http2Exception ignored) {
            // this stream is closed, ignore
            state = State.READ_FRAME;
            return;
        }
        if (stream.continuationData.isEmpty()) {
            stream.stream().priority(http2Priority);
            state = State.READ_FRAME;
        } else {
            throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Received priority while processing headers");
        }
    }

    private void writePingAck() {
        BufferData frame = ping.data();
        Http2FrameHeader header = Http2FrameHeader.create(frame.available(),
                                                          Http2FrameTypes.PING,
                                                          Http2Flag.PingFlags.create(Http2Flag.ACK),
                                                          0);
        ping = null;
        connectionWriter.write(new Http2FrameData(header, frame), FlowControl.NOOP);
        state = State.READ_FRAME;
    }

    private void goAwayFrame() {
        Http2GoAway go = Http2GoAway.create(inProgressFrame());
        receiveFrameListener.frame(ctx, go);
        state = State.FINISHED;
        if (go.errorCode() != Http2ErrorCode.NO_ERROR) {
            ctx.log(LOGGER, DEBUG, "Received go away. Error code: %s, message: %s",
                    go.errorCode(),
                    go.details());
        }
    }

    private void rstStream() {
        Http2RstStream rstStream = Http2RstStream.create(inProgressFrame());
        receiveFrameListener.frame(ctx, rstStream);

        StreamContext streamContext = stream(frameHeader.streamId());
        streamContext.stream().rstStream(rstStream);

        state = State.READ_FRAME;
    }

    private void unknownFrame() {
        // intentionally ignored
        state = State.READ_FRAME;
    }

    private StreamContext stream(int streamId) {
        if (streamId % 2 == 0) {
            // client side must send odd identifiers
            throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                     "Stream " + streamId + " is even, only odd numbers allowed");
        }
        boolean same = streamId == lastStreamId;
        lastStreamId = Math.max(streamId, lastStreamId);

        // this method is only called from a single thread (the connection thread)
        StreamContext streamContext = streams.get(streamId);
        if (streamContext == null) {
            if (same) {
                throw new Http2Exception(Http2ErrorCode.STREAM_CLOSED,
                                         "Stream closed");
            }
            if (streamId < lastStreamId) {
                // check if the newer streams are in idle state (if yes, this is OK)
                for (StreamContext context : streams.values()) {
                    if (context.streamId > streamId && context.stream().streamState() != Http2StreamState.IDLE) {
                        throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                                 "Stream " + streamId
                                                         + " was never created and has lower ID than last: " + lastStreamId);
                    }
                }
            }

            streamContext = new StreamContext(streamId,
                                              new Http2Stream(ctx,
                                                              routing,
                                                              streamId,
                                                              serverSettings,
                                                              clientSettings,
                                                              connectionWriter,
                                                              FlowControl.create(streamId,
                                                                                 streamInitialWindowSize,
                                                                                 connectionWindowSize)));
            streams.put(streamId, streamContext);
        }

        return streamContext;
    }

    private BufferData inProgressFrame() {
        BufferData inProgress = this.frameInProgress;
        this.frameInProgress = null;
        if (inProgress == null) {
            throw new Http2Exception(Http2ErrorCode.INTERNAL, "In progress frame is null for state: " + state);
        }
        return inProgress;
    }

    private void updateHeaderTableSize(long value) {
        try {
            connectionWriter.updateHeaderTableSize(value);
        } catch (InterruptedException e) {
            throw new CloseConnectionException("Failed to update header table size, interrupted", e);
        }
    }

    private enum State {
        READ_FRAME,
        DATA,
        HEADERS,
        PRIORITY,
        RST_STREAM,
        SETTINGS,
        READ_PUSH_PROMISE,
        PING,
        GO_AWAY,
        WINDOW_UPDATE,
        ACK_SETTINGS,
        WRITE_SERVER_SETTINGS,
        FINISHED,
        SEND_PING_ACK,
        CONTINUATION,
        // unknown frames must be discarded
        UNKNOWN
    }

    private static final class StreamRunnable implements Runnable {
        private final Map<Integer, StreamContext> streams;
        private final Http2Stream stream;
        private final int streamId;

        private StreamRunnable(Map<Integer, StreamContext> streams, Http2Stream stream, int streamId) {
            this.streams = streams;
            this.stream = stream;
            this.streamId = streamId;
        }

        @Override
        public void run() {
            stream.run();
            streams.remove(stream.streamId());
        }
    }

    private static class StreamContext {
        private final List<Http2FrameData> continuationData = new ArrayList<>();
        private final int streamId;
        private final Http2Stream stream;

        private Http2FrameHeader continuationHeader;

        StreamContext(int streamId, Http2Stream stream) {
            this.streamId = streamId;
            this.stream = stream;
        }

        public Http2Stream stream() {
            return stream;
        }

        public Http2FrameHeader contHeader() {
            return continuationHeader;
        }

        public List<Http2FrameData> contData() {
            return continuationData;
        }
    }
}
