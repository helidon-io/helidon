/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.media.common;

import java.io.Writer;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A character buffer that acts as a {@link Writer} and uses cached {@code char[]} arrays.
 * <p>
 * Instances of this class are <em>not</em> thread-safe.
 */
public class CharBuffer extends Writer {
    private static final Pool POOL = new Pool(8192);
    private char[] buffer;
    private int count;

    /**
     * Constructor.
     */
    public CharBuffer() {
        buffer = POOL.acquire();
        count = 0;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        if ((off < 0) || (off > cbuf.length) || (len < 0) || ((off + len) - cbuf.length > 0)) {
            throw new IndexOutOfBoundsException();
        }
        ensureCapacity(count + len);
        System.arraycopy(cbuf, off, buffer, count, len);
        count += len;
    }

    /**
     * Returns the number of characters written.
     *
     * @return The count.
     */
    int size() {
        return count;
    }

    /**
     * Returns the content encoded into the given character set.
     *
     * @param charset The character set.
     * @return The encoded content.
     */
    public ByteBuffer encode(Charset charset) {
        final ByteBuffer result = charset.encode(java.nio.CharBuffer.wrap(buffer, 0, count));
        POOL.release(buffer);
        buffer = null;
        return result;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity - buffer.length > 0) {
            grow(minCapacity);
        }
    }

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private void grow(int minCapacity) {
        int oldCapacity = buffer.length;
        int newCapacity = oldCapacity << 1;
        if (newCapacity - minCapacity < 0) {
            newCapacity = minCapacity;
        }
        if (newCapacity - MAX_ARRAY_SIZE > 0) {
            newCapacity = hugeCapacity(minCapacity);
        }
        buffer = Arrays.copyOf(buffer, newCapacity);
    }

    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) {
            throw new OutOfMemoryError();
        }
        return (minCapacity > MAX_ARRAY_SIZE)
               ? Integer.MAX_VALUE
               : MAX_ARRAY_SIZE;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    private static class Pool {
        private volatile SoftReference<ConcurrentLinkedQueue<char[]>> reference;
        private final int arraySize;

        /**
         * Constructor.
         *
         * @param arraySize The size array to allocate when required.
         */
        Pool(final int arraySize) {
            this.arraySize = arraySize;
        }

        /**
         * Acquires an array from the pool if available or creates a new one.
         *
         * @return The array.
         */
        char[] acquire() {
            final char[] array = getQueue().poll();
            return array == null ? new char[arraySize] : array;
        }

        /**
         * Returns an array back to the pool.
         *
         * @param array The array to return.
         */
        void release(final char[] array) {
            getQueue().offer(array);
        }

        private ConcurrentLinkedQueue<char[]> getQueue() {
            final SoftReference<ConcurrentLinkedQueue<char[]>> reference = this.reference;
            if (reference != null) {
                final ConcurrentLinkedQueue<char[]> queue = reference.get();
                if (queue != null) {
                    return queue;
                }
            }
            final ConcurrentLinkedQueue<char[]> queue = new ConcurrentLinkedQueue<>();
            this.reference = new SoftReference<>(queue);
            return queue;
        }
    }
}
