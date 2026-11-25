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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonException;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmileStreamBoundaryTest {
    @ParameterizedTest(name = "{0}, buffer={1}, read={2}, boundary={3}")
    @MethodSource("validBoundaries")
    void preservesValuesAndFollowingTokens(ValueCase value, int bufferSize, int maxRead, int boundaryOffset) {
        int tokenOffset = boundaryOffset - value.focus();
        while (tokenOffset < 5) {
            tokenOffset += bufferSize;
        }
        for (boolean skip : new boolean[] {false, true}) {
            for (boolean marked : new boolean[] {false, true}) {
                byte[] bytes = document(value.encoded(), tokenOffset, true);
                JsonParser parser = SmileParser.create(new ShortReadInputStream(bytes, maxRead), bufferSize);
                assertValueAndTail(parser, value, tokenOffset, skip, marked);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validValues")
    void arrayParserPreservesTheSameValuesAndTokens(ValueCase value) {
        for (boolean skip : new boolean[] {false, true}) {
            for (boolean marked : new boolean[] {false, true}) {
                JsonParser parser = SmileParser.create(document(value.encoded(), 11, true));
                assertValueAndTail(parser, value, 11, skip, marked);
            }
        }
    }

    @ParameterizedTest(name = "Unicode={0}, buffer={1}, read={2}, terminator index={3}")
    @MethodSource("longStringBoundaries")
    void preservesLongStringTerminatorsAtGrowthBoundaries(boolean unicode, int bufferSize, int maxRead, int terminatorIndex) {
        String suffix = unicode ? "©€😀" : "tail";
        int suffixBytes = suffix.getBytes(StandardCharsets.UTF_8).length;
        String expected = "a".repeat(terminatorIndex - 1 - suffixBytes) + suffix;
        byte[] encoded = encode(gen -> gen.write(expected), false);
        assertThat(encoded[0], is(unicode ? SmileConstants.VALUE_LONG_UNICODE : SmileConstants.VALUE_LONG_ASCII));
        assertThat(encoded.length - 1, is(terminatorIndex));
        ValueCase value = new ValueCase("terminator at " + terminatorIndex, encoded, expected, ValueKind.STRING, 0);
        int tokenOffset = bufferSize + 3;

        // A refill compacts the value to index zero; vary its encoded length, not just the discarded prefix.
        for (boolean skip : new boolean[] {false, true}) {
            for (boolean marked : new boolean[] {false, true}) {
                JsonParser parser = SmileParser.create(
                        new ShortReadInputStream(document(encoded, tokenOffset, true), maxRead), bufferSize);
                assertValueAndTail(parser, value, tokenOffset, skip, marked);
            }
        }
    }

    @ParameterizedTest(name = "{0}, buffer={1}, read={2}, boundary={3}")
    @MethodSource("invalidBoundaries")
    void rejectsMalformedAndTruncatedPayloads(InvalidCase value, int bufferSize, int maxRead, int tokenOffset) {
        for (boolean skip : new boolean[] {false, true}) {
            // Skipping requires complete framing, but need not decode malformed payload content.
            if (skip && !value.truncated()) {
                continue;
            }
            for (boolean marked : new boolean[] {false, true}) {
                byte[] bytes = document(value.encoded(), tokenOffset, !value.truncated());
                JsonParser parser = SmileParser.create(new ShortReadInputStream(bytes, maxRead), bufferSize);
                assertPrefix(parser, tokenOffset);
                assertThrows(JsonException.class, () -> {
                    parser.nextToken();
                    if (marked) {
                        parser.mark();
                    }
                    if (skip) {
                        parser.skip();
                    } else {
                        readValue(parser, value.kind());
                    }
                }, value.name() + ", skip=" + skip + ", marked=" + marked);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidValues")
    void arrayParserRejectsTheSameMalformedPayloads(InvalidCase value) {
        JsonParser parser = SmileParser.create(document(value.encoded(), 11, !value.truncated()));
        assertPrefix(parser, 11);
        assertThrows(JsonException.class, () -> {
            parser.nextToken();
            readValue(parser, value.kind());
        });
        if (value.kind() == ValueKind.BINARY && value.truncated()) {
            JsonParser skippingParser = SmileParser.create(document(value.encoded(), 11, false));
            assertPrefix(skippingParser, 11);
            assertThat(skippingParser.nextToken(), is((byte) 'B'));
            assertThrows(JsonException.class, skippingParser::skip);
        }
    }

    @ParameterizedTest(name = "raw={0}, buffer={1} (0 means byte array), size={2}")
    @MethodSource("binarySkips")
    void readsAndSkipsBinaryPayloadAndPreservesFollowingValue(boolean rawBinary, int bufferSize, int size) {
        byte[] payload = new byte[size];
        for (int i = 0; i < size; i++) {
            payload[i] = (byte) (i * 37);
        }
        byte[] encoded = encode(gen -> gen.writeBinary(payload), rawBinary);
        byte[] bytes = document(encoded, 11, true);
        for (boolean skip : new boolean[] {false, true}) {
            JsonParser parser = bufferSize == 0
                    ? SmileParser.create(bytes)
                    : SmileParser.create(new ShortReadInputStream(bytes, 1), bufferSize);

            assertPrefix(parser, 11);
            assertThat(parser.nextToken(), is((byte) 'B'));
            if (skip) {
                parser.skip();
            } else {
                assertThat(parser.readBinary(), is(payload));
            }
            assertThat(parser.nextToken(), is((byte) ','));
            assertThat(parser.nextToken(), is((byte) 't'));
            assertThat(parser.readBoolean(), is(true));
            assertThat(parser.nextToken(), is((byte) ']'));
            assertThat(parser.hasNext(), is(false));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void skipsLargeBinaryWithoutGrowingUnmarkedBuffer(boolean rawBinary) {
        byte[] payload = new byte[65_536];
        Arrays.fill(payload, (byte) 0xFF);
        byte[] encoded = encode(gen -> gen.writeBinary(payload), rawBinary);
        ShortReadInputStream stream = new ShortReadInputStream(document(encoded, 11, true), 7);
        JsonParser parser = SmileParser.create(stream, 16);

        assertPrefix(parser, 11);
        assertThat(parser.nextToken(), is((byte) 'B'));
        parser.skip();
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) 't'));
        assertThat(parser.readBoolean(), is(true));
        assertThat(parser.nextToken(), is((byte) ']'));
        assertThat(parser.hasNext(), is(false));
        assertThat("Unmarked skipping must not retain the entire payload", stream.largestBuffer, lessThanOrEqualTo(32));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5, 16, 512})
    void rejectsRawBinarySkippingWhenHeaderDisablesIt(int bufferSize) {
        byte[] encoded = encode(gen -> gen.writeBinary(new byte[] {1, 2, 3}), true);
        byte[] bytes = document(encoded, 11, true);
        bytes[3] &= ~SmileConstants.HEADER_FEATURE_RAW_BINARY;
        JsonParser parser = bufferSize == 0
                ? SmileParser.create(bytes)
                : SmileParser.create(new ShortReadInputStream(bytes, 1), bufferSize);
        assertPrefix(parser, 11);
        assertThat(parser.nextToken(), is((byte) 'B'));
        assertThrows(JsonException.class, parser::skip);
    }

    @ParameterizedTest(name = "{0}, buffer={2} (0 means byte array), raw={3}")
    @MethodSource("invalidBinaryLengths")
    void rejectsInvalidOrUnavailableBinaryLength(String name, byte[] length, int bufferSize, boolean rawBinary) {
        byte[] encoded = new byte[length.length + 1];
        encoded[0] = rawBinary ? SmileConstants.TOKEN_BINARY_RAW : SmileConstants.TOKEN_BINARY_7BIT;
        System.arraycopy(length, 0, encoded, 1, length.length);
        byte[] bytes = document(encoded, 11, true);
        JsonParser parser = bufferSize == 0
                ? SmileParser.create(bytes)
                : SmileParser.create(new ShortReadInputStream(bytes, 1), bufferSize);

        assertPrefix(parser, 11);
        assertThat(parser.nextToken(), is((byte) 'B'));
        assertThrows(JsonException.class, parser::skip, name);
    }

    @ParameterizedTest(name = "{0}, buffer={2} (0 means byte array), raw={3}")
    @MethodSource("invalidBinaryLengths")
    void rejectsInvalidBinaryLengthWhenReading(String name, byte[] length, int bufferSize, boolean rawBinary) {
        byte[] encoded = new byte[length.length + 1];
        encoded[0] = rawBinary ? SmileConstants.TOKEN_BINARY_RAW : SmileConstants.TOKEN_BINARY_7BIT;
        System.arraycopy(length, 0, encoded, 1, length.length);
        for (boolean followingValue : new boolean[] {false, true}) {
            byte[] bytes = document(encoded, 11, followingValue);
            JsonParser parser = bufferSize == 0
                    ? SmileParser.create(bytes)
                    : SmileParser.create(new ShortReadInputStream(bytes, 1), bufferSize);

            assertPrefix(parser, 11);
            assertThat(parser.nextToken(), is((byte) 'B'));
            assertThrows(JsonException.class, parser::readBinary, name + ", following value=" + followingValue);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5, 16, 512})
    void rejectsBinaryReadWithUnrepresentableEncodedLength(int bufferSize) {
        // Integer.MAX_VALUE raw bytes would require more than Integer.MAX_VALUE 7-bit-encoded bytes.
        byte[] encoded = {SmileConstants.TOKEN_BINARY_7BIT, 0x0F, 0x7F, 0x7F, 0x7F, (byte) 0xBF};
        byte[] bytes = document(encoded, 11, true);
        JsonParser parser = bufferSize == 0
                ? SmileParser.create(bytes)
                : SmileParser.create(new ShortReadInputStream(bytes, 1), bufferSize);

        assertPrefix(parser, 11);
        assertThat(parser.nextToken(), is((byte) 'B'));
        JsonException exception = assertThrows(JsonException.class, parser::readBinary);
        assertThat(exception.getMessage(), containsString("Binary length is too large"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesMarkedArrayWithLinearBufferAllocation(boolean skip) {
        List<String> values = IntStream.range(0, 4096).mapToObj(i -> "item" + i).toList();
        JsonArray expected = JsonArray.createStrings(values);
        byte[] encoded = encode(expected::toJson, false);
        byte[] bytes = document(encoded, 11, true);
        ShortReadInputStream stream = new ShortReadInputStream(bytes, 7);
        JsonParser parser = SmileParser.create(stream, 16);
        ValueCase value = new ValueCase("marked array", encoded, expected, ValueKind.ARRAY, 0);

        assertValueAndTail(parser, value, 11, skip, true);

        assertThat("Retained-buffer allocation must stay linear in the marked input size",
                   stream.allocatedBufferCapacity, lessThanOrEqualTo(4L * bytes.length));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesMarkedLongStringWithLinearBufferAllocation(boolean skip) {
        String expected = "a".repeat(16_384);
        byte[] encoded = encode(gen -> gen.write(expected), false);
        byte[] bytes = document(encoded, 11, true);
        ShortReadInputStream stream = new ShortReadInputStream(bytes, 7);
        JsonParser parser = SmileParser.create(stream, 16);
        ValueCase value = new ValueCase("marked long string", encoded, expected, ValueKind.STRING, 0);

        assertValueAndTail(parser, value, 11, skip, true);

        assertThat("Retained-buffer allocation must stay linear in the marked input size",
                   stream.allocatedBufferCapacity, lessThanOrEqualTo(4L * bytes.length));
    }

    private static Stream<Arguments> binarySkips() {
        return Stream.of(false, true)
                .flatMap(raw -> IntStream.of(0, 5, 16, 512).boxed()
                        .flatMap(bufferSize -> IntStream.of(0, 1, 6, 7, 8, 13, 14, 15, 63, 64, 65, 127, 128, 129, 8192)
                                .mapToObj(size -> Arguments.of(raw, bufferSize, size))));
    }

    private static Stream<Arguments> invalidBinaryLengths() {
        byte[] maximumLong = new byte[10];
        Arrays.fill(maximumLong, (byte) 0x7F);
        maximumLong[0] = 1;
        maximumLong[9] = (byte) 0xBF;
        byte[] overflowingLong = new byte[10];
        overflowingLong[0] = 4;
        overflowingLong[9] = (byte) 0x80;
        byte[] overlongZero = new byte[11];
        overlongZero[10] = (byte) 0x80;
        String[] names = {"invalid terminator", "length 2^31", "length 2^32", "maximum long", "length 2^64", "overlong zero"};
        byte[][] lengths = {
                {(byte) 0xC0},
                {0x10, 0, 0, 0, (byte) 0x80},
                {0x20, 0, 0, 0, (byte) 0x80},
                maximumLong,
                overflowingLong,
                overlongZero
        };
        return IntStream.range(0, lengths.length).boxed()
                .flatMap(i -> IntStream.of(0, 5, 16, 512).boxed()
                        .flatMap(bufferSize -> Stream.of(false, true)
                                .map(raw -> Arguments.of(names[i], lengths[i], bufferSize, raw))));
    }

    private static Stream<Arguments> longStringBoundaries() {
        return Stream.of(false, true)
                .flatMap(unicode -> IntStream.of(128, 512).boxed()
                        .flatMap(bufferSize -> IntStream.of(1, 2, 7, Integer.MAX_VALUE).boxed()
                                .flatMap(maxRead -> IntStream.of(bufferSize - 1, bufferSize, bufferSize + 1,
                                                                2 * bufferSize - 1, 2 * bufferSize, 2 * bufferSize + 1)
                                        .mapToObj(index -> Arguments.of(unicode, bufferSize, maxRead, index)))));
    }

    private static Stream<Arguments> validBoundaries() {
        return validValues().stream().flatMap(SmileStreamBoundaryTest::boundaries);
    }

    private static Stream<Arguments> invalidBoundaries() {
        return invalidValues().stream().flatMap(SmileStreamBoundaryTest::boundaries);
    }

    private static Stream<Arguments> boundaries(Object value) {
        return IntStream.of(5, 7, 16, 512).boxed()
                .flatMap(bufferSize -> IntStream.of(1, 2, 7, Integer.MAX_VALUE).boxed()
                        .flatMap(maxRead -> {
                            IntStream offsets = bufferSize == 512
                                    ? IntStream.concat(IntStream.rangeClosed(508, 516), IntStream.rangeClosed(1020, 1028))
                                    : IntStream.range(bufferSize, 2 * bufferSize);
                            return offsets.mapToObj(offset -> Arguments.of(value, bufferSize, maxRead, offset));
                        }));
    }

    private static List<ValueCase> validValues() {
        List<ValueCase> values = new ArrayList<>();
        values.add(new ValueCase("short ASCII", encode(gen -> gen.write("plain"), false), "plain", ValueKind.STRING, 0));
        values.add(new ValueCase("UTF-8", encode(gen -> gen.write("©€😀"), false), "©€😀", ValueKind.STRING, 0));
        String longText = "abcdefgh".repeat(160);
        byte[] longEncoded = encode(gen -> gen.write(longText), false);
        values.add(new ValueCase("long ASCII", longEncoded, longText, ValueKind.STRING, 0));
        String unicode = "©€😀".repeat(100);
        byte[] unicodeEncoded = encode(gen -> gen.write(unicode), false);
        values.add(new ValueCase("long UTF-8", unicodeEncoded, unicode, ValueKind.STRING, 0));
        values.add(new ValueCase("VInt", encode(gen -> gen.write(-12_345), false), -12_345, ValueKind.INT, 0));
        values.add(new ValueCase("VLong", encode(gen -> gen.write(123_456_789_012_345L), false),
                                 123_456_789_012_345L, ValueKind.LONG, 0));
        values.add(new ValueCase("float", encode(gen -> gen.write(123.5f), false), 123.5f, ValueKind.FLOAT, 0));
        values.add(new ValueCase("double", encode(gen -> gen.write(-1234.25), false), -1234.25, ValueKind.DOUBLE, 0));
        byte[] binary = new byte[137];
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) (i * 37);
        }
        values.add(new ValueCase("7-bit binary length", encode(gen -> gen.writeBinary(binary), false),
                                 binary, ValueKind.BINARY, 1));
        values.add(new ValueCase("raw binary length", encode(gen -> gen.writeBinary(binary), true),
                                 binary, ValueKind.BINARY, 1));

        byte[] shortReference = encode(gen -> {
            gen.writeArrayStart();
            gen.write("shared");
            gen.write("shared");
            gen.writeArrayEnd();
        }, false);
        assertThat(shortReference[shortReference.length - 2], is((byte) 0x01));
        values.add(new ValueCase("short shared value", shortReference, JsonArray.createStrings(List.of("shared", "shared")),
                                 ValueKind.ARRAY, shortReference.length - 2));

        List<JsonValue> sharedValues = new ArrayList<>();
        List<JsonValue> sharedKeys = new ArrayList<>();
        for (int i = 0; i <= 64; i++) {
            sharedValues.add(JsonString.create("v" + i));
            sharedKeys.add(JsonObject.create(Map.of("k" + i, JsonNumber.create(i))));
        }
        sharedValues.add(JsonString.create("v64"));
        sharedKeys.add(JsonObject.create(Map.of("k64", JsonNumber.create(999))));
        JsonArray sharedValueArray = JsonArray.create(sharedValues);
        JsonArray sharedKeyArray = JsonArray.create(sharedKeys);
        byte[] longReference = encode(sharedValueArray::toJson, false);
        byte[] keyReference = encode(sharedKeyArray::toJson, false);
        assertThat(longReference[longReference.length - 3], is((byte) 0xEC));
        assertThat(keyReference[keyReference.length - 7], is((byte) 0x30));
        values.add(new ValueCase("long shared value", longReference, sharedValueArray,
                                 ValueKind.ARRAY, longReference.length - 3));
        values.add(new ValueCase("long shared key", keyReference, sharedKeyArray, ValueKind.ARRAY, keyReference.length - 7));
        return values;
    }

    private static List<InvalidCase> invalidValues() {
        byte[] binary = encode(gen -> gen.writeBinary(new byte[] {1, 2, 3, 4, 5, 6, 7}), false);
        return List.of(
                new InvalidCase("truncated short string", new byte[] {0x44, 'a'}, ValueKind.STRING, true),
                new InvalidCase("truncated UTF-8", new byte[] {(byte) 0x80, (byte) 0xC3}, ValueKind.STRING, true),
                new InvalidCase("invalid UTF-8", new byte[] {(byte) 0x80, (byte) 0xC3, 0x28}, ValueKind.STRING, false),
                new InvalidCase("missing long string terminator", new byte[] {(byte) 0xE0, 'a', 'b'}, ValueKind.STRING, true),
                new InvalidCase("truncated VInt", new byte[] {0x24, 0x01}, ValueKind.INT, true),
                new InvalidCase("invalid VInt terminator", new byte[] {0x24, (byte) 0xC0}, ValueKind.INT, false),
                new InvalidCase("truncated VLong", new byte[] {0x25, 0x01}, ValueKind.LONG, true),
                new InvalidCase("truncated raw binary", new byte[] {(byte) 0xFD, (byte) 0x83, 1, 2}, ValueKind.BINARY, true),
                new InvalidCase("truncated 7-bit binary", Arrays.copyOf(binary, binary.length - 1), ValueKind.BINARY, true),
                new InvalidCase("truncated shared reference", new byte[] {(byte) 0xEC}, ValueKind.STRING, true),
                new InvalidCase("unknown shared reference", new byte[] {(byte) 0xEC, 0x40}, ValueKind.STRING, false));
    }

    private static byte[] encode(Consumer<JsonGenerator> writer, boolean rawBinary) {
        var output = new ByteArrayOutputStream();
        SmileConfig config = SmileConfig.builder().sharedValueStrings(true).rawBinaryEnabled(rawBinary).build();
        try (JsonGenerator generator = SmileGenerator.create(output, config)) {
            writer.accept(generator);
        }
        byte[] bytes = output.toByteArray();
        return Arrays.copyOfRange(bytes, SmileConstants.HEADER_LENGTH, bytes.length);
    }

    private static byte[] document(byte[] value, int tokenOffset, boolean complete) {
        var output = new ByteArrayOutputStream();
        // Enable shared keys/values and raw binary; null padding does not change the shared tables.
        output.writeBytes(new byte[] {0x3A, 0x29, 0x0A, 0x07, (byte) 0xF8});
        for (int i = 5; i < tokenOffset; i++) {
            output.write(SmileConstants.TOKEN_NULL);
        }
        output.writeBytes(value);
        if (complete) {
            output.write(SmileConstants.TOKEN_TRUE);
            output.write(SmileConstants.TOKEN_END_ARRAY);
        }
        return output.toByteArray();
    }

    private static void assertPrefix(JsonParser parser, int tokenOffset) {
        assertThat(parser.currentByte(), is((byte) '['));
        for (int i = 5; i < tokenOffset; i++) {
            assertThat(parser.nextToken(), is((byte) 'n'));
            assertThat(parser.readJsonValue(), is(JsonNull.instance()));
            assertThat(parser.nextToken(), is((byte) ','));
        }
    }

    private static void assertValueAndTail(JsonParser parser, ValueCase value, int tokenOffset, boolean skip, boolean marked) {
        String context = value.name() + ", token offset=" + tokenOffset + ", skip=" + skip + ", marked=" + marked;
        assertPrefix(parser, tokenOffset);
        assertThat(context, parser.nextToken(), is(value.kind().token));
        if (marked) {
            parser.mark();
        }
        if (skip) {
            parser.skip();
        } else {
            assertThat(context, readValue(parser, value.kind()), is(value.expected()));
        }
        if (marked) {
            parser.resetToMark();
            assertThat(context, parser.currentByte(), is(value.kind().token));
            assertThat(context, readValue(parser, value.kind()), is(value.expected()));
        }
        assertThat(context, parser.nextToken(), is((byte) ','));
        assertThat(context, parser.nextToken(), is((byte) 't'));
        assertThat(context, parser.readBoolean(), is(true));
        assertThat(context, parser.nextToken(), is((byte) ']'));
        assertThat(context, parser.hasNext(), is(false));
    }

    private static Object readValue(JsonParser parser, ValueKind kind) {
        return switch (kind) {
        case STRING -> parser.readString();
        case INT -> parser.readInt();
        case LONG -> parser.readLong();
        case FLOAT -> parser.readFloat();
        case DOUBLE -> parser.readDouble();
        case BINARY -> parser.readBinary();
        case ARRAY -> parser.readJsonArray();
        };
    }

    private enum ValueKind {
        STRING('"'),
        INT('1'),
        LONG('1'),
        FLOAT('1'),
        DOUBLE('1'),
        BINARY('B'),
        ARRAY('[');

        private final byte token;

        ValueKind(char token) {
            this.token = (byte) token;
        }
    }

    private record ValueCase(String name, byte[] encoded, Object expected, ValueKind kind, int focus) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record InvalidCase(String name, byte[] encoded, ValueKind kind, boolean truncated) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static final class ShortReadInputStream extends ByteArrayInputStream {
        private final int maxRead;
        private byte[] previousBuffer;
        private int largestBuffer;
        private long allocatedBufferCapacity;

        private ShortReadInputStream(byte[] bytes, int maxRead) {
            super(bytes);
            this.maxRead = maxRead;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (bytes != previousBuffer) {
                allocatedBufferCapacity += bytes.length;
                previousBuffer = bytes;
            }
            largestBuffer = Math.max(largestBuffer, bytes.length);
            return super.read(bytes, offset, Math.min(length, maxRead));
        }
    }
}
