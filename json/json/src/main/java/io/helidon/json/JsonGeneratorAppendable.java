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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.CharBuffer;
import java.util.Base64;

import static java.util.Objects.requireNonNull;

class JsonGeneratorAppendable extends JsonGeneratorBase {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    private static final CharSequence INDENT_CS = CharBuffer.wrap(INDENT);

    private final Appendable appendable;

    JsonGeneratorAppendable(Appendable appendable, boolean prettyPrint) {
        super(prettyPrint);
        this.appendable = requireNonNull(appendable, "appendable");
    }

    @Override
    protected void writeByteExact(byte value) {
        try {
            appendable.append((char) value);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write byte value", e);
        }
    }

    @Override
    protected void writeNewLineIndent(int indentLevel) {
        try {
            appendable.append('\n');
            appendable.append(INDENT_CS, 0, indentLevel * INDENT_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write indentation.", e);
        }
    }

    @Override
    protected void writeInt(int value) {
        writeLong(value);
    }

    @Override
    protected void writeLong(long value) {
        try {
            appendable.append(Long.toString(value));
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
            appendable.append(Float.toString(value));
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
            appendable.append(Double.toString(value));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write double value.", e);
        }
    }

    @Override
    protected void writeBigDecimal(BigDecimal value) {
        try {
            appendable.append(value.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write BigDecimal value.", e);
        }
    }

    @Override
    protected void writeBigInteger(BigInteger value) {
        try {
            appendable.append(value.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write BigInteger value.", e);
        }
    }

    @Override
    protected void writeString(String value) {
        try {
            appendable.append('\"');
            for (int i = 0; i < value.length(); i++) {
                writeJsonChar(value.charAt(i));
            }
            appendable.append('\"');
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write String value.", e);
        }
    }

    @Override
    protected void writeKeyName(JsonKey value) {
        try {
            appendable.append(CharBuffer.wrap(value.quotedChars()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write key value.", e);
        }
    }

    @Override
    protected void writeChar(char value) {
        try {
            appendable.append('\"');
            writeJsonChar(value);
            appendable.append('\"');
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write char value.", e);
        }
    }

    @Override
    protected void writeBoolean(boolean value) {
        try {
            if (value) {
                appendable.append("true");
            } else {
                appendable.append("false");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write boolean value.", e);
        }
    }

    @Override
    protected void writeBinaryArray(byte[] value) {
        try {
            appendable.append('\"');
            appendable.append(Base64.getEncoder().encodeToString(value));
            appendable.append('\"');
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write binary data value.", e);
        }
    }

    @Override
    protected void writeNullValue() {
        try {
            appendable.append("null");
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
            appendable.append("\\b");
            return;
        case '\f':
            appendable.append("\\f");
            return;
        case '\n':
            appendable.append("\\n");
            return;
        case '\r':
            appendable.append("\\r");
            return;
        case '\t':
            appendable.append("\\t");
            return;
        case '\\':
            appendable.append("\\\\");
            return;
        case '\"':
            appendable.append("\\\"");
            return;
        default:
            if (c < 0x20 || Character.isSurrogate(c)) {
                writeUnicodeEscape(c);
            } else {
                appendable.append(c);
            }
        }
    }

    private void writeUnicodeEscape(char c) throws IOException {
        appendable.append('\\');
        appendable.append('u');
        appendable.append(HEX_DIGITS[(c >> 12) & 0xF]);
        appendable.append(HEX_DIGITS[(c >> 8) & 0xF]);
        appendable.append(HEX_DIGITS[(c >> 4) & 0xF]);
        appendable.append(HEX_DIGITS[c & 0xF]);
    }
}
