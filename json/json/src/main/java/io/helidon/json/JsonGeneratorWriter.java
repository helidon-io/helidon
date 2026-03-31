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
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Base64;

class JsonGeneratorWriter extends JsonGeneratorBase {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    private static final char[] TRUE = "true".toCharArray();
    private static final char[] FALSE = "false".toCharArray();
    private static final char[] NULL = "null".toCharArray();

    private final Writer writer;

    JsonGeneratorWriter(Writer writer) {
        this.writer = writer;
    }

    @Override
    protected void writeByteExact(byte value) {
        try {
            writer.write(value);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write byte value", e);
        }
    }

    @Override
    protected void writeInt(int value) {
        writeLong(value);
    }

    @Override
    protected void writeLong(long value) {
        try {
            writer.write(Long.toString(value));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write long value.", e);
        }
    }

    @Override
    protected void writeFloat(float value) {
        if (Float.isNaN(value)) {
            writeString("NaN");
            return;
        } else if (Float.NEGATIVE_INFINITY == value) {
            writeString("-Infinity");
            return;
        } else if (Float.POSITIVE_INFINITY == value) {
            writeString("Infinity");
            return;
        }
        try {
            writer.write(Float.toString(value));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write float value.", e);
        }
    }

    @Override
    protected void writeDouble(double value) {
        if (Double.isNaN(value)) {
            writeString("NaN");
            return;
        } else if (Double.NEGATIVE_INFINITY == value) {
            writeString("-Infinity");
            return;
        } else if (Double.POSITIVE_INFINITY == value) {
            writeString("Infinity");
            return;
        }
        try {
            writer.write(Double.toString(value));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write double value.", e);
        }
    }

    @Override
    protected void writeBigDecimal(BigDecimal value) {
        try {
            writer.write(value.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write BigDecimal value.", e);
        }
    }

    @Override
    protected void writeBigInteger(BigInteger value) {
        try {
            writer.write(value.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write BigInteger value.", e);
        }
    }

    @Override
    protected void writeString(String value) {
        try {
            writer.write('\"');
            for (int i = 0; i < value.length(); i++) {
                writeJsonChar(value.charAt(i));
            }
            writer.write('\"');
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write String value.", e);
        }
    }

    @Override
    protected void writeKeyName(JsonKey value) {
        try {
            writer.write(value.quotedChars());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write key value.", e);
        }
    }

    @Override
    protected void writeChar(char value) {
        try {
            writer.write('\"');
            writeJsonChar(value);
            writer.write('\"');
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write char value.", e);
        }
    }

    @Override
    protected void writeBoolean(boolean value) {
        try {
            if (value) {
                writer.write(TRUE);
            } else {
                writer.write(FALSE);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write boolean value.", e);
        }
    }

    @Override
    protected void writeBinaryArray(byte[] value) {
        try {
            writer.write('\"');
            writer.write(Base64.getEncoder().encodeToString(value));
            writer.write('\"');
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write binary data value.", e);
        }
    }

    @Override
    protected void writeNullValue() {
        try {
            writer.write(NULL);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write null value.", e);
        }
    }

    @Override
    public void close() {
    }

    private void writeJsonChar(char c) throws IOException {
        switch (c) {
        case '\b':
            writer.write("\\b");
            return;
        case '\f':
            writer.write("\\f");
            return;
        case '\n':
            writer.write("\\n");
            return;
        case '\r':
            writer.write("\\r");
            return;
        case '\t':
            writer.write("\\t");
            return;
        case '\\':
            writer.write("\\\\");
            return;
        case '\"':
            writer.write("\\\"");
            return;
        default:
            if (c < 0x20 || Character.isSurrogate(c)) {
                writeUnicodeEscape(c);
            } else {
                writer.write(c);
            }
        }
    }

    private void writeUnicodeEscape(char c) throws IOException {
        writer.write('\\');
        writer.write('u');
        writer.write(HEX_DIGITS[(c >> 12) & 0xF]);
        writer.write(HEX_DIGITS[(c >> 8) & 0xF]);
        writer.write(HEX_DIGITS[(c >> 4) & 0xF]);
        writer.write(HEX_DIGITS[c & 0xF]);
    }
}
