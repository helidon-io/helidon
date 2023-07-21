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

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.socket.SocketContext;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.ReadableEntityBase;
import io.helidon.nima.http2.Http2ErrorCode;
import io.helidon.nima.http2.Http2Exception;
import io.helidon.nima.http2.Http2Flag;
import io.helidon.nima.http2.Http2FrameData;
import io.helidon.nima.http2.Http2FrameHeader;
import io.helidon.nima.http2.Http2FrameListener;
import io.helidon.nima.http2.Http2FrameType;
import io.helidon.nima.http2.Http2FrameTypes;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.http2.Http2HuffmanDecoder;
import io.helidon.nima.http2.Http2LoggingFrameListener;
import io.helidon.nima.http2.Http2Priority;
import io.helidon.nima.http2.Http2RstStream;
import io.helidon.nima.http2.Http2Setting;
import io.helidon.nima.http2.Http2Settings;
import io.helidon.nima.http2.Http2Stream;
import io.helidon.nima.http2.Http2StreamState;
import io.helidon.nima.http2.Http2WindowUpdate;
import io.helidon.nima.http2.StreamFlowControl;
import io.helidon.nima.http2.WindowSize;
import io.helidon.nima.webclient.api.ClientResponseEntity;

import static java.lang.System.Logger.Level.DEBUG;

class Http2ClientStream implements Http2Stream {

    private static final System.Logger LOGGER = System.getLogger(Http2ClientStream.class.getName());
    private final Http2ClientConnection connection;
    private final Http2Settings serverSettings;
    private final SocketContext ctx;
    private final LockingStreamIdSequence streamIdSeq;
    private final Http2FrameListener sendListener = new Http2LoggingFrameListener("cl-send");
    private final Http2FrameListener recvListener = new Http2LoggingFrameListener("cl-recv");
    private final Http2Settings settings = Http2Settings.create();
    private final List<Http2FrameData> continuationData = new ArrayList<>();
    private final StreamBuffer buffer;

    private Http2StreamState state = Http2StreamState.IDLE;
    private Http2Headers currentHeaders;
    private StreamFlowControl flowControl;
    private int streamId;

    Http2ClientStream(Http2ClientConnection connection,
                      Http2Settings serverSettings,
                      SocketContext ctx,
                      Duration timeout,
                      LockingStreamIdSequence streamIdSeq) {
        this.connection = connection;
        this.serverSettings = serverSettings;
        this.ctx = ctx;
        this.streamIdSeq = streamIdSeq;
        this.buffer = new StreamBuffer(streamId, timeout);
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
        currentHeaders = headers;
    }

    @Override
    public void rstStream(Http2RstStream rstStream) {
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
    public void data(Http2FrameHeader header, BufferData data) {
        flowControl.inbound().incrementWindowSize(header.length());
    }

    @Override
    public void priority(Http2Priority http2Priority) {
        //FIXME: priority
    }

    @Override
    public StreamFlowControl flowControl() {
        return flowControl;
    }

    void cancel() {
        Http2RstStream rstStream = new Http2RstStream(Http2ErrorCode.CANCEL);
        Http2FrameData frameData = rstStream.toFrameData(settings, streamId, Http2Flag.NoFlags.create());
        sendListener.frameHeader(ctx, frameData.header());
        sendListener.frame(ctx, rstStream);
        write(frameData, false);
    }

    ReadableEntityBase entity() {
        return ClientResponseEntity.create(
                ContentDecoder.NO_OP,
                this::read,
                this::close,
                ClientRequestHeaders.create(WritableHeaders.create()),
                ClientResponseHeaders.create(WritableHeaders.create()),
                MediaContext.create()
        );
    }

    void close() {
        connection.removeStream(streamId);
    }

    /**
     * Push data or header frame in to stream buffer.
     *
     * @param frameData data or header frame
     */
    void push(Http2FrameData frameData) {
        buffer.push(frameData);
    }

    BufferData read(int i) {
        while (state == Http2StreamState.HALF_CLOSED_LOCAL) {
            Http2FrameData frameData = readOne();
            if (frameData != null) {
                return frameData.data();
            }
        }
        return BufferData.empty();
    }

    void write(Http2Headers http2Headers, boolean endOfStream) {
        this.state = Http2StreamState.checkAndGetState(this.state, Http2FrameType.HEADERS, true, endOfStream, true);
        Http2Flag.HeaderFlags flags;
        if (endOfStream) {
            flags = Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM);
        } else {
            flags = Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS);
        }

        sendListener.headers(ctx, http2Headers);
        try {
            // Keep ascending streamId order among concurrent streams
            // §5.1.1 - The identifier of a newly established stream MUST be numerically
            //          greater than all streams that the initiating endpoint has opened or reserved.
            this.streamId = streamIdSeq.lockAndNext();
            // fixme Configurable initial win size, max frame size
            this.flowControl = connection.flowControl().createStreamFlowControl(
                    streamId,
                    WindowSize.DEFAULT_WIN_SIZE,
                    WindowSize.DEFAULT_MAX_FRAME_SIZE);
            this.connection.addStream(streamId, this);
            // First call to the server-starting stream, needs to be increasing sequence of odd numbers
            connection.writer().writeHeaders(http2Headers, streamId, flags, flowControl.outbound());
        } finally {
            streamIdSeq.unlock();
        }
    }

    void writeData(BufferData entityBytes, boolean endOfStream) {
        Http2FrameHeader frameHeader = Http2FrameHeader.create(entityBytes.available(),
                                                               Http2FrameTypes.DATA,
                                                               Http2Flag.DataFlags.create(endOfStream
                                                                                                  ? Http2Flag.END_OF_STREAM
                                                                                                  : 0),
                                                               streamId);
        Http2FrameData frameData = new Http2FrameData(frameHeader, entityBytes);
        splitAndWrite(frameData);
    }

    Http2Headers readHeaders() {
        while (currentHeaders == null) {
            Http2FrameData frameData = readOne();
            if (frameData != null) {
                throw new IllegalStateException("Unexpected frame type " + frameData.header() + ", HEADERS are expected.");
            }
        }
        return currentHeaders;
    }

    ClientOutputStream outputStream() {
        return new ClientOutputStream();
    }

    private Http2FrameData readOne() {
        Http2FrameData frameData = buffer.poll();

        if (frameData != null) {

            recvListener.frameHeader(ctx, frameData.header());
            recvListener.frame(ctx, frameData.data());

            int flags = frameData.header().flags();
            boolean endOfStream = (flags & Http2Flag.END_OF_STREAM) == Http2Flag.END_OF_STREAM;
            boolean endOfHeaders = (flags & Http2Flag.END_OF_HEADERS) == Http2Flag.END_OF_HEADERS;

            this.state = Http2StreamState.checkAndGetState(this.state,
                                                           frameData.header().type(),
                                                           false,
                                                           endOfStream,
                                                           endOfHeaders);

            switch (frameData.header().type()) {
            case DATA:
                data(frameData.header(), frameData.data());
                return frameData;
            case HEADERS, CONTINUATION:
                continuationData.add(frameData);
                if (endOfHeaders) {
                    var requestHuffman = new Http2HuffmanDecoder();
                    Http2Headers http2Headers = Http2Headers.create(this,
                                                                    connection.getInboundDynamicTable(),
                                                                    requestHuffman,
                                                                    continuationData.toArray(new Http2FrameData[0]));
                    this.headers(http2Headers, endOfStream);
                }
                break;
            default:
                LOGGER.log(DEBUG, "Dropping frame " + frameData.header() + " expected header or data.");
            }
        }
        return null;
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

    class ClientOutputStream extends OutputStream {
        private volatile boolean isClosed;

        @Override
        public void write(int b) throws IOException {
            write(0, 1, (byte) b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            write(off, len, b);
        }

        @Override
        public void close() throws IOException {
            // todo optimize - send last buffer together with end of stream
            writeData(BufferData.empty(), true);
            this.isClosed = true;
            super.close();
        }

        public boolean closed() {
            return isClosed;
        }

        private void write(int off, int len, byte... bytes) {
            writeData(BufferData.create(bytes, off, len), false);
        }
    }
}
