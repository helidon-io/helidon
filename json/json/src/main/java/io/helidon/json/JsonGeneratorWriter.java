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

class JsonGeneratorWriter extends AbstractJsonGenerator {

    private static final char[] TRUE = "true".toCharArray();
    private static final char[] FALSE = "false".toCharArray();
    private static final char[] NULL = "null".toCharArray();

    private final Writer writer;

    JsonGeneratorWriter(Writer writer) {
        this.writer = writer;
    }

    @Override
    void writeByteExact(byte value) {
        try {
            writer.write(value);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write byte value", e);
        }
    }

    @Override
    void writeLong(long value) {
        try {
            writer.write(Long.toString(value));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write long value.", e);
        }
    }

    @Override
    void writeFloat(float value) {
        try {
            writer.write(Float.toString(value));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write float value.", e);
        }
    }

    @Override
    void writeDouble(double value) {
        try {
            writer.write(Double.toString(value));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write double value.", e);
        }
    }

    @Override
    void writeString(String value) {
        try {
            writer.write('\"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                case '\n':
                    writer.write("\\n");
                    break;
                case '\r':
                    writer.write("\\r");
                    break;
                case '\t':
                    writer.write("\\t");
                    break;
                case '\\':
                    writer.write("\\\\");
                    break;
                case '\"':
                    writer.write("\\\"");
                    break;
                default:
                    // Check if the character is printable.
                    // If not, print its unicode value (e.g., \u0000)
                    if (c < 32 || c > 126) {
                        writer.write(String.format("\\u%04x", (int) c));
                    } else {
                        writer.write(c);
                    }
                }
            }
            writer.write('\"');
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write String value.", e);
        }
    }

    @Override
    void writeChar(char value) {
        try {
            writer.write('\"');
            writer.write(value);
            writer.write('\"');
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write char value.", e);
        }
    }

    @Override
    void writeBoolean(boolean value) {
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
    void writeNullValue() {
        try {
            writer.write(NULL);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write null value.", e);
        }
    }

    @Override
    public void close() {
    }
}
