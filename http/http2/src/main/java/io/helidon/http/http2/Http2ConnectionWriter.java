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

package io.helidon.http.http2;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;

/**
 * HTTP/2 connection writer.
 */
public class Http2ConnectionWriter implements Http2StreamWriter {
    private static final int DEFAULT_HEADER_TABLE_SIZE = Http2Setting.HEADER_TABLE_SIZE.defaultValue().intValue();
    private static final int NO_PENDING_HEADER_TABLE_SIZE = -1;

    private final DataWriter writer;

    private final Lock streamLock = new ReentrantLock(true);
    private final SocketContext ctx;
    private final Http2FrameListener listener;
    private final Http2Headers.DynamicTable outboundDynamicTable;
    private final Http2HuffmanEncoder responseHuffman;
    private final BufferData headerBuffer = BufferData.growing(512);
    private int minimumPendingHeaderTableSize = Integer.MAX_VALUE;
    private int pendingHeaderTableSize = NO_PENDING_HEADER_TABLE_SIZE;
    private int minimumUnacknowledgedHeaderTableSize = Integer.MAX_VALUE;
    private int unacknowledgedHeaderTableSize = NO_PENDING_HEADER_TABLE_SIZE;
    private long unacknowledgedProtocolMaxHeaderTableSize = NO_PENDING_HEADER_TABLE_SIZE;

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
        this.outboundDynamicTable = Http2Headers.DynamicTable.create(DEFAULT_HEADER_TABLE_SIZE);
        this.responseHuffman = Http2HuffmanEncoder.create();
    }

    @Override
    public void write(Http2FrameData frame) {
        lockedWrite(frame);
    }

    /**
     * Attempts to write a frame without waiting for another frame write to finish.
     *
     * @param frame frame to write
     * @param beforeWrite action to invoke after acquiring the frame serialization lock and before writing
     * @return {@code true} if the frame was written, {@code false} if another frame write is in progress
     */
    @Api.Internal
    public boolean tryWrite(Http2FrameData frame, Runnable beforeWrite) {
        Objects.requireNonNull(frame);
        Objects.requireNonNull(beforeWrite);
        if (!streamLock.tryLock()) {
            return false;
        }
        try {
            beforeWrite.run();
            noLockWrite(frame);
            return true;
        } finally {
            streamLock.unlock();
        }
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
        Http2Headers.DynamicTable tableSnapshot = pendingHeaderTableSize == NO_PENDING_HEADER_TABLE_SIZE
                ? null
                : outboundDynamicTable.copy();
        try {

            int written = 0;
            headerBuffer.clear();
            writePendingHeaderTableSizeUpdates();
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
                clearPendingHeaderTableSizeUpdates();
                return written;
            }

            // Split header frame to smaller continuation frames RFC 9113 §6.10
            BufferData[] fragments = Http2Headers.split(headerBuffer, maxFrameSize);

            // First header fragment
            BufferData fragment = fragments[0];
            Http2FrameHeader frameHeader;
            frameHeader = Http2FrameHeader.create(fragment.available(),
                    Http2FrameTypes.HEADERS,
                    Http2Flag.HeaderFlags.create(flags.endOfStream() ? Http2Flag.END_OF_STREAM : 0),
                    streamId);
            written += frameHeader.length();
            written += Http2FrameHeader.LENGTH;
            noLockWrite(new Http2FrameData(frameHeader, fragment));
            clearPendingHeaderTableSizeUpdates();

            // Header continuation fragments in the middle
            for (int i = 1; i < fragments.length - 1; i++) {
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
                    Http2Flag.ContinuationFlags.create(Http2Flag.END_OF_HEADERS),
                    streamId);
            written += frameHeader.length();
            written += Http2FrameHeader.LENGTH;
            noLockWrite(new Http2FrameData(frameHeader, fragment));
            return written;
        } catch (Throwable t) {
            if (tableSnapshot != null && pendingHeaderTableSize != NO_PENDING_HEADER_TABLE_SIZE) {
                outboundDynamicTable.restore(tableSnapshot);
            }
            throw t;
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
     * Record an outbound header table size allowed by the peer. The change takes effect after the corresponding SETTINGS
     * acknowledgment is written. The encoder keeps its local default as an upper bound and writes any required HPACK
     * dynamic table size update at the beginning of the next header block.
     *
     * @param newSize maximum size accepted by the peer, in bytes
     * @throws InterruptedException in case we fail to lock on the stream
     */
    public void updateHeaderTableSize(long newSize) throws InterruptedException {
        if (newSize < 0) {
            throw new IllegalArgumentException("Header table size must be greater than or equal to zero");
        }
        streamLock.lockInterruptibly();
        try {
            int effectiveSize = (int) Math.min(DEFAULT_HEADER_TABLE_SIZE, newSize);
            minimumUnacknowledgedHeaderTableSize = Math.min(minimumUnacknowledgedHeaderTableSize, effectiveSize);
            unacknowledgedHeaderTableSize = effectiveSize;
            unacknowledgedProtocolMaxHeaderTableSize = newSize;
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

    private void writePendingHeaderTableSizeUpdates() {
        if (pendingHeaderTableSize == NO_PENDING_HEADER_TABLE_SIZE) {
            return;
        }
        if (minimumPendingHeaderTableSize < pendingHeaderTableSize) {
            headerBuffer.writeHpackInt(minimumPendingHeaderTableSize, 0b00100000, 5);
        }
        headerBuffer.writeHpackInt(pendingHeaderTableSize, 0b00100000, 5);
    }

    private void clearPendingHeaderTableSizeUpdates() {
        minimumPendingHeaderTableSize = Integer.MAX_VALUE;
        pendingHeaderTableSize = NO_PENDING_HEADER_TABLE_SIZE;
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
        applyAcknowledgedHeaderTableSizeUpdates(frameHeader);
    }

    private void applyAcknowledgedHeaderTableSizeUpdates(Http2FrameHeader frameHeader) {
        if (frameHeader.type() != Http2FrameType.SETTINGS
                || !frameHeader.flags(Http2FrameTypes.SETTINGS).ack()
                || unacknowledgedHeaderTableSize == NO_PENDING_HEADER_TABLE_SIZE) {
            return;
        }

        long previousSize = outboundDynamicTable.maxTableSize();
        outboundDynamicTable.protocolMaxTableSize(unacknowledgedProtocolMaxHeaderTableSize);
        if (minimumUnacknowledgedHeaderTableSize < previousSize) {
            outboundDynamicTable.maxTableSize(minimumUnacknowledgedHeaderTableSize);
        }
        if (unacknowledgedHeaderTableSize != outboundDynamicTable.maxTableSize()) {
            outboundDynamicTable.maxTableSize(unacknowledgedHeaderTableSize);
        }
        if (minimumUnacknowledgedHeaderTableSize != previousSize
                || unacknowledgedHeaderTableSize != previousSize) {
            minimumPendingHeaderTableSize = Math.min(minimumPendingHeaderTableSize,
                                                     minimumUnacknowledgedHeaderTableSize);
            pendingHeaderTableSize = unacknowledgedHeaderTableSize;
        }
        minimumUnacknowledgedHeaderTableSize = Integer.MAX_VALUE;
        unacknowledgedHeaderTableSize = NO_PENDING_HEADER_TABLE_SIZE;
        unacknowledgedProtocolMaxHeaderTableSize = NO_PENDING_HEADER_TABLE_SIZE;
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
