/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.nio.ByteOrder;

/**
 * Bytes commonly used in HTTP.
 */
public final class Bytes {
    /**
     * {@code :} byte.
     */
    public static final byte COLON_BYTE = (byte) ':';
    /**
     * {@code  } (space) byte.
     */
    public static final byte SPACE_BYTE = (byte) ' ';
    /**
     * {@code \n} (new line) byte.
     */
    public static final byte LF_BYTE = (byte) '\n';
    /**
     * {@code \r} (carriage return) byte.
     */
    public static final byte CR_BYTE = (byte) '\r';
    /**
     * {@code /} byte.
     */
    public static final byte SLASH_BYTE = (byte) '/';
    /**
     * {@code ;} byte.
     */
    public static final byte SEMICOLON_BYTE = (byte) ';';
    /**
     * {@code ?} byte.
     */
    public static final byte QUESTION_MARK_BYTE = (byte) '?';
    /**
     * {@code #} byte.
     */
    public static final byte HASH_BYTE = (byte) '#';
    /**
     * {@code =} byte.
     */
    public static final byte EQUALS_BYTE = (byte) '=';
    /**
     * {@code &} byte.
     */
    public static final byte AMPERSAND_BYTE = (byte) '&';
    /**
     * {@code %} byte.
     */
    public static final byte PERCENT_BYTE = (byte) '%';
    /**
     * Horizontal tabulator byte.
     */
    public static final byte TAB_BYTE = (byte) '\t';

    private Bytes() {
    }

    /**
     * This is using a SWAR (SIMD Within A Register) batch read technique to minimize bound-checks and improve memory
     * usage while searching for {@code value}.
     * <p>
     * This method does NOT do a bound check on the buffer length and the fromIndex and toIndex, neither does it check
     * if the {@code toIndex} is bigger than the {@code fromIndex}.
     * <p>
     * This method is heavily influenced by Netty implementation.
     *
     * @param buffer    the byte buffer to search
     * @param fromIndex first index in the array
     * @param toIndex   last index in the array
     * @param value     to search for
     */
    public static int firstIndexOf(byte[] buffer, int fromIndex, int toIndex, byte value) {
        if (fromIndex == toIndex || buffer.length == 0) {
            // fast path for empty buffers, or empty range
            return -1;
        }
        int length = toIndex - fromIndex;
        int offset = fromIndex;
        int byteCount = length & 7;
        if (byteCount > 0) {
            int index = unrolledFirstIndexOf(buffer, fromIndex, byteCount, value);
            if (index != -1) {
                return index;
            }
            offset += byteCount;
            if (offset == toIndex) {
                return -1;
            }
        }
        int longCount = length >>> 3;
        ByteOrder nativeOrder = ByteOrder.nativeOrder();
        boolean useLE = nativeOrder == ByteOrder.LITTLE_ENDIAN;
        long pattern = compilePattern(value);
        for (int i = 0; i < longCount; i++) {
            // use the faster available getLong
            long word = useLE ? getLongLe(buffer, offset) : getLong(buffer, offset);
            int index = firstInstance(word, pattern);
            if (index < Long.BYTES) {
                return offset + index;
            }
            offset += Long.BYTES;
        }
        return -1;
    }

    private static long compilePattern(byte byteToFind) {
        return (byteToFind & 0xFFL) * 0x101010101010101L;
    }

    private static int firstInstance(long word, long pattern) {
        long input = word ^ pattern;
        long tmp = (input & 0x7F7F7F7F7F7F7F7FL) + 0x7F7F7F7F7F7F7F7FL;
        tmp = ~(tmp | input | 0x7F7F7F7F7F7F7F7FL);
        int binaryPosition = Long.numberOfTrailingZeros(tmp);
        return binaryPosition >>> 3;
    }

    private static long getLongLe(byte[] buffer, int index) {
        return (long) buffer[index] & 0xff
                | ((long) buffer[index + 1] & 0xff) << 8
                | ((long) buffer[index + 2] & 0xff) << 16
                | ((long) buffer[index + 3] & 0xff) << 24
                | ((long) buffer[index + 4] & 0xff) << 32
                | ((long) buffer[index + 5] & 0xff) << 40
                | ((long) buffer[index + 6] & 0xff) << 48
                | ((long) buffer[index + 7] & 0xff) << 56;
    }

    private static long getLong(byte[] buffer, int index) {
        return ((long) buffer[index] & 0xff) << 56
                | ((long) buffer[index + 1] & 0xff) << 48
                | ((long) buffer[index + 2] & 0xff) << 40
                | ((long) buffer[index + 3] & 0xff) << 32
                | ((long) buffer[index + 4] & 0xff) << 24
                | ((long) buffer[index + 5] & 0xff) << 16
                | ((long) buffer[index + 6] & 0xff) << 8
                | (long) buffer[index + 7] & 0xff;
    }

    private static int unrolledFirstIndexOf(byte[] buffer, int fromIndex, int byteCount, byte value) {
        assert byteCount > 0 && byteCount < 8;
        if (buffer[fromIndex] == value) {
            return fromIndex;
        }
        if (byteCount == 1) {
            return -1;
        }
        if (buffer[fromIndex + 1] == value) {
            return fromIndex + 1;
        }
        if (byteCount == 2) {
            return -1;
        }
        if (buffer[fromIndex + 2] == value) {
            return fromIndex + 2;
        }
        if (byteCount == 3) {
            return -1;
        }
        if (buffer[fromIndex + 3] == value) {
            return fromIndex + 3;
        }
        if (byteCount == 4) {
            return -1;
        }
        if (buffer[fromIndex + 4] == value) {
            return fromIndex + 4;
        }
        if (byteCount == 5) {
            return -1;
        }
        if (buffer[fromIndex + 5] == value) {
            return fromIndex + 5;
        }
        if (byteCount == 6) {
            return -1;
        }
        if (buffer[fromIndex + 6] == value) {
            return fromIndex + 6;
        }
        return -1;
    }
}
