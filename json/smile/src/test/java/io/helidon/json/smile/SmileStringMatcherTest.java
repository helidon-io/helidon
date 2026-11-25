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

package io.helidon.json.smile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import io.helidon.json.JsonException;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonStringMatcher;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmileStringMatcherTest {
    @Test
    void matchesDecodedNamesAcrossStringEncodings() {
        for (Input input : Input.values()) {
            String[] values = {"", "RED", "costarring", "liquid", "CAFÉ", "变量", "𐐀", "a\"b", "a\\b", "\n", "\u0000",
                    "a".repeat(32), "b".repeat(33), "c".repeat(64), "d".repeat(65), "é".repeat(32),
                    "é".repeat(32) + "x", "é".repeat(33)};
            JsonStringMatcher matcher = JsonStringMatcher.create(values);
            for (boolean shared : new boolean[] {false, true}) {
                JsonParser parser = input.parser(strings(shared, values));
                for (int index = 0; index < values.length; index++) {
                    assertThat(parser.nextToken(), is((byte) '"'));
                    assertThat(values[index], parser.readString(matcher), is(index));
                    assertThat(parser.nextToken(), is(index + 1 == values.length ? (byte) ']' : (byte) ','));
                }
            }
        }
    }

    @Test
    void rejectsUnknownCollisionsAndContinues() {
        for (Input input : Input.values()) {
            JsonStringMatcher matcher = JsonStringMatcher.create("project", "RED");
            JsonParser parser = input.parser(strings(false, "unknown-IpUce6", "RED"));
            parser.nextToken();
            assertThat(parser.readString(matcher), is(-1));
            assertThat(parser.nextToken(), is((byte) ','));
            parser.nextToken();
            assertThat(parser.readString(matcher), is(1));
            assertThat(parser.nextToken(), is((byte) ']'));
        }
    }

    @Test
    void preservesSharedValuesAcrossMatchersAndReadMethods() {
        for (Input input : Input.values()) {
            JsonStringMatcher red = JsonStringMatcher.create("RED");
            JsonStringMatcher blue = JsonStringMatcher.create("BLUE");
            JsonParser parser = input.parser(strings(true, "RED", "BLUE", "RED", "BLUE", "RED", "BLUE"));
            parser.nextToken();
            assertThat(parser.readString(red), is(0));
            parser.nextToken();
            parser.nextToken();
            assertThat(parser.readString(red), is(-1));
            parser.nextToken();
            parser.nextToken();
            assertThat(parser.readString(blue), is(-1));
            parser.nextToken();
            parser.nextToken();
            assertThat(parser.readString(blue), is(0));
            parser.nextToken();
            parser.nextToken();
            assertThat(parser.readString(), is("RED"));
            parser.nextToken();
            parser.nextToken();
            assertThat(parser.readStringAsHash(), is(JsonParser.create("\"BLUE\"").readStringAsHash()));
            assertThat(parser.nextToken(), is((byte) ']'));
        }
    }

    @Test
    void preservesObjectKeysAndValueState() {
        for (Input input : Input.values()) {
            JsonStringMatcher key = JsonStringMatcher.create("key\"");
            JsonStringMatcher otherOrder = JsonStringMatcher.create("other", "key\"");
            JsonStringMatcher value = JsonStringMatcher.create("RED");
            for (boolean sharedKeys : new boolean[] {false, true}) {
                for (boolean sharedValues : new boolean[] {false, true}) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    SmileConfig config = SmileConfig.builder()
                            .sharedKeyStrings(sharedKeys)
                            .sharedValueStrings(sharedValues)
                            .build();
                    try (JsonGenerator generator = SmileGenerator.create(bytes, config)) {
                        generator.writeArrayStart();
                        for (int i = 0; i < 2; i++) {
                            generator.writeObjectStart();
                            generator.writeKey("key\"");
                            generator.write("RED");
                            generator.writeObjectEnd();
                        }
                        generator.writeArrayEnd();
                    }
                    JsonParser parser = input.parser(bytes.toByteArray());
                    for (int i = 0; i < 2; i++) {
                        assertThat(parser.nextToken(), is((byte) '{'));
                        parser.nextToken();
                        parser.mark();
                        assertThat(parser.readProperty(otherOrder), is(1));
                        assertThat(parser.readString(value), is(0));
                        parser.resetToMark();
                        assertThat(parser.readString(key), is(0));
                        assertThat(parser.nextToken(), is((byte) ':'));
                        parser.nextToken();
                        assertThat(parser.readString(value), is(0));
                        assertThat(parser.nextToken(), is((byte) '}'));
                        assertThat(parser.nextToken(), is(i == 0 ? (byte) ',' : (byte) ']'));
                    }
                }
            }
        }
    }

    @Test
    void validatesEncodingEvenForUnknownOrMatchingBytes() {
        for (Input input : Input.values()) {
            JsonStringMatcher matcher = JsonStringMatcher.create("é", "?", "\uFFFD", "\uD800");
            for (byte[] malformed : new byte[][] {
                    {0x3a, 0x29, 0x0a, 0, 0x41, (byte) 0xc3, (byte) 0xa9},
                    {0x3a, 0x29, 0x0a, 0, (byte) 0x80, (byte) 0xc0, (byte) 0xaf},
                    {0x3a, 0x29, 0x0a, 0, (byte) 0x81, (byte) 0xed, (byte) 0xa0, (byte) 0x80}}) {
                JsonParser parser = input.parser(malformed);
                assertThrows(JsonException.class, () -> parser.readString(matcher));
            }
            JsonParser truncated = input.parser(new byte[] {0x3a, 0x29, 0x0a, 0, 0x43, 'R', 'E'});
            assertThrows(JsonException.class, () -> truncated.readString(matcher));
        }
    }

    @Test
    void matchesCompleteUnsharedLiteralKeysAtWordBoundaries() {
        String[] keys = {"x", "quoted\"", "12345678", "12345678x", "a".repeat(15), "a".repeat(16),
                "b".repeat(63), "b".repeat(64), "c".repeat(65), "€"};
        JsonStringMatcher matcher = JsonStringMatcher.create(keys);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(output, SmileConfig.builder().sharedKeyStrings(false).build())) {
            generator.writeObjectStart();
            for (int i = 0; i < keys.length; i++) {
                generator.write(keys[i], i);
            }
            generator.write("b".repeat(62) + "x", -1);
            generator.writeObjectEnd();
        }
        for (Input input : Input.values()) {
            JsonParser parser = input.parser(output.toByteArray());
            parser.nextToken();
            for (int i = 0; i < keys.length; i++) {
                assertThat(keys[i], i == 0 ? parser.readProperty(matcher) : parser.nextProperty(matcher), is(i));
                assertThat(parser.readInt(), is(i));
            }
            assertThat(parser.nextProperty(matcher), is(-1));
            assertThat(parser.readInt(), is(-1));
            assertThat(parser.nextProperty(matcher), is(JsonParser.END_OBJECT));
        }
    }

    @Test
    void validatesEveryByteOfUnknownUnsharedAsciiKeys() {
        JsonStringMatcher matcher = JsonStringMatcher.create("known");
        byte[] header = {0x3a, 0x29, 0x0a, 0, SmileConstants.TOKEN_START_OBJECT,
                (byte) (SmileConstants.KEY_SHORT_ASCII_PREFIX + 16)};
        for (int badIndex : new int[] {0, 9, 16}) {
            int end = header.length + 17;
            byte[] bytes = Arrays.copyOf(header, end + 2);
            Arrays.fill(bytes, header.length, end, (byte) 'a');
            bytes[header.length + badIndex] = (byte) 0x80;
            bytes[end] = SmileConstants.TOKEN_NULL;
            bytes[end + 1] = SmileConstants.TOKEN_END_OBJECT;
            for (Input input : Input.values()) {
                JsonParser parser = input.parser(bytes);
                parser.nextToken();
                JsonException exception = assertThrows(JsonException.class, () -> parser.readString(matcher));
                assertThat(exception.getMessage(), containsString("non-ASCII"));
            }
        }
    }

    @Test
    void rejectsTruncatedUnsharedAsciiKey() {
        byte[] bytes = {0x3a, 0x29, 0x0a, 0, SmileConstants.TOKEN_START_OBJECT,
                (byte) (SmileConstants.KEY_SHORT_ASCII_PREFIX + 7), 'a', 'b', 'c'};
        JsonStringMatcher matcher = JsonStringMatcher.create("abcdefgh");
        for (Input input : Input.values()) {
            JsonParser parser = input.parser(bytes);
            parser.nextToken();
            assertThrows(JsonException.class, () -> parser.readString(matcher));
        }
    }

    @Test
    void validatesMatcherAndStringTokenWithoutConsumingInput() {
        for (Input input : Input.values()) {
            JsonStringMatcher matcher = JsonStringMatcher.create("RED");
            JsonParser parser = input.parser(strings(false, "RED"));
            assertThrows(JsonException.class, () -> parser.readString(matcher));
            assertThat(parser.currentByte(), is((byte) '['));
            parser.nextToken();
            assertThrows(NullPointerException.class, () -> parser.readString(null));
            assertThat(parser.readString(matcher), is(0));
        }
    }

    @Test
    void preservesMarksAcrossMatcherReadsAndSharedReferences() {
        for (Input input : Input.values()) {
            for (boolean shared : new boolean[] {false, true}) {
                for (String value : new String[] {"RED", "é".repeat(32) + "x"}) {
                    JsonStringMatcher matcher = JsonStringMatcher.create(value);
                    JsonParser parser = input.parser(strings(shared, value, value));
                    parser.nextToken();
                    parser.mark();
                    assertThat(parser.readString(matcher), is(0));
                    parser.resetToMark();
                    assertThat(parser.currentByte(), is((byte) '"'));
                    assertThat(parser.readString(matcher), is(0));
                    assertThat(parser.nextToken(), is((byte) ','));
                    parser.nextToken();
                    assertThat(parser.readString(matcher), is(0));
                    assertThat(parser.nextToken(), is((byte) ']'));
                }
            }
        }
    }

    private static byte[] strings(boolean sharedValues, String... values) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SmileConfig config = SmileConfig.builder().sharedValueStrings(sharedValues).build();
        try (JsonGenerator generator = SmileGenerator.create(bytes, config)) {
            generator.writeArrayStart();
            for (String value : values) {
                generator.write(value);
            }
            generator.writeArrayEnd();
        }
        return bytes.toByteArray();
    }

    enum Input {
        ARRAY,
        SMALL_STREAM,
        DEFAULT_STREAM;

        JsonParser parser(byte[] bytes) {
            return this == ARRAY ? SmileParser.create(bytes)
                    : SmileParser.create(new ByteArrayInputStream(bytes), this == SMALL_STREAM ? 8 : 512);
        }
    }
}
