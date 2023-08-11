/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.io.UncheckedIOException;
import java.net.SocketException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderNames;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.task.InterruptableTask;
import io.helidon.nima.http2.ConnectionFlowControl;
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
import io.helidon.nima.http2.webserver.spi.Http2SubProtocolSelector;
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
public class Http2Connection implements ServerConnection, InterruptableTask<Void> {
    static final String FULL_PROTOCOL = "HTTP/2.0";
    static final String PROTOCOL = "HTTP";
    static final String PROTOCOL_VERSION = "2.0";

    private static final System.Logger LOGGER = System.getLogger(Http2Connection.class.getName());
    private static final int FRAME_HEADER_LENGTH = 9;

    private final Map<Integer, StreamContext> streams = new HashMap<>(1000);
    private final ConnectionContext ctx;
    private final Http2Config http2Config;
    private final HttpRouting routing;
    private final Http2Headers.DynamicTable requestDynamicTable;
    private final Http2HuffmanDecoder requestHuffman;
    private final Http2FrameListener receiveFrameListener = // Http2FrameListener.create(List.of());
            Http2FrameListener.create(List.of(new Http2LoggingFrameListener("recv")));
    private final Http2ConnectionWriter connectionWriter;
    private final List<Http2SubProtocolSelector> subProviders;
    private final DataReader reader;
    private final Http2Settings serverSettings;
    private final boolean sendErrorDetails;
    private final ConnectionFlowControl flowControl;

    // initial client settings, until we receive real ones
    private Http2Settings clientSettings = Http2Settings.builder()
            .build();
    private Http2FrameHeader frameHeader;
    private BufferData frameInProgress;
    private Http2Ping ping;
    private boolean expectPreface;
    private HttpPrologue upgradePrologue;
    private Http2Headers upgradeHeaders;
    private State state = State.WRITE_SERVER_SETTINGS;
    private int continuationExpectedStreamId;
    private int lastStreamId;
    private long maxClientConcurrentStreams;
    private volatile ZonedDateTime lastRequestTimestamp;
    private volatile Thread myThread;
    private volatile boolean canRun = true;

    Http2Connection(ConnectionContext ctx, Http2Config http2Config, List<Http2SubProtocolSelector> subProviders) {
        this.ctx = ctx;
        this.http2Config = http2Config;
        this.serverSettings = Http2Settings.builder()
                .update(builder -> settingsUpdate(http2Config, builder))
                .add(Http2Setting.ENABLE_PUSH, false)
                .build();
        this.connectionWriter = new Http2ConnectionWriter(ctx,
                                                          ctx.dataWriter(),
                                                          List.of(new Http2LoggingFrameListener("send")));
        this.subProviders = subProviders;
        this.requestDynamicTable = Http2Headers.DynamicTable.create(
                serverSettings.value(Http2Setting.HEADER_TABLE_SIZE));
        this.requestHuffman = Http2HuffmanDecoder.create();
        this.routing = ctx.router().routing(HttpRouting.class, HttpRouting.empty());
        this.reader = ctx.dataReader();
        this.sendErrorDetails = http2Config.sendErrorDetails();
        this.maxClientConcurrentStreams = http2Config.maxConcurrentStreams();

        // Flow control is initialized by RFC 9113 default values
        this.flowControl = ConnectionFlowControl.serverBuilder(this::writeWindowUpdateFrame)
                .initialWindowSize(http2Config.initialWindowSize())
                .blockTimeout(http2Config.flowControlTimeout())
                .maxFrameSize(http2Config.maxFrameSize())
                .build();
        this.lastRequestTimestamp = Http.DateTime.timestamp();
    }

    private static void settingsUpdate(Http2Config config, Http2Settings.Builder builder) {
        applySetting(builder, config.maxFrameSize(), Http2Setting.MAX_FRAME_SIZE);
        applySetting(builder, config.maxHeaderListSize(), Http2Setting.MAX_HEADER_LIST_SIZE);
        applySetting(builder, config.maxConcurrentStreams(), Http2Setting.MAX_CONCURRENT_STREAMS);
        applySetting(builder, config.initialWindowSize(), Http2Setting.INITIAL_WINDOW_SIZE);
    }

    // Add value to the builder only when differs from default
    private static void applySetting(Http2Settings.Builder builder, long value, Http2Setting<Long> settings) {
        if (value != settings.defaultValue()) {
            builder.add(settings, value);
        }
    }

    @Override
    public void handle(Semaphore requestSemaphore) throws InterruptedException {
        try {
            doHandle(requestSemaphore);
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
                                                sendErrorDetails ? e.getMessage() : "");
            connectionWriter.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()));
            state = State.FINISHED;
        } catch (CloseConnectionException
                 | InterruptedException
                 | UncheckedIOException e) {
            throw e;
        } catch (Throwable e) {
            if (state == State.FINISHED) {
                // we have already finished this
                return;
            }
            Http2GoAway frame = new Http2GoAway(0,
                                                Http2ErrorCode.INTERNAL,
                                                sendErrorDetails ? e.getClass().getName() + ": " + e.getMessage() : "");
            connectionWriter.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()));
            state = State.FINISHED;
            throw e;
        }
    }

    /**
     * Client settings, obtained from SETTINGS frame or HTTP/2 upgrade request.
     *
     * @param http2Settings client settings to use
     */
    public void clientSettings(Http2Settings http2Settings) {
        this.clientSettings = http2Settings;
        this.receiveFrameListener.frame(ctx, 0, clientSettings);
        if (this.clientSettings.hasValue(Http2Setting.HEADER_TABLE_SIZE)) {
            updateHeaderTableSize(clientSettings.value(Http2Setting.HEADER_TABLE_SIZE));
        }

        if (this.clientSettings.hasValue(Http2Setting.INITIAL_WINDOW_SIZE)) {
            Long initialWindowSize = clientSettings.value(Http2Setting.INITIAL_WINDOW_SIZE);

            //6.9.2/3 - legal range for the increment to the flow-control window is 1 to 2^31-1 (2,147,483,647) octets.
            if (initialWindowSize > WindowSize.MAX_WIN_SIZE) {
                Http2GoAway frame = new Http2GoAway(0,
                                                    Http2ErrorCode.FLOW_CONTROL,
                                                    "Window " + initialWindowSize + " size too large");
                connectionWriter.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()));
            }

            //6.9.1/1 - changing the flow-control window for streams that are not yet active
            flowControl.resetInitialWindowSize(initialWindowSize.intValue());

            //6.9.2/1 - SETTINGS frame can alter the initial flow-control
            //   window size for streams with active flow-control windows (that is,
            //   streams in the "open" or "half-closed (remote)" state)
            for (StreamContext sctx : streams.values()) {
                Http2StreamState streamState = sctx.stream.streamState();
                if (streamState == Http2StreamState.OPEN || streamState == Http2StreamState.HALF_CLOSED_REMOTE) {
                    sctx.stream.flowControl().outbound().resetStreamWindowSize(initialWindowSize.intValue());
                }
            }

            // Unblock frames waiting for update
            this.flowControl.outbound().triggerUpdate();
        }

        if (this.clientSettings.hasValue(Http2Setting.MAX_FRAME_SIZE)) {
            Long maxFrameSize = this.clientSettings.value(Http2Setting.MAX_FRAME_SIZE);
            // specification defines, that the frame size must be between the initial size (16384) and 2^24-1
            if (maxFrameSize < WindowSize.DEFAULT_MAX_FRAME_SIZE || maxFrameSize > WindowSize.MAX_MAX_FRAME_SIZE) {
                throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                         "Frame size must be between 2^14 and 2^24-1, but is: " + maxFrameSize);
            }

            flowControl.resetMaxFrameSize(maxFrameSize.intValue());
        }

        // Set server MAX_CONCURRENT_STREAMS limit when client sends number lower than hard limit
        // from configuration. Refuse settings if client sends larger number than is configured.
        this.clientSettings.presentValue(Http2Setting.MAX_CONCURRENT_STREAMS)
                .ifPresent(it -> {
                    if (http2Config.maxConcurrentStreams() >= it) {
                        maxClientConcurrentStreams = it;
                    } else {
                        Http2GoAway frame =
                                new Http2GoAway(0,
                                                Http2ErrorCode.PROTOCOL,
                                                "Value of maximum concurrent streams limit " + it
                                                        + " exceeded hard limit value "
                                                        + http2Config.maxConcurrentStreams());
                        connectionWriter.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()));
                    }
                });
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
    public boolean canInterrupt() {
        return streams.isEmpty();
    }

    @Override
    public String toString() {
        return "[" + ctx.socketId() + " " + ctx.childSocketId() + "]";
    }

    @Override
    public Duration idleTime() {
        return Duration.between(lastRequestTimestamp, Http.DateTime.timestamp());
    }

    @Override
    public void close(boolean interrupt) {
        // either way, finish
        this.canRun = false;

        if (interrupt) {
            // interrupt regardless of current state
            if (myThread != null) {
                myThread.interrupt();
            }
        } else if (canInterrupt()) {
            // only interrupt when not processing a request (there is a chance of a race condition, this edge case
            // is ignored
            myThread.interrupt();
        }
    }

    // jUnit Http2Config pkg only visible test accessor.
    Http2Config config() {
        return http2Config;
    }

    // jUnit Http2Settings pkg only visible test accessor.
    Http2Settings serverSettings() {
        return serverSettings;
    }

    private void doHandle(Semaphore requestSemaphore) throws InterruptedException {
        myThread = Thread.currentThread();
        while (canRun && state != State.FINISHED) {
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
                dispatchHandler(requestSemaphore);
            } else {
                dispatchHandler(requestSemaphore);
            }
        }
        if (state != State.FINISHED) {
            Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.NO_ERROR, "Idle timeout");
            connectionWriter.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()));
        }
    }

    private void dispatchHandler(Semaphore requestSemaphore) {
        switch (state) {
        case CONTINUATION -> doContinuation();
        case WRITE_SERVER_SETTINGS -> writeServerSettings();
        case WINDOW_UPDATE -> readWindowUpdateFrame();
        case SETTINGS -> doSettings();
        case ACK_SETTINGS -> ackSettings();
        case DATA -> dataFrame();
        case HEADERS -> doHeaders(requestSemaphore);
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


        int streamId;
        try {
            frameHeader = Http2FrameHeader.create(frameHeaderBuffer);
            streamId = frameHeader.streamId();
            receiveFrameListener.frameHeader(ctx, streamId, frameHeaderBuffer);
        } catch (Throwable e) {
            // cannot determine correct stream id
            receiveFrameListener.frameHeader(ctx, 0, frameHeaderBuffer);
            throw e;
        }
        receiveFrameListener.frameHeader(ctx, frameHeader.streamId(), this.frameHeader);

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

        receiveFrameListener.frame(ctx, streamId, frameInProgress);
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

        stream(frameHeader.streamId())
                .addContinuation(new Http2FrameData(frameHeader, inProgressFrame()));

        if (flags.endOfHeaders()) {
            state = State.HEADERS;
        } else {
            state = State.READ_FRAME;
        }
    }

    private void writeServerSettings() {
        connectionWriter.write(serverSettings.toFrameData(serverSettings, 0, Http2Flag.SettingsFlags.create(0)));

        // Initial window size for connection is not configurable, subsequent update win update is needed
        int connectionWinSizeUpd = http2Config.initialWindowSize() - WindowSize.DEFAULT_WIN_SIZE;
        if (connectionWinSizeUpd > 0) {
            Http2WindowUpdate windowUpdate = new Http2WindowUpdate(http2Config.initialWindowSize() - WindowSize.DEFAULT_WIN_SIZE);
            connectionWriter.write(windowUpdate.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()));
        }

        state = State.READ_FRAME;
    }

    private void readWindowUpdateFrame() {
        Http2WindowUpdate windowUpdate = Http2WindowUpdate.create(inProgressFrame());
        int streamId = frameHeader.streamId();

        receiveFrameListener.frame(ctx, streamId, windowUpdate);

        state = State.READ_FRAME;

        boolean overflow;
        int increment = windowUpdate.windowSizeIncrement();


        if (streamId == 0) {
            // overall connection
            if (increment == 0) {
                Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.PROTOCOL, "Window size 0");
                connectionWriter.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()));
            }
            overflow = flowControl.incrementOutboundConnectionWindowSize(increment) > WindowSize.MAX_WIN_SIZE;
            if (overflow) {
                Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.FLOW_CONTROL, "Window size too big. Max: ");
                connectionWriter.write(frame.toFrameData(clientSettings, 0, Http2Flag.NoFlags.create()));
            }
        } else {
            try {
                StreamContext stream = stream(streamId);
                stream.stream().windowUpdate(windowUpdate);
            } catch (Http2Exception ignored) {
                // stream closed
            }
        }
    }

    // Used in inbound flow control instance to write WINDOW_UPDATE frame.
    private void writeWindowUpdateFrame(int streamId, Http2WindowUpdate windowUpdateFrame) {
        connectionWriter.write(windowUpdateFrame.toFrameData(clientSettings, streamId, Http2Flag.NoFlags.create()));
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
            clientSettings(Http2Settings.create(inProgressFrame()));

            // TODO for each
            //        Http2Setting.MAX_HEADER_LIST_SIZE;
            state = State.ACK_SETTINGS;
        }
    }

    private void ackSettings() {
        Http2Flag.SettingsFlags flags = Http2Flag.SettingsFlags.create(Http2Flag.ACK);
        Http2FrameHeader header = Http2FrameHeader.create(0, Http2FrameTypes.SETTINGS, flags, 0);
        connectionWriter.write(new Http2FrameData(header, BufferData.empty()));
        state = State.READ_FRAME;

        if (upgradeHeaders != null) {
            // initial request from outside
            io.helidon.common.http.Headers httpHeaders = upgradeHeaders.httpHeaders();
            boolean hasEntity = httpHeaders.contains(HeaderNames.CONTENT_LENGTH)
                    || httpHeaders.contains(Http.Headers.TRANSFER_ENCODING_CHUNKED);
            // we now have all information needed to execute
            Http2Stream stream = stream(1).stream();
            stream.prologue(upgradePrologue);
            stream.headers(upgradeHeaders, !hasEntity);
            upgradeHeaders = null;
            ctx.executor()
                    .submit(new StreamRunnable(streams, stream, Thread.currentThread()));
        }
    }

    private void dataFrame() {
        BufferData buffer;

        int streamId = frameHeader.streamId();
        StreamContext stream = stream(streamId);
        stream.stream().checkDataReceivable();

        // Flow-control: reading frameHeader.length() bytes from HTTP2 socket for known stream ID.
        int length = frameHeader.length();
        if (length > 0) {
            if (streamId > 0 && frameHeader.type() != Http2FrameType.HEADERS) {
                // Stream ID > 0: update connection and stream
                stream.stream()
                        .flowControl()
                        .inbound()
                        .decrementWindowSize(length);
            }
        }

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

    private void doHeaders(Semaphore requestSemaphore) {
        int streamId = frameHeader.streamId();
        StreamContext streamContext = stream(streamId);

        streamContext.stream().checkHeadersReceivable();

        // first frame, expecting continuation
        if (frameHeader.type() == Http2FrameType.HEADERS && !frameHeader.flags(Http2FrameTypes.HEADERS).endOfHeaders()) {
            // this needs to retain the data until we receive last continuation, cannot use the same data
            streamContext.addHeadersToBeContinued(frameHeader, inProgressFrame().copy());
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
            headers = Http2Headers.create(stream,
                                          requestDynamicTable,
                                          requestHuffman,
                                          streamContext.contData());
            endOfStream = streamContext.contHeader().flags(Http2FrameTypes.HEADERS).endOfStream();
            streamContext.clearContinuations();
            continuationExpectedStreamId = 0;
        } else {
            endOfStream = frameHeader.flags(Http2FrameTypes.HEADERS).endOfStream();
            headers = Http2Headers.create(stream,
                                          requestDynamicTable,
                                          requestHuffman,
                                          new Http2FrameData(frameHeader, inProgressFrame()));
        }

        receiveFrameListener.headers(ctx, streamId, headers);
        headers.validateRequest();
        String path = headers.path();
        Http.Method method = headers.method();
        HttpPrologue httpPrologue = HttpPrologue.create(FULL_PROTOCOL,
                                                        PROTOCOL,
                                                        PROTOCOL_VERSION,
                                                        method,
                                                        path,
                                                        http2Config.validatePath());
        stream.prologue(httpPrologue);
        stream.requestSemaphore(requestSemaphore);
        stream.headers(headers, endOfStream);
        state = State.READ_FRAME;

        this.lastRequestTimestamp = Http.DateTime.timestamp();
        // we now have all information needed to execute
        ctx.executor()
                .submit(new StreamRunnable(streams, stream, Thread.currentThread()));
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
            receiveFrameListener.frame(ctx, 0, ping);
            state = State.SEND_PING_ACK;
        }
    }

    private void doPriority() {
        Http2Priority http2Priority = Http2Priority.create(inProgressFrame());
        receiveFrameListener.frame(ctx, http2Priority.streamId(), http2Priority);

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
        connectionWriter.write(new Http2FrameData(header, frame));
        state = State.READ_FRAME;
    }

    private void goAwayFrame() {
        Http2GoAway go = Http2GoAway.create(inProgressFrame());
        receiveFrameListener.frame(ctx, 0, go);
        state = State.FINISHED;
        if (go.errorCode() != Http2ErrorCode.NO_ERROR) {
            ctx.log(LOGGER, DEBUG, "Received go away. Error code: %s, message: %s",
                    go.errorCode(),
                    go.details());
        }
    }

    private void rstStream() {
        Http2RstStream rstStream = Http2RstStream.create(inProgressFrame());
        receiveFrameListener.frame(ctx, frameHeader.streamId(), rstStream);

        try {
            StreamContext streamContext = stream(frameHeader.streamId());
            streamContext.stream().rstStream(rstStream);

            state = State.READ_FRAME;
        } catch (Http2Exception e) {
            if (e.getMessage().startsWith("Stream closed")) {
                // expected state, probably closed by remote peer
                state = State.READ_FRAME;
                return;
            }
            throw e;
        }
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

            // 5.1.2 MAX_CONCURRENT_STREAMS limit check - stream error of type PROTOCOL_ERROR or REFUSED_STREAM
            if (streams.size() > maxClientConcurrentStreams) {
                throw new Http2Exception(Http2ErrorCode.REFUSED_STREAM,
                                         "Maximum concurrent streams limit " + maxClientConcurrentStreams + " exceeded");
            }

            streamContext = new StreamContext(streamId,
                                              http2Config.maxHeaderListSize(),
                                              new Http2Stream(ctx,
                                                              routing,
                                                              http2Config,
                                                              subProviders,
                                                              streamId,
                                                              serverSettings,
                                                              clientSettings,
                                                              connectionWriter,
                                                              flowControl));
            streams.put(streamId, streamContext);
        }
        // any request for a specific stream is now considered a valid update of connection (ignoring management messages
        // on stream 0)
        this.lastRequestTimestamp = Http.DateTime.timestamp();
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

    private record StreamRunnable(Map<Integer, StreamContext> streams,
                                  Http2Stream stream,
                                  Thread handlerThread) implements Runnable {

        @Override
        public void run() {
            try {
                stream.run();
            } catch (UncheckedIOException e) {
                // Broken connection
                if (e.getCause() instanceof SocketException) {
                    // Interrupt handler thread
                    handlerThread.interrupt();
                    LOGGER.log(DEBUG, "Socket error on writer thread", e);
                } else {
                    throw e;
                }
            } finally {
                streams.remove(stream.streamId());
            }
        }
    }

    private static class StreamContext {
        private final List<Http2FrameData> continuationData = new ArrayList<>();
        private final long maxHeaderListSize;
        private final int streamId;
        private final Http2Stream stream;
        private long headerListSize = 0;

        private Http2FrameHeader continuationHeader;

        StreamContext(int streamId, long maxHeaderListSize, Http2Stream stream) {
            this.streamId = streamId;
            this.maxHeaderListSize = maxHeaderListSize;
            this.stream = stream;
        }

        public Http2Stream stream() {
            return stream;
        }

        Http2FrameData[] contData() {
            return continuationData.toArray(new Http2FrameData[0]);
        }

        Http2FrameHeader contHeader() {
            return continuationHeader;
        }

        void addContinuation(Http2FrameData frameData) {
            if (continuationData.isEmpty()) {
                throw new Http2Exception(Http2ErrorCode.PROTOCOL, "Received continuation without headers.");
            }
            this.continuationData.add(frameData);
            addAndValidateHeaderListSize(frameData.header().length());
        }

        void addHeadersToBeContinued(Http2FrameHeader frameHeader,  BufferData bufferData) {
            clearContinuations();
            continuationHeader = frameHeader;
            this.continuationData.add(new Http2FrameData(frameHeader, bufferData));
            addAndValidateHeaderListSize(frameHeader.length());
        }

        private void addAndValidateHeaderListSize(int headerSizeIncrement){
            // Check MAX_HEADER_LIST_SIZE
            headerListSize += headerSizeIncrement;
            if (headerListSize > maxHeaderListSize){
                throw new Http2Exception(Http2ErrorCode.REQUEST_HEADER_FIELDS_TOO_LARGE,
                        "Request Header Fields Too Large");
            }
        }

        private void clearContinuations() {
            continuationData.clear();
            headerListSize = 0;
        }
    }
}
