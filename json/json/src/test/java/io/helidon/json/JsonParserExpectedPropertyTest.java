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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonParserExpectedPropertyTest {

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void readsTheExpectedPropertyAndPreservesTheCursorOnAMiss(ParserMethod method) {
        JsonParser parser = method.createParser("{\"first\":0,\"costarring\":true,\"a.property.longer.than.two.words\":[1]}", 128);
        readFirstProperty(parser);
        parser.mark();

        assertThat(parser.tryNextProperty(JsonKey.create("liquid")), is(false));
        assertThat(parser.currentByte(), is((byte) '0'));
        assertThat(parser.tryNextProperty(JsonKey.create("costarring")), is(true));
        assertThat(parser.currentByte(), is((byte) 't'));
        assertThat(parser.readBoolean(), is(true));
        assertThat(parser.tryNextProperty(JsonKey.create("a.property.longer.than.two.words")), is(true));
        assertThat(parser.currentByte(), is((byte) '['));
        parser.skip();
        assertThat(parser.nextToken(), is((byte) '}'));

        parser.resetToMark();
        assertThat(parser.currentByte(), is((byte) '0'));
        assertThat(parser.tryNextProperty(JsonKey.create("costarring")), is(true));
        assertThat(parser.readBoolean(), is(true));
    }

    @Test
    void expectedPropertyRespectsTheInputSlice() {
        byte[] padded = "prefix{\"first\":0,\"amount\":27}".getBytes(StandardCharsets.UTF_8);
        int start = "prefix".length();
        int length = "{\"first\":0,\"amount\":".length();
        JsonParser parser = JsonParser.create(padded, start, length);
        readFirstProperty(parser);

        assertThat(parser.tryNextProperty(JsonKey.create("amount")), is(false));
        assertThat(parser.currentByte(), is((byte) '0'));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void expectedPropertyCannotConsumeAnUnpairedSurrogate(ParserMethod method) {
        JsonParser parser = method.createParser("{\"first\":0,\"\\uD800\":1}", 64);
        readFirstProperty(parser);

        assertThat(parser.tryNextProperty(JsonKey.create("\uD800")), is(false));
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThrows(JsonException.class, () -> parser.readString(JsonStringMatcher.create("other")));
    }

    @Test
    void checksEveryBufferedFrameBoundaryAndKeepsTheMissPosition() {
        for (int length : List.of(0, 1, 3, 4, 5, 11, 12, 13, 23, 24)) {
            String name = "abcdefghijklmnopqrstuvwx".substring(0, length);
            JsonKey key = JsonKey.create(name);
            String prefix = "{\"first\":0";
            String header = ",\"" + name + "\":";
            byte[] bytes = ("xx" + prefix + header + "1},0123456789").getBytes(StandardCharsets.UTF_8);
            int value = prefix.length() + header.length();
            for (int size = prefix.length(); size <= value; size++) {
                JsonParser p = JsonParser.create(bytes, 2, size);
                readFirstProperty(p);
                assertThat(p.tryNextProperty(key), is(false));
                assertThat(p.currentByte(), is((byte) '0'));
            }
            JsonParser present = JsonParser.create(bytes, 2, value + 1);
            readFirstProperty(present);
            assertThat(present.tryNextProperty(key), is(true));
            assertThat(present.readInt(), is(1));
            for (int i = 0; i < header.length(); i++) {
                byte[] changed = bytes.clone();
                changed[2 + prefix.length() + i] ^= 1;
                JsonParser p = JsonParser.create(changed, 2, value + 2);
                readFirstProperty(p);
                assertThat(p.tryNextProperty(key), is(false));
                assertThat(p.currentByte(), is((byte) '0'));
            }
        }
    }

    @Test
    void declinesAProbeWithoutRefillingAndAllowsTheNormalReaderToContinue() {
        JsonParser parser = ParserMethod.STREAM.createParser("{\"first\":0,\"next\":1}", 6);
        readFirstProperty(parser);
        assertThat(parser.tryNextProperty(JsonKey.create("next")), is(false));
        assertThat(parser.currentByte(), is((byte) '0'));
        assertThat(parser.nextProperty(JsonStringMatcher.create("next")), is(0));
        assertThat(parser.readInt(), is(1));
    }

    @Test
    void firstProbeChecksItsCompleteFrameAndLogicalSlice() {
        for (int length : List.of(0, 1, 4, 5, 11, 12, 13, 23)) {
            String name = "abcdefghijklmnopqrstuvw".substring(0, length);
            JsonKey key = JsonKey.create(name);
            String header = "\"" + name + "\":";
            byte[] bytes = ("xx" + header + "7,0123456789").getBytes(StandardCharsets.UTF_8);
            for (int size = 1; size <= header.length(); size++) {
                JsonParser parser = JsonParser.create(bytes, 2, size);
                assertThat(parser.tryReadProperty(key), is(false));
                assertThat(parser.currentByte(), is((byte) '"'));
            }
            JsonParser parser = JsonParser.create(bytes, 2, header.length() + 1);
            assertThat(parser.tryReadProperty(key), is(true));
            assertThat(parser.readInt(), is(7));
            for (int i = 0; i < header.length(); i++) {
                byte[] changed = bytes.clone();
                changed[2 + i] ^= 1;
                JsonParser mismatch = JsonParser.create(changed, 2, header.length() + 1);
                byte before = mismatch.currentByte();
                assertThat(mismatch.tryReadProperty(key), is(false));
                assertThat(mismatch.currentByte(), is(before));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void firstProbeDeclinesEquivalentEscapesAndMalformedValuesWithoutAdvancing(ParserMethod method) {
        JsonKey key = JsonKey.create("id");
        JsonParser alternate = method.createParser("\"i\\u0064\":0", 32);
        assertThat(alternate.tryReadProperty(key), is(false));
        assertThat(alternate.readProperty(JsonStringMatcher.create("id")), is(0));
        assertThat(alternate.readInt(), is(0));
        JsonParser invalid = method.createParser("\"id\":}", 32);
        assertThat(invalid.tryReadProperty(key), is(false));
        assertThat(invalid.currentByte(), is((byte) '"'));
        assertThrows(NullPointerException.class, () -> invalid.tryReadProperty(null));
        JsonParser following = method.createParser("{\"first\":0,\"id\":}", 32);
        readFirstProperty(following);
        assertThat(following.tryNextProperty(key), is(false));
        assertThrows(NullPointerException.class, () -> following.tryNextProperty(null));
        assertThat(following.currentByte(), is((byte) '0'));
        JsonParser surrogate = method.createParser("\"\\uD800\":7", 32);
        assertThat(surrogate.tryReadProperty(JsonKey.create("\uD800")), is(false));
        assertThrows(JsonException.class, () -> surrogate.readString(JsonStringMatcher.create("id")));
    }

    private static void readFirstProperty(JsonParser parser) {
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("first"));
        assertThat(parser.nextToken(), is((byte) ':'));
        parser.nextToken();
        assertThat(parser.readInt(), is(0));
    }
}
