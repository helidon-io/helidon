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
        JsonStringLexer distant = JsonStringLexer.withUtf8(decoded, (_, _) -> {
            throw new AssertionError("Window is outside the logical slice");
        }, Integer.MAX_VALUE, names);
        assertThat(JsonParser.create(data, 7, length).readString(distant), is(1));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void verifiesEveryByteOfLongWireNames(ParserMethod method) {
        String prefix = "配送設定".repeat(3) + ".";
        int offset = prefix.getBytes(StandardCharsets.UTF_8).length;
        String first = prefix + "postalCode";
        String second = prefix + "postalMode";
        String[] names = {first, second, "short", prefix + "\"", prefix + "\uD800"};
        JsonStringLexer lexer = JsonStringLexer.withUtf8(JsonStringLexer.create(names), (_, _) -> 0, offset, names);
        names[0] = "replaced";
        List<String> keys = List.of(first, second, prefix + "postalNode", first + "s", "short",
                                    prefix + "postal\\u0043ode", prefix + "\\\"", prefix + "?");
        List<Integer> ordinals = List.of(0, 1, -1, -1, 2, 0, 3, -1);
        for (int i = 0; i < keys.size(); i++) {
            for (int size : List.of(7, 128)) {
                JsonParser parser = method.createParser("\"" + keys.get(i) + "\":7", size);
                parser.mark();
                assertThat(parser.readString(lexer), is(ordinals.get(i)));
                assertThat(parser.currentByte(), is((byte) '"'));
                parser.resetToMark();
                assertThat(parser.readProperty(lexer), is(ordinals.get(i)));
                assertThat(parser.readInt(), is(7));
            }
        }
    }

    @Test
    void longLiteralsRespectTheLogicalEndAndValidateRawUtf8() {
        String name = "情報".repeat(10) + "abcdefgh";
        JsonStringLexer lexer = JsonStringLexer.withUtf8(JsonStringLexer.create(name, "short"), (_, _) -> 0, 29, name, "short");
        byte[] bytes = ("xx\"" + name + "\",bytes outside the boundary").getBytes(StandardCharsets.UTF_8);
        int length = name.getBytes(StandardCharsets.UTF_8).length + 2;
        for (int size = 1; size < length; size++) {
            JsonParser parser = JsonParser.create(bytes, 2, size);
            assertThrows(JsonException.class, () -> parser.readString(lexer));
        }
        assertThat(JsonParser.create(bytes, 2, length).readString(lexer), is(0));
        bytes[22] = (byte) 0xff;
        assertThrows(JsonException.class, () -> JsonParser.create(bytes, 2, length).readString(lexer));
    }

    @Test
    void verifiesNamesAroundThePackedUtf8Limit() {
        for (int bytes : List.of(23, 24, 25, 1024)) {
            String name = "x".repeat(bytes - 4) + "😀";
            JsonStringLexer lexer = JsonStringLexer.withUtf8(JsonStringLexer.create(name, "other"), (_, _) -> 0, 0, name, "other");
            JsonParser parser = JsonParser.create("\"" + name + "\":7");
            assertThat(parser.readProperty(lexer), is(0));
            assertThat(parser.readInt(), is(7));
            String middle = name.substring(0, 16) + "y" + name.substring(17);
            JsonParser miss = JsonParser.create("\"" + middle + "\":7");
            assertThat(miss.readProperty(lexer), is(-1));
        }
    }

    @Test
    void longSelectorChecksMiddleBytesAndUsesThePhysicalWindow() {
        String prefix = "abcdefghijklmnopqrst";
        String name = prefix + "postalCode" + "t".repeat(12);
        long[] words = new long[2];
        JsonStringLexer lexer = JsonStringLexer.withUtf8(JsonStringLexer.create(name, "other"), (first, second) -> {
            words[0] = first;
            words[1] = second;
            return 0;
        }, prefix.length(), name, "other");
        assertThat(JsonParser.create("\"" + name + "\"").readString(lexer), is(0));
        assertThat((int) words[0] & 0xff, is((int) 'p'));
        assertThat((int) words[1] & 0xff, is((int) 'd'));
        String miss = prefix + "postalMode" + "t".repeat(12);
        assertThat(JsonParser.create("\"" + miss + "\"").readString(lexer), is(-1));
        String invalid = name + "\uD800";
        JsonStringLexer surrogate = JsonStringLexer.withUtf8(JsonStringLexer.create(invalid), (_, _) -> 0, 0, invalid);
        assertThat(JsonParser.create("\"" + name + "?\"").readString(surrogate), is(-1));
        assertThat(surrogate.match(invalid), is(0));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void compactEscapesCrossEachWordBoundary(ParserMethod method) {
        String name = "abcdefghijklmnopqrstuvw";
        String[] names = {name, "other"};
        JsonStringLexer lexer = JsonStringLexer.withUtf8(JsonStringLexer.create(names), (_, _) -> 0, 0, names);
        for (int a : List.of(0, 6, 7, 8, 14, 15, 16, 21)) {
            for (int b : List.of(a + 1, 22)) {
                String wire = escapedAt(name, a, b);
                for (int buffer : List.of(7, 64)) {
                    for (String value : List.of(wire, "x" + wire)) {
                        JsonParser p = method.createParser("\"" + value + "\",12345678901234567890", buffer);
                        int ordinal = value.equals(wire) ? 0 : -1;
                        p.mark();
                        assertThat(p.readString(lexer), is(ordinal));
                        assertThat(p.currentByte(), is((byte) '"'));
                        assertThat(p.nextToken(), is((byte) ','));
                        p.resetToMark();
                        assertThat(p.readString(lexer), is(ordinal));
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void resumesEscapesWithHighBytesAndSurrogatesAcrossRefills(ParserMethod method) {
        String[] names = {"abcdefg東京xyz", "abcdefg😀xyz", "abcdefg東😀xyz", "a東éabcdefghijkl", "a東\"abcdefghijkl"};
        JsonStringLexer lexer = JsonStringLexer.create(names);
        List<String> spellings = List.of("abcdefg\\u6771\\u4EACxyz", "abcdefg\\uD83D\\uDE00xyz",
                                         "abcdefg\\u6771\\uD83D\\uDE00xyz", "a\\u6771éabcdefghijkl", "a\\u6771\\\"abcdefghijkl");
        for (int i = 0; i < spellings.size(); i++) {
            for (int buffer = 6; buffer <= 24; buffer++) {
                JsonParser parser = method.createParser("\"" + spellings.get(i) + "\",1", buffer);
                assertThat(parser.readString(lexer), is(i));
                assertThat(parser.nextToken(), is((byte) ','));
            }
        }
        for (String invalid : List.of("abcdefg\\u6771\\uD800!", "abcdefg\\u6771\\uDX00", "abcdefg\\u6771\\uDC00")) {
            JsonParser parser = method.createParser("\"" + invalid + "\"", 7);
            assertThrows(JsonException.class, () -> parser.readString(lexer));
        }
    }

    @Test
    void escapedBlocksHonorTheLogicalSlice() {
        String wire = "\"abc\\u6771defgh\\u00e9xyz\"";
        byte[] bytes = ("prefix" + wire + ",valid bytes beyond the slice").getBytes(StandardCharsets.UTF_8);
        JsonStringLexer lexer = JsonStringLexer.create("abc東defghéxyz");
        for (int length = 1; length < wire.length(); length++) {
            JsonParser parser = JsonParser.create(bytes, 6, length);
            assertThrows(JsonException.class, () -> parser.readString(lexer));
        }
        assertThat(JsonParser.create(bytes, 6, wire.length()).readString(lexer), is(0));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void consumesUnknownsOnlyAfterValidatingTheName(ParserMethod method) {
        String[] names = {"abcde", "axcye", "abcdefgh", "abcdefgh12345678", "", "é"};
        JsonStringLexer lexer = JsonStringLexer.withUtf8(JsonStringLexer.create(names), (_, _) -> 0, 0, names);
        for (int i = 0; i < names.length; i++) {
            JsonParser p = method.createParser("\"" + names[i] + "\",12345678901234567890", 64);
            assertThat(p.readString(lexer), is(i));
            assertThat(p.nextToken(), is((byte) ','));
        }
        for (String miss : List.of("azcze", "xxxxxxxx", "xxxxxxxxxxxxxxxx", "abcdefghijklmnopqrstuvw")) {
            JsonParser p = method.createParser("\"" + miss + "\",7", 64);
            assertThat(p.readString(lexer), is(-1));
            assertThat(p.nextToken(), is((byte) ','));
        }
        for (String invalid : List.of("unknown\\u00FG", "unknown\\u00a", "unknown\\uD800!",
                                      "unknown\\u0078xxxxxxxx\t", "unknown\\u00e9\\uDC00")) {
            assertThrows(JsonException.class, () -> method.createParser("\"" + invalid + "\",12345678", 64).readString(lexer));
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void readsPropertyNamesAndTheirDelimitersAcrossRefills(ParserMethod method) {
        String[] names = {"id", "label", "東京"};
        JsonStringLexer lexer = JsonStringLexer.withUtf8(JsonStringLexer.create(names), (_, _) -> 0, 0, names);
        for (int size : List.of(6, 9, 23, 64)) {
            String document = "{\"id\":1, \n \"unknown\\u0078\" \t: [1,2],\"東京\":{\"label\":3},\"id\":4 \n}";
            JsonParser parser = method.createParser(document, size);
            parser.mark();
            for (int pass = 0; pass < 2; pass++) {
                if (pass > 0) {
                    parser.resetToMark();
                }
                assertThat(parser.nextToken(), is((byte) '"'));
                assertThat(parser.readProperty(lexer), is(0));
                assertThat(parser.readInt(), is(1));
                assertThat(parser.nextProperty(lexer), is(-1));
                assertThat(parser.currentByte(), is((byte) '['));
                parser.skip();
                assertThat(parser.nextProperty(lexer), is(2));
                assertThat(parser.currentByte(), is((byte) '{'));
                assertThat(parser.nextToken(), is((byte) '"'));
                assertThat(parser.readProperty(lexer), is(1));
                assertThat(parser.readInt(), is(3));
                assertThat(parser.nextProperty(lexer), is(JsonParser.END_OBJECT));
                assertThat(parser.nextProperty(lexer), is(0));
                assertThat(parser.readInt(), is(4));
                assertThat(parser.nextProperty(lexer), is(JsonParser.END_OBJECT));
                assertThat(parser.currentByte(), is((byte) '}'));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void rejectsMalformedPropertyHeaders(ParserMethod method) {
        JsonStringLexer lexer = JsonStringLexer.create("a", "b");
        for (String document : List.of("{,}", "{,\"a\":1}", "{a:1}", "{\"a\" 1}", "{\"a\":", "{\"a\\u00Q1\":1}")) {
            JsonParser parser = method.createParser(document, 6);
            parser.nextToken();
            assertThrows(JsonException.class, () -> parser.readProperty(lexer));
        }
        for (String tail : List.of(",}", " \"b\":2}", ",1:2}", ",\"b\" 2}", ",\"b\":", ",")) {
            JsonParser parser = method.createParser("{\"a\":1" + tail, 6);
            assertThat(parser.nextToken(), is((byte) '"'));
            assertThat(parser.readProperty(lexer), is(0));
            assertThat(parser.readInt(), is(1));
            assertThrows(JsonException.class, () -> parser.nextProperty(lexer));
        }
        JsonParser malformed = method.createParser("{\"other\":tru{\"a\":2}", 32);
        malformed.nextToken();
        assertThat(malformed.readProperty(lexer), is(-1));
        assertThrows(JsonException.class, () -> {
            malformed.skip();
            malformed.nextProperty(lexer);
        });
        assertThrows(NullPointerException.class, () -> method.createParser("\"a\":0").readProperty(null));
        assertThrows(NullPointerException.class, () -> method.createParser("0}").nextProperty(null));
    }

    @Test
    void propertyHeaderHonorsTheLogicalArraySlice() {
        byte[] bytes = "xx{\"abcdefgh\":7}xx".getBytes(StandardCharsets.UTF_8);
        JsonStringLexer lexer = JsonStringLexer.withAscii(JsonStringLexer.create("abcdefgh"), (_, _) -> 0, "abcdefgh");
        for (int length = 2; length < 13; length++) {
            JsonParser parser = JsonParser.create(bytes, 2, length);
            parser.nextToken();
            assertThrows(JsonException.class, () -> parser.readProperty(lexer));
        }
        JsonParser parser = JsonParser.create(bytes, 2, 13);
        parser.nextToken();
        assertThat(parser.readProperty(lexer), is(0));
        assertThat(parser.readInt(), is(7));
        assertThrows(JsonException.class, () -> parser.nextProperty(lexer));
        JsonParser complete = JsonParser.create(bytes, 2, 14);
        complete.nextToken();
        assertThat(complete.readProperty(lexer), is(0));
        assertThat(complete.readInt(), is(7));
        assertThat(complete.nextProperty(lexer), is(JsonParser.END_OBJECT));
    }

    @Test
    void subsequentPropertyHonorsTheLogicalArraySlice() {
        byte[] bytes = "xx{\"a\":1,\"a\":2}xx".getBytes(StandardCharsets.UTF_8);
        JsonStringLexer lexer = JsonStringLexer.create("a");
        for (int length = 6; length < 12; length++) {
            JsonParser parser = JsonParser.create(bytes, 2, length);
            parser.nextToken();
            assertThat(parser.readProperty(lexer), is(0));
            assertThat(parser.readInt(), is(1));
            assertThrows(JsonException.class, () -> parser.nextProperty(lexer));
        }
        JsonParser complete = JsonParser.create(bytes, 2, 13);
        complete.nextToken();
        assertThat(complete.readProperty(lexer), is(0));
        assertThat(complete.readInt(), is(1));
        assertThat(complete.nextProperty(lexer), is(0));
        assertThat(complete.readInt(), is(2));
        assertThat(complete.nextProperty(lexer), is(JsonParser.END_OBJECT));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void readsBufferedUtf8AcrossDecodedWordAndStreamBoundaries(ParserMethod method) {
        String units = "\u0080\u07ff\u0800€\ud7ff\ue000\uffff😀東京";
        for (int prefix = 0; prefix < Long.BYTES; prefix++) {
            String name = "x".repeat(prefix) + units + "tail";
            JsonStringLexer lexer = JsonStringLexer.create(name, name + "s");
            for (int size : List.of(6, 7, 8, 13, 128)) {
                JsonParser parser = method.createParser("\"" + name + "\":7", size);
                parser.mark();
                assertThat(parser.readString(lexer), is(0));
                assertThat(parser.currentByte(), is((byte) '"'));
                parser.resetToMark();
                assertThat(parser.readProperty(lexer), is(0));
                assertThat(parser.readInt(), is(7));
            }
        }
    }

    @Test
    void validatesBufferedUtf8AfterTheDecodedStateRejectsAPrefix() {
        JsonStringLexer lexer = JsonStringLexer.create("different");
        byte[] prefix = "xx\"abcdefgh東".getBytes(StandardCharsets.UTF_8);
        byte[] tail = "本語abcdefghijkl\"".getBytes(StandardCharsets.UTF_8);
        List<byte[]> invalid = List.of(new byte[] {(byte) 0xc0, (byte) 0x80},
                                      new byte[] {(byte) 0xc2, 0x7f},
                                      new byte[] {(byte) 0xe0, (byte) 0x9f, (byte) 0x80},
                                      new byte[] {(byte) 0xed, (byte) 0xa0, (byte) 0x80},
                                      new byte[] {(byte) 0xe3, 0x41, (byte) 0x80},
                                      new byte[] {(byte) 0xe3, (byte) 0x81, 0x41},
                                      new byte[] {(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80},
                                      new byte[] {(byte) 0x80});
        for (byte[] sequence : invalid) {
            byte[] bytes = Arrays.copyOf(prefix, prefix.length + sequence.length + tail.length);
            System.arraycopy(sequence, 0, bytes, prefix.length, sequence.length);
            System.arraycopy(tail, 0, bytes, prefix.length + sequence.length, tail.length);
            for (JsonParser parser : List.of(JsonParser.create(bytes, 2, bytes.length - 2),
                                              JsonParser.create(new ByteArrayInputStream(bytes, 2, bytes.length - 2), 7),
                                              JsonParser.create(new ByteArrayInputStream(bytes, 2, bytes.length - 2), 128))) {
                assertThrows(JsonException.class, () -> parser.readString(lexer));
                assertThat(parser.currentByte(), is(sequence[sequence.length - 1]));
            }
        }
    }

    @Test
    void bufferedUtf8HonorsTheLogicalEndOfEveryCodePoint() {
        String name = "é東京é😀予約";
        byte[] known = ("\"" + name + "\"").getBytes(StandardCharsets.UTF_8);
        byte[] storage = ("pre\"" + name + "\",complete trailing bytes").getBytes(StandardCharsets.UTF_8);
        JsonStringLexer lexer = JsonStringLexer.create(name);
        for (int length = 1; length < known.length; length++) {
            JsonParser parser = JsonParser.create(storage, 3, length);
            assertThrows(JsonException.class, () -> parser.readString(lexer));
        }
        assertThat(JsonParser.create(storage, 3, known.length).readString(lexer), is(0));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void preservesBothUtf16PlanesAcrossRawAndEscapedNames(ParserMethod method) {
        JsonStringLexer lexer = JsonStringLexer.create("東京é京都😀Ā終");
        for (int size : List.of(7, 16, 128)) {
            for (String wire : List.of("東京\\u00e9京都\\uD83D\\uDE00Ā終", "\\u6771京é京都😀\\u0100終")) {
                JsonParser parser = method.createParser("\"" + wire + "\",7", size);
                assertThat(parser.readString(lexer), is(0));
                assertThat(parser.nextToken(), is((byte) ','));
            }
            JsonParser malformed = method.createParser("\"東京\\uD800é京都\"", size);
            assertThrows(JsonException.class, () -> malformed.readString(lexer));
        }
    }

    @Test
    void shortAsciiNamesStillPassThePhysicalSecondWordToTheSelector() {
        String[] names = {"a", "ab", "abcdefg"};
        JsonStringLexer decoded = JsonStringLexer.create(names);
        byte[] data = "pre\"a\":1234567890abcdef-extra".getBytes(StandardCharsets.UTF_8);
        for (int available = Long.BYTES; available <= 2 * Long.BYTES; available++) {
            long second = 0;
            for (int i = Long.BYTES; i < available; i++) {
                second |= (data[4 + i] & 0xffL) << ((i - Long.BYTES) * Byte.SIZE);
            }
            long[] supplied = {-1, -1};
            JsonStringLexer lexer = JsonStringLexer.withAscii(decoded, (first, following) -> {
                supplied[0] = first;
                supplied[1] = following;
                return 0;
            }, names);
            JsonParser parser = JsonParser.create(data, 3, available + 1);
            assertThat(parser.readString(lexer), is(0));
            assertThat(supplied[0], is(0x35343332313a2261L));
            assertThat(supplied[1], is(second));
            assertThat(parser.currentByte(), is((byte) '"'));
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void verifiesShortCandidatesThroughTheirClosingQuote(ParserMethod method) {
        String[] names = {"", "a", "ab", "abcd", "abcde", "abcdef", "abcdefg"};
        JsonStringLexer decoded = JsonStringLexer.create(names);
        for (int i = 0; i < names.length; i++) {
            int guess = i;
            JsonStringLexer lexer = JsonStringLexer.withAscii(decoded, (_, _) -> guess, names);
            for (int size : List.of(6, 32)) {
                String wire = "\"" + names[i] + "\":123456789";
                JsonParser parser = method.createParser(wire, size);
                assertThat(parser.readString(lexer), is(i));
                assertThat(parser.nextToken(), is((byte) ':'));
                String near = "\"" + names[i] + "~\":123456789";
                assertThat(method.createParser(near, size).readProperty(lexer), is(-1));
                int other = (i + 1) % names.length;
                assertThat(method.createParser("\"" + names[other] + "\":123456789", size).readProperty(lexer), is(other));
            }
        }
        String longest = names[names.length - 1];
        JsonStringLexer predicted = JsonStringLexer.withAscii(decoded, (_, _) -> names.length - 1, names);
        for (int i = 0; i < longest.length(); i++) {
            char[] changed = longest.toCharArray();
            changed[i] = '~';
            JsonParser parser = method.createParser("\"" + new String(changed) + "\":123456789", 32);
            assertThat(parser.readProperty(predicted), is(-1));
        }
        JsonStringLexer invalid = JsonStringLexer.withAscii(decoded, (_, _) -> Integer.MAX_VALUE, names);
        assertThat(method.createParser("\"a\\u0062\":0", 32).readProperty(invalid), is(2));
    }

    @Test
    void shortVocabulariesOwnTheirNamesAtTheStrategyBoundary() {
        for (int count : List.of(2, 12, 13)) {
            String[] names = new String[count];
            for (int i = 0; i < count; i++) {
                names[i] = "key" + i;
            }
            int last = count - 1;
            String expected = names[last];
            JsonStringLexer lexer = JsonStringLexer.withAscii(JsonStringLexer.create(names), (_, _) -> last, names);
            names[last] = "changed";
            assertThat(JsonParser.create("\"" + expected + "\":123456789").readProperty(lexer), is(last));
            assertThat(JsonParser.create("\"changed\":123456789").readProperty(lexer), is(-1));
        }
    }

    private static String escapedAt(String value, int first, int second) {
        StringBuilder wire = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (i == first || i == second) {
                wire.append("\\u00").append(Integer.toHexString(value.charAt(i)));
            } else {
                wire.append(value.charAt(i));
            }
        }
        return wire.toString();
    }
}
