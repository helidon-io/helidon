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
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonStringLexerTest {

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void matchesDecodedNamesAndPreservesTheDelimiter(ParserMethod method) {
        String[] names = {"", "user", "userId", "abcdefgh", "abcdefgh12345678", "abcdefg😀", "a\u0000", "a\"", "a\\"};
        JsonStringLexer lexer = JsonStringLexer.create(names);
        List<String> spellings = List.of("", "user", "us\\u0065rId", "abcdefgh", "abcdefgh12345678",
                                         "abcdefg\\uD83D\\uDE00", "a\\u0000", "a\\\"", "a\\\\");
        for (int i = 0; i < spellings.size(); i++) {
            assertThat(lexer.match(names[i]), is(i));
            JsonParser parser = method.createParser("\"" + spellings.get(i) + "\",5", 7);
            parser.mark();
            assertThat(parser.readString(lexer), is(i));
            assertThat(parser.nextToken(), is((byte) ','));
            parser.resetToMark();
            assertThat(parser.readString(lexer), is(i));
        }
        JsonParser unicode = method.createParser("\"abcdefg😀\"", 7);
        assertThat(unicode.readString(lexer), is(5));
        for (String miss : List.of("userI", "userId2", "abcdefgh123456789", "a", "abcdefg😁")) {
            JsonParser parser = method.createParser("\"" + miss + "\",5", 7);
            assertThat(parser.readString(lexer), is(-1));
            assertThat(parser.nextToken(), is((byte) ','));
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void validatesTheRestOfAnUnknownName(ParserMethod method) {
        JsonStringLexer lexer = JsonStringLexer.create("known");
        for (String invalid : List.of("\"unregistered\\uXYZZ\"", "\"unregistered\\uD83D!\"",
                                      "\"abcdefghijk\t\"", "\"no closing quote", "\"unregistered\\x\"")) {
            assertThrows(JsonException.class, () -> method.createParser(invalid, 7).readString(lexer), invalid);
        }
        assertThrows(JsonException.class, () -> method.createParser("null").readString(lexer));
    }

    @Test
    void honorsLogicalInputLimitAndChecksMalformedUtf8AfterAMiss() {
        JsonStringLexer lexer = JsonStringLexer.create("revision", "abcdefg");
        byte[] bytes = "xx\"revision\",0".getBytes(StandardCharsets.UTF_8);
        for (int length = 1; length < 10; length++) {
            JsonParser parser = JsonParser.create(bytes, 2, length);
            assertThrows(JsonException.class, () -> parser.readString(lexer));
        }
        assertThat(JsonParser.create(bytes, 2, 10).readString(lexer), is(0));
        byte[] malformed = {'"', 'u', 'n', 'k', 'n', 'o', 'w', 'n', 'x', (byte) 0xc0, (byte) 0x80, '"'};
        assertThrows(JsonException.class, () -> JsonParser.create(malformed).readString(lexer));
    }

    @Test
    void ownsItsNamesAndHandlesExactBlockLengths() {
        String[] names = {"abcdefgh", "abcdefgh12345678", "abcdefgh12345679", ""};
        JsonStringLexer lexer = JsonStringLexer.create(names);
        names[0] = "other";
        assertThat(lexer.match("abcdefgh"), is(0));
        assertThat(lexer.match("abcdefgh12345679"), is(2));
        assertThat(lexer.match("abcdefgh1234567"), is(-1));
        assertThat(lexer.match(""), is(3));
        assertThrows(IllegalArgumentException.class, () -> JsonStringLexer.create("abcdefgh", "abcdefgh"));
        assertThat(JsonStringLexer.create().match(""), is(-1));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void rawSelectionAlwaysVerifiesTheCompleteSpelling(ParserMethod method) {
        String[] names = {"user", "userId", "abcdefgh1234567", "ascii\"quote", ""};
        JsonStringLexer decoded = JsonStringLexer.create(names);
        // Deliberately predict the same field for every input.
        JsonStringLexer lexer = JsonStringLexer.withAscii(decoded, (_, _) -> 0, names);
        for (String spelling : List.of("userId", "us\\u0065rId", "userIdx", "user", "user\\\"x", "ascii\\\"quote")) {
            String json = "\"" + spelling + "\",12345678901234567890";
            JsonParser parser = method.createParser(json, 32);
            int expected = decoded.match(method.createParser(json).readString());
            assertThat(parser.readString(lexer), is(expected));
            assertThat(parser.currentByte(), is((byte) '"'));
            assertThat(parser.nextToken(), is((byte) ','));
        }
        assertThrows(JsonException.class, () -> method.createParser("\"user\\x followed by padding\"", 32).readString(lexer));
        JsonStringLexer outside = JsonStringLexer.withAscii(decoded, (_, _) -> Integer.MAX_VALUE, names);
        assertThat(method.createParser("\"user\",12345678", 32).readString(outside), is(0));
    }

    @Test
    void rawWindowsRespectAllShortAndOverlappingSliceBoundaries() {
        for (int length = 0; length < 3 * Long.BYTES; length++) {
            String name = "abcdefghijklmnopqrstuvw".substring(0, length);
            byte[] data = ("prefix\"" + name + "\",padding after the logical input").getBytes(StandardCharsets.UTF_8);
            for (String[] names : List.of(new String[] {name}, new String[] {name, "other"})) {
                JsonStringLexer lexer = JsonStringLexer.withAscii(JsonStringLexer.create(names), (_, _) -> 0, names);
                for (int limit = 1; limit < length + 2; limit++) {
                    JsonParser parser = JsonParser.create(data, 6, limit);
                    assertThrows(JsonException.class, () -> parser.readString(lexer));
                }
                assertThat(JsonParser.create(data, 6, length + 2).readString(lexer), is(0));
                JsonParser padded = JsonParser.create(data, 6, data.length - 6);
                assertThat(padded.readString(lexer), is(0));
                assertThat(padded.nextToken(), is((byte) ','));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void continuesThroughLongSharedPrefixes(ParserMethod method) {
        String prefix = "abcdefgh".repeat(2048);
        JsonStringLexer lexer = JsonStringLexer.create(prefix, prefix + "x", prefix + "😀");
        JsonParser parser = method.createParser("[\"" + prefix + "\",\"" + prefix + "x\",\"" + prefix
                                                        + "\\uD83D\\uDE00\",\"" + prefix + "y\"]", 7);
        for (int expected : List.of(0, 1, 2, -1)) {
            assertThat(parser.nextToken(), is((byte) '"'));
            assertThat(parser.readString(lexer), is(expected));
            assertThat(parser.nextToken(), is(expected < 0 ? (byte) ']' : (byte) ','));
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void matchesCanonicalUtf8AndEquivalentEscapes(ParserMethod method) {
        String[] names = {"設定前置甲", "設定前置乙", "abcdefg😀", "\uD800"};
        JsonStringLexer decoded = JsonStringLexer.create(names);
        for (int ordinal = 0; ordinal < 3; ordinal++) {
            int selected = ordinal;
            JsonStringLexer lexer = JsonStringLexer.withUtf8(decoded, (_, _) -> selected, 8, names);
            String spelling = List.of("設定前置\\u7532", "設定前置\\u4e59", "abcdefg\\uD83D\\uDE00").get(ordinal);
            for (String wire : List.of(names[ordinal], spelling)) {
                for (int buffer : List.of(7, 64)) {
                    JsonParser parser = method.createParser("\"" + wire + "\",12345678901234567890", buffer);
                    parser.mark();
                    assertThat(parser.readString(lexer), is(ordinal));
                    assertThat(parser.nextToken(), is((byte) ','));
                    parser.resetToMark();
                    assertThat(parser.readString(lexer), is(ordinal));
                }
            }
        }
        JsonStringLexer surrogate = JsonStringLexer.withUtf8(decoded, (_, _) -> 3, 0, names);
        assertThat(method.createParser("\"?\",1234567890", 32).readString(surrogate), is(-1));
        assertThat(surrogate.match("\uD800"), is(3));
    }

    @Test
    void checksUtf8ThroughTheQuoteAndWithinTheLogicalSlice() {
        String name = "設".repeat(6) + "😀";
        JsonStringLexer lexer = JsonStringLexer.withUtf8(JsonStringLexer.create("other", name), (_, _) -> 1, 8,
                                                          "other", name);
        byte[] text = ("padding\"" + name + "\",bytes beyond the logical input").getBytes(StandardCharsets.UTF_8);
        int complete = name.getBytes(StandardCharsets.UTF_8).length + 2;
        for (int size = 1; size < complete; size++) {
            JsonParser parser = JsonParser.create(text, 7, size);
            assertThrows(JsonException.class, () -> parser.readString(lexer));
        }
        assertThat(JsonParser.create(text, 7, complete).readString(lexer), is(1));
        byte[] bad = Arrays.copyOf(text, text.length);
        bad[7 + complete - 2] = (byte) 0xff;
        assertThrows(JsonException.class, () -> JsonParser.create(bad, 7, complete).readString(lexer));
    }

    @Test
    void passesOnlyTheShiftedLogicalWindowToTheSelector() {
        String name = "abé123456789";
        String[] names = {"other", name};
        JsonStringLexer decoded = JsonStringLexer.create(names);
        long[] words = new long[2];
        JsonStringLexer lexer = JsonStringLexer.withUtf8(decoded, (first, second) -> {
            words[0] = first;
            words[1] = second;
            return 1;
        }, 3, names);
        byte[] data = ("padding\"" + name + "\",outside the slice").getBytes(StandardCharsets.UTF_8);
        int length = name.getBytes(StandardCharsets.UTF_8).length + 2;
        JsonParser parser = JsonParser.create(data, 7, length);
        assertThat(parser.readString(lexer), is(1));
        assertThat(words[0], is(0x37363534333231a9L));
        assertThat(words[1], is(0x223938L));
        assertThrows(IllegalArgumentException.class, () -> JsonStringLexer.withUtf8(decoded, (_, _) -> 0, -1, names));
        assertThrows(IllegalArgumentException.class, () -> JsonStringLexer.withUtf8(decoded, (_, _) -> 0, 9, names));
    }
}
