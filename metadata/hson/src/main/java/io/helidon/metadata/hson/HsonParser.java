/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.metadata.hson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;

class HsonParser {
    private static final int MAX_FIELD_LENGTH = 64000;
    private static final byte COMMA = (byte) ',';
    private static final byte QUOTES = (byte) '"';
    private static final byte ARRAY_START = (byte) '[';
    private static final byte ARRAY_END = (byte) ']';
    private static final byte OBJECT_START = (byte) '{';
    private static final byte OBJECT_END = (byte) '}';
    private static final byte BACKSLASH = (byte) '\\';

    private final DataReader reader;
    private int position;

    private HsonParser(DataReader reader) {
        this.reader = reader;
    }

    static Hson.Value<?> parse(InputStream stream) {
        DataReader dr = new DataReader(() -> {
            byte[] buffer = new byte[1024];
            try {
                int num = stream.read(buffer);
                if (num > 0) {
                    return buffer;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            return null;
        });
        return new HsonParser(dr).read(true);
    }

    private Hson.Value<?> read(boolean topLevel) {
        byte next = skipWhitespace();
        if (next == ARRAY_START) {
            return readArray();
        } else if (next == OBJECT_START) {
            return readObject();
        }
        if (topLevel) {
            throw new HsonException("Index: " + position
                                            + ": failed to parse HSON, invalid object/array opening character: \n"
                                            + BufferData.create(new byte[] {next}).debugDataHex());
        }
        if (next == QUOTES) {
            return readString("Object");
        }
        return readValue();
    }

    private Hson.Value<?> readArray() {
        skip(); // skip [

        List<Hson.Value<?>> values = new ArrayList<>();

        while (true) {
            byte next = skipWhitespace();
            if (next == ARRAY_END) {
                skip();
                // end of array
                return Hson.Array.create(values);
            }

            Hson.Value<?> value = switch (next) {
                case OBJECT_START -> readObject();
                case ARRAY_START -> readArray();
                case QUOTES -> readString("Array");
                default -> readValue();
            };
            values.add(value);

            next = skipWhitespace();
            if (next == COMMA) {
                skip(); // ,
            } else {
                // this must be array end
                next = skipWhitespace();
                if (next == ARRAY_END) {
                    skip();
                    return Hson.Array.create(values);
                } else {
                    throw new HsonException("Index: " + position
                                                    + ": value not followed by a comma, and array does not end");
                }
            }
        }
    }

    private Hson.Value<Hson.Object> readObject() {
        skip(); // skip {

        var object = Hson.Object.builder();

        while (true) {
            byte next = skipWhitespace();
            if (next == OBJECT_END) {
                skip(); // skip }
                return object.build();
            }
            // now we have "key": value (may be an object, value, string)
            String key = readKey();
            skipWhitespace();
            next = read();
            if (next != Bytes.COLON_BYTE) {
                throw new HsonException("Index: " + position
                                                + ": key is not followed by a colon. Key: " + BufferData.create(key)
                        .debugDataHex());
            }
            skipWhitespace();
            // the value may be object, array, value
            Hson.Value<?> value = read(false);
            object.set(key, value);
            next = skipWhitespace();
            if (next == COMMA) {
                skip(); // ,
            } else {
                // this must be object end
                next = skipWhitespace();
                if (next == OBJECT_END) {
                    skip(); // skip }
                    return object.build();
                } else {
                    throw new HsonException("Index: " + position
                                                    + ": value not followed by a comma, and object does not end. Found: \n"
                                                    + BufferData.create(new byte[] {next}).debugDataHex() + ", for key: \n"
                                                    + BufferData.create(key).debugDataHex());
                }
            }
        }
    }

    private String readKey() {
        byte read = reader.lookup();
        if (read != QUOTES) {
            throw new HsonException("Index: " + position
                                            + ": keys must be quoted, invalid beginning of key");
        }
        return readString("Key").value();
    }

    private Hson.Value<String> readString(String type) {
        skip(); // skip "

        // now go until the first unescaped quotes
        ByteArrayOutputStream value = new ByteArrayOutputStream();
        int count = 0;
        boolean escaping = false;
        while (count < MAX_FIELD_LENGTH) {
            byte next = reader.read();

            if (!escaping && next == QUOTES) {
                return HsonValues.StringValue.create(value.toString(StandardCharsets.UTF_8));
            }
            if (escaping) {
                escaping = false;
                char nextChar = (char) (next & 0xff);
                if (nextChar == 'u') {
                    // there must be 4 hexadecimal digits after this
                    String hexadecimalEscape = reader.readAsciiString(4);
                    value.write((char) Integer.parseInt(hexadecimalEscape, 16));
                } else {
                    byte toWrite = switch (nextChar) {
                        case 'f' -> (byte) '\f';
                        case 'n' -> (byte) '\n';
                        case 'r' -> (byte) '\r';
                        case 't' -> (byte) '\t';
                        case 'b' -> (byte) '\b';
                        case '\\' -> (byte) '\\';
                        case '\"' -> (byte) '\"';
                        case '/' -> (byte) '/';
                        default -> throw new HsonException("Index " + position
                                                                   + ": invalid escape char after backslash: '"
                                                                   + nextChar + "'");
                    };
                    value.write(toWrite);
                }
            } else if (next == BACKSLASH) {
                escaping = true;
            } else {
                value.write(next);
            }
            count++;
        }

        throw new HsonException("Index: " + position
                                        + ": " + type + " failed to find end quotes, or length is bigger than allowed. Max "
                                        + "length: "
                                        + MAX_FIELD_LENGTH + " bytes");
    }

    private Hson.Value<?> readValue() {
        String value = toNonStringValueEnd();

        // true | false, integer, double
        if ("true".equals(value) || "false".equals(value)) {
            return HsonValues.BooleanValue.create(Boolean.parseBoolean(value));
        }
        if ("null".equals(value)) {
            return HsonValues.NullValue.INSTANCE;
        }
        try {
            return HsonValues.NumberValue.create(new BigDecimal(value));
        } catch (NumberFormatException e) {
            throw new HsonException("Index: " + position
                                            + ": cannot parse HSON value into a number. Data: "
                                            + BufferData.create(value).debugDataHex());
        }
    }

    private String toNonStringValueEnd() {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();

        // anything from here to next whitespace or comma (separating values)
        while (true) {
            byte next = reader.lookup();
            if (whitespace(next)) {
                break;
            }
            if (next == COMMA || next == ARRAY_END || next == OBJECT_END) {
                break;
            }
            skip();
            bo.write(next);
        }

        return bo.toString(StandardCharsets.US_ASCII);
    }

    private byte skipWhitespace() {
        while (true) {
            byte lookup = reader.lookup();
            if (whitespace(lookup)) {
                skip();
            } else {
                return lookup;
            }
        }
    }

    private boolean whitespace(byte lookup) {
        return switch (lookup) {
            case Bytes.SPACE_BYTE, Bytes.TAB_BYTE, Bytes.CR_BYTE, Bytes.LF_BYTE -> true;
            default -> false;
        };
    }

    private void skip() {
        reader.skip(1);
        position++;
    }

    private byte read() {
        byte r = reader.read();
        position++;
        return r;
    }
}
