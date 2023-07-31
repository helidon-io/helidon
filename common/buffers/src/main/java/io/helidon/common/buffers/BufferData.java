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

package io.helidon.common.buffers;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Wrapper around a byte array.
 */
public interface BufferData {
    /**
     * Empty byte array.
     */
    byte[] EMPTY_BYTES = new byte[0];

    /**
     * Fixed size buffer data.
     *
     * @param length length of the underlying buffer
     * @return new byte array buffer data
     */
    static BufferData create(int length) {
        return new FixedBufferData(length);
    }

    /**
     * Fixed size buffer data fully written.
     * @param bytes byte array
     * @return new byte array buffer data with write position moved to the last byte
     */
    static BufferData create(byte[] bytes) {
        return new FixedBufferData(bytes);
    }

    /**
     * Fixed size buffer data fully written.
     *
     * @param bytes byte array
     * @param position position within the byte array
     * @param length number of bytes from position that contain the data
     * @return new byte array buffer data read to be read
     */
    static BufferData create(byte[] bytes, int position, int length) {
        return new FixedBufferData(bytes, position, length);
    }

    /**
     * Fixed size buffer data fully written.
     * @param bytes byte array
     * @param offset offset within the byte array
     * @param length length
     * @return new byte array buffer data that are read only
     */
    static BufferData createReadOnly(byte[] bytes, int offset, int length) {
        return new ReadOnlyArrayData(bytes, offset, length);
    }

    /**
     * Growing buffer data.
     * The buffer will grow when necessary to accommodate more bytes.
     * @param initialLength initial buffer length
     * @return growing buffer data
     */
    static BufferData growing(int initialLength) {
        return new GrowingBufferData(initialLength);
    }

    /**
     * Buffer data mapping multiple buffers.
     * @param data data to wrap
     * @return composite buffer data
     */
    static BufferData create(BufferData... data) {
        if (data.length == 1) {
            return data[0];
        }
        if (data.length == 0) {
            return BufferUtil.EMPTY_BUFFER;
        }

        return new CompositeArrayBufferData(data);
    }

    /**
     * Composite buffer data that are mutable.
     * @return composite buffer
     */
    static CompositeBufferData createComposite() {
        return new CompositeListBufferData();
    }

    /**
     * Composite buffer data that are mutable with initial value.
     *
     * @param first first buffer to be added to the composite buffer
     * @return composite buffer
     */
    static CompositeBufferData createComposite(BufferData first) {
        return new CompositeListBufferData(first);
    }

    /**
     * Create composite buffer data from a list.
     *
     * @param data list of buffers to use
     * @return composite buffer
     */
    static BufferData create(List<BufferData> data) {
        if (data.size() == 1) {
            return data.iterator().next();
        }
        if (data.isEmpty()) {
            return BufferUtil.EMPTY_BUFFER;
        }
        return new CompositeListBufferData(data);
    }

    /**
     * Integer to binary string.
     *
     * @param value integer to print as binary
     * @return binary value
     */
    static String toBinaryString(int value) {
        return BufferUtil.toBinaryString(value);
    }

    /**
     * Get 31 bits of an integer, ignoring the first bit.
     * @param value integer value (32 bits)
     * @return integer value (31 bits)
     */
    static int toInt31(int value) {
        return value & 0x7FFFFFFF;
    }

    /**
     * Empty buffer data.
     * @return empty buffer
     */
    static BufferData empty() {
        return BufferUtil.EMPTY_BUFFER;
    }

    /**
     * Create buffer data from a string.
     * @param stringValue UTF-8 string
     * @return buffer data with bytes of the string
     */
    static BufferData create(String stringValue) {
        Objects.requireNonNull(stringValue);
        if (stringValue.isEmpty()) {
            return empty();
        }
        return create(stringValue.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reset read and write position of this buffer.
     * Does not impact length.
     *
     * @return this instance
     */
    BufferData reset();

    /**
     * Set read position to 0, so others may read the same data from the start.
     *
     * @return this instance
     */
    BufferData rewind();

    /**
     * Reset read and write position and make the buffer empty.
     * Growing buffer length is set to 0
     *
     * @return this instance
     */
    BufferData clear();

    /**
     * Write all available bytes of this buffer to the output stream.
     *
     * @param out output stream
     */
    void writeTo(OutputStream out);

    /**
     * Read bytes from the input stream.
     * Reads at least 1 byte.
     * @param in input stream
     * @return number of bytes read, -1 if the input stream is finished
     */
    int readFrom(InputStream in);

    /**
     * Read a single byte from this buffer.
     * @return next byte
     */
    int read();

    /**
     * Read bytes from this buffer into the provided buffer.
     * @param bytes buffer to write to
     * @return number of bytes actually written to the provided buffer
     */
    default int read(byte[] bytes) {
        return read(bytes, 0, bytes.length);
    }

    /**
     * Read bytes from this buffer into the provided buffer.
     *
     * @param bytes buffer to write to
     * @param position position in the buffer
     * @param length length that can be written
     * @return actual number of bytes written
     */
    int read(byte[] bytes, int position, int length);

    /**
     * Do an operation on each byte in this buffer.
     *
     * @param length number of bytes to go through
     * @param consumer function that consumes a byte and returns {@code true} if we should proceed to the next byte
     */
    default void forEach(int length, Function<Byte, Boolean> consumer) {
        for (int i = 0; i < length; i++) {
            if (!consumer.apply((byte) read())) {
                break;
            }
        }
    }

    /**
     * Read a UTF-8 string from this buffer.
     *
     * @param length number of bytes to read
     * @return string from the bytes
     */
    default String readString(int length) {
        return readString(length, StandardCharsets.UTF_8);
    }

    /**
     * Read a string from this buffer.
     *
     * @param length number of bytes to read
     * @param charset charset of the string, such as {@link java.nio.charset.StandardCharsets#UTF_8} or {@link java.nio.charset.StandardCharsets#US_ASCII}
     * @return string from the bytes
     */
    String readString(int length, Charset charset);

    /**
     * Whether this buffer is fully consumed (all available bytes were read).
     * @return if this buffer is consumed
     */
    boolean consumed();

    /**
     * Read 16-bit integer.
     *
     * @return integer from the next 2 bytes
     */
    default int readInt16() {
        int ch1 = read();
        int ch2 = read();

        return (ch1 << 8) + ch2;
    }

    /**
     * Read a 24-bit integer.
     *
     * @return integer from the next 3 bytes
     */
    default int readInt24() {
        int ch1 = read();
        int ch2 = read();
        int ch3 = read();

        return (ch1 << 16) + (ch2 << 8) + ch3;
    }

    /**
     * Read 32-bit integer.
     *
     * @return integer from the next 4 bytes
     */
    default int readInt32() {
        int ch1 = read();
        int ch2 = read();
        int ch3 = read();
        int ch4 = read();

        return (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4;
    }

    /**
     * Read 32-bit unsigned integer (must be represented as a long, as Java integer is 32-bit signed).
     *
     * @return long from the next 4 bytes
     */
    default long readUnsignedInt32() {
        int ch1 = read();
        int ch2 = read();
        int ch3 = read();
        int ch4 = read();

        return ((long) ch1 << 24) + ((long) ch2 << 16) + ((long) ch3 << 8) + ch4;
    }

    /**
     * Read 64-bit long.
     *
     * @return long from the next 8 bytes
     */
    default long readLong() {
        return ((long) read() << 56)
                + ((long) (read() & 255) << 48)
                + ((long) (read() & 255) << 40)
                + ((long) (read() & 255) << 32)
                + ((long) (read() & 255) << 24)
                + ((read() & 255) << 16)
                + ((read() & 255) << 8)
                + ((read() & 255));
    }

    /**
     * Write 8-bit integer.
     *
     * @param number integer to write as a single byte
     * @return this buffer
     */
    default BufferData writeInt8(int number) {
        return write(number);
    }

    /**
     * Write 16-bit integer.
     *
     * @param number integer to write as 2 bytes
     * @return this buffer
     */
    default BufferData writeInt16(int number) {
        write((byte) (number >>> 8));
        write((byte) number);
        return this;
    }

    /**
     * Write 24-bit integer.
     *
     * @param number integer to write as 3 bytes
     * @return this buffer
     */
    default BufferData writeInt24(int number) {
        write((byte) (number >>> 16));
        write((byte) (number >>> 8));
        write((byte) number);
        return this;
    }

    /**
     * Write 32-bit integer.
     *
     * @param number integer to write as 4 bytes
     * @return this buffer
     */
    default BufferData writeInt32(int number) {
        write((byte) (number >>> 24));
        write((byte) (number >>> 16));
        write((byte) (number >>> 8));
        write((byte) number);
        return this;
    }

    /**
     * Write 32-bit unsigned integer.
     *
     * @param number long to write as 4 bytes
     * @return this buffer
     */
    default BufferData writeUnsignedInt32(long number) {
        write((byte) (number >>> 24));
        write((byte) (number >>> 16));
        write((byte) (number >>> 8));
        write((byte) number);
        return this;
    }

    /**
     * Write a byte.
     *
     * @param value value
     * @return this buffer
     */
    BufferData write(int value);

    /**
     * Write n bytes from this buffer to the provided buffer.
     *
     * @param writeBuffer buffer to write to
     * @param length number of bytes to write
     * @return number of bytes actually written
     */
    int writeTo(ByteBuffer writeBuffer, int length);

    /**
     * Write the byte array to this buffer.
     *
     * @param bytes byte to write
     */
    default void write(byte[] bytes) {
        write(bytes, 0, bytes.length);
    }

    /**
     * Write the byte array to this buffer.
     *
     * @param bytes bytes to write
     * @param offset offset within the array
     * @param length number of bytes to write (from the offset)
     */
    void write(byte[] bytes, int offset, int length);

    /**
     * Write the provided buffer to this buffer.
     * @param toWrite buffer to write
     */
    default void write(BufferData toWrite) {
        write(toWrite, toWrite.available());
    }

    /**
     * Write n bytes from the provided buffer to this buffer.
     * @param toWrite buffer to write
     * @param length number of bytes to write
     */
    void write(BufferData toWrite, int length);

    /**
     * HPack integer value (may be 1 or more bytes).
     *
     * TODO enforce limit that the hpack int is max 4 bytes (as otherwise we overflow int and this may be an attack)
     * TODO enforce limit to string values (not here, but on headers processing)
     *
     * @param originalValue value (only bitsOfPrefix are used, bits before that are ignored)
     * @param bitsOfPrefix number of bits significant in the value
     * @return integer value
     */
    default int readHpackInt(int originalValue, int bitsOfPrefix) {
        // significant bits of the value

        int value = originalValue & (0b11111111 >> (8 - bitsOfPrefix));
        /*
        The value is computed as
        max + (read integer from the next x 7 bits, where next 7 bits are read if the first bit of the octet is 1)
         */
        int max = (1 << bitsOfPrefix) - 1;
        if (value < max) { // 31 is the max number of 5 bits
            // value fits into the prefix, no need to read additional bytes
            // System.out.println("Resolved original value " + Integer.toBinaryString(originalValue) + " to " + value);
            return value;
        }
        //System.out.println("Original value " + Integer.toBinaryString(originalValue) + " has more than one byte, carry on: "
        // + value);
        int shiftBy = 0;
        while (true) {
            int next = read();
            //System.out.println("Read additional byte " + Integer.toBinaryString(next));
            value += (next & 0b01111111) << shiftBy; // add all valid bits to the number and continue next cycle
            shiftBy += 7; // the next iteration must be shifted by 7 additional bits

            if ((next & 0b10000000) == 0) {
                // last byte
                //System.out.println("Resolved values to " + value);
                return value;
            }
        }
    }

    /**
     * Write hpack integer to this buffer.
     *
     * @param value the full value we want to write
     * @param prefixedInt value to store in the other bits of the first byte
     * @param bitPrefix bits reserved for our value in the first byte
     * @return this instance
     */
    default BufferData writeHpackInt(int value, int prefixedInt, int bitPrefix) {
        // we do not want possible garbage from wrong value
        int prefixedValue = prefixedInt & (~(0b11111111 >> (8 - bitPrefix)));

        int max = (1 << bitPrefix) - 1;
        if (value < max) {
            write(value | prefixedValue);
            return this;
        }
        write(max | prefixedValue);

        // now encode the rest of the value
        int remainingValue = value - max;
        while (true) {
            if (remainingValue < (1 << 7)) {
                write(remainingValue);
                return this;
            }
            // write the seven bits + the first bit to mark this continues
            int toWrite = (remainingValue & 0b01111111) | 0b10000000;
            write(toWrite);

            // shift to the right by seven bits
            remainingValue = remainingValue >> 7;
        }
    }

    /**
     * Debug this buffer as binary string.
     *
     * @return binary string debug data (including headers)
     */
    String debugDataBinary();

    /**
     * Debug this buffer as hex string.
     *
     * @param fullBuffer whether to debug all bytes in this buffer ({@code true}), or only unread bytes {@code false})
     * @return hex debug data (including headers)
     */
    String debugDataHex(boolean fullBuffer);

    /**
     * Debug the full buffer.
     * @return hex debug data (including headers)
     */
    default String debugDataHex() {
        return debugDataHex(true);
    }

    /**
     * Copy the underlying data into a new buffer that does not retain any reference.
     * Reads this buffer fully and creates a new instance that is not completed.
     *
     * @return copy of data
     */
    default BufferData copy() {
        byte[] copy = new byte[available()];
        read(copy, 0, available());
        return BufferData.create(copy);
    }

    /**
     * Number of bytes available for reading.
     *
     * @return available bytes
     */
    int available();

    /**
     * Skip the next n bytes (move read position).
     * @param length number of bytes to skip
     */
    void skip(int length);

    /**
     * Find index of the provided byte from the current position.
     *
     * @param aByte byte to find
     * @return index of the byte, or {@code -1} if not found
     */
    int indexOf(byte aByte);

    /**
     * Find last index of the provided byte from the current position.
     *
     * @param aByte byte to find
     * @return index of the byte, or {@code -1} if not found
     */
    default int lastIndexOf(byte aByte) {
        return lastIndexOf(aByte, available());
    }

    /**
     * Find last index of the provided byte from the current position.
     *
     * @param aByte byte to find
     * @param length maximal length to search for (e.g. this will be the last byte before reaching the length)
     * @return index of the byte, or {@code -1} if not found
     */
    int lastIndexOf(byte aByte, int length);

    /**
     * Trim the last x bytes from a buffer (remove them).
     *
     * @param x trim by this number of bytes
     * @return this instance
     */
    BufferData trim(int x);

    /**
     * Number of bytes that can be written to this instance.
     *
     * @return capacity of this buffer
     */
    int capacity();

    /**
     * Write ascii string to this buffer.
     * @param text ascii string to write
     */
    default void writeAscii(String text) {
        write(text.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Get byte at index (current read position + index).
     * Does not modify any position.
     *
     * @param index index to get
     * @return byte at the index
     */
    int get(int index);

    /**
     * Read the content of this data as bytes.
     * This method always creates a new byte array.
     *
     * @return byte array with {@link #available()} bytes, may be empty
     */
    default byte[] readBytes() {
        byte[] bytes = new byte[available()];
        read(bytes);
        return bytes;
    }
}
