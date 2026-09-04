/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

package io.helidon.quic;

import java.nio.ByteBuffer;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;

/**
 * QUIC packets and frames commonly use a variable-length encoding for
 * non-negative values. This encoding ensures that smaller values will use less
 * in the packet or frame.
 *
 * <p>The QUIC variable-length encoding reserves the two most significant bits
 * of the first byte to encode the size of the length value as a base 2 logarithm
 * value. The length itself is then encoded on the remaining bits, in network
 * byte order. This means that the length values will be encoded on 1, 2, 4, or
 * 8 bytes and can encode 6-, 14-, 30-, or 62-bit values
 * respectively, or a value within the range of 0 to 4611686018427387903
 * inclusive.
 *
 * <p>Specification: https://www.rfc-editor.org/rfc/rfc9000.html#integer-encoding
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public class VariableLengthEncoder {
    /**
     * The maximum number of bytes on which a variable length
     * integer can be encoded.
     */
    public static final int MAX_INTEGER_LENGTH = 8;
    /**
     * The maximum value a variable length integer can
     * take.
     */
    public static final long MAX_ENCODED_INTEGER = (1L << 62) - 1;

    static {
    }

    private VariableLengthEncoder() {
        throw new InternalError("should not come here");
    }

    /**
     * Decode a variable length value from {@code ByteBuffer}. This method assumes that the
     * position of {@code buffer} has been set to the first byte where the length
     * begins. If the methods completes successfully, the position will be set
     * to the byte after the last byte read.
     *
     * @param buffer the {@code ByteBuffer} that the length will be decoded from
     * @return the value. If an error occurs, {@code -1} is returned and
     *        the buffer position is left unchanged.
     */
    public static long decode(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        int position = buffer.position();
        if (position == buffer.limit()) {
            return -1;
        }

        int firstByte = buffer.get(position) & 0xFF;
        int length = 1 << (firstByte >> 6);
        if (buffer.limit() - position < length) {
            return -1;
        }

        long result = firstByte & 0x3F;
        for (int i = 1; i < length; i++) {
            result = (result << Byte.SIZE) + (buffer.get(position + i) & 0xFF);
        }
        buffer.position(position + length);
        return result;
    }

    /**
     * Decode a variable length value from {@code BufferData}. This method assumes that the
     * read position of {@code buffer} is set to the first byte where the length begins.
     * If the method completes successfully, the read position is advanced to the byte after
     * the last byte read.
     *
     * @param buffer the {@code BufferData} that the length will be decoded from
     * @return the value. If an error occurs, {@code -1} is returned and
     *        the buffer read position is left unchanged.
     */
    public static long decode(BufferData buffer) {
        if (buffer.available() == 0) {
            return -1;
        }

        int lenByte = buffer.get(0) & 0xFF;
        int prefix = lenByte >> 6;
        int len = 1 << prefix;
        if (buffer.available() < len) {
            return -1;
        }

        long result = lenByte & 0x3F;
        for (int i = 1; i < len; i++) {
            result = (result << Byte.SIZE) + (buffer.get(i) & 0xFF);
        }
        buffer.skip(len);

        return result;
    }

    /**
     * Decode a variable length value from {@code BuffersReader}. This method assumes that the
     * position of {@code buffers} has been set to the first byte where the length
     * begins. If the methods completes successfully, the position will be set
     * to the byte after the last byte.
     *
     * @param buffers the {@code BuffersReader} that the length will be decoded from
     * @return the value. If an error occurs, {@code -1} is returned and
     *        the buffer position is left unchanged.
     */
    public static long decode(BuffersReader buffers) {
        if (!buffers.hasRemaining()) {
            return -1;
        }

        long pos = buffers.position();
        int lenByte = buffers.get(pos) & 0xFF;
        pos++;
        // read size of length from leading two bits
        int prefix = lenByte >> 6;
        int len = 1 << prefix;
        // retrieve remaining bits that constitute the length
        long result = lenByte & 0x3F;
        long idx = 0;
        long lim = buffers.limit();
        if (lim - pos < len - 1) {
            return -1;
        }
        while (idx++ < len - 1) {
            result = ((result << Byte.SIZE) + (buffers.get(pos) & 0xFF));
            pos++;
        }
        // Set position of ByteBuffer to next byte following length
        buffers.position(pos);

        return result;
    }

    /**
     * Encode (a variable length) value into {@code ByteBuffer}. This method assumes that the
     * position of {@code buffer} has been set to the first byte where the length
     * begins. If the methods completes successfully, the position will be set
     * to the byte after the last length byte.
     *
     * @param buffer the {@code ByteBuffer} that the length will be encoded into
     * @param value  the variable length value
     * @return the {@code position} of the buffer
     * @throws IllegalArgumentException if value supplied falls outside of acceptable bounds  [0, 2^62-1],
     *                                 or if the given buffer doesn't contain enough space to encode the
     *                                 value
     */
    public static int encode(ByteBuffer buffer, long value) throws IllegalArgumentException {
        // check for valid parameters
        validateValue(value);
        int lengthSize = encodedSize(value);
        if (lengthSize > buffer.remaining()) {
            throw new IllegalArgumentException("buffer does not contain enough bytes to store length");
        }
        return encode(lengthSize, value, buffer::put);
    }

    /**
     * Encode (a variable length) value into {@code BufferData}. This method assumes that the
     * write position of {@code buffer} has been set to the first byte where the length begins.
     * If the method completes successfully, the write position is set to the byte after the
     * last encoded byte.
     *
     * @param buffer the {@code BufferData} that the length will be encoded into
     * @param value  the variable length value
     * @return the number of bytes written
     * @throws IllegalArgumentException if value supplied falls outside of acceptable bounds [0, 2^62-1],
     *                                 or if the given buffer doesn't contain enough space to encode the
     *                                 value
     */
    public static int encode(BufferData buffer, long value) throws IllegalArgumentException {
        validateValue(value);
        int lengthSize = encodedSize(value);
        try {
            return encode(lengthSize, value, buffer::write);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("buffer does not contain enough bytes to store length", e);
        }
    }

    /**
     * Returns the variable length prefix.
     * The variable length prefix is the base 2 logarithm of
     * the number of bytes required to encode
     * a positive value as a variable length integer:
     * [0, 1, 2, 3] for [1, 2, 4, 8] bytes.
     *
     * @param value the value to encode
     * @return the base 2 logarithm of the number of bytes required to encode
     *        the value as a variable length integer.
     * @throws IllegalArgumentException if the supplied value falls outside the acceptable bounds [0, 2^62-1]
     */
    public static int variableLengthPrefix(long value) throws IllegalArgumentException {
        if ((value > MAX_ENCODED_INTEGER) || (value < 0)) {
            throw new IllegalArgumentException("invalid length");
        }

        int lengthPrefix;
        if (value > (1L << 30) - 1) {
            lengthPrefix = 3; // 8 bytes
        } else if (value > (1L << 14) - 1) {
            lengthPrefix = 2; // 4 bytes
        } else if (value > (1L << 6) - 1) {
            lengthPrefix = 1; // 2 bytes
        } else {
            lengthPrefix = 0; // 1 byte
        }

        return lengthPrefix;
    }

    /**
     * Returns the number of bytes needed to encode
     * the given value as a variable length integer.
     * This a number between 1 and 8.
     *
     * @param value the value to encode
     * @return the number of bytes needed to encode
     *        the given value as a variable length integer.
     * @throws IllegalArgumentException if the value supplied falls outside of acceptable bounds [0, 2^62-1]
     */
    public static int encodedSize(long value) throws IllegalArgumentException {
        if (value < 0 || value > MAX_ENCODED_INTEGER) {
            throw new IllegalArgumentException("invalid variable length integer: " + value);
        }
        return 1 << variableLengthPrefix(value);
    }

    /**
     * Peeks at a variable length value encoded at the given offset.
     * If the byte buffer doesn't contain enough bytes to read the
     * variable length value, -1 is returned.
     *
     * <p>This method doesn't advance the buffer position.
     *
     * @param buffer the buffer to read from
     * @param offset the offset in the buffer to start reading from
     * @return the variable length value encoded at the given offset, or -1
     */
    public static long peekEncodedValue(ByteBuffer buffer, int offset) {
        return peekEncodedValue(BuffersReader.single(buffer), offset);
    }

    /**
     * Peeks at a variable length value encoded at the given offset.
     * If the byte buffer doesn't contain enough bytes to read the
     * variable length value, -1 is returned.
     *
     * This method doesn't advance the buffer position.
     *
     * @param buffers the buffer to read from
     * @param offset  the offset in the buffer to start reading from
     * @return the variable length value encoded at the given offset, or -1
     */
    public static long peekEncodedValue(BuffersReader buffers, long offset) {

        // figure out on how many bytes the length is encoded.
        int size = peekEncodedValueSize(buffers, offset);
        if (size <= 0) {
            return -1L;
        }

        // check that we have enough bytes in the buffer
        long limit = buffers.limit();
        long pos = offset;
        if (limit - size < pos) {
            return -1L;
        }

        // peek at the variable length:
        //  - read first byte
        int first = buffers.get(pos++);
        long res = first & 0x3F;
        if (size == 1) {
            return res;
        }

        // - read the rest of the bytes
        size -= 1;
        for (int i = 0; i < size; i++) {
            if (limit <= pos) {
                return -1L;
            }
            res = (res << 8) | (long) (buffers.get(pos++) & 0xFF);
        }
        return res;
    }

    /**
     * Peeks at a variable length value encoded at the given offset,
     * and return the number of bytes on which this value is encoded.
     * If the byte buffer is empty or the offset is past
     * the limit -1 is returned.
     * This method doesn't advance the buffer position.
     *
     * @param buffer the buffer to read from
     * @param offset the offset in the buffer to start reading from
     * @return the number of bytes on which the variable length
     *        value is encoded at the given offset, or -1
     */
    public static int peekEncodedValueSize(ByteBuffer buffer, int offset) {
        return peekEncodedValueSize(BuffersReader.single(buffer), offset);
    }

    /**
     * Peeks at a variable length value encoded at the given offset,
     * and return the number of bytes on which this value is encoded.
     * If the byte buffer is empty or the offset is past
     * the limit -1 is returned.
     * This method doesn't advance the buffer position.
     *
     * @param buffers the buffers to read from
     * @param offset  the offset in the buffer to start reading from
     * @return the number of bytes on which the variable length
     *        value is encoded at the given offset, or -1
     */
    public static int peekEncodedValueSize(BuffersReader buffers, long offset) {
        long limit = buffers.limit();
        long pos = offset;
        if (limit <= pos) {
            return -1;
        }
        int first = buffers.get(pos);
        int prefix = (first & 0xC0) >>> 6;
        int size = 1 << prefix;
        return size;
    }

    private static void validateValue(long value) {
        if (value < 0 || value > MAX_ENCODED_INTEGER) {
            throw new IllegalArgumentException("value supplied falls outside of acceptable bounds");
        }
    }

    private static int encode(int lengthSize, long value, ByteWriter writer) {

        int lengthPrefix = switch (lengthSize) {
            case 1 -> 0x00;
            case 2 -> 0x40;
            case 4 -> 0x80;
            case 8 -> 0xC0;
            default -> throw new IllegalArgumentException("Unexpected QUIC integer length " + lengthSize);
        };

        long mask = 255L << (Byte.SIZE * (lengthSize - 1));
        boolean isFirstByte = true;
        for (int i = lengthSize; i > 0; i--) {

            long b = value & mask;
            for (int j = i - 1; j > 0; j--) {
                b >>= Byte.SIZE;
            }


            if (isFirstByte) {
                writer.write((byte) (b | lengthPrefix));
                isFirstByte = false;
            } else {
                writer.write((byte) b);
            }
            mask = (mask >>> Byte.SIZE);
        }
        return lengthSize;
    }

    @FunctionalInterface
    private interface ByteWriter {
        void write(byte value);
    }
}
