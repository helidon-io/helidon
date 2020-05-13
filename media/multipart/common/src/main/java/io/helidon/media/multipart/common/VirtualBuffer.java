/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.multipart.common;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A virtual buffer to work against multiple consecutive {@link ByteBuffer}.
 * This allows to parse through multiple buffers until enough data is available
 * to be consumed without any copying.
 * <p>
 * Data can be consumed using {@link #slice(int, int)}.
 * </p>
 * <p>
 * Buffers are accumulated in a {@link LinkedList} using
 * {@link #offer(ByteBuffer, int)}, the buffers before the specified offset are
 * automatically removed from the list.
 * </p>
 */
final class VirtualBuffer {

    private final LinkedList<ByteBuffer> buffers;
    private int offset;
    private int length;

    /**
     * Create a new virtual buffer.
     */
    VirtualBuffer() {
        offset = 0;
        length = 0;
        buffers = new LinkedList<>();
    }

    /**
     * Get the buffer length.
     * @return buffer length
     */
    int length() {
        return length;
    }

    /**
     * Count the number of underlying buffers.
     * @return buffer count
     */
    int buffersCount() {
        return buffers.size();
    }

    /**
     * Add a new buffer and discard all the buffers before the specified offset.
     *
     * @param buffer the byte buffer to add
     * @param newOffset the new offset position
     * @throws IllegalStateException if buffer corresponding to the new offset
     * position cannot be found
     * @throws IllegalArgumentException if offset is negative
     */
    void offer(ByteBuffer buffer, int newOffset) {
        if (newOffset < 0) {
            throw new IllegalArgumentException("Negative offset: " + newOffset);
        }
        buffers.offer(buffer.asReadOnlyBuffer());
        length = length + buffer.limit() - newOffset;
        Iterator<ByteBuffer> it = buffers.iterator();
        int pos = 0; // absolute position for current buffer start
        int off = offset + newOffset; // new absolute offset with current buffers
        boolean found = false;
        while (it.hasNext() && pos <= off) {
            int nextPosition = pos + it.next().limit();
            if (nextPosition >= off) {
                offset = off - pos;
                found = true;
                break;
            }
            pos = nextPosition;
            it.remove();
        }
        if (!found) {
            throw new IllegalStateException("Unable to find new absolute position for offset: " + newOffset);
        }
    }

    /**
     * Get a single byte.
     *
     * @param index virtual index
     * @return byte
     * @throws IndexOutOfBoundsException - If the preconditions on the offset
     * and length parameters do not hold
     */
    byte getByte(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("Invalid index: " + index);
        }
        int pos = 0; // absolute position for current buffer start
        int off = offset + index; // actual offset
        for (ByteBuffer buffer : buffers) {
            int nextPos = pos + buffer.limit();
            if (nextPos > off) {
                return buffer.get(off - pos);
            }
            pos = nextPos;
        }
        // should not be reachable
        throw new IllegalStateException("End of virtual buffer");
    }

    /**
     * Get bytes.
     * The data is always copied.
     *
     * @param begin begin virtual index
     * @param len number of bytes to return starting from the begin index
     * @return byte array
     * @throws IndexOutOfBoundsException - If the preconditions on the offset
     * and length parameters do not hold
     * @throws BufferUnderflowException - If the buffer's current position is
     * not smaller than its limit
     */
    byte[] getBytes(int begin, int len) {
        checkBounds(begin, begin + len - 1);
        byte[] dst = new byte[len];
        int pos = 0; // absolute position for current buffer start
        int count = 0; // written bytes count
        int off = offset + begin; // actual offset
        for (ByteBuffer buffer : buffers) {
            int nextPos = pos + buffer.limit();
            int index;
            while (count < len) {
                index = off + count;
                if (index >= nextPos) {
                    break;
                }
                dst[count] = buffer.get(index - pos);
                count++;
            }
            pos = nextPos;
        }
        if (count < len - 1) {
            throw new BufferUnderflowException();
        }
        return dst;
    }

    /**
     * Create a read-only slice with the specified range.
     *
     * @param begin begin index
     * @param end end index
     * @return ByteBuffer
     * @throws IndexOutOfBoundsException - If the preconditions on the offset
     * and length parameters do not hold
     */
    List<ByteBuffer> slice(int begin, int end) {
        checkBounds(begin, end);
        List<ByteBuffer> slices = new ArrayList<>();
        int len = end - begin;
        int pos = 0; // absolute position for current buffer start
        int count = 0; // sliced bytes count
        int off = offset + begin; // actual offset
        for (ByteBuffer buffer : buffers) {
            int limit = buffer.limit();
            int nextPos = pos + limit;
            if (off < nextPos) {
                // in-range
                ByteBuffer slice = buffer.asReadOnlyBuffer();
                int index = off < pos ? 0 : off - pos;
                slice.position(index);
                slices.add(slice);
                if (off + len <= nextPos) {
                    // last slice
                    slice.limit(index + len - count);
                    return slices;
                } else {
                    count += (limit - index);
                }
            }
            pos = nextPos;
        }
        throw new BufferUnderflowException();
    }

    /**
     * Check the given inclusive range and throw an exception if the range is
     * invalid.
     *
     * @param begin range start
     * @param end range end
     * @throws IndexOutOfBoundsException - If the preconditions on the offset
     * and length parameters do not hold
     */
    private void checkBounds(int begin, int end) {
        if (!(begin >= 0 && begin < length)
                || !(end > 0 && end <= length)
                || begin > end) {
            throw new IndexOutOfBoundsException("Invalid range, begin=" + begin + ", end=" + end);
        }
    }

    /**
     * Remove all the underlying {@link ByteBuffer}.
     */
    void close() {
        buffers.clear();
    }
}
