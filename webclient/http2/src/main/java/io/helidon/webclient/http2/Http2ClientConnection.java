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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.http2.ConnectionFlowControl;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ConnectionWriter;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameListener;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2GoAway;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2LoggingFrameListener;
import io.helidon.http.http2.Http2Ping;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2Util;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.WindowSize;
import io.helidon.webclient.api.ClientConnection;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

/**
 * Represents an HTTP2 connection on the client.
 */
public class Http2ClientConnection {
    private static final System.Logger LOGGER = System.getLogger(Http2ClientConnection.class.getName());
    private static final int FRAME_HEADER_LENGTH = 9;
    private final Http2FrameListener sendListener = new Http2LoggingFrameListener("cl-send");
    private final Http2FrameListener recvListener = new Http2LoggingFrameListener("cl-recv");
    private final LockingStreamIdSequence streamIdSeq = new LockingStreamIdSequence();
    private final ReadWriteLock streamsLock = new ReentrantReadWriteLock();
    // streams may be accessed from connection thread, or stream thread, must be guarded by the above lock
    private final Map<Integer, Http2ClientStream> streams = new HashMap<>();
    private final ConnectionFlowControl connectionFlowControl;
    private final Http2Headers.DynamicTable inboundDynamicTable =
            Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
    private final Http2ClientProtocolConfig protocolConfig;
    private final ClientConnection connection;
    private final SocketContext ctx;
    private final Http2ConnectionWriter writer;
    private final DataReader reader;
    private final DataWriter dataWriter;
    private final Semaphore pingPongSemaphore = new Semaphore(0);
    private final Http2ClientConfig clientConfig;
    private volatile int lastStreamId;

    private Http2Settings serverSettings = Http2Settings.builder()
            .build();
    private Future<?> handleTask;
    private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);

    Http2ClientConnection(Http2ClientImpl http2Client, ClientConnection connection) {
        this.protocolConfig = http2Client.protocolConfig();
        this.clientConfig = http2Client.clientConfig();
        this.connectionFlowControl = ConnectionFlowControl.clientBuilder(this::writeWindowsUpdate)
                .maxFrameSize(protocolConfig.maxFrameSize())
                .initialWindowSize(protocolConfig.initialWindowSize())
                .blockTimeout(protocolConfig.flowControlBlockTimeout())
                .build();
        this.connection = connection;
        this.ctx = connection.helidonSocket();
        this.dataWriter = connection.writer();
        this.reader = connection.reader();
        this.writer = new Http2ConnectionWriter(connection.helidonSocket(), connection.writer(), List.of());
    }

    /**
     * Creates an HTTP2 client connection.
     *
     * @param http2Client the HTTP2 client
     * @param connection the client connection
     * @param sendSettings whether to send the settings or not
     * @return an HTTP2 client connection
     */
    public static Http2ClientConnection create(Http2ClientImpl http2Client,
                                        ClientConnection connection,
                                        boolean sendSettings) {

        Http2ClientConnection h2conn = new Http2ClientConnection(http2Client, connection);
        h2conn.start(http2Client.protocolConfig(), http2Client.webClient().executor(), sendSettings);

        return h2conn;
    }

    Http2ConnectionWriter writer() {
        return writer;
    }

    Http2Headers.DynamicTable getInboundDynamicTable() {
        return this.inboundDynamicTable;
    }

    ConnectionFlowControl flowControl() {
        return this.connectionFlowControl;
    }

    Http2ClientStream stream(int streamId) {
        Lock lock = streamsLock.readLock();
        lock.lock();
        try {
            return streams.get(streamId);
        } finally {
            lock.unlock();
        }

    }

    /**
     * Stream ID sequence.
     *
     * @return the ID sequence
     */
    public LockingStreamIdSequence streamIdSequence() {
        return streamIdSeq;
    }

    Http2ClientStream createStream(Http2StreamConfig config) {
        //FIXME: priority
        Http2ClientStream stream = new Http2ClientStream(this,
                serverSettings,
                ctx,
                config,
                clientConfig,
                streamIdSeq);
        return stream;
    }

    /**
     * Adds a stream to the connection.
     *
     * @param streamId the stream ID
     * @param stream the stream
     */
    public void addStream(int streamId, Http2ClientStream stream) {
        Lock lock = streamsLock.writeLock();
        lock.lock();
        try {
            this.streams.put(streamId, stream);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a stream from the connection.
     *
     * @param streamId the stream ID
     */
    public void removeStream(int streamId) {
        Lock lock = streamsLock.writeLock();
        lock.lock();
        try {
            this.streams.remove(streamId);
        } finally {
            lock.unlock();
        }

    }

    Http2ClientStream tryStream(Http2StreamConfig config) {
        try {
            return createStream(config);
        } catch (IllegalStateException | UncheckedIOException e) {
            return null;
        }
    }

    boolean closed() {
        return state.get().closed() || (protocolConfig.ping() && !ping());
    }

    boolean ping() {
        Http2Ping ping = Http2Ping.create();
        Http2FrameData frameData = ping.toFrameData();
        sendListener.frameHeader(ctx, 0, frameData.header());
        sendListener.frame(ctx, 0, ping);
        try {
            this.writer().writeData(frameData, FlowControl.Outbound.NOOP);
            return pingPongSemaphore.tryAcquire(protocolConfig.pingTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (UncheckedIOException | InterruptedException e) {
            ctx.log(LOGGER, DEBUG, "Ping failed!", e);
            return false;
        }
    }

    void pong() {
        pingPongSemaphore.release();
    }

    void updateLastStreamId(int lastStreamId) {
        this.lastStreamId = lastStreamId;
    }

    /**
     * Closes this connection.
     */
    public void close() {
        this.goAway(0, Http2ErrorCode.NO_ERROR, "Closing connection");
        if (state.getAndSet(State.CLOSED) != State.CLOSED) {
            try {
                handleTask.cancel(true);
                ctx.log(LOGGER, TRACE, "Closing connection");
                connection.closeResource();
            } catch (Throwable e) {
                ctx.log(LOGGER, TRACE, "Failed to close HTTP/2 connection.", e);
            }
        }
    }

    static Http2Settings settings(Http2ClientProtocolConfig config) {
        Http2Settings.Builder b = Http2Settings.builder();
        if (config.maxHeaderListSize() > 0) {
            b.add(Http2Setting.MAX_HEADER_LIST_SIZE, config.maxHeaderListSize());
        }
        return b.add(Http2Setting.INITIAL_WINDOW_SIZE, (long) config.initialWindowSize())
                .add(Http2Setting.MAX_FRAME_SIZE, (long) config.maxFrameSize())
                .add(Http2Setting.ENABLE_PUSH, false)
                .build();
    }

    private void sendPreface(Http2ClientProtocolConfig config, boolean sendSettings) {
        BufferData prefaceData = Http2Util.prefaceData();
        sendListener.frame(ctx, 0, prefaceData);
        dataWriter.writeNow(prefaceData);
        if (sendSettings) {
            // §3.5 Preface bytes must be followed by setting frame
            Http2Settings http2Settings = settings(config);
            Http2Flag.SettingsFlags flags = Http2Flag.SettingsFlags.create(0);
            Http2FrameData frameData = http2Settings.toFrameData(null, 0, flags);
            sendListener.frameHeader(ctx, 0, frameData.header());
            sendListener.frame(ctx, 0, http2Settings);
            writer.write(frameData);
        }
        // Initial window size for connection is not configurable, subsequent update win update is needed
        int connectionWinSizeUpd = config.initialWindowSize() - WindowSize.DEFAULT_WIN_SIZE;
        if (connectionWinSizeUpd > 0) {
            Http2WindowUpdate windowUpdate = new Http2WindowUpdate(connectionWinSizeUpd);
            Http2FrameData frameData = windowUpdate.toFrameData(null, 0, Http2Flag.NoFlags.create());
            sendListener.frameHeader(ctx, 0, frameData.header());
            sendListener.frame(ctx, 0, windowUpdate);
            writer.write(frameData);
        }
    }

    private void start(Http2ClientProtocolConfig protocolConfig,
                       ExecutorService executor,
                       boolean sendSettings) {
        CountDownLatch cdl = new CountDownLatch(1);

        handleTask = executor.submit(() -> {
            ctx.log(LOGGER, TRACE, "Starting HTTP/2 connection, thread: %s", Thread.currentThread().getName());
            try {
                sendPreface(protocolConfig, sendSettings);
            } catch (Throwable e) {
                ctx.log(LOGGER, WARNING, "Failed to send preface.", e);
            } finally {
                // we must wait until the preface is sent, before continuing with client operations
                cdl.countDown();
            }

            // now switch to HTTP/2
            try {
                while (!Thread.interrupted()) {
                    if (!handle()) {
                        this.close();
                        ctx.log(LOGGER, TRACE, "Connection closed");
                        return;
                    }
                }
                ctx.log(LOGGER, TRACE, "Client listener interrupted");
            } catch (Throwable t) {
                this.close();
                ctx.log(LOGGER, DEBUG, "Failed to handle HTTP/2 client connection", t);
            }
        });

        try {
            if (!cdl.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Filed to send HTTP/2 preface within 20 seconds, this connection is broken");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted while waiting for preface to be sent", e);
        }
    }

    private void writeWindowsUpdate(int streamId, Http2WindowUpdate windowUpdateFrame) {
        if (streamId == 0) {
            writer.write(windowUpdateFrame.toFrameData(serverSettings, streamId, Http2Flag.NoFlags.create()));
            return;
        }
        if (streamId < lastStreamId) {
            Lock lock = streamsLock.readLock();
            lock.lock();
            try {
                for (var s : streams.values()) {
                    if (s.streamId() > streamId && s.streamState() != Http2StreamState.IDLE) {
                        // RC against parallel newer streams, data already buffered at client being read
                        // There is no need to do request for more data as stream is no more usable
                        return;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
        Http2ClientStream stream = stream(streamId);
        if (stream != null && !stream.streamState().equals(Http2StreamState.CLOSED)) {
            writer.write(windowUpdateFrame.toFrameData(serverSettings, streamId, Http2Flag.NoFlags.create()));
        }
    }

    private boolean handle() {
        this.reader.ensureAvailable();
        BufferData frameHeaderBuffer = this.reader.readBuffer(FRAME_HEADER_LENGTH);
        Http2FrameHeader frameHeader = Http2FrameHeader.create(frameHeaderBuffer);
        frameHeader.type().checkLength(frameHeader.length());
        BufferData data;
        if (frameHeader.length() != 0) {
            data = this.reader.readBuffer(frameHeader.length());
        } else {
            data = BufferData.empty();
        }

        int streamId = frameHeader.streamId();

        switch (frameHeader.type()) {
        case GO_AWAY:
            Http2GoAway http2GoAway = Http2GoAway.create(data);
            recvListener.frameHeader(ctx, streamId, frameHeader);
            recvListener.frame(ctx, streamId, http2GoAway);
            this.close();
            ctx.log(LOGGER, TRACE, "Connection closed by remote peer, error code: %s, last stream: %d",
                    http2GoAway.errorCode(),
                    http2GoAway.lastStreamId());
            return false;
        case SETTINGS:
            Http2Flag.SettingsFlags flags = frameHeader.flags(Http2FrameTypes.SETTINGS);

            // if ack flag set, empty frame and no processing
            if (flags.ack()) {
                if (frameHeader.length() > 0) {
                    throw new Http2Exception(Http2ErrorCode.FRAME_SIZE,
                                             "Settings with ACK should not have payload.");
                }
                return true;
            }

            serverSettings = Http2Settings.create(data);
            recvListener.frameHeader(ctx, streamId, frameHeader);
            recvListener.frame(ctx, streamId, serverSettings);
            // §4.3.1 Endpoint communicates the size chosen by its HPACK decoder context
            inboundDynamicTable.protocolMaxTableSize(serverSettings.value(Http2Setting.HEADER_TABLE_SIZE));
            if (serverSettings.hasValue(Http2Setting.MAX_FRAME_SIZE)) {
                connectionFlowControl.resetMaxFrameSize(serverSettings.value(Http2Setting.MAX_FRAME_SIZE).intValue());
            }
            // §6.5.2 Update initial window size for new streams and window sizes of all already existing streams
            if (serverSettings.hasValue(Http2Setting.INITIAL_WINDOW_SIZE)) {
                Long initWinSizeLong = serverSettings.value(Http2Setting.INITIAL_WINDOW_SIZE);
                if (initWinSizeLong > WindowSize.MAX_WIN_SIZE) {
                    goAway(streamId, Http2ErrorCode.FLOW_CONTROL, "Window size too big. Max: ");
                    throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                             "Received too big INITIAL_WINDOW_SIZE " + initWinSizeLong);
                }
                int initWinSize = initWinSizeLong.intValue();
                connectionFlowControl.resetInitialWindowSize(initWinSize);
                Lock lock = streamsLock.readLock();
                lock.lock();
                try {
                    streams.values().forEach(stream -> stream.flowControl().outbound().resetStreamWindowSize(initWinSize));
                } finally {
                    lock.unlock();
                }

            }
            // §6.5.3 Settings Synchronization
            ackSettings();
            //FIXME: Max number of concurrent streams
            return true;

        case WINDOW_UPDATE:
            Http2WindowUpdate windowUpdate = Http2WindowUpdate.create(data);
            recvListener.frameHeader(ctx, streamId, frameHeader);
            recvListener.frame(ctx, streamId, windowUpdate);
            // Outbound flow-control window update
            if (streamId == 0) {
                int increment = windowUpdate.windowSizeIncrement();
                boolean overflow;
                // overall connection
                if (increment == 0) {
                    Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.PROTOCOL, "Window size 0");
                    writer.write(frame.toFrameData(serverSettings, 0, Http2Flag.NoFlags.create()));
                }
                overflow = connectionFlowControl.incrementOutboundConnectionWindowSize(increment) > WindowSize.MAX_WIN_SIZE;
                if (overflow) {
                    Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.FLOW_CONTROL, "Window size too big. Max: ");
                    writer.write(frame.toFrameData(serverSettings, 0, Http2Flag.NoFlags.create()));
                }

            } else {
                stream(streamId)
                        .windowUpdate(windowUpdate);
            }
            return true;
        case PING:
            if (streamId != 0) {
                throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                         "Received ping for a stream " + streamId);
            }
            if (frameHeader.length() != 8) {
                throw new Http2Exception(Http2ErrorCode.FRAME_SIZE,
                                         "Received ping with wrong size. Should be 8 bytes, is " + frameHeader.length());
            }
            if (!frameHeader.flags(Http2FrameTypes.PING).ack()) {
                Http2Ping ping = Http2Ping.create(data);
                recvListener.frame(ctx, streamId, ping);
                BufferData frame = ping.data();
                Http2FrameHeader header = Http2FrameHeader.create(frame.available(),
                                                                  Http2FrameTypes.PING,
                                                                  Http2Flag.PingFlags.create(Http2Flag.ACK),
                                                                  0);
                writer.write(new Http2FrameData(header, frame));
            } else {
                pong();
            }
            break;

        case RST_STREAM:
            Http2RstStream rstStream = Http2RstStream.create(data);
            recvListener.frame(ctx, streamId, rstStream);
            stream(streamId).rstStream(rstStream);
            break;

        case DATA:
            Http2ClientStream stream = stream(streamId);
            if (stream == null) {
                // most likely a closed stream
                ctx.log(LOGGER, DEBUG, "%d: received data for stream %d, which does not exist", 0, streamId);
            } else {
                stream.flowControl().inbound().decrementWindowSize(frameHeader.length());
                ctx.log(LOGGER, DEBUG, "%d: received data for stream %d", 0, streamId);
                stream.push(new Http2FrameData(frameHeader, data));
            }
            break;
        case HEADERS, CONTINUATION:
            stream(streamId).push(new Http2FrameData(frameHeader, data));
            return true;

        default:
            LOGGER.log(WARNING, "Unsupported frame type!! " + frameHeader.type());
        }

        return true;
    }

    private void ackSettings() {
        Http2Flag.SettingsFlags flags = Http2Flag.SettingsFlags.create(Http2Flag.ACK);
        Http2Settings http2Settings = Http2Settings.create();
        Http2FrameData frameData = http2Settings.toFrameData(null, 0, flags);
        sendListener.frameHeader(ctx, 0, frameData.header());
        sendListener.frame(ctx, 0, http2Settings);
        writer.write(frameData);
    }

    private void goAway(int streamId, Http2ErrorCode errorCode, String msg) {
        if (State.OPEN == state.getAndSet(State.GO_AWAY)) {
            Http2Settings http2Settings = Http2Settings.create();
            Http2GoAway frame = new Http2GoAway(streamId, errorCode, msg);
            writer.write(frame.toFrameData(http2Settings, 0, Http2Flag.NoFlags.create()));
        }
    }

    private enum State {
        CLOSED(true),
        GO_AWAY(true),
        OPEN(false);

        private final boolean closed;

        State(boolean closed){
            this.closed = closed;
        }

        boolean closed() {
            return closed;
        }
    }
}
