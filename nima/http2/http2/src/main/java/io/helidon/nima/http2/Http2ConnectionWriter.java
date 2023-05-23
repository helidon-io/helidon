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

package io.helidon.nima.http2;

import java.util.List;
import java.util.concurrent.Callable;
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

    // todo replace with prioritized lock (stream priority + connection writes have highest prio)
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
        this.responseHuffman = new Http2HuffmanEncoder();
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

        return withStreamLock(() -> {
            int written = 0;
            headerBuffer.clear();
            headers.write(outboundDynamicTable, responseHuffman, headerBuffer);
            Http2FrameHeader frameHeader = Http2FrameHeader.create(headerBuffer.available(),
                                                                   Http2FrameTypes.HEADERS,
                                                                   flags,
                                                                   streamId);
            written += frameHeader.length();
            written += Http2FrameHeader.LENGTH;

            noLockWrite(new Http2FrameData(frameHeader, headerBuffer));

            return written;
        });
    }

    @Override
    public int writeHeaders(Http2Headers headers,
                            int streamId,
                            Http2Flag.HeaderFlags flags,
                            Http2FrameData dataFrame,
                            FlowControl.Outbound flowControl) {
        // this is executing in the thread of the stream
        // we must enforce parallelism of exactly 1, to make sure the dynamic table is updated
        // and then immediately written

        return withStreamLock(() -> {
            int bytesWritten = 0;

            headerBuffer.clear();
            headers.write(outboundDynamicTable, responseHuffman, headerBuffer);
            bytesWritten += headerBuffer.available();

            Http2FrameHeader frameHeader = Http2FrameHeader.create(headerBuffer.available(),
                                                                   Http2FrameTypes.HEADERS,
                                                                   flags,
                                                                   streamId);
            bytesWritten += Http2FrameHeader.LENGTH;

            noLockWrite(new Http2FrameData(frameHeader, headerBuffer));
            writeData(dataFrame, flowControl);
            bytesWritten += Http2FrameHeader.LENGTH;
            bytesWritten += dataFrame.header().length();

            return bytesWritten;
        });
    }

    /**
     * Update header table size.
     *
     * @param newSize in bytes
     * @throws InterruptedException in case we fail to lock on the stream
     */
    public void updateHeaderTableSize(long newSize) throws InterruptedException {
        withStreamLock(() -> {
            outboundDynamicTable.protocolMaxTableSize(newSize);
            return null;
        });
    }

    private void lockedWrite(Http2FrameData frame) {
        withStreamLock(() -> {
            noLockWrite(frame);
            return null;
        });
    }

    private <T> T withStreamLock(Callable<T> callable) {
        try {
            streamLock.lockInterruptibly();
            try {
                return callable.call();
            } finally {
                streamLock.unlock();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void noLockWrite(Http2FrameData frame) {
        Http2FrameHeader frameHeader = frame.header();
        listener.frameHeader(ctx, frameHeader);

        BufferData headerData = frameHeader.write();
        listener.frameHeader(ctx, headerData);

        if (frameHeader.length() == 0) {
            writer.write(headerData);
        } else {
            BufferData data = frame.data().copy();
            listener.frame(ctx, data);
            writer.write(BufferData.create(headerData, data));
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
                flowControl.decrementWindowSize(currFrame.header().length());
                flowControl.blockTillUpdate();
                currFrame = splitFrames[1];
            }
        }
    }

    // TODO use for fastpath
    //    private void noLockWrite(Http2FrameData... frames) {
    //        List<BufferData> toWrite = new LinkedList<>();
    //
    //        for (Http2FrameData frame : frames) {
    //            BufferData headerData = frame.header().write();
    //
    //            listener.frameHeader(ctx, frame.header());
    //            listener.frameHeader(ctx, headerData);
    //
    //            toWrite.add(headerData);
    //
    //            BufferData data = frame.data();
    //
    //            if (data.available() != 0) {
    //                toWrite.add(data);
    //            }
    //        }
    //
    //        writer.write(toWrite.toArray(new BufferData[0]));
    //    }
}
