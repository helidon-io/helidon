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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.ToLongFunction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NegativeIntegerBoundaryTest {

    @ParameterizedTest
    @EnumSource(IntegerReader.class)
    void readsNegativeValuesAcrossBufferBoundary(IntegerReader reader) {
        for (int padding : new int[] {510, 511, 512}) {
            for (String value : new String[] {"-5", "-0", Long.toString(reader.minimum)}) {
                String json = " ".repeat(padding) + value;
                JsonParser parser = JsonParser.create(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

                assertThat("padding " + padding + ", value " + value, reader.read.applyAsLong(parser), is(Long.parseLong(value)));
                assertThat(parser.hasNext(), is(false));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(IntegerReader.class)
    void readsNegativeValuesFromOneByteReads(IntegerReader reader) {
        JsonParser parser = JsonParser.create(new OneByteInputStream("[-12,-34," + reader.minimum + ",-0]"));
        for (long expected : new long[] {-12, -34, reader.minimum, 0}) {
            assertThat(parser.nextToken(), is((byte) '-'));
            assertThat(reader.read.applyAsLong(parser), is(expected));
            assertThat(parser.nextToken(), is(expected == 0 ? (byte) ']' : (byte) ','));
        }
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(IntegerReader.class)
    void rejectsTruncatedSignAtBufferBoundary(IntegerReader reader) {
        for (int padding : new int[] {510, 511, 512}) {
            String json = " ".repeat(padding) + "-";
            JsonParser parser = JsonParser.create(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            assertThrows(JsonException.class, () -> reader.read.applyAsLong(parser));
        }
        JsonParser parser = JsonParser.create(new OneByteInputStream("-"));
        assertThrows(JsonException.class, () -> reader.read.applyAsLong(parser));
    }

    @ParameterizedTest
    @EnumSource(IntegerReader.class)
    void rejectsInvalidDigitAfterBoundarySign(IntegerReader reader) {
        for (String value : new String[] {"-]", "- ", "-x"}) {
            String json = " ".repeat(511) + value;
            JsonParser parser = JsonParser.create(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            JsonException exception = assertThrows(JsonException.class, () -> reader.read.applyAsLong(parser));
            assertThat(exception.getMessage(), containsString("Expected number"));

            JsonParser shortReadParser = JsonParser.create(new OneByteInputStream(value));
            exception = assertThrows(JsonException.class, () -> reader.read.applyAsLong(shortReadParser));
            assertThat(exception.getMessage(), containsString("Expected number"));
        }
    }

    @ParameterizedTest
    @EnumSource(IntegerReader.class)
    void rejectsNegativeOverflowAcrossBufferBoundary(IntegerReader reader) {
        String json = " ".repeat(511) + reader.overflow;
        JsonParser parser = JsonParser.create(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        JsonException exception = assertThrows(JsonException.class, () -> reader.read.applyAsLong(parser));
        assertThat(exception.getMessage(), containsString("too big"));

        JsonParser shortReadParser = JsonParser.create(new OneByteInputStream(reader.overflow));
        exception = assertThrows(JsonException.class, () -> reader.read.applyAsLong(shortReadParser));
        assertThat(exception.getMessage(), containsString("too big"));
    }

    private enum IntegerReader {
        BYTE(JsonParser::readByte, Byte.MIN_VALUE, "-129"),
        SHORT(JsonParser::readShort, Short.MIN_VALUE, "-32769"),
        INT(JsonParser::readInt, Integer.MIN_VALUE, "-2147483649"),
        LONG(JsonParser::readLong, Long.MIN_VALUE, "-9223372036854775809");

        private final ToLongFunction<JsonParser> read;
        private final long minimum;
        private final String overflow;

        IntegerReader(ToLongFunction<JsonParser> read, long minimum, String overflow) {
            this.read = read;
            this.minimum = minimum;
            this.overflow = overflow;
        }
    }

    private static final class OneByteInputStream extends ByteArrayInputStream {
        OneByteInputStream(String json) {
            super(json.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return super.read(bytes, offset, Math.min(length, 1));
        }
    }
}
