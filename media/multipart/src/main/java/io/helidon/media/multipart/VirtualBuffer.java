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
package io.helidon.media.multipart;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * A virtual buffer to work against multiple consecutive {@link ByteBuffer}.
 * This allows to parse through multiple buffers until enough data is available
 * to be consumed without any copying.
 * <p>
 * Data can be consumed using {@link #slice(int, int)}.
 * </p>
 * <p>
 * Buffers are accumulated using {@link #offer(ByteBuffer, int)}, the buffers before the specified offset are
 * automatically removed discarded.
 * </p>
 */
final class VirtualBuffer {

    private static final int DEFAULT_CAPACITY = 8;

    private ByteBuffer[] buffers;
    private int[] bufferIds;
    private int count;
    private int startIndex;
    private int endIndex;
    private int voffset;
    private int vlength;
    private int nextId;

    /**
     * Create a new virtual buffer.
     * @param capacity the buffer size
     */
    private VirtualBuffer(int initialCapacity) {
        bufferIds = new int[initialCapacity];
        buffers = new ByteBuffer[initialCapacity];
        voffset = 0;
        vlength = 0;
        nextId = 0;
        count = 0;
        startIndex = 0;
        endIndex = 0;
    }

    /**
     * Create a new virtual buffer.
     * @param capacity the buffer size
     */
    VirtualBuffer() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Get the buffer length.
     * @return buffer length
     */
    int length() {
        return vlength;
    }

    /**
     * Count the number of underlying buffers.
     * @return buffer count
     */
    int buffersCount() {
        return count;
    }

    /**
     * Remove all the underlying {@link ByteBuffer}.
     */
    void clear() {
        Arrays.fill(buffers, null);
        Arrays.fill(bufferIds, 0);
        voffset = 0;
        vlength = 0;
        nextId = 0;
        count = 0;
        startIndex = 0;
        endIndex = 0;
    }

    /**
     * Add a new buffer and discard all the buffers before the specified offset.
     *
     * @param buffer the byte buffer to add
     * @param newOffset the new offset position
     * @throws IllegalStateException if buffer corresponding to the new offset
     * position cannot be found
     * @throws IllegalArgumentException if offset is negative
     * @return buffer id
     */
    int offer(ByteBuffer buffer, int newOffset) {
        if (newOffset < 0) {
            throw new IllegalArgumentException("Negative offset: " + newOffset);
        }
        if (buffers.length == count) {
            doubleCapacity();
        }
        buffers[endIndex] = buffer.asReadOnlyBuffer();
        bufferIds[endIndex] = ++nextId;
        if (nextId == Integer.MAX_VALUE) {
            nextId = 0;
        }
        count++;
        endIndex = nextBufferIndex(endIndex);
        vlength = vlength + buffer.limit() - newOffset;
        int pos = 0; // absolute position for current buffer start
        int off = voffset + newOffset; // new absolute offset with current buffers
        boolean found = false;
        for (int i = startIndex; isBufferIndex(i) && pos <= off; i = nextBufferIndex(i)) {
            int nextPosition = pos + buffers[i].limit();
            if (nextPosition >= off) {
                voffset = off - pos;
                found = true;
                break;
            }
            pos = nextPosition;
            // remove
            buffers[i] = null;
            bufferIds[i] = 0;
            count--;
            startIndex = nextBufferIndex(startIndex);
        }
        if (!found) {
            throw new IllegalStateException("Unable to find new absolute position for offset: " + newOffset);
        }
        return nextId;
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
        if (index < 0 || index >= vlength) {
            throw new IndexOutOfBoundsException("Invalid index: " + index);
        }
        int pos = 0; // absolute position for current buffer start
        int off = voffset + index; // actual offset
        for (int i = startIndex; isBufferIndex(i); i = nextBufferIndex(i)) {
            ByteBuffer buffer = buffers[i];
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
        int nbytes = 0; // written bytes count
        int off = voffset + begin; // actual offset
        for (int i = startIndex; isBufferIndex(i); i = nextBufferIndex(i)) {
            ByteBuffer buffer = buffers[i];
            int nextPos = pos + buffer.limit();
            int index;
            while (nbytes < len) {
                index = off + nbytes;
                if (index >= nextPos) {
                    break;
                }
                dst[nbytes] = buffer.get(index - pos);
                nbytes++;
            }
            pos = nextPos;
        }
        if (nbytes < len - 1) {
            throw new BufferUnderflowException();
        }
        return dst;
    }

    /**
     * Create a read-only slice with the specified range.
     *
     * @param begin begin index
     * @param end end index
     * @return read only byte buffers for the slice mapped to their original buffer ids
     * @throws IndexOutOfBoundsException - If the preconditions on the offset
     * and length parameters do not hold
     */
    LinkedList<BufferEntry> slice(int begin, int end) {
        checkBounds(begin, end);
        LinkedList<BufferEntry> slices = new LinkedList<>();
        int len = end - begin;
        int pos = 0; // absolute position for current buffer start
        int nslices = 0; // sliced bytes count
        int off = voffset + begin; // actual offset
        for (int i = startIndex; isBufferIndex(i); i = nextBufferIndex(i)) {
            ByteBuffer buffer = buffers[i];
            int limit = buffer.limit();
            int nextPos = pos + limit;
            if (off < nextPos) {
                // in-range
                ByteBuffer slice = buffer.asReadOnlyBuffer();
                int index = off < pos ? 0 : off - pos;
                slice.position(index);
                slices.add(new BufferEntry(slice, bufferIds[i]));
                if (off + len <= nextPos) {
                    // last slice
                    slice.limit(index + len - nslices);
                    return slices;
                } else {
                    nslices += (limit - index);
                }
            }
            pos = nextPos;
        }
        throw new BufferUnderflowException();
    }

    /**
     * A virtual buffer entry.
     */
    static final class BufferEntry {

        private final ByteBuffer buffer;
        private final int id;

        private BufferEntry(ByteBuffer buffer, int id) {
            this.buffer = buffer;
            this.id = id;
        }

        /**
         * Get the byte buffer.
         * @return ByteBuffer
         */
        ByteBuffer buffer() {
            return buffer;
        }

        /**
         * Get the mapped id.
         * @return id
         */
        int id() {
            return id;
        }
    }

    /**
     * Double the size of the underlying arrays.
     */
    private void doubleCapacity() {
        ByteBuffer[] newBuffers = new ByteBuffer[buffers.length * 2];
        int[] newIds = new int[buffers.length * 2];
        int count1 = count - (startIndex + 1);
        int count2 = count - count1;
        System.arraycopy(buffers, startIndex, newBuffers, 0, count1);
        System.arraycopy(buffers, 0, newBuffers, count1, count2);
        System.arraycopy(bufferIds, startIndex, newIds, 0, count1);
        System.arraycopy(bufferIds, 0, newIds, count1, count2);
        buffers = newBuffers;
        bufferIds = newIds;
        startIndex = 0;
        endIndex = count - 1;
    }

    /**
     * Test if the given index is a valid buffer index.
     * @param index index to test
     * @return {@code true} if a valid index
     */
    private boolean isBufferIndex(int index) {
        if (endIndex > startIndex) {
            return index < endIndex && index >= startIndex;
        }
        return index < endIndex || index >= startIndex;
    }

    /**
     * Compute the next buffer index.
     * @param index current index
     * @return next circular index
     */
    private int nextBufferIndex(int index) {
        if (index + 1 == buffers.length) {
            return 0;
        } else {
            return index + 1;
        }
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
        if (!(begin >= 0 && begin < vlength)
                || !(end > 0 && end <= vlength)
                || begin > end) {
            throw new IndexOutOfBoundsException("Invalid range, begin=" + begin + ", end=" + end);
        }
    }
}
