/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.Objects;

import io.helidon.common.Api;

/**
 * Shared codec for HPACK/QPACK prefixed integers.
 */
@Api.Internal
public final class PrefixedIntegerCodec {
    private static final int MAX_INT_CONTINUATION_BYTES = 5;
    private static final int MAX_LONG_CONTINUATION_BYTES = 10;

    private PrefixedIntegerCodec() {
    }

    /**
     * Read a prefixed integer into an {@code int}.
     *
     * @param source source buffer positioned after the first byte
     * @param firstByte first byte of the encoded integer
     * @param prefixBits number of bits in the first byte reserved for the value, between {@code 1} and {@code 8}
     * @return decoded integer value
     * @throws java.lang.IllegalArgumentException if the prefix width is outside the supported range
     */
    public static int readInt(BufferData source, int firstByte, int prefixBits) {
        long value = readLong(source, firstByte, prefixBits, Integer.MAX_VALUE, MAX_INT_CONTINUATION_BYTES);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("HPACK integer value exceeds int range: " + value);
        }
        return (int) value;
    }

    /**
     * Read a prefixed integer into a {@code long}.
     *
     * @param source source buffer positioned after the first byte
     * @param firstByte first byte of the encoded integer
     * @param prefixBits number of bits in the first byte reserved for the value, between {@code 1} and {@code 8}
     * @return decoded integer value
     * @throws java.lang.IllegalArgumentException if the prefix width is outside the supported range
     */
    public static long readLong(BufferData source, int firstByte, int prefixBits) {
        return readLong(source, firstByte, prefixBits, Long.MAX_VALUE, MAX_LONG_CONTINUATION_BYTES);
    }

    /**
     * Write an {@code int} prefixed integer.
     *
     * @param target target buffer
     * @param value full non-negative value to write
     * @param leadingBits bits stored in the first byte outside of the value prefix
     * @param prefixBits number of bits in the first byte reserved for the value, between {@code 1} and {@code 8}
     * @return target buffer
     * @throws java.lang.IllegalArgumentException if the value or prefix width is outside the supported range
     */
    public static BufferData writeInt(BufferData target, int value, int leadingBits, int prefixBits) {
        return write(target, value, leadingBits, prefixBits);
    }

    /**
     * Write a {@code long} prefixed integer.
     *
     * @param target target buffer
     * @param value full non-negative value to write
     * @param leadingBits bits stored in the first byte outside of the value prefix
     * @param prefixBits number of bits in the first byte reserved for the value, between {@code 1} and {@code 8}
     * @return target buffer
     * @throws java.lang.IllegalArgumentException if the value or prefix width is outside the supported range
     */
    public static BufferData writeLong(BufferData target, long value, int leadingBits, int prefixBits) {
        return write(target, value, leadingBits, prefixBits);
    }

    private static long readLong(BufferData source, int firstByte, int prefixBits, long maxValue, int maxContinuationBytes) {
        Objects.requireNonNull(source, "source");
        int max = prefixMask(prefixBits);
        long value = firstByte & max;
        if (value < max) {
            return value;
        }

        int shiftBy = 0;
        for (int i = 0; i < maxContinuationBytes; i++) {
            int next = readContinuation(source);
            int addition = next & 0b0111_1111;
            if (addition > (maxValue - value) >>> shiftBy) {
                throw new IllegalArgumentException("Prefixed integer value exceeds supported range");
            }
            value += (long) addition << shiftBy;
            if ((next & 0b1000_0000) == 0) {
                return value;
            }
            shiftBy += 7;
        }
        throw new IllegalArgumentException("Prefixed integer is too long");
    }

    private static int readContinuation(BufferData source) {
        if (source.available() == 0) {
            throw new IllegalArgumentException("Prefixed integer is truncated");
        }
        return source.read();
    }

    private static BufferData write(BufferData target, long value, int leadingBits, int prefixBits) {
        Objects.requireNonNull(target, "target");
        if (value < 0) {
            throw new IllegalArgumentException("Prefixed integer value must not be negative: " + value);
        }
        int max = prefixMask(prefixBits);
        int maskedLeadingBits = leadingBits & ~max;

        if (value < max) {
            target.write(maskedLeadingBits | (int) value);
            return target;
        }

        target.write(maskedLeadingBits | max);

        long remaining = value - max;
        while (remaining >= (1 << 7)) {
            target.write((int) ((remaining & 0b0111_1111) | 0b1000_0000));
            remaining >>>= 7;
        }
        target.write((int) remaining);
        return target;
    }

    private static int prefixMask(int prefixBits) {
        if (prefixBits < 1 || prefixBits > Byte.SIZE) {
            throw new IllegalArgumentException("Prefix width must be between 1 and 8 bits: " + prefixBits);
        }
        return (1 << prefixBits) - 1;
    }
}
