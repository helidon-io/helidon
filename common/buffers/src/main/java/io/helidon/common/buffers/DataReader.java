/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.buffers;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Data reader that can pull additional data.
 */
public class DataReader {
    private final Supplier<byte[]> bytesSupplier;
    private final boolean ignoreLoneEol;
    private Node head;
    private Node tail;
    private DataListener listener;
    private Object context;

    /**
     * Data reader from a supplier of bytes.
     *
     * @param bytesSupplier supplier that can be pulled for more data
     */
    public DataReader(Supplier<byte[]> bytesSupplier) {
        this.ignoreLoneEol = false;
        this.bytesSupplier = bytesSupplier;
        // we cannot block until data is actually ready to be consumed
        this.head = new Node(BufferData.EMPTY_BYTES);
        this.tail = this.head;
    }

    /**
     * Data reader from a supplier of bytes.
     *
     * @param bytesSupplier supplier that can be pulled for more data
     * @param ignoreLoneEol ignore LF without CR and CR without LF
     */
    public DataReader(Supplier<byte[]> bytesSupplier, boolean ignoreLoneEol) {
        this.ignoreLoneEol = ignoreLoneEol;
        this.bytesSupplier = bytesSupplier;
        // we cannot block until data is actually ready to be consumed
        this.head = new Node(BufferData.EMPTY_BYTES);
        this.tail = this.head;
    }

    /**
     * Number of bytes available in the currently pulled data.
     *
     * @return number of bytes available
     */
    public int available() {
        int a = 0;
        for (Node n = head; n != null; n = n.next) {
            a += n.available();
        }
        return a;
    }

    /**
     * Pull next data.
     */
    public void pullData() {
        byte[] bytes = bytesSupplier.get();
        if (bytes == null) {
            throw new InsufficientDataAvailableException();
        }
        Node n = new Node(bytes);
        tail.next = n;
        tail = n;
    }

    /**
     * Skip n bytes.
     *
     * @param lenToSkip number of bytes to skip (must be less or equal to current capacity)
     */
    public void skip(int lenToSkip) {
        while (lenToSkip > 0) {
            ensureAvailable();
            lenToSkip = head.skip(lenToSkip);
        }
    }

    /**
     * Ensure we have at least one byte available.
     */
    // remove consumed head of the list
    // make sure that head has available
    // may block to read
    public void ensureAvailable() {
        while (!head.hasAvailable()) {
            if (head.next == null) {
                pullData();
            }
            head = head.next;
        }
    }

    /**
     * Read 1 byte.
     *
     * @return next byte
     */
    public byte read() {
        ensureAvailable();
        return head.bytes[head.position++];
    }

    /**
     * Look at the next byte (does not modify position).
     *
     * @return next byte
     */
    public byte lookup() {
        ensureAvailable();
        return head.bytes[head.position];
    }

    /**
     * Does the data start with a new line (CRLF).
     *
     * @return whether the data starts with a new line (will pull data to have at least two bytes available)
     */
    public boolean startsWithNewLine() {
        ensureAvailable();
        byte[] bytes = head.bytes;
        int pos = head.position;
        if (bytes[pos] == Bytes.CR_BYTE && ((pos + 1 < bytes.length) ? bytes[pos + 1] : head.next().peek()) == Bytes.LF_BYTE) {
            return true;
        }
        return false;
    }

    /**
     * Does the current data start with the prefix.
     *
     * @param prefix prefix to find, will pull data to have at least prefix.length bytes available
     * @return whether the data starts with the provided prefix
     */
    public boolean startsWith(byte[] prefix) {
        ensureAvailable(); // we have at least 1 byte
        if (prefix.length <= head.available()) { // fast case
            return Arrays.equals(head.bytes, head.position, head.position + prefix.length, prefix, 0, prefix.length);
        } else {
            int offset = 0;
            int remaining = prefix.length;
            for (Node n = head; remaining > 0; n = n.next) {
                int toCmp = Math.min(remaining, n.available());
                if (!Arrays.equals(n.bytes, n.position, n.position + toCmp, prefix, offset, offset + toCmp)) {
                    return false;
                }
                remaining -= toCmp;
                offset += toCmp;
                if (remaining > 0 && n.next == null) {
                    pullData();
                }
            }
            return true;
        }
    }

    /**
     * Read next buffer.
     * Will read {@link #available()} number of bytes into a buffer and move position.
     *
     * @return buffer data wrapping the available bytes
     */
    public BufferData readBuffer() {
        ensureAvailable();
        int size = head.available();
        BufferData result = BufferData.create(head.bytes, head.position, size);
        skip(size);
        return result;
    }

    /**
     * Read next buffer of defined size. Will pull additional data if length is not available.
     * Will move position.
     *
     * @param length length of data to read
     * @return buffer data with the length requested
     */
    public BufferData readBuffer(int length) {
        BufferData data = getBuffer(length); // TODO optimization - merge getChunk and skip into one loop; if required
        skip(length);
        return data;
    }

    /**
     * Get the next buffer of the requested size without moving position.
     *
     * @param length bytes to read
     * @return buffer data with the length requested
     */
    public BufferData getBuffer(int length) {
        ensureAvailable(); // we have at least 1 byte
        if (length <= head.available()) { // fast case
            return new ReadOnlyArrayData(head.bytes, head.position, length);
        } else {
            List<BufferData> data = new ArrayList<>();
            int remaining = length;
            for (Node n = head; remaining > 0; n = n.next) {
                int toAdd = Math.min(remaining, n.available());
                data.add(new ReadOnlyArrayData(n.bytes, n.position, toAdd));
                remaining -= toAdd;
                if (remaining > 0 && n.next == null) {
                    pullData();
                }
            }
            return BufferData.create(data);
        }
    }

    /**
     * Read the next {@code len} bytes as a {@link LazyString}.
     * This should be used for example for headers, where we want to materialize the string only when needed.
     *
     * @param charset character set to use
     * @param len     number of bytes of the string
     * @return lazy string
     */
    public LazyString readLazyString(Charset charset, int len) {
        ensureAvailable(); // we have at least 1 byte
        if (len <= head.available()) { // fast case
            LazyString s = new LazyString(head.bytes, head.position, len, charset);
            head.position += len;
            return s;
        } else {
            byte[] b = new byte[len];
            int remaining = len;
            for (Node n = head; remaining > 0; n = n.next) {
                ensureAvailable();
                int toAdd = Math.min(remaining, n.available());
                System.arraycopy(n.bytes, n.position, b, len - remaining, toAdd);
                remaining -= toAdd;
                n.position += toAdd;
                if (remaining > 0 && n.next == null) {
                    pullData();
                }
            }
            return new LazyString(b, charset);
        }
    }

    /**
     * Read ascii string.
     *
     * @param len number of bytes of the string
     * @return string value
     */
    public String readAsciiString(int len) {
        ensureAvailable(); // we have at least 1 byte
        if (len <= head.available()) { // fast case
            String s = new String(head.bytes, head.position, len, StandardCharsets.US_ASCII);
            head.position += len;
            return s;
        } else {
            byte[] b = new byte[len];
            int remaining = len;
            for (Node n = head; remaining > 0; n = n.next) {
                ensureAvailable();
                int toAdd = Math.min(remaining, n.available());
                System.arraycopy(n.bytes, n.position, b, len - remaining, toAdd);
                remaining -= toAdd;
                n.position += toAdd;
                if (remaining > 0 && n.next == null) {
                    pullData();
                }
            }
            return new String(b, StandardCharsets.US_ASCII);
        }
    }

    /**
     * Read an ascii string until new line.
     *
     * @return string with the next line
     * @throws io.helidon.common.buffers.DataReader.IncorrectNewLineException when new line cannot be found
     */
    public String readLine() throws IncorrectNewLineException {
        int i = findNewLine(Integer.MAX_VALUE);
        String s = readAsciiString(i);
        skip(2);
        return s;
    }

    /**
     * Find the byte or next new line.
     *
     * @param b   - byte to find
     * @param max - search limit
     * @return i &gt; 0 - index;
     *         i == max - not found;
     *         i &lt; 0 - new line found at  (-i-1) position
     * @throws io.helidon.common.buffers.DataReader.IncorrectNewLineException in case new line was incorrect (such as CR not before LF)
     */
    public int findOrNewLine(byte b, int max) throws IncorrectNewLineException {
        ensureAvailable();
        int idx = 0;
        Node n = head;
        while (true) {
            byte[] barr = n.bytes;
            for (int i = n.position; i < barr.length && idx < max; i++, idx++) {
                if (barr[i] == Bytes.LF_BYTE && !ignoreLoneEol) {
                    throw new IncorrectNewLineException("Found LF (" + idx + ") without preceding CR. :\n" + this.debugDataHex());
                } else if (barr[i] == Bytes.CR_BYTE) {
                    byte nextByte;
                    if (i + 1 < barr.length) {
                        nextByte = barr[i + 1];
                    } else {
                        nextByte = n.next().peek();
                    }
                    if (nextByte == Bytes.LF_BYTE) {
                        return -idx - 1;
                    }
                    if (!ignoreLoneEol) {
                        throw new IncorrectNewLineException("Found CR (" + idx
                                                                    + ") without following LF. :\n" + this.debugDataHex());
                    }
                } else if (barr[i] == b) {
                    return idx;
                }
            }
            if (idx == max) {
                return max;
            }
            n = n.next();
        }
    }

    /**
     * Debug data as a hex string.
     *
     * @return hex string, including headers
     */
    public String debugDataHex() {
        return getBuffer(available()).debugDataHex(true);
    }

    /**
     * Find new line with the next n bytes.
     *
     * @param max length to search
     * @return index of the new line, or max if not found
     * @throws io.helidon.common.buffers.DataReader.IncorrectNewLineException in case there is a LF without CR,
     *              or CR without a LF
     */
    public int findNewLine(int max) throws IncorrectNewLineException {
        ensureAvailable();
        int idx = 0;
        Node n = head;
        while (true) {
            byte[] barr = n.bytes;
            for (int i = n.position; i < barr.length && idx < max; i++, idx++) {
                if (barr[i] == Bytes.LF_BYTE && !ignoreLoneEol) {
                    throw new IncorrectNewLineException("Found LF (" + idx + ") without preceding CR. :\n" + this.debugDataHex());
                } else if (barr[i] == Bytes.CR_BYTE) {
                    byte nextByte;
                    if (i + 1 < barr.length) {
                        nextByte = barr[i + 1];
                    } else {
                        nextByte = n.next().peek();
                    }
                    if (nextByte == Bytes.LF_BYTE) {
                        return idx;
                    }
                    if (!ignoreLoneEol) {
                        throw new IncorrectNewLineException("Found CR (" + idx
                                                                    + ") without following LF. :\n" + this.debugDataHex());
                    }
                }
            }
            if (idx == max) {
                return max;
            }
            n = n.next();
        }
    }

    /**
     * Configure data listener.
     *
     * @param listener listener to write information to
     * @param context  context
     * @param <T>      type of the context
     */
    public <T> void listener(DataListener<T> listener, T context) {
        this.listener = listener;
        this.context = context;
    }

    /**
     * New line not valid.
     */
    public static class IncorrectNewLineException extends RuntimeException {
        /**
         * Incorrect new line.
         *
         * @param message descriptive message
         */
        public IncorrectNewLineException(String message) {
            super(message);
        }
    }

    /**
     * Not enough data available to finish the requested operation.
     */
    public static class InsufficientDataAvailableException extends RuntimeException {
        /**
         * Create a new instance. This exception does not have any other constructors.
         */
        public InsufficientDataAvailableException() {
        }
    }

    private class Node {
        private final byte[] bytes;
        private int position;
        private Node next;

        Node(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public String toString() {
            return position + " of " + Arrays.toString(bytes);
        }

        int available() {
            return bytes.length - position;
        }

        boolean hasAvailable() {
            return position < bytes.length;
        }

        /*
         * returns number of skipped bytes
         */
        int skip(int lenToSkip) {
            int newPos = position + lenToSkip;
            if (newPos <= bytes.length) {
                position = newPos;
                return 0;
            } else {
                lenToSkip -= (bytes.length - position);
                position = bytes.length;
                return lenToSkip;
            }
        }

        Node next() {
            if (this.next == null) {
                assert this == tail;
                pullData();
                assert this.next != null;
            }
            return this.next;
        }

        byte peek() {
            return bytes[position];
        }
    }
}
