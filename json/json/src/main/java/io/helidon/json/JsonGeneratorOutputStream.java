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
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import io.helidon.common.buffers.Bytes;

class JsonGeneratorOutputStream extends JsonGeneratorBase {

    private static final int INITIAL_BUFFER_SIZE = 256;
    private static final int MAX_BUFFER_SIZE = 1024;
    private static final String LONG_MIN_VALUE_TEXT = Long.toString(Long.MIN_VALUE);
    private static final byte[] HEX_DIGITS = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] INDENT = new byte[STACK_SIZE * INDENT_SIZE];

    static {
        java.util.Arrays.fill(INDENT, (byte) ' ');
    }

    private final OutputStream outputStream;
    private byte[] buffer = new byte[INITIAL_BUFFER_SIZE];
    private byte[] digits;
    private int index = 0;
    private boolean closed;

    JsonGeneratorOutputStream(OutputStream outputStream, boolean prettyPrint) {
        super(prettyPrint);
        this.outputStream = outputStream;
    }

    @Override
    protected void ensureCapacity(int extra) {
        if (index + extra < buffer.length) {
            return;
        }
        if (extra <= MAX_BUFFER_SIZE && buffer.length < MAX_BUFFER_SIZE) {
            growBuffer(Math.min(MAX_BUFFER_SIZE, index + extra));
            if (index + extra <= buffer.length) {
                return;
            }
        }
        writeBuffer();
        if (extra > buffer.length) {
            growBuffer(extra);
        }
    }

    @Override
    protected void writeNewLineIndent(int indentLevel) {
        int indentLength = indentLevel * INDENT_SIZE;
        ensureCapacity(indentLength + 1);
        buffer[index++] = (byte) '\n';
        System.arraycopy(INDENT, 0, buffer, index, indentLength);
        index += indentLength;
    }

    @Override
    protected void writeString(String value) {
        ensureCapacity(1);
        buffer[index++] = Bytes.DOUBLE_QUOTE_BYTE;

        int asciiStart = 0;
        int length = value.length();
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (isAsciiPlain(c)) {
                continue;
            }
            writeAscii(value, asciiStart, i);
            encodeChar(c);
            asciiStart = i + 1;
        }
        writeAscii(value, asciiStart, length);

        ensureCapacity(1);
        buffer[index++] = Bytes.DOUBLE_QUOTE_BYTE;
    }

    @Override
    protected void writeKeyName(JsonKey value) {
        writeBytes(value.quotedUtf8());
    }

    @Override
    protected void writeChar(char value) {
        ensureCapacity(1);
        buffer[index++] = Bytes.DOUBLE_QUOTE_BYTE;
        encodeChar(value);
        ensureCapacity(1);
        buffer[index++] = Bytes.DOUBLE_QUOTE_BYTE;
    }

    @Override
    protected void writeByteExact(byte value) {
        ensureCapacity(1);
        buffer[index++] = value;
    }

    @Override
    protected void writeInt(int value) {
        writeLong(value);
    }

    @Override
    protected void writeLong(long value) {
        if (value == 0) {
            writeByteExact(Bytes.ZERO_DIGIT_BYTE);
            return;
        }
        if (value == Long.MIN_VALUE) {
            writeAscii(LONG_MIN_VALUE_TEXT);
            return;
        }
        byte[] digitsBuffer = digitsBuffer();
        long toProcess = value;
        int digits = 0;
        boolean negative = value < 0;
        if (negative) {
            ensureCapacity(1);
            buffer[index++] = Bytes.MINUS_SIGN_BYTE;
            toProcess = -toProcess;
        }
        while (toProcess > 0) {
            digitsBuffer[digits++] = (byte) ('0' + toProcess % 10);
            toProcess /= 10;
        }
        ensureCapacity(digits);
        for (int i = --digits; i >= 0; i--) {
            buffer[index++] = digitsBuffer[i];
        }
    }

    @Override
    protected void writeFloat(float value) {
        //Performance improvement needed
        if (Float.isNaN(value)) {
            ensureCapacity(5);
            buffer[index++] = (byte) '"';
            buffer[index++] = (byte) 'N';
            buffer[index++] = (byte) 'a';
            buffer[index++] = (byte) 'N';
            buffer[index++] = (byte) '"';
            return;
        } else if (Float.NEGATIVE_INFINITY == value) {
            ensureCapacity(11);
            buffer[index++] = (byte) '"';
            buffer[index++] = (byte) '-';
            buffer[index++] = (byte) 'I';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'f';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 't';
            buffer[index++] = (byte) 'y';
            buffer[index++] = (byte) '"';
            return;
        } else if (Float.POSITIVE_INFINITY == value) {
            ensureCapacity(10);
            buffer[index++] = (byte) '"';
            buffer[index++] = (byte) 'I';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'f';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 't';
            buffer[index++] = (byte) 'y';
            buffer[index++] = (byte) '"';
            return;
        } else if (value == 0.0) {
            ensureCapacity(3);
            buffer[index++] = (byte) '0';
            buffer[index++] = (byte) '.';
            buffer[index++] = (byte) '0';
            return;
        }
        writeAscii(Float.toString(value));
    }

    @Override
    protected void writeDouble(double value) {
        //Performance improvement needed
        if (Double.isNaN(value)) {
            ensureCapacity(5);
            buffer[index++] = (byte) '"';
            buffer[index++] = (byte) 'N';
            buffer[index++] = (byte) 'a';
            buffer[index++] = (byte) 'N';
            buffer[index++] = (byte) '"';
            return;
        } else if (Double.NEGATIVE_INFINITY == value) {
            ensureCapacity(11);
            buffer[index++] = (byte) '"';
            buffer[index++] = (byte) '-';
            buffer[index++] = (byte) 'I';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'f';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 't';
            buffer[index++] = (byte) 'y';
            buffer[index++] = (byte) '"';
            return;
        } else if (Double.POSITIVE_INFINITY == value) {
            ensureCapacity(10);
            buffer[index++] = (byte) '"';
            buffer[index++] = (byte) 'I';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'f';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 'n';
            buffer[index++] = (byte) 'i';
            buffer[index++] = (byte) 't';
            buffer[index++] = (byte) 'y';
            buffer[index++] = (byte) '"';
            return;
        } else if (value == 0.0) {
            ensureCapacity(3);
            buffer[index++] = (byte) '0';
            buffer[index++] = (byte) '.';
            buffer[index++] = (byte) '0';
            return;
        }
        writeAscii(Double.toString(value));
    }

    @Override
    protected void writeBigDecimal(BigDecimal value) {
        writeAscii(value.toString());
    }

    @Override
    protected void writeBigInteger(BigInteger value) {
        writeAscii(value.toString());
    }

    @Override
    protected void writeBoolean(boolean value) {
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
    protected void writeBinaryArray(byte[] value) {
        ensureCapacity(1);
        buffer[index++] = Bytes.DOUBLE_QUOTE_BYTE;
        writeBuffer();
        byte[] data = Base64.getEncoder().encode(value);
        writeData(data, 0, data.length);
        buffer[index++] = Bytes.DOUBLE_QUOTE_BYTE;
    }

    @Override
    protected void writeNullValue() {
        ensureCapacity(4);
        buffer[index++] = 'n';
        buffer[index++] = 'u';
        buffer[index++] = 'l';
        buffer[index++] = 'l';
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                outputStream.write(buffer, 0, index);
                outputStream.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void writeBuffer() {
        if (index == 0) {
            return;
        }
        writeData(buffer, 0, index);
        index = 0;
    }

    private void writeData(byte[] array, int start, int length) {
        try {
            outputStream.write(array, start, length);
        } catch (IOException e) {
            throw new JsonException("Stream write failed", e);
        }
    }

    private void growBuffer(int requiredCapacity) {
        int newLength = buffer.length;
        while (newLength < requiredCapacity) {
            newLength <<= 1;
        }
        buffer = Arrays.copyOf(buffer, newLength);
    }

    private void writeAscii(String value) {
        writeAscii(value, 0, value.length());
    }

    private void writeBytes(byte[] value) {
        int length = value.length;
        ensureCapacity(length);
        System.arraycopy(value, 0, buffer, index, length);
        index += length;
    }

    @SuppressWarnings("deprecation")
    private void writeAscii(String value, int start, int end) {
        int remaining = end - start;
        while (remaining > 0) {
            if (index == buffer.length) {
                writeBuffer();
            }
            int chunkLength = Math.min(remaining, buffer.length - index);
            value.getBytes(start, start + chunkLength, buffer, index);
            index += chunkLength;
            start += chunkLength;
            remaining -= chunkLength;
        }
    }

    private byte[] digitsBuffer() {
        byte[] result = digits;
        if (result == null) {
            result = new byte[20];
            digits = result;
        }
        return result;
    }

    private static boolean isAsciiPlain(char c) {
        return c >= 0x20 && c < 0x80 && c != '"' && c != '\\';
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
