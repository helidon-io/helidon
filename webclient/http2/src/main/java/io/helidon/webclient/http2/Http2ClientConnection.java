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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
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
import io.helidon.http.http2.Http2HuffmanDecoder;
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
    private static final long NO_PING_ACK = Long.MIN_VALUE;
    private final Http2FrameListener sendListener = new Http2LoggingFrameListener("cl-send");
    private final Http2FrameListener recvListener = new Http2LoggingFrameListener("cl-recv");
    private final LockingStreamIdSequence streamIdSeq = new LockingStreamIdSequence();
    private final ReadWriteLock streamsLock = new ReentrantReadWriteLock();
    // streams may be accessed from connection thread, or stream thread, must be guarded by the above lock
    private final Map<Integer, Http2ClientStream> streams = new HashMap<>();
    private final ConnectionFlowControl connectionFlowControl;
    private final Http2Headers.DynamicTable inboundDynamicTable =
            Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
    private final AtomicLong inboundHeaderBlockSequence = new AtomicLong();
    private final ReentrantLock inboundHeaderDecodeLock = new ReentrantLock();
    private final Condition inboundHeaderDecodeTurn = inboundHeaderDecodeLock.newCondition();
    private final Http2ClientProtocolConfig protocolConfig;
    private final ClientConnection connection;
    private final SocketContext ctx;
    private final Http2ConnectionWriter writer;
    private final DataReader reader;
    private final DataWriter dataWriter;
    private final Semaphore pingPongSemaphore = new Semaphore(0);
    private final AtomicLong pingIdSequence = new AtomicLong();
    private final Http2ClientConfig clientConfig;
    private final ReentrantLock reservedStreamsLock = new ReentrantLock();
    private final CountDownLatch initialSettingsLatch = new CountDownLatch(1);
    private volatile int lastStreamId;
    private volatile long expectedPingAck = NO_PING_ACK;
    private volatile long peerMaxConcurrentStreams = Http2Setting.MAX_CONCURRENT_STREAMS.defaultValue();
    private volatile boolean initialSettingsReceived;
    private int reservedStreams;

    private Http2Settings serverSettings = Http2Settings.builder()
            .build();
    private Future<?> handleTask;
    private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);
    // Guarded by inboundHeaderDecodeLock.
    private long nextInboundHeaderBlockToDecode = 1;

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
        h2conn.awaitInitialSettings();

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

    /**
     * Creates a new client stream after the peer's initial {@code SETTINGS} are known.
     * Waiting here ensures we honor peer-provided limits such as
     * {@code SETTINGS_MAX_CONCURRENT_STREAMS} before reserving capacity.
     *
     * @param config stream configuration
     * @return a new client stream with one reserved peer-concurrency slot
     */
    Http2ClientStream createStream(Http2StreamConfig config) {
        // The peer can tighten MAX_CONCURRENT_STREAMS in its first SETTINGS frame.
        awaitInitialSettings();
        if (!reserveStream()) {
            throw new IllegalStateException("Peer max concurrent streams reached: " + peerMaxConcurrentStreams);
        }
        Http2ClientStream stream = new Http2ClientStream(this,
                serverSettings,
                ctx,
                config,
                clientConfig,
                streamIdSeq);
        return stream;
    }

    /**
     * Releases one previously reserved peer-concurrency slot.
     * This is invoked from stream close paths so capacity is returned both after
     * successful exchanges and after failures during stream startup.
     */
    void releaseReservedStream() {
        reservedStreamsLock.lock();
        try {
            if (reservedStreams > 0) {
                reservedStreams--;
            }
        } finally {
            reservedStreamsLock.unlock();
        }
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

    /**
     * Sends a connection health-check PING and waits for the matching ACK.
     * This method tracks a single in-flight health-check ping per connection.
     */
    boolean ping() {
        long pingId = pingIdSequence.incrementAndGet();
        Http2Ping ping = Http2Ping.create(pingData(pingId));
        Http2FrameData frameData = ping.toFrameData();
        sendListener.frameHeader(ctx, 0, frameData.header());
        sendListener.frame(ctx, 0, ping);
        pingPongSemaphore.drainPermits();
        expectedPingAck = pingId;
        try {
            this.writer().writeData(frameData, FlowControl.Outbound.NOOP);
            boolean pongReceived = pingPongSemaphore.tryAcquire(protocolConfig.pingTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!pongReceived) {
                pingPongSemaphore.drainPermits();
            }
            return pongReceived;
        } catch (UncheckedIOException | InterruptedException e) {
            ctx.log(LOGGER, DEBUG, "Ping failed!", e);
            return false;
        } finally {
            if (expectedPingAck == pingId) {
                expectedPingAck = NO_PING_ACK;
            }
        }
    }

    /**
     * Completes a connection health-check ping when the ACK echoes the same opaque 8-byte PING payload we sent.
     * RFC 9113, Section 6.7: "Responses to PING frames MUST contain the identical payload..."
     *
     * @param pingId decoded value of the echoed PING payload
     */
    void pong(long pingId) {
        if (expectedPingAck == pingId) {
            pingPongSemaphore.release();
        }
    }

    void updateLastStreamId(int lastStreamId) {
        this.lastStreamId = lastStreamId;
    }

    /**
     * Closes this connection.
     */
    public void close() {
        initialSettingsLatch.countDown();
        this.goAway(0, Http2ErrorCode.NO_ERROR, "Closing connection");
        if (state.getAndSet(State.CLOSED) != State.CLOSED) {
            signalInboundHeaderDecodeWaiters();
            try {
                handleTask.cancel(true);
                ctx.log(LOGGER, TRACE, "Closing connection");
                connection.closeResource();
            } catch (Throwable e) {
                ctx.log(LOGGER, TRACE, "Failed to close HTTP/2 connection.", e);
            }
        } else {
            signalInboundHeaderDecodeWaiters();
        }
    }

    /**
     * Closes the connection after a stream is abandoned with queued or in-progress
     * inbound header decoding.
     * This is intentional: later streams decode HPACK state in connection wire order,
     * so abandoning an earlier block would otherwise deadlock the remaining decoders.
     *
     * @param streamId stream that was closed while still owning undecoded headers
     */
    void closeUndecodedHeaders(int streamId) {
        ctx.log(LOGGER, DEBUG, "%d: closing connection because stream %d was abandoned with undecoded headers",
                0, streamId);
        close();
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

    /**
     * Reads the HTTP/2 headers for the specified client stream from this connection.
     * Thread-safe: Uses a connection-level decode lock to preserve inbound HPACK state.
     *
     * @param stream the HTTP/2 client stream for which headers are being read
     * @param decoder the Huffman decoder to decode the headers
     * @param headers the existing headers object to populate or use as a basis
     * @param headerBlockOrder the order in which the complete inbound header block arrived on the wire
     * @param array the array of HTTP/2 frame data to process
     * @return the processed HTTP/2 headers
     */
    Http2Headers readHeaders(Http2ClientStream stream,
                             Http2HuffmanDecoder decoder,
                             Http2Headers headers,
                             long headerBlockOrder,
                             Http2FrameData[] array) {
        inboundHeaderDecodeLock.lock();
        try {
            // HPACK dynamic-table updates are connection-scoped, so blocks must decode in wire order.
            while (headerBlockOrder != nextInboundHeaderBlockToDecode) {
                if (state.get().closed()) {
                    throw new IllegalStateException("Connection closed while waiting to decode HTTP/2 headers");
                }
                inboundHeaderDecodeTurn.await();
            }
            try {
                return Http2Headers.create(stream,
                                           inboundDynamicTable,
                                           decoder,
                                           headers,
                                           array);
            } finally {
                nextInboundHeaderBlockToDecode++;
                inboundHeaderDecodeTurn.signalAll();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to decode HTTP/2 headers", e);
        } finally {
            inboundHeaderDecodeLock.unlock();
        }
    }

    /**
     * Wakes threads waiting for their inbound HPACK decode turn during shutdown.
     * Without this signal, a blocked decoder could wait forever for an earlier
     * header block that will never finish once the connection is closing.
     */
    private void signalInboundHeaderDecodeWaiters() {
        inboundHeaderDecodeLock.lock();
        try {
            inboundHeaderDecodeTurn.signalAll();
        } finally {
            inboundHeaderDecodeLock.unlock();
        }
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

    /**
     * Waits for the peer's initial {@code SETTINGS} frame to be processed.
     * Stream creation depends on these values, especially the peer's concurrent
     * stream limit, so callers must not proceed until they are known.
     */
    private void awaitInitialSettings() {
        if (initialSettingsReceived) {
            return;
        }
        try {
            if (!initialSettingsLatch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Failed to receive initial HTTP/2 settings within 20 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for initial HTTP/2 settings", e);
        }
        if (!initialSettingsReceived) {
            throw new IllegalStateException("Connection closed before initial HTTP/2 settings were received");
        }
    }

    /**
     * Reserves one peer-concurrency slot before a stream is opened on the wire.
     * Reserving early prevents concurrent callers from overshooting the peer's
     * advertised {@code SETTINGS_MAX_CONCURRENT_STREAMS} while stream startup is in progress.
     *
     * @return {@code true} if a slot was reserved, {@code false} if the peer limit was already reached
     */
    private boolean reserveStream() {
        reservedStreamsLock.lock();
        try {
            if (reservedStreams >= peerMaxConcurrentStreams) {
                return false;
            }
            reservedStreams++;
            return true;
        } finally {
            reservedStreamsLock.unlock();
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
        BufferData data = readFrameData(frameHeader);
        int streamId = frameHeader.streamId();

        return switch (frameHeader.type()) {
        case GO_AWAY -> handleGoAwayFrame(streamId, frameHeader, data);
        case SETTINGS -> handleSettingsFrame(streamId, frameHeader, data);
        case WINDOW_UPDATE -> handleWindowUpdateFrame(streamId, frameHeader, data);
        case PING -> handlePingFrame(streamId, frameHeader, data);
        case RST_STREAM -> {
            handleRstStreamFrame(streamId, data);
            yield true;
        }
        case DATA -> {
            handleDataFrame(streamId, frameHeader, data);
            yield true;
        }
        case HEADERS, CONTINUATION -> handleHeadersFrame(streamId, frameHeader, data);
        default -> {
            LOGGER.log(WARNING, "Unsupported frame type!! " + frameHeader.type());
            yield true;
        }
        };
    }

    private BufferData readFrameData(Http2FrameHeader frameHeader) {
        if (frameHeader.length() == 0) {
            return BufferData.empty();
        }
        return reader.readBuffer(frameHeader.length());
    }

    private boolean handleGoAwayFrame(int streamId, Http2FrameHeader frameHeader, BufferData data) {
        Http2GoAway http2GoAway = Http2GoAway.create(data);
        recvListener.frameHeader(ctx, streamId, frameHeader);
        recvListener.frame(ctx, streamId, http2GoAway);
        this.close();
        ctx.log(LOGGER, TRACE, "Connection closed by remote peer, error code: %s, last stream: %d",
                http2GoAway.errorCode(),
                http2GoAway.lastStreamId());
        return false;
    }

    private boolean handleSettingsFrame(int streamId, Http2FrameHeader frameHeader, BufferData data) {
        Http2Flag.SettingsFlags flags = frameHeader.flags(Http2FrameTypes.SETTINGS);
        if (flags.ack()) {
            if (frameHeader.length() > 0) {
                throw new Http2Exception(Http2ErrorCode.FRAME_SIZE,
                                         "Settings with ACK should not have payload.");
            }
            return true;
        }

        Http2Settings receivedSettings = Http2Settings.create(data);
        recvListener.frameHeader(ctx, streamId, frameHeader);
        recvListener.frame(ctx, streamId, receivedSettings);
        serverSettings = mergeSettings(serverSettings, receivedSettings);
        updatePeerSettings(receivedSettings);
        initialSettingsReceived = true;
        initialSettingsLatch.countDown();
        ackSettings();
        return true;
    }

    private void updatePeerSettings(Http2Settings receivedSettings) {
        if (receivedSettings.hasValue(Http2Setting.MAX_CONCURRENT_STREAMS) || !initialSettingsReceived) {
            reservedStreamsLock.lock();
            try {
                peerMaxConcurrentStreams = serverSettings.value(Http2Setting.MAX_CONCURRENT_STREAMS);
            } finally {
                reservedStreamsLock.unlock();
            }
        }
        if (receivedSettings.hasValue(Http2Setting.HEADER_TABLE_SIZE)) {
            inboundDynamicTable.protocolMaxTableSize(serverSettings.value(Http2Setting.HEADER_TABLE_SIZE));
        }
        if (receivedSettings.hasValue(Http2Setting.MAX_FRAME_SIZE)) {
            connectionFlowControl.resetMaxFrameSize(serverSettings.value(Http2Setting.MAX_FRAME_SIZE).intValue());
        }
        if (receivedSettings.hasValue(Http2Setting.INITIAL_WINDOW_SIZE)) {
            updateInitialWindowSize(receivedSettings.value(Http2Setting.INITIAL_WINDOW_SIZE));
        }
    }

    private void updateInitialWindowSize(long initWinSizeLong) {
        if (initWinSizeLong > WindowSize.MAX_WIN_SIZE) {
            goAway(0, Http2ErrorCode.FLOW_CONTROL, "Window size too big. Max: ");
            throw new Http2Exception(Http2ErrorCode.PROTOCOL,
                                     "Received too big INITIAL_WINDOW_SIZE " + initWinSizeLong);
        }
        int initWinSize = (int) initWinSizeLong;
        connectionFlowControl.resetInitialWindowSize(initWinSize);
        Lock lock = streamsLock.readLock();
        lock.lock();
        try {
            streams.values().forEach(stream -> stream.flowControl().outbound().resetStreamWindowSize(initWinSize));
        } finally {
            lock.unlock();
        }
    }

    private static Http2Settings mergeSettings(Http2Settings currentSettings, Http2Settings receivedSettings) {
        Http2Settings.Builder builder = Http2Settings.builder();
        mergeSetting(builder, currentSettings, receivedSettings, Http2Setting.HEADER_TABLE_SIZE);
        mergeSetting(builder, currentSettings, receivedSettings, Http2Setting.ENABLE_PUSH);
        mergeSetting(builder, currentSettings, receivedSettings, Http2Setting.MAX_CONCURRENT_STREAMS);
        mergeSetting(builder, currentSettings, receivedSettings, Http2Setting.INITIAL_WINDOW_SIZE);
        mergeSetting(builder, currentSettings, receivedSettings, Http2Setting.MAX_FRAME_SIZE);
        mergeSetting(builder, currentSettings, receivedSettings, Http2Setting.MAX_HEADER_LIST_SIZE);
        return builder.build();
    }

    private static <T> void mergeSetting(Http2Settings.Builder builder,
                                         Http2Settings currentSettings,
                                         Http2Settings receivedSettings,
                                         Http2Setting<T> setting) {
        if (receivedSettings.hasValue(setting)) {
            builder.add(setting, receivedSettings.value(setting));
        } else if (currentSettings.hasValue(setting)) {
            builder.add(setting, currentSettings.value(setting));
        }
    }

    private boolean handleWindowUpdateFrame(int streamId, Http2FrameHeader frameHeader, BufferData data) {
        Http2WindowUpdate windowUpdate = Http2WindowUpdate.create(data);
        recvListener.frameHeader(ctx, streamId, frameHeader);
        recvListener.frame(ctx, streamId, windowUpdate);
        if (streamId == 0) {
            updateConnectionWindow(windowUpdate);
        } else {
            stream(streamId).windowUpdate(windowUpdate);
        }
        return true;
    }

    private void updateConnectionWindow(Http2WindowUpdate windowUpdate) {
        int increment = windowUpdate.windowSizeIncrement();
        if (increment == 0) {
            Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.PROTOCOL, "Window size 0");
            writer.write(frame.toFrameData(serverSettings, 0, Http2Flag.NoFlags.create()));
        }
        boolean overflow = connectionFlowControl.incrementOutboundConnectionWindowSize(increment) > WindowSize.MAX_WIN_SIZE;
        if (overflow) {
            Http2GoAway frame = new Http2GoAway(0, Http2ErrorCode.FLOW_CONTROL, "Window size too big. Max: ");
            writer.write(frame.toFrameData(serverSettings, 0, Http2Flag.NoFlags.create()));
        }
    }

    private boolean handlePingFrame(int streamId, Http2FrameHeader frameHeader, BufferData data) {
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
            pong(data.readLong());
        }
        return true;
    }

    private void handleRstStreamFrame(int streamId, BufferData data) {
        Http2RstStream rstStream = Http2RstStream.create(data);
        recvListener.frame(ctx, streamId, rstStream);
        stream(streamId).rstStream(rstStream);
    }

    private void handleDataFrame(int streamId, Http2FrameHeader frameHeader, BufferData data) {
        Http2ClientStream stream = stream(streamId);
        if (stream == null) {
            ctx.log(LOGGER, DEBUG, "%d: received data for stream %d, which does not exist", 0, streamId);
            return;
        }
        stream.flowControl().inbound().decrementWindowSize(frameHeader.length());
        ctx.log(LOGGER, DEBUG, "%d: received data for stream %d", 0, streamId);
        stream.push(new Http2FrameData(frameHeader, data));
    }

    private boolean handleHeadersFrame(int streamId, Http2FrameHeader frameHeader, BufferData data) {
        Http2ClientStream headerStream = stream(streamId);
        if (headerStream == null) {
            ctx.log(LOGGER, DEBUG, "%d: received %s for stream %d, which does not exist", 0, frameHeader.type(), streamId);
            return true;
        }
        if ((frameHeader.flags() & Http2Flag.END_OF_HEADERS) == Http2Flag.END_OF_HEADERS) {
            // Capture the arrival order of each complete header block before stream threads decode it.
            headerStream.inboundHeaderBlock(inboundHeaderBlockSequence.incrementAndGet());
        }
        headerStream.push(new Http2FrameData(frameHeader, data));
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

    private static BufferData pingData(long pingId) {
        return BufferData.create(Long.BYTES)
                .writeInt64(pingId);
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
