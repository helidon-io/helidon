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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonStringLookupTest {

    private static final JsonStringLookup NAMES = JsonStringLookup.create("project", "costarring", "liquid", "", "café", "😀");
    private static final String LONG_NAME = "a".repeat(63) + "😀é" + "z".repeat(65);
    private static final String ESCAPED_LONG_NAME = "a".repeat(63) + "\\uD83D\\uDE00\\u00e9" + "z".repeat(65);
    private static final String[] SHORT_OUTLIERS = {"k", "id", "kind", "shorter", "customer", "customerPropert", "é", "😀"};

    @Test
    void matchesCompleteNames() {
        assertThat(NAMES.match("project"), is(0));
        assertThat(NAMES.match("unknown-IpUce6"), is(-1));
        assertThat(NAMES.match("costarring"), is(1));
        assertThat(NAMES.match("liquid"), is(2));
        assertThat(NAMES.match(""), is(3));
        assertThat(NAMES.match("café"), is(4));
        assertThat(NAMES.match("😀"), is(5));
    }

    @Test
    void matchesUtf8ValueSlices() {
        String[] names = {"", "project", "costarring", "liquid", "CAFÉ", "变量", "𐐀", "a\"b", "a\\b", "\n", "\u0000"};
        JsonStringLookup lookup = JsonStringLookup.create(names);
        for (int index = 0; index < names.length; index++) {
            byte[] bytes = ("prefix" + names[index] + "suffix").getBytes(StandardCharsets.UTF_8);
            byte[] original = bytes.clone();
            int length = names[index].getBytes(StandardCharsets.UTF_8).length;
            assertThat(names[index], lookup.matchUtf8(bytes, 6, length), is(index));
            assertThat(bytes, is(original));
        }
        byte[] unknown = "unknown-IpUce6".getBytes(StandardCharsets.UTF_8);
        assertThat(lookup.matchUtf8(unknown, 0, unknown.length), is(-1));
        byte[] prefix = "proj".getBytes(StandardCharsets.UTF_8);
        assertThat(lookup.matchUtf8(prefix, 0, prefix.length), is(-1));
    }

    @Test
    void matchesMixedLengthUtf8ValueSlices() {
        for (String shortName : SHORT_OUTLIERS) {
            String[] names = namesWithShortOutlier(shortName);
            JsonStringLookup lookup = JsonStringLookup.create(names);
            for (int index = 0; index < names.length; index++) {
                byte[] bytes = ("prefix" + names[index] + " suffix").getBytes(StandardCharsets.UTF_8);
                int length = names[index].getBytes(StandardCharsets.UTF_8).length;
                assertThat(names[index], lookup.matchUtf8(bytes, 6, length), is(index));
                assertThat(names[index], lookup.matchUtf8(bytes, 6, length + 1), is(-1));
            }
        }
    }

    @Test
    void treatsUtf8BytesAsDecodedValues() {
        JsonStringLookup lookup = JsonStringLookup.create("\n", "\\n", "\"");
        assertThat(lookup.matchUtf8(new byte[] {'\n'}, 0, 1), is(0));
        assertThat(lookup.matchUtf8(new byte[] {'\\', 'n'}, 0, 2), is(1));
        assertThat(lookup.matchUtf8(new byte[] {'"'}, 0, 1), is(2));
    }

    @Test
    void malformedUtf8DoesNotMatchReplacementOrSurrogateNames() {
        JsonStringLookup lookup = JsonStringLookup.create("?", "\uFFFD", "é", "\uD800", "\n\uD800");
        for (byte[] bytes : new byte[][] {
                {(byte) 0xff}, {(byte) 0xc0, (byte) 0xaf}, {(byte) 0xed, (byte) 0xa0, (byte) 0x80}, {(byte) 0xc3}}) {
            assertThat(lookup.matchUtf8(bytes, 0, bytes.length), is(-1));
        }
        JsonStringLookup invalidNames = JsonStringLookup.create("\uD800", "\n\uD800", "\"\uDC00");
        assertThat(invalidNames.matchUtf8(new byte[] {'?'}, 0, 1), is(-1));
        assertThat(invalidNames.matchUtf8(new byte[] {'\n', '?'}, 0, 2), is(-1));
        assertThat(invalidNames.matchUtf8(new byte[] {'"', '?'}, 0, 2), is(-1));
    }

    @Test
    void validatesUtf8SliceBoundaries() {
        JsonStringLookup lookup = JsonStringLookup.create("", "abc");
        byte[] bytes = "xabcx".getBytes(StandardCharsets.UTF_8);
        assertThat(lookup.matchUtf8(bytes, 1, 3), is(1));
        assertThat(lookup.matchUtf8(bytes, bytes.length, 0), is(0));
        assertThrows(NullPointerException.class, () -> lookup.matchUtf8(null, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> lookup.matchUtf8(bytes, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> lookup.matchUtf8(bytes, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> lookup.matchUtf8(bytes, 1, Integer.MAX_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> lookup.matchUtf8(bytes, bytes.length, 1));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void matchesEveryAsciiUnicodeEscapeAndPreservesTheFollowingValue(ParserMethod method) {
        StringBuilder name = new StringBuilder();
        StringBuilder escaped = new StringBuilder();
        for (int value = 0; value < 128; value++) {
            name.append((char) value);
            escaped.append(String.format(value % 2 == 0 ? "\\u%04x" : "\\u%04X", value));
        }
        JsonStringLookup lookup = JsonStringLookup.create(name.toString());
        for (int padding = 0; padding < 4; padding++) {
            JsonParser parser = method.createParser(" ".repeat(padding) + "[\"" + escaped + "\",7]", 6);
            assertThat(parser.nextToken(), is((byte) '"'));
            assertThat(parser.readString(lookup), is(0));
            assertThat(parser.nextToken(), is((byte) ','));
            parser.nextToken();
            assertThat(parser.readInt(), is(7));
            assertThat(parser.nextToken(), is((byte) ']'));
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void rejectsControlBytesInsideKnownAsciiEscapes(ParserMethod method) {
        for (int digit = 0; digit < 10; digit++) {
            JsonStringLookup lookup = JsonStringLookup.create(String.valueOf((char) (0x40 + digit)));
            byte[] bytes = {'"', '\\', 'u', '0', '0', '4', (byte) (0x10 + digit), '"'};
            JsonParser parser = method == ParserMethod.ARRAY
                    ? JsonParser.create(bytes)
                    : JsonParser.create(new ByteArrayInputStream(bytes), 6);
            assertThrows(JsonException.class, () -> parser.readString(lookup));
        }
    }

    @Test
    void rejectsNullAndDuplicateNames() {
        assertThrows(NullPointerException.class, () -> JsonStringLookup.create((String[]) null));
        assertThrows(NullPointerException.class, () -> JsonStringLookup.create("project", null));
        assertThrows(NullPointerException.class, () -> NAMES.match(null));
        assertThrows(IllegalArgumentException.class, () -> JsonStringLookup.create("project", "project"));
    }

    @Test
    void retainsSnapshotOfNames() {
        String[] names = {"project", "name"};
        JsonStringLookup lookup = JsonStringLookup.create(names);
        names[0] = "changed";
        assertThat(lookup.match("project"), is(0));
        assertThat(lookup.match("changed"), is(-1));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void matchesDecodedNamesAndPreservesDelimiters(ParserMethod method) {
        String[] inputs = {"project", "pro\\u006aect", "costarring", "liquid", "", "café", "caf\\u00e9",
                "😀", "\\uD83D\\uDE00", "unknown-IpUce6", "unknown"};
        int[] expected = {0, 0, 1, 2, 3, 4, 4, 5, 5, -1, -1};
        for (int bufferSize : new int[] {6, 7, 16, 512}) {
            for (int index = 0; index < inputs.length; index++) {
                JsonParser parser = method.createParser("[\"" + inputs[index] + "\",7]", bufferSize);
                parser.nextToken();
                assertThat(inputs[index], parser.readString(NAMES), is(expected[index]));
                assertThat(parser.currentByte(), is((byte) '"'));
                assertThat(parser.nextToken(), is((byte) ','));
                assertThat(parser.nextToken(), is((byte) '7'));
                assertThat(parser.readInt(), is(7));
                assertThat(parser.nextToken(), is((byte) ']'));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void disambiguatesKnownAndUnknownCollisions(ParserMethod method) {
        for (JsonStringLookup lookup : new JsonStringLookup[] {
                JsonStringLookup.create("project"), JsonStringLookup.create("project", "name")}) {
            JsonParser parser = method.createParser("\"unknown-IpUce6\"", 512);
            assertThat(parser.readString(lookup), is(-1));
        }
        for (String name : new String[] {"costarring", "liquid"}) {
            JsonParser parser = method.createParser("\"" + name + "\"", 512);
            assertThat(parser.readString(NAMES), is(name.equals("costarring") ? 1 : 2));
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void preservesMarkAcrossFastAndDecodedPaths(ParserMethod method) {
        for (String input : new String[] {"project", "pro\\u006aect", "unknown-IpUce6"}) {
            for (int bufferSize : new int[] {6, 512}) {
                JsonParser parser = method.createParser("{\"" + input + "\":7}", bufferSize);
                parser.mark();
                parser.nextToken();
                int expected = input.equals("unknown-IpUce6") ? -1 : 0;
                assertThat(parser.readString(NAMES), is(expected));
                parser.resetToMark();
                assertThat(parser.currentByte(), is((byte) '{'));
                parser.nextToken();
                assertThat(parser.readString(NAMES), is(expected));
                assertThat(parser.nextToken(), is((byte) ':'));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void rejectsMalformedAndNonStringInput(ParserMethod method) {
        for (String input : new String[] {"null", "7", "true", "\"unterminated", "\"pro\\qject\"",
                "\"\\uD800\"", "\"\\uDC00\"", "\"raw\ncontrol\""}) {
            for (int bufferSize : new int[] {6, 512}) {
                JsonParser parser = method.createParser(input, bufferSize);
                assertThrows(JsonException.class, () -> parser.readString(NAMES), input);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void singletonLookupDoesNotBypassStringValidation(ParserMethod method) {
        JsonParser invalidParser = method.createParser("\"raw\ncontrol\"", 512);
        JsonStringLookup lookup = JsonStringLookup.create("raw\ncontrol");
        assertThrows(JsonException.class, () -> invalidParser.readString(lookup));

        JsonParser parser = method.createParser("\"?\"", 512);
        assertThat(parser.readString(JsonStringLookup.create("\uD800")), is(-1));
        parser = method.createParser("\"?\"", 512);
        assertThat(parser.readString(JsonStringLookup.create("\uD800", "name")), is(-1));
        parser = method.createParser("\"\\\\\"", 512);
        assertThat(parser.readString(JsonStringLookup.create("\\")), is(0));
        parser = method.createParser("\"\\\"\"", 512);
        assertThat(parser.readString(JsonStringLookup.create("\"")), is(0));
        parser = method.createParser("\"\\u0000\"", 6);
        assertThat(parser.readString(JsonStringLookup.create("\u0000")), is(0));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void preservesStringSyntaxWhenMatchingNames(ParserMethod method) {
        for (int bufferSize : new int[] {6, 512}) {
            JsonStringLookup surrogateName = JsonStringLookup.create("\uD800abcd\uDC00");
            JsonParser surrogateParser = method.createParser("[\"\\uD800abcd\\uDC00\"]", bufferSize);
            surrogateParser.nextToken();
            assertThrows(JsonException.class, () -> surrogateParser.readString(surrogateName));

            JsonParser quoteParser = method.createParser("[\"\\u007Aa\"bc\"]", bufferSize);
            quoteParser.nextToken();
            assertThat(quoteParser.readString(JsonStringLookup.create("za\"bc")), is(-1));
            assertThat(quoteParser.currentByte(), is((byte) '"'));
            assertThat(quoteParser.nextToken(), is((byte) 'b'));

            JsonParser escapeParser = method.createParser("[\"a\\bc\"]", bufferSize);
            escapeParser.nextToken();
            assertThat(escapeParser.readString(JsonStringLookup.create("a\\bc")), is(-1));
            assertThat(escapeParser.nextToken(), is((byte) ']'));

            byte[] malformed = {'[', '"', 'a', (byte) 0xe9, 'b', 'c', '"', ']'};
            JsonParser utf8Parser = method == ParserMethod.ARRAY
                    ? JsonParser.create(malformed)
                    : JsonParser.create(new ByteArrayInputStream(malformed), bufferSize);
            utf8Parser.nextToken();
            assertThrows(JsonException.class, () -> utf8Parser.readString(JsonStringLookup.create("aébc")));
        }
    }

    @Test
    void rejectsMalformedUtf8() {
        byte[] invalid = {'"', (byte) 0xc0, (byte) 0xaf, '"'};
        for (JsonParser parser : new JsonParser[] {
                JsonParser.create(invalid), JsonParser.create(new ByteArrayInputStream(invalid), 6)}) {
            assertThrows(JsonException.class, () -> parser.readString(NAMES));
        }
    }

    @Test
    void respectsByteArraySliceBounds() {
        for (String name : new String[] {"project", "propertyName0"}) {
            byte[] input = ("prefix\"" + name + "\" trailing bytes").getBytes(StandardCharsets.UTF_8);
            JsonStringLookup lookup = JsonStringLookup.create(name);
            JsonParser truncated = JsonParser.create(input, 6, name.length() + 1);
            assertThrows(JsonException.class, () -> truncated.readString(lookup));

            JsonParser complete = JsonParser.create(input, 6, name.length() + 2);
            assertThat(complete.readString(lookup), is(0));
            assertThat(complete.currentByte(), is((byte) '"'));
            assertThat(complete.hasNext(), is(false));
        }
    }

    @Test
    void defaultImplementationMatchesValueParser() {
        JsonParser parser = new JsonValueParser(JsonString.create("project"));
        assertThat(parser.readString(NAMES), is(0));
        parser = new JsonValueParser(JsonString.create("unknown-IpUce6"));
        assertThat(parser.readString(NAMES), is(-1));
        JsonParser nullParser = new JsonValueParser(JsonNull.instance());
        assertThrows(JsonException.class, () -> nullParser.readString(NAMES));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void emptyLookupStillConsumesAndValidatesStrings(ParserMethod method) {
        JsonParser parser = method.createParser("[\"project\",7]", 512);
        parser.nextToken();
        assertThat(parser.readString(JsonStringLookup.create()), is(-1));
        assertThat(parser.nextToken(), is((byte) ','));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void rejectsNullLookupWithoutConsumingInput(ParserMethod method) {
        JsonParser parser = method.createParser("\"project\"", 512);
        assertThrows(NullPointerException.class, () -> parser.readString(null));
        assertThat(parser.readString(NAMES), is(0));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void distinguishesPackedNamesAndPreservesFollowingInput(ParserMethod method) {
        String[] names = new String[16];
        for (int index = 0; index < names.length; index++) {
            names[index] = "a".repeat(index + 1);
        }
        JsonStringLookup lookup = JsonStringLookup.create(names);
        for (int bufferSize : new int[] {6, 16, 512}) {
            for (int index = 0; index < names.length; index++) {
                JsonParser parser = method.createParser("[\"" + names[index] + "\",123456789]", bufferSize);
                parser.nextToken();
                assertThat(parser.readString(lookup), is(index));
                assertThat(parser.nextToken(), is((byte) ','));
                parser.nextToken();
                assertThat(parser.readInt(), is(123456789));

                String unknown = names[index] + "b";
                parser = method.createParser("[\"" + unknown + "\",123456789]", bufferSize);
                parser.nextToken();
                assertThat(parser.readString(lookup), is(-1));
                assertThat(parser.nextToken(), is((byte) ','));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void matchesNamesWithTheSamePrefix(ParserMethod method) {
        String[] names = {"project", "projector", "proxy", "projects"};
        JsonStringLookup lookup = JsonStringLookup.create(names);
        for (int index = 0; index < names.length; index++) {
            JsonParser parser = method.createParser("\"" + names[index] + "\"", 512);
            assertThat(parser.readString(lookup), is(index));
        }
        JsonParser parser = method.createParser("\"pro\"", 512);
        assertThat(parser.readString(lookup), is(-1));
        parser = method.createParser("\"projection\"", 512);
        assertThat(parser.readString(lookup), is(-1));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void matchesShortNamesAlongsideLongSharedPrefixes(ParserMethod method) {
        for (String shortName : SHORT_OUTLIERS) {
            String[] names = namesWithShortOutlier(shortName);
            JsonStringLookup lookup = JsonStringLookup.create(names);
            for (int index = 0; index < names.length; index++) {
                StringBuilder escaped = new StringBuilder();
                for (int character = 0; character < names[index].length(); character++) {
                    escaped.append(String.format("\\u%04x", (int) names[index].charAt(character)));
                }
                for (String input : new String[] {names[index], escaped.toString()}) {
                    for (int bufferSize : new int[] {6, 16, 512}) {
                        JsonParser standalone = method.createParser("\"" + input + "\"", bufferSize);
                        assertThat(input, standalone.readString(lookup), is(index));
                        assertThat(standalone.currentByte(), is((byte) '"'));
                        assertThat(standalone.hasNext(), is(false));

                        JsonParser parser = method.createParser("[\"" + input + "\",123456789]", bufferSize);
                        parser.nextToken();
                        assertThat(input, parser.readString(lookup), is(index));
                        assertThat(parser.currentByte(), is((byte) '"'));
                        assertThat(parser.nextToken(), is((byte) ','));
                        parser.nextToken();
                        assertThat(parser.readInt(), is(123456789));
                        assertThat(parser.nextToken(), is((byte) ']'));
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void mixedLengthNamesRespectTruncatedInputBounds(ParserMethod method) {
        for (String shortName : SHORT_OUTLIERS) {
            String[] names = namesWithShortOutlier(shortName);
            JsonStringLookup lookup = JsonStringLookup.create(names);
            for (int index : new int[] {0, names.length - 1}) {
                String name = names[index];
                byte[] input = ("prefix\"" + name + "\" trailing bytes").getBytes(StandardCharsets.UTF_8);
                int length = name.getBytes(StandardCharsets.UTF_8).length + 2;
                for (int truncatedLength = 1; truncatedLength < length; truncatedLength++) {
                    JsonParser truncated = method == ParserMethod.ARRAY
                            ? JsonParser.create(input, 6, truncatedLength)
                            : JsonParser.create(new ByteArrayInputStream(input, 6, truncatedLength), 6);
                    assertThrows(JsonException.class, () -> truncated.readString(lookup), name + " length " + truncatedLength);
                }
                JsonParser complete = method == ParserMethod.ARRAY
                        ? JsonParser.create(input, 6, length)
                        : JsonParser.create(new ByteArrayInputStream(input, 6, length), 6);
                assertThat(name, complete.readString(lookup), is(index));
                assertThat(complete.currentByte(), is((byte) '"'));
                assertThat(complete.hasNext(), is(false));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void mixedLengthLookupDoesNotMatchBytesFromFollowingValues(ParserMethod method) {
        String value = "A".repeat(64);
        for (String shortName : new String[] {"kind", "customer", "é"}) {
            JsonStringLookup lookup = JsonStringLookup.create(namesWithShortOutlier(shortName));
            for (String name : new String[] {"x", shortName + "x", "customerPropertyNameZ"}) {
                for (int bufferSize : new int[] {6, 16, 512}) {
                    JsonParser parser = method.createParser("{\"" + name + "\":\"" + value + "\"}", bufferSize);
                    parser.nextToken();
                    assertThat(name, parser.readString(lookup), is(-1));
                    assertThat(parser.nextToken(), is((byte) ':'));
                    assertThat(parser.nextToken(), is((byte) '"'));
                    assertThat(parser.readString(), is(value));
                    assertThat(parser.nextToken(), is((byte) '}'));
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void matchesEscapedPrefixNamesInRegistrationOrder(ParserMethod method) {
        JsonStringLookup lookup = JsonStringLookup.create("projector", "", "project", "p", "projects");
        String[] inputs = {"projec\\u0074or", "", "\\u0070roject", "\\u0070", "projec\\u0074s",
                "\\u0070ro", "projec\\u0074ors"};
        int[] expected = {0, 1, 2, 3, 4, -1, -1};
        for (int bufferSize : new int[] {6, 512}) {
            for (int index = 0; index < inputs.length; index++) {
                JsonParser parser = method.createParser("[\"" + inputs[index] + "\",7]", bufferSize);
                parser.nextToken();
                assertThat(inputs[index], parser.readString(lookup), is(expected[index]));
                assertThat(parser.currentByte(), is((byte) '"'));
                assertThat(parser.nextToken(), is((byte) ','));
                parser.nextToken();
                assertThat(parser.readInt(), is(7));
                assertThat(parser.nextToken(), is((byte) ']'));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void preservesRegistrationOrderAcrossNestedPrefixes(ParserMethod method) {
        String[] names = {"b\uFFFFy", "a\u0000x", "a", "", "b", "a\uFFFF", "b\u0000z", "a\u0000"};
        String[] original = names.clone();
        JsonStringLookup lookup = JsonStringLookup.create(names);
        assertThat(names, arrayContaining(original));

        String[] inputs = {"b\\uFFFFy", "a\\u0000x", "\\u0061", "", "\\u0062", "a\\uFFFF", "b\\u0000z", "a\\u0000",
                "a\\u0000z", "b\\u0000x", "a\\uFFFFy", "b\\uFFFF", "b\\u0000", "a\\u0001"};
        for (int bufferSize : new int[] {6, 512}) {
            for (int index = 0; index < inputs.length; index++) {
                JsonParser parser = method.createParser("[\"" + inputs[index] + "\",7]", bufferSize);
                parser.nextToken();
                int expected = index < names.length ? index : -1;
                assertThat(inputs[index], parser.readString(lookup), is(expected));
                assertThat(parser.currentByte(), is((byte) '"'));
                assertThat(parser.nextToken(), is((byte) ','));
                parser.nextToken();
                assertThat(parser.readInt(), is(7));
                assertThat(parser.nextToken(), is((byte) ']'));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void matchesManyDistinctEscapedCharacters(ParserMethod method) {
        String[] names = new String[258];
        for (int index = 0; index < 256; index++) {
            names[index] = String.valueOf((char) (255 - index));
        }
        names[256] = "\uE000";
        names[257] = "\uFFFF";
        JsonStringLookup lookup = JsonStringLookup.create(names);
        for (int bufferSize : new int[] {6, 512}) {
            for (int index = 0; index < names.length; index++) {
                String input = String.format("\\u%04x", (int) names[index].charAt(0));
                JsonParser parser = method.createParser("[\"" + input + "\",7]", bufferSize);
                parser.nextToken();
                assertThat(input, parser.readString(lookup), is(index));
                assertThat(parser.nextToken(), is((byte) ','));
                parser.nextToken();
                assertThat(parser.readInt(), is(7));
                assertThat(parser.nextToken(), is((byte) ']'));
            }
            for (String input : new String[] {"\\u0100", "\\uFFFE"}) {
                JsonParser parser = method.createParser("[\"" + input + "\",7]", bufferSize);
                parser.nextToken();
                assertThat(input, parser.readString(lookup), is(-1));
                assertThat(parser.nextToken(), is((byte) ','));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void matchesVeryLongEscapedNameAndContinues(ParserMethod method) {
        String name = "a".repeat(10_000);
        JsonStringLookup lookup = JsonStringLookup.create(name, "a");
        String input = "[\"\\u0061" + name.substring(1) + "\",7,\"\\u0061\"]";
        JsonParser parser = method.createParser(input, 6);
        parser.nextToken();
        assertThat(parser.readString(lookup), is(0));
        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.nextToken(), is((byte) ','));
        parser.nextToken();
        assertThat(parser.readInt(), is(7));
        assertThat(parser.nextToken(), is((byte) ','));
        parser.nextToken();
        assertThat(parser.readString(lookup), is(1));
        assertThat(parser.nextToken(), is((byte) ']'));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void matchesEscapedAsciiNameAcrossOffsetsAndInputBounds(ParserMethod method) {
        JsonStringLookup lookup = JsonStringLookup.create("abcdefghijklmnop", "abcde", "abcdefghi");
        for (int padding = 0; padding < 8; padding++) {
            String input = " ".repeat(padding) + "[\"\\u0061bcdefghijklmnop\",7]";
            for (int bufferSize : new int[] {6, 16, 512}) {
                JsonParser parser = method.createParser(input, bufferSize);
                parser.nextToken();
                assertThat(parser.readString(lookup), is(0));
                assertThat(parser.currentByte(), is((byte) '"'));
                assertThat(parser.nextToken(), is((byte) ','));
                parser.nextToken();
                assertThat(parser.readInt(), is(7));
                assertThat(parser.nextToken(), is((byte) ']'));
            }
            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            int closingQuote = bytes.length - 4;
            for (int limit = padding + 8; limit <= closingQuote; limit++) {
                byte[] truncated = Arrays.copyOf(bytes, limit);
                JsonParser parser = method == ParserMethod.ARRAY
                        ? JsonParser.create(bytes, 0, limit)
                        : JsonParser.create(new ByteArrayInputStream(truncated), 6);
                parser.nextToken();
                assertThrows(JsonException.class, () -> parser.readString(lookup));
                if (method == ParserMethod.ARRAY) {
                    JsonParser physical = JsonParser.create(truncated);
                    physical.nextToken();
                    assertThrows(JsonException.class, () -> physical.readString(lookup));
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void rejectsShortUnknownNamesBeforeLongValues(ParserMethod method) {
        for (String[] names : new String[][] {{"field0", "field1"}, {"propertyName0", "propertyName1"}}) {
            JsonStringLookup lookup = JsonStringLookup.create(names);
            for (int bufferSize : new int[] {6, 16, 512}) {
                JsonParser parser = method.createParser("{\"x\":\"abcdefgh0\"}", bufferSize);
                parser.nextToken();
                assertThat(parser.readString(lookup), is(-1));
                assertThat(parser.nextToken(), is((byte) ':'));
                parser.nextToken();
                assertThat(parser.readString(), is("abcdefgh0"));
                assertThat(parser.nextToken(), is((byte) '}'));

                parser = method.createParser("\"x\"", bufferSize);
                assertThat(parser.readString(lookup), is(-1));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void disambiguatesDecodedNameHashCollisions(ParserMethod method) {
        // Aa/BB and AaAa/BBBB/AaBB/BBAa collide under String.hashCode().
        JsonStringLookup lookup = JsonStringLookup.create("Aa", "BB", "AaAa", "BBBB", "");
        String[] inputs = {"\\u0041a", "\\u0042B", "Aa\\u0041a", "BB\\u0042B",
                "Aa\\u0042B", "BB\\u0041a", "\\u0000"};
        int[] expected = {0, 1, 2, 3, -1, -1, -1};
        for (int bufferSize : new int[] {6, 16, 512}) {
            for (int index = 0; index < inputs.length; index++) {
                JsonParser parser = method.createParser("[\"" + inputs[index] + "\",7]", bufferSize);
                parser.nextToken();
                assertThat(inputs[index], parser.readString(lookup), is(expected[index]));
                assertThat(parser.currentByte(), is((byte) '"'));
                assertThat(parser.nextToken(), is((byte) ','));
                parser.nextToken();
                assertThat(parser.readInt(), is(7));
                assertThat(parser.nextToken(), is((byte) ']'));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void distinguishesSupplementaryNamesFromBmpHashCollisions(ParserMethod method) {
        String collidingName = "\uD7FF\uE582";
        assertThat(collidingName.hashCode(), is("😀".hashCode()));
        JsonStringLookup lookup = JsonStringLookup.create("\u0000", "\uFFFF", collidingName);
        String[] inputs = {"\\u0000", "\uFFFF", "\\uFFFF", collidingName, "\\uD7FF\\uE582", "😀", "\\uD83D\\uDE00"};
        int[] expected = {0, 1, 1, 2, 2, -1, -1};
        for (int bufferSize : new int[] {6, 16, 512}) {
            for (int index = 0; index < inputs.length; index++) {
                JsonParser parser = method.createParser("[\"" + inputs[index] + "\",7]", bufferSize);
                parser.nextToken();
                assertThat(inputs[index], parser.readString(lookup), is(expected[index]));
                assertThat(parser.currentByte(), is((byte) '"'));
                assertThat(parser.nextToken(), is((byte) ','));
                parser.nextToken();
                assertThat(parser.readInt(), is(7));
                assertThat(parser.nextToken(), is((byte) ']'));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void matchesSupplementaryNamesInEitherRegistrationOrder(ParserMethod method) {
        String[][] names = {{"😀", "known"}, {"known", "😀"}};
        for (int index = 0; index < names.length; index++) {
            JsonStringLookup lookup = JsonStringLookup.create(names[index]);
            JsonParser parser = method.createParser("[\"\\uD83D\\uDE00\",7]", 6);
            parser.nextToken();
            assertThat(parser.readString(lookup), is(index));
            assertThat(parser.nextToken(), is((byte) ','));
            parser.nextToken();
            assertThat(parser.readInt(), is(7));
            assertThat(parser.nextToken(), is((byte) ']'));
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void matchesEscapedNamesAtTheKnownLengthLimit(ParserMethod method) {
        String[][] names = {{"abcdefgh", "\\u0061bcdefgh", "abcdef\\u0067h"},
                {"中abcdefg", "\\u4E2Dabcdefg", "中abcde\\u0066g"},
                {"abcdefg中", "\\u0061bcdefg中", "abcdefg\\u4E2D"},
                {"😀abcdef", "\\uD83D\\uDE00abcdef", "😀abcd\\u0065f"},
                {"abcdef😀", "\\u0061bcdef😀", "abcdef\\uD83D\\uDE00"},
                {"\"中", "\\\"中", "\\u0022\\u4E2D"},
                {"\n中", "\\n中", "\\u000A\\u4E2D"},
                {"\"😀", "\\\"😀", "\\u0022\\uD83D\\uDE00"}};
        for (String[] representations : names) {
            JsonStringLookup lookup = JsonStringLookup.create(representations[0]);
            for (int index = 1; index < representations.length; index++) {
                String input = representations[index];
                for (String suffix : new String[] {"", "x"}) {
                    int expected = suffix.isEmpty() ? 0 : -1;
                    for (int bufferSize : new int[] {6, 16, 512}) {
                        JsonParser parser = method.createParser("[\"" + input + suffix + "\",7,\"" + input + "\"]", bufferSize);
                        parser.nextToken();
                        assertThat(input + suffix, parser.readString(lookup), is(expected));
                        assertThat(parser.currentByte(), is((byte) '"'));
                        assertThat(parser.nextToken(), is((byte) ','));
                        parser.nextToken();
                        assertThat(parser.readInt(), is(7));
                        assertThat(parser.nextToken(), is((byte) ','));
                        parser.nextToken();
                        assertThat(parser.readString(lookup), is(0));
                        assertThat(parser.nextToken(), is((byte) ']'));
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void reusesDecodedBufferAcrossNamesAndStringValues(ParserMethod method) {
        JsonStringLookup lookup = JsonStringLookup.create(LONG_NAME, "Aa", "");
        for (String longName : new String[] {LONG_NAME, ESCAPED_LONG_NAME}) {
            String json = "{\"" + longName + "\":\"value\\n😀\",\"\\u0041a\":\"short\",\"\\u0042B\":7,\"\":9}";
            for (int bufferSize : new int[] {6, 16, 512}) {
                JsonParser parser = method.createParser(json, bufferSize);
                parser.nextToken();
                assertThat(parser.readString(lookup), is(0));
                assertThat(parser.nextToken(), is((byte) ':'));
                parser.nextToken();
                String retainedValue = parser.readString();
                assertThat(parser.nextToken(), is((byte) ','));
                parser.nextToken();
                assertThat(parser.readString(lookup), is(1));
                assertThat(parser.nextToken(), is((byte) ':'));
                parser.nextToken();
                assertThat(parser.readString(), is("short"));
                assertThat(parser.nextToken(), is((byte) ','));
                parser.nextToken();
                assertThat(parser.readString(lookup), is(-1));
                assertThat(parser.nextToken(), is((byte) ':'));
                parser.nextToken();
                assertThat(parser.readInt(), is(7));
                assertThat(parser.nextToken(), is((byte) ','));
                parser.nextToken();
                assertThat(parser.readString(lookup), is(2));
                assertThat(parser.nextToken(), is((byte) ':'));
                parser.nextToken();
                assertThat(parser.readInt(), is(9));
                assertThat(parser.nextToken(), is((byte) '}'));
                assertThat(retainedValue, is("value\n😀"));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void preservesMarkForLongDecodedName(ParserMethod method) {
        JsonStringLookup lookup = JsonStringLookup.create(LONG_NAME);
        for (String longName : new String[] {LONG_NAME, ESCAPED_LONG_NAME}) {
            for (int bufferSize : new int[] {6, 16, 512}) {
                JsonParser parser = method.createParser("{\"" + longName + "\":\"value\"}", bufferSize);
                parser.mark();
                parser.nextToken();
                assertThat(parser.readString(lookup), is(0));
                parser.resetToMark();
                assertThat(parser.currentByte(), is((byte) '{'));
                parser.nextToken();
                assertThat(parser.readString(lookup), is(0));
                assertThat(parser.nextToken(), is((byte) ':'));
                parser.nextToken();
                assertThat(parser.readString(), is("value"));
                assertThat(parser.nextToken(), is((byte) '}'));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void consumesOverlongUnknownNameAndContinues(ParserMethod method) {
        JsonStringLookup lookup = JsonStringLookup.create("project", "");
        String[] unknownNames = {"\\u0061" + "a".repeat(255), "é" + "a".repeat(255), "\\u0000".repeat(256),
                "\\u0061" + "a".repeat(255) + "\\uD83D\\uDE00tail"};
        for (String unknownName : unknownNames) {
            for (int bufferSize : new int[] {6, 16, 512}) {
                String json = "{\"" + unknownName + "\":7,\"pro\\u006aect\":\"done\"}";
                JsonParser parser = method.createParser(json, bufferSize);
                parser.nextToken();
                assertThat(parser.readString(lookup), is(-1));
                assertThat(parser.nextToken(), is((byte) ':'));
                parser.nextToken();
                assertThat(parser.readInt(), is(7));
                assertThat(parser.nextToken(), is((byte) ','));
                parser.nextToken();
                assertThat(parser.readString(lookup), is(0));
                assertThat(parser.nextToken(), is((byte) ':'));
                parser.nextToken();
                assertThat(parser.readString(), is("done"));
                assertThat(parser.nextToken(), is((byte) '}'));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void validatesMalformedTailOfOverlongUnknownName(ParserMethod method) {
        JsonStringLookup lookup = JsonStringLookup.create("a");
        for (String tail : new String[] {"\\q", "\\uD800", "\\uDC00", "\\uD800x", "\\uD800\\u0061", "\\uD800\\n", "\n"}) {
            for (int bufferSize : new int[] {6, 16, 512}) {
                String json = "[\"\\u0061" + "a".repeat(128) + tail + "\",7]";
                JsonParser parser = method.createParser(json, bufferSize);
                parser.nextToken();
                assertThrows(JsonException.class, () -> parser.readString(lookup), tail);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void validatesMalformedTailOfShortUnknownUnicodeName(ParserMethod method) {
        for (JsonStringLookup lookup : new JsonStringLookup[] {
                JsonStringLookup.create("abcdefgh"), JsonStringLookup.create("abcdefgh", "😀")}) {
            for (String prefix : new String[] {"é", "\\u00E9", "𐐀", "\\uD801\\uDC00"}) {
                for (String tail : new String[] {"\\q", "\\uD800", "\\uD800x", "\\uDC00", "\n"}) {
                    for (int bufferSize : new int[] {6, 16, 512}) {
                        String input = prefix + "ab" + tail;
                        JsonParser parser = method.createParser("[\"" + input + "\",7]", bufferSize);
                        parser.nextToken();
                        assertThrows(JsonException.class, () -> parser.readString(lookup), input);
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void validatesUtf8TailOfOverlongUnknownName(ParserMethod method) {
        JsonStringLookup lookup = JsonStringLookup.create("a");
        byte[] input = ("[\"\\u0061" + "a".repeat(128) + "xx\"]").getBytes(StandardCharsets.UTF_8);
        input[input.length - 4] = (byte) 0xc0;
        input[input.length - 3] = (byte) 0xaf;
        for (int bufferSize : new int[] {6, 16, 512}) {
            JsonParser parser = method == ParserMethod.ARRAY
                    ? JsonParser.create(input)
                    : JsonParser.create(new ByteArrayInputStream(input), bufferSize);
            parser.nextToken();
            assertThrows(JsonException.class, () -> parser.readString(lookup));
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void handlesSurrogatePairsAcrossKnownNameLength(ParserMethod method) {
        String[] knownNames = {"x", "xy", "😀"};
        String[][] inputs = {{"😀", "\\uD83D\\uDE00"}, {"é😀", "\\u00e9\\uD83D\\uDE00"}, {"😀", "\\uD83D\\uDE00"}};
        int[] expected = {-1, -1, 0};
        for (int index = 0; index < knownNames.length; index++) {
            JsonStringLookup lookup = JsonStringLookup.create(knownNames[index]);
            for (int bufferSize : new int[] {6, 16, 512}) {
                for (String input : inputs[index]) {
                    JsonParser parser = method.createParser("[\"" + input + "\",7]", bufferSize);
                    parser.nextToken();
                    assertThat(input, parser.readString(lookup), is(expected[index]));
                    assertThat(parser.nextToken(), is((byte) ','));
                    parser.nextToken();
                    assertThat(parser.readInt(), is(7));
                    assertThat(parser.nextToken(), is((byte) ']'));
                }
                String incompletePair = index == 1 ? "\\u00e9\\uD83D" : "\\uD83D";
                JsonParser parser = method.createParser("\"" + incompletePair + "\"", bufferSize);
                assertThrows(JsonException.class, () -> parser.readString(lookup));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void handlesDecodedInputWithZeroKnownNameLength(ParserMethod method) {
        JsonStringLookup[] lookups = {JsonStringLookup.create(), JsonStringLookup.create("")};
        for (int index = 0; index < lookups.length; index++) {
            for (int bufferSize : new int[] {6, 16, 512}) {
                JsonParser empty = method.createParser(" ".repeat(bufferSize - 1) + "\"\"", bufferSize);
                assertThat(empty.readString(lookups[index]), is(index - 1));
                for (String input : new String[] {"\\u0061", "😀", "\\uD83D\\uDE00", "\\u0000"}) {
                    JsonParser parser = method.createParser("[\"" + input + "\",7]", bufferSize);
                    parser.nextToken();
                    assertThat(parser.readString(lookups[index]), is(-1));
                    assertThat(parser.nextToken(), is((byte) ','));
                    parser.nextToken();
                    assertThat(parser.readInt(), is(7));
                    assertThat(parser.nextToken(), is((byte) ']'));
                }
            }
        }
    }

    @Test
    void matchesCompleteUtf8ByteSlices() {
        String[] names = {"é", "café", "中文", "𐐀", "😀", "aaaaaé", "aaaaaaé",
                "aaaaaaaaaaaaaé", "aaaaaaaaaaaaaaé", "客户订单状态字段"};
        JsonStringLookup lookup = JsonStringLookup.create(names);
        for (int index = 0; index < names.length; index++) {
            byte[] input = ("prefix[\"" + names[index] + "\",123456789]").getBytes(StandardCharsets.UTF_8);
            byte[] original = input.clone();
            int start = 8;
            int closingQuote = start + names[index].getBytes(StandardCharsets.UTF_8).length;
            long result = lookup.match(input, start, input.length);
            assertThat(names[index], result, is(((long) closingQuote << 32) | index));
            assertThat(input, is(original));
            assertThat(lookup.match(input, start, closingQuote), is(Long.MIN_VALUE));
        }
    }

    @Test
    void shortNamesRespectByteSliceBounds() {
        String[][] schemas = {{"a", "b"}, {"aa", "ab"}, {"aaa", "aab"}, {"aaaa", "aaab"},
                {"é", "ê"}, {"中", "文"}, {"😀", "😃"}, {"prefixA", "prefixB"}};
        for (String[] names : schemas) {
            JsonStringLookup lookup = JsonStringLookup.create(names);
            for (int index = 0; index < names.length; index++) {
                int nameLength = names[index].getBytes(StandardCharsets.UTF_8).length;
                byte[] input = ("\"" + names[index] + "\",123456789").getBytes(StandardCharsets.UTF_8);
                for (int available = 0; available <= 8; available++) {
                    int limit = 1 + available;
                    long expected = available > nameLength
                            ? ((long) (1 + nameLength) << 32) | index
                            : Long.MIN_VALUE;
                    assertThat(names[index] + " logical limit " + limit,
                               lookup.match(input, 1, limit), is(expected));
                    byte[] bounded = Arrays.copyOf(input, limit);
                    assertThat(names[index] + " physical limit " + limit,
                               lookup.match(bounded, 1, limit), is(expected));
                    JsonParser parser = JsonParser.create(input, 0, limit);
                    if (available > nameLength) {
                        assertThat(parser.readString(lookup), is(index));
                        assertThat(parser.currentByte(), is((byte) '"'));
                    } else {
                        assertThrows(JsonException.class, () -> parser.readString(lookup));
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void matchesNamesAcrossOffsetsAndBufferBoundaries(ParserMethod method) {
        String[] names = {"a", "aa", "aaa", "aaaa", "é", "中", "café", "中文", "😀", "aaaaaé", "aaaaaaé",
                "aaaaaaaaaaaaaé", "aaaaaaaaaaaaaaé"};
        JsonStringLookup lookup = JsonStringLookup.create(names);
        for (int padding = 0; padding < 8; padding++) {
            for (int bufferSize : new int[] {6, 16, 512}) {
                for (int index = 0; index < names.length; index++) {
                    String input = " ".repeat(padding) + "[\"" + names[index] + "\",7]";
                    JsonParser parser = method.createParser(input, bufferSize);
                    parser.nextToken();
                    assertThat(names[index], parser.readString(lookup), is(index));
                    assertThat(parser.currentByte(), is((byte) '"'));
                    assertThat(parser.nextToken(), is((byte) ','));
                    parser.nextToken();
                    assertThat(parser.readInt(), is(7));
                    assertThat(parser.nextToken(), is((byte) ']'));
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void verifiesAllUtf8BitsAndTheClosingQuote(ParserMethod method) {
        JsonStringLookup lookup = JsonStringLookup.create("é", "café", "😀", "aaa");
        for (String name : new String[] {"C)", "éx", "aaaa", "caféx", "😀x"}) {
            for (String suffix : new String[] {"]", ",123456789]"}) {
                JsonParser parser = method.createParser("[\"" + name + "\"" + suffix, 512);
                parser.nextToken();
                assertThat(parser.readString(lookup), is(-1));
                assertThat(parser.nextToken(), is((byte) suffix.charAt(0)));
            }
        }
        for (byte[] malformed : new byte[][] {
                {'[', '"', 'c', 'a', 'f', (byte) 0xc3, (byte) 0x29, '"', ',', '7', ']'},
                {'[', '"', (byte) 0xc3, (byte) 0x29, '"', ']'}}) {
            JsonParser parser = method == ParserMethod.ARRAY
                    ? JsonParser.create(malformed)
                    : JsonParser.create(new ByteArrayInputStream(malformed), 6);
            parser.nextToken();
            assertThrows(JsonException.class, () -> parser.readString(lookup));
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void doesNotEncodeUnpairedSurrogatesAsReplacementNames(ParserMethod method) {
        for (String name : new String[] {"\uD800", "\uDC00", "a\uD800", "\uD800a", "a\uDC00", "😀\uD800"}) {
            JsonStringLookup lookup = JsonStringLookup.create(name);
            String replacement = new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            JsonParser parser = method.createParser("[\"" + replacement + "\",123456789]", 512);
            parser.nextToken();
            assertThat(parser.readString(lookup), is(-1));
            assertThat(parser.nextToken(), is((byte) ','));
        }
    }

    private static String[] namesWithShortOutlier(String shortName) {
        String[] names = new String[9];
        for (int index = 0; index < names.length - 1; index++) {
            names[index] = "customerPropertyName" + (char) ('A' + index);
        }
        names[names.length - 1] = shortName;
        return names;
    }
}
