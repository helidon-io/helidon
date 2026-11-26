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
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamBoundaryTest {
    @ParameterizedTest(name = "buffer={0}, read={1}, token offset={2}")
    @MethodSource("boundaries")
    void preservesValuesAndFollowingTokens(int bufferSize, int maxRead, int tokenOffset) {
        for (ValueCase value : ValueCase.values()) {
            for (ReadOperation operation : ReadOperation.values()) {
                for (boolean marked : new boolean[] {false, true}) {
                    String context = value + ", " + operation + ", marked=" + marked;
                    byte[] json = document(value.encoded.getBytes(StandardCharsets.UTF_8), tokenOffset, true);
                    JsonParser parser = JsonParser.create(new ShortReadInputStream(json, maxRead), bufferSize);
                    assertValueAndTail(parser, value.encoded, value.expected, operation, marked, context);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ValueCase.class)
    void arrayParserPreservesTheSameValuesAndTokens(ValueCase value) {
        for (ReadOperation operation : ReadOperation.values()) {
            for (boolean marked : new boolean[] {false, true}) {
                JsonParser parser = JsonParser.create(document(value.encoded.getBytes(StandardCharsets.UTF_8), 11, true));
                assertValueAndTail(parser, value.encoded, value.expected, operation, marked,
                                   value + ", " + operation + ", marked=" + marked);
            }
        }
    }

    @ParameterizedTest(name = "buffer={0}, read={1}, closing quote index={2}")
    @MethodSource("stringGrowthBoundaries")
    void preservesClosingQuotesAtGrowthBoundaries(int bufferSize, int maxRead, int quoteIndex) {
        String[] encodedSuffixes = {"tail", "\\\"\\\\", "😀"};
        String[] decodedSuffixes = {"tail", "\"\\", "😀"};
        for (int i = 0; i < encodedSuffixes.length; i++) {
            int suffixBytes = encodedSuffixes[i].getBytes(StandardCharsets.UTF_8).length;
            String padding = "a".repeat(quoteIndex - 1 - suffixBytes);
            String encoded = "\"" + padding + encodedSuffixes[i] + "\"";
            JsonString expected = JsonString.create(padding + decodedSuffixes[i]);
            byte[] bytes = encoded.getBytes(StandardCharsets.UTF_8);
            assertThat(bytes.length - 1, is(quoteIndex));
            // Materialization may retain the value from index zero after compaction.
            byte[] json = document(bytes, bufferSize + 3, true);
            for (ReadOperation operation : ReadOperation.values()) {
                for (boolean marked : new boolean[] {false, true}) {
                    JsonParser parser = JsonParser.create(new ShortReadInputStream(json, maxRead), bufferSize);
                    assertValueAndTail(parser, encoded, expected, operation, marked,
                                       "suffix=" + encodedSuffixes[i] + ", " + operation + ", marked=" + marked);
                }
            }
        }
    }

    @ParameterizedTest(name = "buffer={0}, read={1}, token offset={2}")
    @MethodSource("boundaries")
    void rejectsMalformedAndTruncatedValues(int bufferSize, int maxRead, int tokenOffset) {
        for (InvalidCase value : invalidValues()) {
            for (ReadOperation operation : ReadOperation.values()) {
                // skip locates a value's end without decoding its content; truncation must still fail.
                if (operation == ReadOperation.SKIP && !value.truncated()) {
                    continue;
                }
                for (boolean marked : new boolean[] {false, true}) {
                    byte[] json = document(value.encoded(), tokenOffset, !value.truncated());
                    JsonParser parser = JsonParser.create(new ShortReadInputStream(json, maxRead), bufferSize);
                    assertPrefix(parser);
                    String context = value.name() + ", " + operation + ", marked=" + marked;
                    assertThrows(JsonException.class, () -> {
                        parser.nextToken();
                        if (marked) {
                            parser.mark();
                        }
                        readValue(parser, operation);
                    }, context);
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidValues")
    void arrayParserRejectsTheSameMalformedValues(InvalidCase value) {
        for (ReadOperation operation : ReadOperation.values()) {
            if (operation == ReadOperation.SKIP && !value.truncated()) {
                continue;
            }
            JsonParser parser = JsonParser.create(document(value.encoded(), 11, !value.truncated()));
            assertPrefix(parser);
            assertThrows(JsonException.class, () -> {
                parser.nextToken();
                readValue(parser, operation);
            }, value.name() + ", " + operation);
        }
    }

    private static Stream<Arguments> boundaries() {
        return IntStream.of(6, 7, 16, 512).boxed()
                .flatMap(bufferSize -> IntStream.of(1, 2, 7, Integer.MAX_VALUE).boxed()
                        .flatMap(maxRead -> {
                            IntStream offsets = bufferSize == 512
                                    ? IntStream.concat(IntStream.rangeClosed(508, 516), IntStream.rangeClosed(1020, 1028))
                                    : IntStream.range(bufferSize, 2 * bufferSize);
                            return offsets.mapToObj(offset -> Arguments.of(bufferSize, maxRead, offset));
                        }));
    }

    private static Stream<Arguments> stringGrowthBoundaries() {
        return IntStream.of(16, 512).boxed()
                .flatMap(bufferSize -> IntStream.of(1, 2, 7, Integer.MAX_VALUE).boxed()
                        .flatMap(maxRead -> IntStream.of(bufferSize - 1, bufferSize, bufferSize + 1,
                                                        2 * bufferSize - 1, 2 * bufferSize, 2 * bufferSize + 1)
                                .mapToObj(index -> Arguments.of(bufferSize, maxRead, index))));
    }

    private static List<InvalidCase> invalidValues() {
        return List.of(
                new InvalidCase("invalid escape", "\"a\\q\"".getBytes(StandardCharsets.UTF_8), false),
                new InvalidCase("invalid Unicode escape", "\"a\\u00Q0\"".getBytes(StandardCharsets.UTF_8), false),
                new InvalidCase("unpaired surrogate", "\"a\\uD83D\"".getBytes(StandardCharsets.UTF_8), false),
                new InvalidCase("invalid UTF-8", new byte[] {'"', 'a', (byte) 0xC3, 0x28, '"'}, false),
                new InvalidCase("invalid true", "truX".getBytes(StandardCharsets.UTF_8), false),
                new InvalidCase("invalid false", "falsX".getBytes(StandardCharsets.UTF_8), false),
                new InvalidCase("invalid null", "nulX".getBytes(StandardCharsets.UTF_8), false),
                new InvalidCase("missing quote", "\"unfinished".getBytes(StandardCharsets.UTF_8), true),
                new InvalidCase("truncated escape", "\"a\\".getBytes(StandardCharsets.UTF_8), true),
                new InvalidCase("truncated Unicode escape", "\"a\\u12".getBytes(StandardCharsets.UTF_8), true),
                new InvalidCase("truncated UTF-8", new byte[] {'"', 'a', (byte) 0xF0, (byte) 0x9F}, true),
                new InvalidCase("truncated true", "tru".getBytes(StandardCharsets.UTF_8), true),
                new InvalidCase("truncated false", "fals".getBytes(StandardCharsets.UTF_8), true),
                new InvalidCase("truncated null", "nul".getBytes(StandardCharsets.UTF_8), true));
    }

    private static byte[] document(byte[] value, int tokenOffset, boolean complete) {
        // Consume a real value before the padding so a refill can discard an already-read prefix.
        byte[] prefix = ("[0," + " ".repeat(tokenOffset - 3)).getBytes(StandardCharsets.UTF_8);
        byte[] suffix = complete ? ",\"after\"]".getBytes(StandardCharsets.UTF_8) : new byte[0];
        byte[] json = new byte[prefix.length + value.length + suffix.length];
        System.arraycopy(prefix, 0, json, 0, prefix.length);
        System.arraycopy(value, 0, json, prefix.length, value.length);
        System.arraycopy(suffix, 0, json, prefix.length + value.length, suffix.length);
        return json;
    }

    private static void assertValueAndTail(JsonParser parser,
                                          String encoded,
                                          JsonValue expected,
                                          ReadOperation operation,
                                          boolean marked,
                                          String context) {
        assertPrefix(parser);
        assertThat(context, parser.nextToken(), is((byte) encoded.charAt(0)));
        if (marked) {
            parser.mark();
        }
        JsonValue actual = readValue(parser, operation);
        if (operation != ReadOperation.SKIP) {
            assertThat(context, actual, is(expected));
        }
        if (marked) {
            parser.resetToMark();
            assertThat(context, parser.currentByte(), is((byte) encoded.charAt(0)));
            // Materialize after replay even when the first pass only skipped the value.
            assertThat(context, parser.readJsonValue(), is(expected));
        }
        assertThat(context, parser.currentByte(), is((byte) encoded.charAt(encoded.length() - 1)));
        assertThat(context, parser.nextToken(), is((byte) ','));
        assertThat(context, parser.nextToken(), is((byte) '"'));
        assertThat(context, parser.readString(), is("after"));
        assertThat(context, parser.nextToken(), is((byte) ']'));
        assertThat(context, parser.hasNext(), is(false));
    }

    private static void assertPrefix(JsonParser parser) {
        assertThat(parser.currentByte(), is((byte) '['));
        assertThat(parser.nextToken(), is((byte) '0'));
        assertThat(parser.readInt(), is(0));
        assertThat(parser.nextToken(), is((byte) ','));
    }

    private static JsonValue readValue(JsonParser parser, ReadOperation operation) {
        return switch (operation) {
        case DIRECT -> switch (parser.currentByte()) {
            case '"' -> JsonString.create(parser.readString());
            case 't', 'f' -> JsonBoolean.create(parser.readBoolean());
            case 'n' -> parser.readJsonValue();
            default -> throw new IllegalArgumentException("Unexpected fixture token: " + parser.currentByte());
        };
        case VALUE -> {
            JsonValue value = parser.readJsonValue();
            // String decoding is lazy; exercise materialization as well as locating the closing quote.
            yield value instanceof JsonString string ? JsonString.create(string.value()) : value;
        }
        case SKIP -> {
            parser.skip();
            // The caller checks following tokens; skip does not produce a value.
            yield JsonNull.instance();
        }
        };
    }

    private enum ReadOperation {
        DIRECT,
        VALUE,
        SKIP
    }

    private enum ValueCase {
        ASCII("\"plain\"", JsonString.create("plain")),
        ESCAPED_QUOTE("\"ab\\\"cd\"", JsonString.create("ab\"cd")),
        ESCAPED_BACKSLASH("\"ab\\\\cd\"", JsonString.create("ab\\cd")),
        UNICODE_ESCAPE("\"ab\\u20ACcd\"", JsonString.create("ab€cd")),
        SURROGATE_PAIR("\"ab\\uD83D\\uDE00cd\"", JsonString.create("ab😀cd")),
        UTF8_TWO_BYTES("\"ab©cd\"", JsonString.create("ab©cd")),
        UTF8_THREE_BYTES("\"ab€cd\"", JsonString.create("ab€cd")),
        UTF8_FOUR_BYTES("\"ab😀cd\"", JsonString.create("ab😀cd")),
        LONG_ESCAPED_STRING("\"" + "abc\\\"def\\\\ghi".repeat(100) + "\"", JsonString.create("abc\"def\\ghi".repeat(100))),
        TRUE("true", JsonBoolean.TRUE),
        FALSE("false", JsonBoolean.FALSE),
        NULL("null", JsonNull.instance());

        private final String encoded;
        private final JsonValue expected;

        ValueCase(String encoded, JsonValue expected) {
            this.encoded = encoded;
            this.expected = expected;
        }
    }

    private record InvalidCase(String name, byte[] encoded, boolean truncated) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static final class ShortReadInputStream extends ByteArrayInputStream {
        private final int maxRead;

        private ShortReadInputStream(byte[] bytes, int maxRead) {
            super(bytes);
            this.maxRead = maxRead;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return super.read(bytes, offset, Math.min(length, maxRead));
        }
    }
}
