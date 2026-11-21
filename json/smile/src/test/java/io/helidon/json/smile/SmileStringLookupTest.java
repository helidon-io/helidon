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

import io.helidon.json.JsonException;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonStringLookup;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmileStringLookupTest {
    @Test
    void matchesDecodedNamesAcrossStringEncodings() {
        for (Input input : Input.values()) {
            String[] values = {"", "RED", "costarring", "liquid", "CAFÉ", "变量", "𐐀", "a\"b", "a\\b", "\n", "\u0000",
                    "a".repeat(32), "b".repeat(33), "c".repeat(64), "d".repeat(65), "é".repeat(32),
                    "é".repeat(32) + "x", "é".repeat(33)};
            JsonStringLookup lookup = JsonStringLookup.create(values);
            for (boolean shared : new boolean[] {false, true}) {
                JsonParser parser = input.parser(strings(shared, values));
                for (int index = 0; index < values.length; index++) {
                    assertThat(parser.nextToken(), is((byte) '"'));
                    assertThat(values[index], parser.readString(lookup), is(index));
                    assertThat(parser.nextToken(), is(index + 1 == values.length ? (byte) ']' : (byte) ','));
                }
            }
        }
    }

    @Test
    void rejectsUnknownCollisionsAndContinues() {
        for (Input input : Input.values()) {
            JsonStringLookup lookup = JsonStringLookup.create("project", "RED");
            JsonParser parser = input.parser(strings(false, "unknown-IpUce6", "RED"));
            parser.nextToken();
            assertThat(parser.readString(lookup), is(-1));
            assertThat(parser.nextToken(), is((byte) ','));
            parser.nextToken();
            assertThat(parser.readString(lookup), is(1));
            assertThat(parser.nextToken(), is((byte) ']'));
        }
    }

    @Test
    void preservesSharedValuesAcrossLookupsAndReadMethods() {
        for (Input input : Input.values()) {
            JsonStringLookup red = JsonStringLookup.create("RED");
            JsonStringLookup blue = JsonStringLookup.create("BLUE");
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
            JsonStringLookup key = JsonStringLookup.create("key\"");
            JsonStringLookup value = JsonStringLookup.create("RED");
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
            JsonStringLookup lookup = JsonStringLookup.create("é", "?", "\uFFFD", "\uD800");
            for (byte[] malformed : new byte[][] {
                    {0x3a, 0x29, 0x0a, 0, 0x41, (byte) 0xc3, (byte) 0xa9},
                    {0x3a, 0x29, 0x0a, 0, (byte) 0x80, (byte) 0xc0, (byte) 0xaf},
                    {0x3a, 0x29, 0x0a, 0, (byte) 0x81, (byte) 0xed, (byte) 0xa0, (byte) 0x80}}) {
                JsonParser parser = input.parser(malformed);
                assertThrows(JsonException.class, () -> parser.readString(lookup));
            }
            JsonParser truncated = input.parser(new byte[] {0x3a, 0x29, 0x0a, 0, 0x43, 'R', 'E'});
            assertThrows(JsonException.class, () -> truncated.readString(lookup));
        }
    }

    @Test
    void validatesLookupAndStringTokenWithoutConsumingInput() {
        for (Input input : Input.values()) {
            JsonStringLookup lookup = JsonStringLookup.create("RED");
            JsonParser parser = input.parser(strings(false, "RED"));
            assertThrows(JsonException.class, () -> parser.readString(lookup));
            assertThat(parser.currentByte(), is((byte) '['));
            parser.nextToken();
            assertThrows(NullPointerException.class, () -> parser.readString(null));
            assertThat(parser.readString(lookup), is(0));
        }
    }

    @Test
    void preservesMarksAcrossLookupReadsAndSharedReferences() {
        for (Input input : Input.values()) {
            for (boolean shared : new boolean[] {false, true}) {
                for (String value : new String[] {"RED", "é".repeat(32) + "x"}) {
                    JsonStringLookup lookup = JsonStringLookup.create(value);
                    JsonParser parser = input.parser(strings(shared, value, value));
                    parser.nextToken();
                    parser.mark();
                    assertThat(parser.readString(lookup), is(0));
                    parser.resetToMark();
                    assertThat(parser.currentByte(), is((byte) '"'));
                    assertThat(parser.readString(lookup), is(0));
                    assertThat(parser.nextToken(), is((byte) ','));
                    parser.nextToken();
                    assertThat(parser.readString(lookup), is(0));
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
