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

package io.helidon.nima.http2.webclient;

import java.io.IOException;
import java.io.OutputStream;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.nima.http.media.ReadableEntityBase;
import io.helidon.nima.http2.Http2ErrorCode;
import io.helidon.nima.http2.Http2Flag;
import io.helidon.nima.http2.Http2FrameData;
import io.helidon.nima.http2.Http2FrameHeader;
import io.helidon.nima.http2.Http2FrameListener;
import io.helidon.nima.http2.Http2FrameType;
import io.helidon.nima.http2.Http2FrameTypes;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.http2.Http2LoggingFrameListener;
import io.helidon.nima.http2.Http2RstStream;
import io.helidon.nima.http2.Http2Settings;
import io.helidon.nima.http2.Http2StreamState;

class Http2ClientStream {
    private final Http2ClientConnection myConnection;
    private final SocketContext ctx;
    private final int streamId;
    private final Http2FrameListener sendListener = new Http2LoggingFrameListener("cl-send");
    private final Http2FrameListener recvListener = new Http2LoggingFrameListener("cl-recv");
    // todo configure
    private final Http2Settings settings = Http2Settings.create();
    private volatile Http2StreamState state = Http2StreamState.IDLE;

    Http2ClientStream(Http2ClientConnection myConnection, SocketContext ctx, int streamId) {
        this.myConnection = myConnection;
        this.ctx = ctx;
        this.streamId = streamId;
    }

    void cancel() {
        Http2RstStream rstStream = new Http2RstStream(Http2ErrorCode.CANCEL);
        Http2FrameData frameData = rstStream.toFrameData(settings, streamId, Http2Flag.NoFlags.create());
        sendListener.frameHeader(ctx, frameData.header());
        sendListener.frame(ctx, rstStream);
        write(frameData, false);
    }

    ReadableEntityBase entity() {
        return null;
    }

    void write(Http2Headers http2Headers, boolean endOfStream) {
        this.state = Http2StreamState.checkAndGetState(this.state, Http2FrameType.HEADERS, true, endOfStream, true);
        //myConnection.writeHeaders(streamId, sendListener, http2Headers, endOfStream);
    }

    void writeData(BufferData entityBytes, boolean endOfStream) {
        // todo split to frames if bigger than max frame size
        // todo handle flow control
        Http2FrameHeader frameHeader = Http2FrameHeader.create(entityBytes.available(),
                                                               Http2FrameTypes.DATA,
                                                               Http2Flag.DataFlags.create(endOfStream
                                                                                                  ? Http2Flag.END_OF_STREAM
                                                                                                  : 0),
                                                               streamId);
        sendListener.frameHeader(ctx, frameHeader);
        sendListener.frame(ctx, entityBytes);
        write(new Http2FrameData(frameHeader, entityBytes), endOfStream);
    }

    Http2Headers readHeaders() {
        return null;
    }

    ClientOutputStream outputStream() {
        return new ClientOutputStream();
    }

    private void write(Http2FrameData frameData, boolean endOfStream) {
        this.state = Http2StreamState.checkAndGetState(this.state,
                                                       frameData.header().type(),
                                                       true,
                                                       endOfStream,
                                                       false);
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
