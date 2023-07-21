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

package io.helidon.nima.http2.webclient;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.nima.http2.ConnectionFlowControl;
import io.helidon.nima.http2.Http2ConnectionWriter;
import io.helidon.nima.http2.Http2ErrorCode;
import io.helidon.nima.http2.Http2Exception;
import io.helidon.nima.http2.Http2Flag;
import io.helidon.nima.http2.Http2FrameData;
import io.helidon.nima.http2.Http2FrameHeader;
import io.helidon.nima.http2.Http2FrameListener;
import io.helidon.nima.http2.Http2FrameTypes;
import io.helidon.nima.http2.Http2GoAway;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.http2.Http2LoggingFrameListener;
import io.helidon.nima.http2.Http2Ping;
import io.helidon.nima.http2.Http2RstStream;
import io.helidon.nima.http2.Http2Setting;
import io.helidon.nima.http2.Http2Settings;
import io.helidon.nima.http2.Http2WindowUpdate;
import io.helidon.nima.http2.WindowSize;
import io.helidon.nima.webclient.api.ClientConnection;
import io.helidon.nima.webclient.api.WebClient;

import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

class Http2ClientConnection {
    private static final System.Logger LOGGER = System.getLogger(Http2ClientConnection.class.getName());
    private static final int FRAME_HEADER_LENGTH = 9;
    private static final byte[] PRIOR_KNOWLEDGE_PREFACE =
            "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private final Http2FrameListener sendListener = new Http2LoggingFrameListener("cl-send");
    private final Http2FrameListener recvListener = new Http2LoggingFrameListener("cl-recv");
    private final LockingStreamIdSequence streamIdSeq = new LockingStreamIdSequence();
    private final Map<Integer, Http2ClientStream> streams = new HashMap<>();
    private final ConnectionFlowControl connectionFlowControl;
    private final Http2Headers.DynamicTable inboundDynamicTable =
            Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
    private final ClientConnection connection;
    private final HelidonSocket helidonSocket;
    private final Http2ConnectionWriter writer;
    private final DataReader reader;
    private final DataWriter dataWriter;

    private Http2Settings serverSettings = Http2Settings.builder()
            .build();
    private Future<?> handleTask;

    Http2ClientConnection(Http2ClientProtocolConfig protocolConfig, ClientConnection connection) {
        this.connectionFlowControl = ConnectionFlowControl.clientBuilder(this::writeWindowsUpdate)
                .maxFrameSize(protocolConfig.maxFrameSize())
                .initialWindowSize(protocolConfig.initialWindowSize())
                .blockTimeout(protocolConfig.flowControlBlockTimeout())
                .build();
        this.connection = connection;
        this.helidonSocket = connection.helidonSocket();
        this.dataWriter = connection.writer();
        this.reader = connection.reader();
        this.writer = new Http2ConnectionWriter(connection.helidonSocket(), connection.writer(), List.of());
    }

    static Http2ClientConnection create(WebClient webClient,
                                        Http2ClientProtocolConfig protocolConfig,
                                        ClientConnection connection) {

        Http2ClientConnection h2conn = new Http2ClientConnection(protocolConfig, connection);
        h2conn.start(webClient.executor());

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
        return streams.get(streamId);
    }

    Http2ClientStream createStream(Http2StreamConfig config) {
        //FIXME: priority
        return new Http2ClientStream(this,
                                     serverSettings,
                                     helidonSocket,
                                     config.timeout(),
                                     streamIdSeq);
    }

    void addStream(int streamId, Http2ClientStream stream) {
        this.streams.put(streamId, stream);
    }

    void removeStream(int streamId) {
        this.streams.remove(streamId);
    }

    Http2ClientStream tryStream(Http2StreamConfig config) {
        try {
            return createStream(config);
        } catch (IllegalStateException | UncheckedIOException e) {
            return null;
        }
    }

    void close() {
        try {
            handleTask.cancel(true);
            connection.close();
        } catch (Throwable e) {
            helidonSocket.log(LOGGER, TRACE, "Failed to close HTTP/2 connection.", e);
        }
    }

    void sendPreface(Http2ClientProtocolConfig config, boolean sendSettings) {
        dataWriter.writeNow(BufferData.create(PRIOR_KNOWLEDGE_PREFACE));
        if (sendSettings) {
            // §3.5 Preface bytes must be followed by setting frame
            Http2Settings.Builder b = Http2Settings.builder();
            if (config.maxHeaderListSize() > 0) {
                b.add(Http2Setting.MAX_HEADER_LIST_SIZE, config.maxHeaderListSize());
            }
            Http2Settings http2Settings = b
                    .add(Http2Setting.INITIAL_WINDOW_SIZE, (long) config.initialWindowSize())
                    .add(Http2Setting.MAX_FRAME_SIZE, (long) config.maxFrameSize())
                    .add(Http2Setting.ENABLE_PUSH, false)
                    .build();

            Http2Flag.SettingsFlags flags = Http2Flag.SettingsFlags.create(0);
            Http2FrameData frameData = http2Settings.toFrameData(null, 0, flags);
            sendListener.frameHeader(helidonSocket, frameData.header());
            sendListener.frame(helidonSocket, http2Settings);
            writer.write(frameData);
        }
        // First connection window update, with prefetch increment
        Http2WindowUpdate windowUpdate = new Http2WindowUpdate(config.prefetch());
        Http2Flag.NoFlags flags = Http2Flag.NoFlags.create();
        Http2FrameData frameData = windowUpdate.toFrameData(null, 0, flags);
        sendListener.frameHeader(helidonSocket, frameData.header());
        sendListener.frame(helidonSocket, windowUpdate);
        writer.write(frameData);
    }

    private void start(ExecutorService executor) {
        handleTask = executor.submit(() -> {
            while (!Thread.interrupted()) {
                handle();
            }
            helidonSocket.log(LOGGER, TRACE, "Client listener interrupted");
        });
    }

    private void writeWindowsUpdate(int streamId, Http2WindowUpdate windowUpdateFrame) {
        writer.write(windowUpdateFrame.toFrameData(serverSettings, streamId, Http2Flag.NoFlags.create()));
    }

    private void handle() {
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
            recvListener.frameHeader(helidonSocket, frameHeader);
            recvListener.frame(helidonSocket, http2GoAway);
            this.close();
            throw new IllegalStateException("Connection closed by the other side, error code: "
                                                    + http2GoAway.errorCode()
                                                    + " lastStreamId: " + http2GoAway.lastStreamId());

        case SETTINGS:
            serverSettings = Http2Settings.create(data);
            recvListener.frameHeader(helidonSocket, frameHeader);
            recvListener.frame(helidonSocket, serverSettings);
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
                streams.values().forEach(stream -> stream.flowControl().outbound().resetStreamWindowSize(initWinSize));

            }
            // §6.5.3 Settings Synchronization
            ackSettings();
            //FIXME: Max number of concurrent streams
            return;

        case WINDOW_UPDATE:
            Http2WindowUpdate windowUpdate = Http2WindowUpdate.create(data);
            recvListener.frameHeader(helidonSocket, frameHeader);
            recvListener.frame(helidonSocket, windowUpdate);
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
            return;
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
                recvListener.frame(helidonSocket, ping);
                BufferData frame = ping.data();
                Http2FrameHeader header = Http2FrameHeader.create(frame.available(),
                                                                  Http2FrameTypes.PING,
                                                                  Http2Flag.PingFlags.create(Http2Flag.ACK),
                                                                  0);
                writer.write(new Http2FrameData(header, frame));
            }
            break;

        case RST_STREAM:
            Http2RstStream rstStream = Http2RstStream.create(data);
            recvListener.frame(helidonSocket, rstStream);
            stream(streamId).rstStream(rstStream);
            break;

        case DATA:
            Http2ClientStream stream = stream(streamId);
            stream.flowControl().inbound().decrementWindowSize(frameHeader.length());
            stream.push(new Http2FrameData(frameHeader, data));
            break;

        case HEADERS, CONTINUATION:
            stream(streamId).push(new Http2FrameData(frameHeader, data));
            return;

        default:
            LOGGER.log(WARNING, "Unsupported frame type!! " + frameHeader.type());
        }

    }

    private void ackSettings() {
        Http2Flag.SettingsFlags flags = Http2Flag.SettingsFlags.create(Http2Flag.ACK);
        Http2Settings http2Settings = Http2Settings.create();
        Http2FrameData frameData = http2Settings.toFrameData(null, 0, flags);
        sendListener.frameHeader(helidonSocket, frameData.header());
        sendListener.frame(helidonSocket, http2Settings);
        writer.write(frameData);
    }

    private void goAway(int streamId, Http2ErrorCode errorCode, String msg) {
        Http2Settings http2Settings = Http2Settings.create();
        Http2GoAway frame = new Http2GoAway(streamId, errorCode, msg);
        writer.write(frame.toFrameData(http2Settings, 0, Http2Flag.NoFlags.create()));
    }
}
