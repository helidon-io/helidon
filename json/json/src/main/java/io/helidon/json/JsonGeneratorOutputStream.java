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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.common.buffers.Bytes;

class JsonGeneratorOutputStream extends AbstractJsonGenerator {

    private static final byte[] HEX_DIGITS = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

    private final OutputStream outputStream;
    private final byte[] buffer = new byte[256];
    private final byte[] digits = new byte[20];
    private int index = 0;
    private boolean closed;

    JsonGeneratorOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    void ensureCapacity(int extra) {
        if (index + extra >= buffer.length) {
            writeBuffer();
        }
    }

    @Override
    void writeString(String value) {
        ensureCapacity(1);
        buffer[index++] = Bytes.DOUBLE_QUOTE_BYTE;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            encodeChar(c);
        }
        ensureCapacity(1);
        buffer[index++] = Bytes.DOUBLE_QUOTE_BYTE;
    }

    @Override
    void writeChar(char value) {
        ensureCapacity(1);
        buffer[index++] = Bytes.DOUBLE_QUOTE_BYTE;
        encodeChar(value);
        ensureCapacity(1);
        buffer[index++] = Bytes.DOUBLE_QUOTE_BYTE;
    }

    @Override
    void writeByteExact(byte value) {
        ensureCapacity(1);
        buffer[index++] = value;
    }

    @Override
    void writeLong(long value) {
        if (value == 0) {
            writeByteExact(Bytes.ZERO_DIGIT_BYTE);
        }
        long toProcess = value;
        int digits = 0;
        boolean negative = value < 0;
        if (negative) {
            ensureCapacity(1);
            buffer[index++] = Bytes.MINUS_SIGN_BYTE;
            toProcess = -toProcess;
        }
        while (toProcess > 0) {
            this.digits[digits++] = (byte) ('0' + toProcess % 10);
            toProcess /= 10;
        }
        ensureCapacity(digits);
        for (int i = --digits; i >= 0; i--) {
            buffer[index++] = this.digits[i];
        }
    }

    @Override
    void writeFloat(float value) {
        //Performance improvement needed
        if (Float.isNaN(value)) {
            ensureCapacity(3);
            buffer[index++] = (byte) 'N';
            buffer[index++] = (byte) 'a';
            buffer[index++] = (byte) 'N';
            return;
        } else if (Float.NEGATIVE_INFINITY == value) {
            ensureCapacity(9);
            buffer[index++] = (byte) '-';
            buffer[index++] = (byte) 'I';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'f';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 't';
            buffer[index++] = (byte) 'y';
            return;
        } else if (Float.POSITIVE_INFINITY == value) {
            ensureCapacity(8);
            buffer[index++] = (byte) 'I';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'f';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 't';
            buffer[index++] = (byte) 'y';
            return;
        } else if (value == 0.0) {
            ensureCapacity(3);
            buffer[index++] = (byte) '0';
            buffer[index++] = (byte) '.';
            buffer[index++] = (byte) '0';
            return;
        }

        // Convert to string (optimized native routine)
        String str = Float.toString(value);
        int len = str.length();

        ensureCapacity(len);
        for (int i = 0; i < len; i++) {
            buffer[index + i] = (byte) str.charAt(i); // ASCII digits + '.', 'E', '-', etc.
        }
        index += len;
    }

    @Override
    void writeDouble(double value) {
        //Performance improvement needed
        if (Double.isNaN(value)) {
            ensureCapacity(3);
            buffer[index++] = (byte) 'N';
            buffer[index++] = (byte) 'a';
            buffer[index++] = (byte) 'N';
            return;
        } else if (Double.NEGATIVE_INFINITY == value) {
            ensureCapacity(9);
            buffer[index++] = (byte) '-';
            buffer[index++] = (byte) 'I';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'f';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 't';
            buffer[index++] = (byte) 'y';
            return;
        } else if (Double.POSITIVE_INFINITY == value) {
            ensureCapacity(8);
            buffer[index++] = (byte) 'I';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'f';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 't';
            buffer[index++] = (byte) 'y';
            return;
        } else if (value == 0.0) {
            ensureCapacity(3);
            buffer[index++] = (byte) '0';
            buffer[index++] = (byte) '.';
            buffer[index++] = (byte) '0';
            return;
        }

        // Convert to string (optimized native routine)
        String str = Double.toString(value);
        int len = str.length();

        ensureCapacity(len);
        for (int i = 0; i < len; i++) {
            buffer[index + i] = (byte) str.charAt(i); // ASCII digits + '.', 'E', '-', etc.
        }
        index += len;
    }

    @Override
    void writeBoolean(boolean value) {
        if (value) {
            ensureCapacity(4);
            buffer[index++] = 't';
            buffer[index++] = 'r';
            buffer[index++] = 'u';
            buffer[index++] = 'e';
        } else {
            ensureCapacity(5);
            buffer[index++] = 'f';
            buffer[index++] = 'a';
            buffer[index++] = 'l';
            buffer[index++] = 's';
            buffer[index++] = 'e';
        }
    }

    @Override
    void writeNullValue() {
        ensureCapacity(4);
        buffer[index++] = 'n';
        buffer[index++] = 'u';
        buffer[index++] = 'l';
        buffer[index++] = 'l';
    }

    @Override
    public void close() throws Exception {
        if (!closed) {
            closed = true;
            outputStream.write(buffer, 0, index);
            outputStream.flush();
        }
    }

    private void writeBuffer() {
        if (index == 0) {
            return;
        }
        try {
            outputStream.write(buffer, 0, index);
            index = 0;
        } catch (IOException e) {
            throw new JsonException("Stream write failed", e);
        }
    }

    /**
     * Encodes a char into JSON string format, handling control characters, escapes, and UTF-8 encoding.
     * Follows JSON string encoding rules as per RFC 8259.
     */
    private void encodeChar(char c) {
        if (c < 0x20) {
            // Control characters (0x00-0x1F) must be escaped
            ensureCapacity(2); //There will be at least one more byte
            buffer[index++] = Bytes.BACKSLASH_BYTE;
            if (c == '\n') {
                buffer[index++] = (byte) 'n';
            } else if (c == '\r') {
                buffer[index++] = (byte) 'r';
            } else if (c == '\t') {
                buffer[index++] = (byte) 't';
            } else if (c == '\b') {
                buffer[index++] = (byte) 'b';
            } else if (c == '\f') {
                buffer[index++] = (byte) 'f';
            } else {
                // Other control chars use \\uXXXX format
                ensureCapacity(5);
                buffer[index++] = 'u';
                buffer[index++] = '0';
                buffer[index++] = '0';
                buffer[index++] = HEX_DIGITS[(c >> 4) & 0xF];
                buffer[index++] = HEX_DIGITS[c & 0xF];
            }
        } else if (c == '"' || c == '\\') {
            // JSON special characters must be escaped
            ensureCapacity(2);
            buffer[index++] = Bytes.BACKSLASH_BYTE;
            buffer[index++] = (byte) c;
        } else if (c < 0x80) {
            // ASCII character (0x20-0x7F): write as-is
            ensureCapacity(1);
            buffer[index++] = (byte) c;
        } else if (c < 0x800) {
            // 2-byte UTF-8 sequence: 110xxxxx 10yyyyyy
            ensureCapacity(2);
            buffer[index++] = (byte) (0b11000000 | (c >> 6));      // First byte: 110xxxxx
            buffer[index++] = (byte) (0b10000000 | (c & 0x3F));    // Second byte: 10yyyyyy
        } else if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
            // Surrogates are written as \\uXXXX (JSON doesn't support UTF-16 surrogates directly)
            ensureCapacity(6);
            buffer[index++] = Bytes.BACKSLASH_BYTE;
            buffer[index++] = 'u';
            buffer[index++] = HEX_DIGITS[(c >> 12) & 0xF];  // High nibble of high byte
            buffer[index++] = HEX_DIGITS[(c >> 8) & 0xF];   // Low nibble of high byte
            buffer[index++] = HEX_DIGITS[(c >> 4) & 0xF];   // High nibble of low byte
            buffer[index++] = HEX_DIGITS[c & 0xF];          // Low nibble of low byte
        } else {
            // 3-byte UTF-8 sequence: 1110xxxx 10yyyyyy 10zzzzzz (for BMP characters)
            ensureCapacity(3);
            buffer[index++] = (byte) (0b11100000 | (c >> 12));         // First byte: 1110xxxx
            buffer[index++] = (byte) (0b10000000 | ((c >> 6) & 0x3F)); // Second byte: 10yyyyyy
            buffer[index++] = (byte) (0b10000000 | (c & 0x3F));        // Third byte: 10zzzzzz
        }
    }

}
