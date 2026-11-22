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

package io.helidon.json;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import io.helidon.common.Api;

/**
 * A precomputed JSON object key.
 * <p>
 * The generator can write the pre-escaped name directly. A text parser can use the same name
 * to recognize a following object property without decoding it again.
 * </p>
 */
@Api.Preview
public final class JsonKey {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private final int frameBytes;
    private final long head;
    // A mask for a short frame or the last eight bytes of a longer one.
    private final long tail;
    private final String value;
    private final byte[] quotedUtf8;
    private final char[] quotedChars;

    private JsonKey(String value) {
        this.value = Objects.requireNonNull(value);
        String quoted = quotedValue(value);
        this.quotedChars = quoted.toCharArray();
        this.quotedUtf8 = quoted.getBytes(StandardCharsets.UTF_8);
        int length = quotedUtf8.length;
        // An unpaired surrogate can be written as an escape but cannot occur in a valid decoded name.
        frameBytes = length - 2 == value.length() || hasValidSurrogates(value)
                ? Math.min(length, 2 * Long.BYTES) + 2
                : -1;
        if (frameBytes < 0 || frameBytes > 2 * Long.BYTES) {
            head = 0;
            tail = 0;
        } else if (frameBytes <= Long.BYTES) {
            head = frameWord(0, frameBytes);
            tail = -1L >>> ((Long.BYTES - frameBytes) * Byte.SIZE);
        } else {
            head = frameWord(0, Long.BYTES);
            tail = frameWord(frameBytes - Long.BYTES, frameBytes);
        }
    }

    /**
     * Create a new precomputed JSON key.
     *
     * @param value raw key value
     * @return precomputed key
     */
    public static JsonKey create(String value) {
        return new JsonKey(value);
    }

    /**
     * Raw key value without JSON quoting.
     *
     * @return raw key value
     */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JsonKey other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    byte[] quotedUtf8() {
        return quotedUtf8;
    }

    char[] quotedChars() {
        return quotedChars;
    }

    int firstPropertyValue(byte[] buffer, int start, int limit) {
        int available = limit - start;
        if (frameBytes < 0 || available - 1 <= quotedUtf8.length) {
            return -1;
        }
        int value = start + quotedUtf8.length + 1;
        if (frameBytes <= Long.BYTES && available >= Long.BYTES) {
            if ((JsonTextParser.word(buffer, start) & (tail >>> Byte.SIZE)) != (head >>> Byte.SIZE)) {
                return -1;
            }
        } else if (frameBytes > Long.BYTES && frameBytes <= 2 * Long.BYTES) {
            if (((JsonTextParser.word(buffer, start) << Byte.SIZE) | ',') != head
                    || JsonTextParser.word(buffer, value - Long.BYTES) != tail) {
                return -1;
            }
        } else if (buffer[value - 1] != ':' || !Arrays.equals(buffer, start, value - 1, quotedUtf8, 0, quotedUtf8.length)) {
            return -1;
        }
        return valueStart(buffer[value]) ? value : -1;
    }

    int nextPropertyValue(byte[] buffer, int start, int limit) {
        int available = limit - start;
        if (frameBytes < 0 || available - 2 <= quotedUtf8.length) {
            return -1;
        }
        int end = start + 2 + quotedUtf8.length;
        if (frameBytes <= Long.BYTES && available >= Long.BYTES) {
            if ((JsonTextParser.word(buffer, start) & tail) != head) {
                return -1;
            }
        } else if (frameBytes > Long.BYTES && frameBytes <= 2 * Long.BYTES) {
            if (JsonTextParser.word(buffer, start) != head || JsonTextParser.word(buffer, end - Long.BYTES) != tail) {
                return -1;
            }
        } else if (buffer[start] != ',' || buffer[end - 1] != ':'
                || !Arrays.equals(buffer, start + 1, end - 1, quotedUtf8, 0, quotedUtf8.length)) {
            return -1;
        }
        return valueStart(buffer[end]) ? end : -1;
    }

    private static boolean valueStart(byte next) {
        return (next >= '0' && next <= '9') || next == '"' || next == '-' || next == '{' || next == '['
                || next == 't' || next == 'f' || next == 'n';
    }

    private static boolean hasValidSurrogates(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i == name.length() || !Character.isLowSurrogate(name.charAt(i))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    private static String quotedValue(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            appendEscaped(builder, value.charAt(i));
        }
        builder.append('"');
        return builder.toString();
    }

    private static void appendEscaped(StringBuilder builder, char c) {
        switch (c) {
        case '\b':
            builder.append("\\b");
            return;
        case '\f':
            builder.append("\\f");
            return;
        case '\n':
            builder.append("\\n");
            return;
        case '\r':
            builder.append("\\r");
            return;
        case '\t':
            builder.append("\\t");
            return;
        case '\\':
            builder.append("\\\\");
            return;
        case '"':
            builder.append("\\\"");
            return;
        default:
            if (c < 0x20 || Character.isSurrogate(c)) {
                appendUnicodeEscape(builder, c);
            } else {
                builder.append(c);
            }
        }
    }

    private static void appendUnicodeEscape(StringBuilder builder, char c) {
        builder.append('\\');
        builder.append('u');
        builder.append(HEX_DIGITS[(c >> 12) & 0xF]);
        builder.append(HEX_DIGITS[(c >> 8) & 0xF]);
        builder.append(HEX_DIGITS[(c >> 4) & 0xF]);
        builder.append(HEX_DIGITS[c & 0xF]);
    }

    private long frameWord(int start, int end) {
        long word = 0;
        for (int i = start; i < end; i++) {
            int b = i == 0 ? ',' : i == frameBytes - 1 ? ':' : quotedUtf8[i - 1] & 0xff;
            word |= (long) b << ((i - start) * Byte.SIZE);
        }
        return word;
    }
}
