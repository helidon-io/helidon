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

/*
 * This class is mostly copied from Netty.
 * Original Copyright:
 *
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.common.buffers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import io.helidon.common.Api;

/**
 * HPACK and QPACK Huffman codec defined by RFC 7541, Appendix B.
 */
@Api.Internal
public final class HuffmanCodec {
    private HuffmanCodec() {
    }

    /**
     * Calculate the encoded length of a value.
     *
     * @param value octet-valued characters to encode
     * @return encoded byte count
     * @throws java.lang.IllegalArgumentException if a character is outside the octet range or the encoded length
     *                                            exceeds the supported array range
     */
    public static int encodedLength(CharSequence value) {
        Objects.requireNonNull(value, "value");
        long bitLength = 0;
        for (int i = 0; i < value.length(); i++) {
            bitLength += HuffmanTables.HUFFMAN_CODE_LENGTHS[octet(value.charAt(i))];
        }
        long byteLength = (bitLength + 7) >>> 3;
        if (byteLength > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Encoded Huffman value is too long: " + byteLength);
        }
        return (int) byteLength;
    }

    /**
     * Encode a value into a destination byte array.
     * <p>
     * If the destination is too small, its contents are undefined and must be discarded.
     *
     * @param value octet-valued characters to encode
     * @param destination destination byte array
     * @return encoded byte count, or {@code -1} if the destination is too small
     * @throws java.lang.IllegalArgumentException if a character is outside the octet range
     */
    public static int encode(CharSequence value, byte[] destination) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(destination, "destination");

        int index = 0;
        long current = 0;
        int bitCount = 0;

        for (int i = 0; i < value.length(); i++) {
            int octet = octet(value.charAt(i));
            int code = HuffmanTables.HUFFMAN_CODES[octet];
            int codeLength = HuffmanTables.HUFFMAN_CODE_LENGTHS[octet];

            current <<= codeLength;
            current |= code;
            bitCount += codeLength;

            while (bitCount >= 8) {
                if (index == destination.length) {
                    return -1;
                }
                bitCount -= 8;
                destination[index++] = (byte) (current >> bitCount);
            }
        }

        if (bitCount > 0) {
            if (index == destination.length) {
                return -1;
            }
            current <<= 8 - bitCount;
            current |= 0xFF >>> bitCount;
            destination[index++] = (byte) current;
        }

        return index;
    }

    /**
     * Decode bytes into a destination byte array.
     * <p>
     * Invalid input may partially advance the source and modify the destination. Range and capacity failures are
     * detected before either is modified.
     *
     * @param source encoded bytes
     * @param encodedLength encoded byte count
     * @param destination decoded byte destination
     * @return decoded byte count
     * @throws java.lang.IndexOutOfBoundsException if the encoded range exceeds the source or the destination cannot
     *                                             hold the maximum possible decoded value
     * @throws java.lang.IllegalArgumentException if the input is malformed
     */
    public static int decode(BufferData source, int encodedLength, byte[] destination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.checkFromIndexSize(0, encodedLength, source.available());
        long maximumDecodedLength = (long) encodedLength * 8 / 5;
        if (maximumDecodedLength > destination.length) {
            throw new IndexOutOfBoundsException("Decoded Huffman value may require " + maximumDecodedLength
                                                       + " bytes, destination has " + destination.length);
        }

        int destinationIndex = 0;
        int state = 0;

        for (int i = 0; i < encodedLength; i++) {
            int input = source.read();

            state = transition(state, input >> 4);
            if ((state & HuffmanTables.HUFFMAN_EMIT_SYMBOL_SHIFT) != 0) {
                destination[destinationIndex++] = (byte) state;
            }

            state = transition(state, input);
            if ((state & HuffmanTables.HUFFMAN_EMIT_SYMBOL_SHIFT) != 0) {
                destination[destinationIndex++] = (byte) state;
            }
        }

        validateCompletion(state, encodedLength);
        return destinationIndex;
    }

    /**
     * Decode bytes into an appendable.
     * <p>
     * Invalid input or an append failure may partially advance the source and modify the destination. Range failures
     * are detected before either is modified.
     *
     * @param source encoded bytes
     * @param encodedLength encoded byte count
     * @param destination decoded character destination
     * @throws java.lang.IndexOutOfBoundsException if the encoded range exceeds the source
     * @throws java.lang.IllegalArgumentException if the input is malformed
     * @throws java.io.UncheckedIOException if appending fails
     */
    public static void decode(BufferData source, int encodedLength, Appendable destination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.checkFromIndexSize(0, encodedLength, source.available());

        int state = 0;

        for (int i = 0; i < encodedLength; i++) {
            int input = source.read();

            state = transition(state, input >> 4);
            if ((state & HuffmanTables.HUFFMAN_EMIT_SYMBOL_SHIFT) != 0) {
                append(destination, (char) (state & 0xFF));
            }

            state = transition(state, input);
            if ((state & HuffmanTables.HUFFMAN_EMIT_SYMBOL_SHIFT) != 0) {
                append(destination, (char) (state & 0xFF));
            }
        }

        validateCompletion(state, encodedLength);
    }

    private static int octet(char value) {
        if (value > 0xFF) {
            throw new IllegalArgumentException("Character is outside the octet range: " + (int) value);
        }
        return value;
    }

    private static int transition(int state, int input) {
        int next = HuffmanTables.HUFFS[state >> 12 | (input & 0x0F)];
        if ((next & HuffmanTables.HUFFMAN_FAIL_SHIFT) != 0) {
            throw new IllegalArgumentException("Cannot decode Huffman encoded value");
        }
        return next;
    }

    private static void append(Appendable destination, char value) {
        try {
            destination.append(value);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append decoded Huffman data", e);
        }
    }

    private static void validateCompletion(int state, int encodedLength) {
        if (encodedLength != 0
                && (state & HuffmanTables.HUFFMAN_COMPLETE_SHIFT) != HuffmanTables.HUFFMAN_COMPLETE_SHIFT) {
            throw new IllegalArgumentException("Huffman encoding has invalid padding or is truncated");
        }
    }
}
