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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;

/**
 * HTTP/2 connection writer.
 */
public class Http2ConnectionWriter implements Http2StreamWriter {
    private static final Runnable NO_OP = () -> { };

    private final DataWriter writer;

    private final Lock streamLock = new ReentrantLock(true);
    private final Lock windowUpdateLock = new ReentrantLock();
    private final Map<Integer, Long> pendingWindowUpdates = new LinkedHashMap<>();
    private final AtomicBoolean windowUpdateWriteScheduled = new AtomicBoolean();
    private final AtomicReference<Throwable> windowUpdateFailure = new AtomicReference<>();
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
        Objects.requireNonNull(frame);
        if (frame.header().type() != Http2FrameType.WINDOW_UPDATE) {
            lockedWrite(frame);
            return;
        }

        // The common path stays synchronous. Only hand off when another frame is holding the serialization lock
        // during a blocking transport write, otherwise an inbound flow-control callback could stop duplex progress.
        boolean locked = false;
        try {
            windowUpdateLock.lock();
            try {
                throwIfWindowUpdateFailed();
                locked = streamLock.tryLock(0, TimeUnit.NANOSECONDS);
                if (!locked) {
                    Http2WindowUpdate windowUpdate = Http2WindowUpdate.create(frame.data());
                    int streamId = frame.header().streamId();
                    pendingWindowUpdates.merge(streamId, (long) windowUpdate.windowSizeIncrement(), Long::sum);
                }
            } finally {
                windowUpdateLock.unlock();
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
        if (locked) {
            try {
                throwIfWindowUpdateFailed();
                int count = pendingWindowUpdateCount();
                Http2FrameData pending;
                for (int i = 0; i < count && (pending = pollWindowUpdate()) != null; i++) {
                    noLockWrite(pending);
                }
                noLockWrite(frame);
            } catch (Throwable t) {
                failWindowUpdates(t);
                throw t;
            } finally {
                streamLock.unlock();
            }
            return;
        }
        scheduleWindowUpdateWrite();
    }

    @Override
    public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
        writeData(frame, flowControl, NO_OP);
    }

    /**
     * Write a frame with flow control and notify when an {@code END_STREAM}
     * data frame has been written and accounted for.
     *
     * @param frame                   data frame
     * @param flowControl             outbound flow control
     * @param onEndStreamFrameWritten action to run after the {@code END_STREAM}
     *                                data frame is written and accounted for
     * @return number of bytes written
     */
    public int writeData(Http2FrameData frame, FlowControl.Outbound flowControl, Runnable onEndStreamFrameWritten) {
        Objects.requireNonNull(frame);
        Objects.requireNonNull(flowControl);
        Objects.requireNonNull(onEndStreamFrameWritten);
        int written = 0;
        for (Http2FrameData f : frame.split(flowControl.maxFrameSize())) {
            written += splitAndWrite(f, flowControl, onEndStreamFrameWritten);
        }
        return written;
    }

    @Override
    public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags, FlowControl.Outbound flowControl) {
        return writeHeaders(headers, streamId, flags, flowControl, NO_OP);
    }

    /**
     * Write headers and notify when an {@code END_STREAM} headers frame has
     * been written.
     *
     * @param headers                   headers
     * @param streamId                  stream ID
     * @param flags                     flags to use
     * @param flowControl               flow control
     * @param onEndStreamFrameWritten   action to run after the {@code END_STREAM}
     *                                  headers frame is written
     * @return number of bytes written
     */
    public int writeHeaders(Http2Headers headers,
                            int streamId,
                            Http2Flag.HeaderFlags flags,
                            FlowControl.Outbound flowControl,
                            Runnable onEndStreamFrameWritten) {
        Objects.requireNonNull(headers);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(flowControl);
        Objects.requireNonNull(onEndStreamFrameWritten);
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
                if (flags.endOfStream()) {
                    onEndStreamFrameWritten.run();
                }
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
            if (flags.endOfStream()) {
                onEndStreamFrameWritten.run();
            }
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
        return writeHeaders(headers, streamId, flags, dataFrame, flowControl, NO_OP);
    }

    /**
     * Write headers and entity, and notify when an {@code END_STREAM} data
     * frame has been written and accounted for.
     *
     * @param headers                   headers
     * @param streamId                  stream ID
     * @param flags                     header flags
     * @param dataFrame                 data frame
     * @param flowControl               flow control
     * @param onEndStreamFrameWritten   action to run after the {@code END_STREAM}
     *                                  data frame is written and accounted for
     * @return number of bytes written
     */
    public int writeHeaders(Http2Headers headers,
                            int streamId,
                            Http2Flag.HeaderFlags flags,
                            Http2FrameData dataFrame,
                            FlowControl.Outbound flowControl,
                            Runnable onEndStreamFrameWritten) {
        Objects.requireNonNull(dataFrame);
        Objects.requireNonNull(onEndStreamFrameWritten);
        // Executed on stream thread
        int bytesWritten = 0;
        bytesWritten += writeHeaders(headers, streamId, flags, flowControl);
        writeData(dataFrame, flowControl, onEndStreamFrameWritten);
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
            throwIfWindowUpdateFailed();
            if (frame.header().type() == Http2FrameType.RST_STREAM) {
                Http2FrameData windowUpdate;
                while ((windowUpdate = pollWindowUpdate(frame.header().streamId())) != null) {
                    noLockWrite(windowUpdate);
                }
            }
            noLockWrite(frame);
        } catch (Throwable t) {
            if (frame.header().type() == Http2FrameType.RST_STREAM) {
                failWindowUpdates(t);
            }
            throw t;
        } finally {
            streamLock.unlock();
        }
    }

    private void scheduleWindowUpdateWrite() {
        if (!hasPendingWindowUpdates() || !windowUpdateWriteScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            Thread.ofVirtual()
                    .name("helidon-http2-window-update-" + ctx.socketId())
                    .start(this::drainWindowUpdates);
        } catch (RuntimeException | Error e) {
            windowUpdateWriteScheduled.set(false);
            failWindowUpdates(e);
            throw e;
        }
    }

    private void drainWindowUpdates() {
        try {
            lock();
            try {
                int count = pendingWindowUpdateCount();
                Http2FrameData frame;
                for (int i = 0; i < count && (frame = pollWindowUpdate()) != null; i++) {
                    noLockWrite(frame);
                }
            } finally {
                streamLock.unlock();
            }
        } catch (Throwable t) {
            failWindowUpdates(t);
            try {
                writer.close();
            } catch (Throwable closeFailure) {
                t.addSuppressed(closeFailure);
            }
        } finally {
            windowUpdateWriteScheduled.set(false);
            if (hasPendingWindowUpdates()) {
                scheduleWindowUpdateWrite();
            }
        }
    }

    private Http2FrameData pollWindowUpdate() {
        windowUpdateLock.lock();
        try {
            if (pendingWindowUpdates.isEmpty()) {
                return null;
            }
            int streamId = pendingWindowUpdates.keySet().iterator().next();
            return pollWindowUpdateNoLock(streamId);
        } finally {
            windowUpdateLock.unlock();
        }
    }

    private Http2FrameData pollWindowUpdate(int streamId) {
        windowUpdateLock.lock();
        try {
            return pollWindowUpdateNoLock(streamId);
        } finally {
            windowUpdateLock.unlock();
        }
    }

    private Http2FrameData pollWindowUpdateNoLock(int streamId) {
        Long increment = pendingWindowUpdates.remove(streamId);
        if (increment == null) {
            return null;
        }
        int writeIncrement = (int) Math.min(increment, Integer.MAX_VALUE);
        if (increment > Integer.MAX_VALUE) {
            pendingWindowUpdates.put(streamId, increment - Integer.MAX_VALUE);
        }
        BufferData data = BufferData.create(4);
        data.writeInt32(writeIncrement);
        Http2FrameHeader header = Http2FrameHeader.create(4,
                                                           Http2FrameTypes.WINDOW_UPDATE,
                                                           Http2Flag.NoFlags.create(),
                                                           streamId);
        return new Http2FrameData(header, data);
    }

    private boolean hasPendingWindowUpdates() {
        windowUpdateLock.lock();
        try {
            return !pendingWindowUpdates.isEmpty();
        } finally {
            windowUpdateLock.unlock();
        }
    }

    private int pendingWindowUpdateCount() {
        windowUpdateLock.lock();
        try {
            return pendingWindowUpdates.size();
        } finally {
            windowUpdateLock.unlock();
        }
    }

    private void failWindowUpdates(Throwable failure) {
        windowUpdateFailure.compareAndSet(null, failure);
        clearWindowUpdates();
    }

    private void clearWindowUpdates() {
        windowUpdateLock.lock();
        try {
            pendingWindowUpdates.clear();
        } finally {
            windowUpdateLock.unlock();
        }
    }

    private void throwIfWindowUpdateFailed() {
        Throwable failure = windowUpdateFailure.get();
        if (failure != null) {
            throw new IllegalStateException("Failed to write HTTP/2 window update", failure);
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

    private int splitAndWrite(Http2FrameData frame,
                              FlowControl.Outbound flowControl,
                              Runnable onEndStreamFrameWritten) {
        int written = 0;
        Http2FrameData currFrame = frame;
        while (true) {
            Http2FrameData[] splitFrames;
            lock();
            try {
                splitFrames = flowControl.cut(currFrame);
                if (splitFrames.length == 1) {
                    // windows are wide enough
                    written += noLockWriteData(currFrame, flowControl, onEndStreamFrameWritten);
                    break;
                } else if (splitFrames.length == 2) {
                    // write send-able part and block until window update with the rest
                    written += noLockWriteData(splitFrames[0], flowControl, onEndStreamFrameWritten);
                }
            } finally {
                streamLock.unlock();
            }
            if (splitFrames.length == 0) {
                // block until window update
                flowControl.blockTillUpdate();
            } else if (splitFrames.length == 2) {
                flowControl.blockTillUpdate();
                currFrame = splitFrames[1];
            }
        }
        return written;
    }

    private int noLockWriteData(Http2FrameData frame,
                                FlowControl.Outbound flowControl,
                                Runnable onEndStreamFrameWritten) {
        noLockWrite(frame);
        flowControl.decrementWindowSize(frame.header().length());
        if (frame.header().type() == Http2FrameType.DATA
                && frame.header().flags(Http2FrameTypes.DATA).endOfStream()) {
            onEndStreamFrameWritten.run();
        }
        return frameBytes(frame);
    }

    private static int frameBytes(Http2FrameData frame) {
        return frame.header().length() + Http2FrameHeader.LENGTH;
    }

}
