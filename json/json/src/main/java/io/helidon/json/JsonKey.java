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
import java.util.Objects;

/**
 * A precomputed JSON object key.
 * <p>
 * The key keeps pre-escaped character and UTF-8 representations so repeated object-field writes can avoid
 * re-encoding the property name on each serialization call.
 * </p>
 */
public final class JsonKey {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private final String value;
    private final byte[] quotedUtf8;
    private final char[] quotedChars;

    private JsonKey(String value) {
        this.value = Objects.requireNonNull(value);
        String quoted = quotedValue(value);
        this.quotedChars = quoted.toCharArray();
        this.quotedUtf8 = quoted.getBytes(StandardCharsets.UTF_8);
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
}
