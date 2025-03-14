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

package io.helidon.http.http2;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;

/**
 * HTTP/2 connection writer.
 */
public class Http2ConnectionWriter implements Http2StreamWriter {
    private final DataWriter writer;

    private final Lock streamLock = new ReentrantLock(true);
    private final SocketContext ctx;
    private final Http2FrameListener listener;
    private final Http2Headers.DynamicTable outboundDynamicTable;
    private final Http2HuffmanEncoder responseHuffman;
    private final BufferData headerBuffer = BufferData.growing(512);

    /**
     * A new writer.
     *
     * @param ctx                connection context
     * @param writer             data writer
     * @param sendFrameListeners send frame listeners
     */
    public Http2ConnectionWriter(SocketContext ctx, DataWriter writer, List<Http2FrameListener> sendFrameListeners) {
        this.ctx = ctx;
        this.listener = Http2FrameListener.create(sendFrameListeners);
        this.writer = writer;

        // initial size is based on our settings, then updated with client settings
        this.outboundDynamicTable = Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
        this.responseHuffman = Http2HuffmanEncoder.create();
    }

    @Override
    public void write(Http2FrameData frame) {
        lockedWrite(frame);
    }

    @Override
    public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
        for (Http2FrameData f : frame.split(flowControl.maxFrameSize())) {
            splitAndWrite(f, flowControl);
        }
    }

    @Override
    public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags, FlowControl.Outbound flowControl) {
        // this is executing in the thread of the stream
        // we must enforce parallelism of exactly 1, to make sure the dynamic table is updated
        // and then immediately written

        int maxFrameSize = flowControl.maxFrameSize();

        lock();
        try {

            int written = 0;
            headerBuffer.clear();
            headers.write(outboundDynamicTable, responseHuffman, headerBuffer);

            // Fast path when headers fits within the SETTINGS_MAX_FRAME_SIZE
            if (headerBuffer.available() <= maxFrameSize) {
                Http2FrameHeader frameHeader = Http2FrameHeader.create(headerBuffer.available(),
                        Http2FrameTypes.HEADERS,
                        flags,
                        streamId);
                written += frameHeader.length();
                written += Http2FrameHeader.LENGTH;

                noLockWrite(new Http2FrameData(frameHeader, headerBuffer));
                return written;
            }

            // Split header frame to smaller continuation frames RFC 9113 §6.10
            BufferData[] fragments = Http2Headers.split(headerBuffer, maxFrameSize);

            // First header fragment
            BufferData fragment = fragments[0];
            Http2FrameHeader frameHeader;
            frameHeader = Http2FrameHeader.create(fragment.available(),
                    Http2FrameTypes.HEADERS,
                    Http2Flag.HeaderFlags.create(0),
                    streamId);
            written += frameHeader.length();
            written += Http2FrameHeader.LENGTH;
            noLockWrite(new Http2FrameData(frameHeader, fragment));

            // Header continuation fragments in the middle
            for (int i = 1; i < fragments.length; i++) {
                fragment = fragments[i];
                frameHeader = Http2FrameHeader.create(fragment.available(),
                        Http2FrameTypes.CONTINUATION,
                        Http2Flag.ContinuationFlags.create(0),
                        streamId);
                written += frameHeader.length();
                written += Http2FrameHeader.LENGTH;
                noLockWrite(new Http2FrameData(frameHeader, fragment));
            }

            // Last header continuation fragment
            fragment = fragments[fragments.length - 1];
            frameHeader = Http2FrameHeader.create(fragment.available(),
                    Http2FrameTypes.CONTINUATION,
                    // Last fragment needs to indicate the end of headers
                    Http2Flag.ContinuationFlags.create(flags.value() | Http2Flag.END_OF_HEADERS),
                    streamId);
            written += frameHeader.length();
            written += Http2FrameHeader.LENGTH;
            noLockWrite(new Http2FrameData(frameHeader, fragment));
            return written;
        } finally {
            streamLock.unlock();
        }
    }

    @Override
    public int writeHeaders(Http2Headers headers,
                            int streamId,
                            Http2Flag.HeaderFlags flags,
                            Http2FrameData dataFrame,
                            FlowControl.Outbound flowControl) {
        // Executed on stream thread
        int bytesWritten = 0;
        bytesWritten += writeHeaders(headers, streamId, flags, flowControl);
        writeData(dataFrame, flowControl);
        bytesWritten += Http2FrameHeader.LENGTH;
        bytesWritten += dataFrame.header().length();

        return bytesWritten;
    }

    /**
     * Update header table size.
     *
     * @param newSize in bytes
     * @throws InterruptedException in case we fail to lock on the stream
     */
    public void updateHeaderTableSize(long newSize) throws InterruptedException {
        lock();
        try {
            outboundDynamicTable.protocolMaxTableSize(newSize);
        } finally {
            streamLock.unlock();
        }
    }

    private void lockedWrite(Http2FrameData frame) {
        lock();
        try {
            noLockWrite(frame);
        } finally {
            streamLock.unlock();
        }
    }

    private void lock() {
        try {
            streamLock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
    }

    private void noLockWrite(Http2FrameData frame) {
        Http2FrameHeader frameHeader = frame.header();
        int streamId = frameHeader.streamId();
        listener.frameHeader(ctx, streamId, frameHeader);

        BufferData headerData = frameHeader.write();
        listener.frameHeader(ctx, streamId, headerData);

        if (frameHeader.length() == 0) {
            writer.writeNow(headerData);
        } else {
            BufferData data = frame.data().copy();
            listener.frame(ctx, streamId, data);
            writer.writeNow(BufferData.create(headerData, data));
        }
    }

    private void splitAndWrite(Http2FrameData frame, FlowControl.Outbound flowControl) {
        Http2FrameData currFrame = frame;
        while (true) {
            Http2FrameData[] splitFrames = flowControl.cut(currFrame);
            if (splitFrames.length == 1) {
                // windows are wide enough
                lockedWrite(currFrame);
                flowControl.decrementWindowSize(currFrame.header().length());
                break;
            } else if (splitFrames.length == 0) {
                // block until window update
                flowControl.blockTillUpdate();
            } else if (splitFrames.length == 2) {
                // write send-able part and block until window update with the rest
                lockedWrite(splitFrames[0]);
                flowControl.decrementWindowSize(splitFrames[0].header().length());
                flowControl.blockTillUpdate();
                currFrame = splitFrames[1];
            }
        }
    }
}
