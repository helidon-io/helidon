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

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonException;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;
import io.helidon.json.JsonValueType;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Shared parser conformance cases executed by both byte-array and stream-backed Smile parsers.
 *
 * <p>Spec-trace comments quote exact Smile spec section titles and then paraphrase the exercised rule.</p>
 */
abstract class SmileParserTestBase {

    @FunctionalInterface
    protected interface JsonGeneratorWriter {
        void write(JsonGenerator generator) throws Exception;
    }

    abstract JsonParser createParser(byte[] smileData);

    protected byte[] generateSmileBytes(JsonGeneratorWriter writer) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(baos)) {
            writer.write(generator);
        }
        return baos.toByteArray();
    }

    protected byte[] generateSmileBytes(SmileConfig config, JsonGeneratorWriter writer) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(baos, config)) {
            writer.write(generator);
        }
        return baos.toByteArray();
    }

    /*
     * Spec: "Token class: Simple literals, numbers".
     * Rule: null, false, true, and the empty String are dedicated scalar tokens that parsers must decode directly.
     */
    @Test
    public void testParseBooleanTrue() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write(true));
        JsonParser parser = createParser(smileData);
        boolean result = parser.readBoolean();

        assertThat(result, is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseBooleanFalse() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write(false));
        JsonParser parser = createParser(smileData);
        boolean result = parser.readBoolean();

        assertThat(result, is(false));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseEmptyString() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write(""));
        JsonParser parser = createParser(smileData);
        String result = parser.readString();

        assertThat(result, is(""));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseBasicAsciiString() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write("Hello World"));
        JsonParser parser = createParser(smileData);
        String result = parser.readString();

        assertThat(result, is("Hello World"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseStringWithEscapes() throws Exception {
        String testString = "Hello \"World\"\n\t";
        byte[] smileData = generateSmileBytes(gen -> gen.write(testString));
        JsonParser parser = createParser(smileData);
        String result = parser.readString();

        assertThat(result, is(testString));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseUnicodeString() throws Exception {
        String testString = "Hello 世界 € 😀";
        byte[] smileData = generateSmileBytes(gen -> gen.write(testString));
        JsonParser parser = createParser(smileData);
        String result = parser.readString();

        assertThat(result, is(testString));
        assertThat(parser.hasNext(), is(false));
    }

    /*
     * Spec: "Token class: Small integers" and "Token class: Simple literals, numbers".
     * Rule: parsers must handle inline small integers, VInt-backed ints/longs, IEEE-754 floats/doubles, and big
     * numbers.
     */
    @Test
    public void testParseIntegerZero() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write(0));
        JsonParser parser = createParser(smileData);
        int result = parser.readInt();

        assertThat(result, is(0));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseIntegerPositive() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write(12345));
        JsonParser parser = createParser(smileData);
        int result = parser.readInt();

        assertThat(result, is(12345));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseIntegerNegative() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write(-12345));
        JsonParser parser = createParser(smileData);
        int result = parser.readInt();

        assertThat(result, is(-12345));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseLongValue() throws Exception {
        long testValue = 123456789012345L;
        byte[] smileData = generateSmileBytes(gen -> gen.write(testValue));
        JsonParser parser = createParser(smileData);
        long result = parser.readLong();

        assertThat(result, is(testValue));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatValue() throws Exception {
        float testValue = 123.456f;
        byte[] smileData = generateSmileBytes(gen -> gen.write(testValue));
        JsonParser parser = createParser(smileData);
        float result = parser.readFloat();

        assertThat(result, is(testValue));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseBigInteger() throws Exception {
        BigInteger testValue = new BigInteger("123456789012345678901234567890");
        byte[] smileData = generateSmileBytes(gen -> gen.write(testValue));
        JsonParser parser = createParser(smileData);

        BigInteger result = parser.readBigInteger();

        assertThat(result, is(testValue));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseBigDecimal() throws Exception {
        BigDecimal testValue = new BigDecimal("1234567890.12345");
        byte[] smileData = generateSmileBytes(gen -> gen.write(testValue));
        JsonParser parser = createParser(smileData);

        BigDecimal result = parser.readBigDecimal();

        assertThat(result, is(testValue));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseNegativeScaleBigDecimal() throws Exception {
        BigDecimal testValue = new BigDecimal("1E+3");
        byte[] smileData = generateSmileBytes(gen -> gen.write(testValue));
        JsonParser parser = createParser(smileData);

        BigDecimal result = parser.readBigDecimal();

        assertThat(result, is(testValue));
        assertThat(result.scale(), is(testValue.scale()));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadJsonNumberPreservesBigInteger() throws Exception {
        BigInteger testValue = new BigInteger("123456789012345678901234567890");
        byte[] smileData = generateSmileBytes(gen -> gen.write(testValue));
        JsonParser parser = createParser(smileData);

        JsonNumber result = parser.readJsonNumber();

        assertThat(result.bigDecimalValue().toBigIntegerExact(), is(testValue));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseDoubleValue() throws Exception {
        double testValue = 123.456789;
        byte[] smileData = generateSmileBytes(gen -> gen.write(testValue));
        JsonParser parser = createParser(smileData);
        double result = parser.readDouble();

        assertThat(result, is(testValue));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseScientificNotation() throws Exception {
        double testValue = 1.23e10;
        byte[] smileData = generateSmileBytes(gen -> gen.write(testValue));
        JsonParser parser = createParser(smileData);
        double result = parser.readDouble();

        assertThat(result, is(testValue));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseNull() throws Exception {
        byte[] smileData = generateSmileBytes(JsonGenerator::writeNull);
        JsonParser parser = createParser(smileData);

        assertThat(parser.checkNull(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseEmptyObject() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeObjectStart();
            gen.writeObjectEnd();
        });
        JsonParser parser = createParser(smileData);
        JsonObject result = parser.readJsonObject();

        assertThat(result.keys().isEmpty(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    /*
     * Spec: "High-level format".
     * Rule: the header is optional, and headerless decoding falls back to the default feature set
     * (shared keys on, shared values off, raw binary off).
     */
    @Test
    public void testParseHeaderlessSmileWithDefaultSharedKeyHandling() {
        byte[] smileData = new byte[] {
                SmileConstants.TOKEN_START_ARRAY,
                SmileConstants.TOKEN_START_OBJECT,
                (byte) (SmileConstants.KEY_SHORT_ASCII_PREFIX),
                (byte) 'k',
                (byte) 0xC2,
                SmileConstants.TOKEN_END_OBJECT,
                SmileConstants.TOKEN_START_OBJECT,
                (byte) SmileConstants.KEY_SHARED_SHORT_MIN,
                (byte) 0xC4,
                SmileConstants.TOKEN_END_OBJECT,
                SmileConstants.TOKEN_END_ARRAY
        };

        JsonParser parser = createParser(smileData);
        JsonArray result = parser.readJsonArray();

        assertThat(result.values().size(), is(2));
        assertThat(result.get(0, JsonNull.instance()).asObject().intValue("k").orElseThrow(), is(1));
        assertThat(result.get(1, JsonNull.instance()).asObject().intValue("k").orElseThrow(), is(2));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testUnsupportedHeaderVersionFails() {
        byte[] smileData = new byte[] {0x3A, 0x29, 0x0A, 0x11};
        assertThrows(JsonException.class, () -> createParser(smileData));
    }

    @Test
    public void testSharedKeyReferenceDisabledByHeaderFails() {
        byte[] smileData = new byte[] {0x3A, 0x29, 0x0A, 0x00, (byte) 0xFA, 0x40, (byte) 0xC2, (byte) 0xFB};

        JsonParser parser = createParser(smileData);
        assertThrows(JsonException.class, parser::readJsonObject);
    }

    /*
     * Spec: "High-level format" and "Tokens: key mode".
     * Rule: structural tokens nest deterministically, and object contents alternate between key-mode names
     * and value-mode payloads until `END_OBJECT`.
     */
    @Test
    public void testParseSimpleObject() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeObjectStart();
            gen.write("key", "value");
            gen.writeObjectEnd();
        });
        JsonParser parser = createParser(smileData);
        JsonObject result = parser.readJsonObject();

        assertThat(result.stringValue("key").orElseThrow(), is("value"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseObjectWithUnicodeKey() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeObjectStart();
            gen.write("ключ", "value");
            gen.writeObjectEnd();
        });

        JsonParser parser = createParser(smileData);
        JsonObject result = parser.readJsonObject();

        assertThat(result.stringValue("ключ").orElseThrow(), is("value"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseObjectWithLongKey() throws Exception {
        String longKey = "k".repeat(65);
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeObjectStart();
            gen.write(longKey, "value");
            gen.writeObjectEnd();
        });

        JsonParser parser = createParser(smileData);
        JsonObject result = parser.readJsonObject();

        assertThat(result.stringValue(longKey).orElseThrow(), is("value"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseComplexObject() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeObjectStart();
            gen.write("string", "hello");
            gen.write("number", 123);
            gen.write("boolean", true);
            gen.write("null", (String) null);
            gen.writeObjectEnd();
        });
        JsonParser parser = createParser(smileData);
        JsonObject result = parser.readJsonObject();

        assertThat(result.stringValue("string").orElseThrow(), is("hello"));
        assertThat(result.intValue("number").orElseThrow(), is(123));
        assertThat(result.booleanValue("boolean").orElseThrow(), is(true));
        assertThat(result.value("null", JsonNumber.create(1)), is(JsonNull.instance()));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseNestedObject() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeObjectStart();
            gen.writeKey("nested");
            gen.writeObjectStart();
            gen.write("deep", "value");
            gen.writeObjectEnd();
            gen.writeObjectEnd();
        });
        JsonParser parser = createParser(smileData);
        JsonObject result = parser.readJsonObject();

        JsonObject nested = result.objectValue("nested").orElseThrow();
        assertThat(nested.stringValue("deep").orElseThrow(), is("value"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseEmptyArray() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            gen.writeArrayEnd();
        });
        JsonParser parser = createParser(smileData);
        JsonArray result = parser.readJsonArray();

        assertThat(result.values().isEmpty(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseSimpleArray() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            gen.write(1);
            gen.write("hello");
            gen.write(true);
            gen.writeArrayEnd();
        });
        JsonParser parser = createParser(smileData);
        JsonArray result = parser.readJsonArray();

        assertThat(result.values().size(), is(3));
        assertThat(result.get(0, JsonNull.instance()).asNumber().intValue(), is(1));
        assertThat(result.get(1, JsonNull.instance()).asString().value(), is("hello"));
        assertThat(result.get(2, JsonNull.instance()).asBoolean().value(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseNestedArray() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            gen.writeArrayStart();
            gen.write(1);
            gen.write(2);
            gen.writeArrayEnd();
            gen.writeArrayStart();
            gen.write(3);
            gen.write(4);
            gen.writeArrayEnd();
            gen.writeArrayEnd();
        });
        JsonParser parser = createParser(smileData);
        JsonArray result = parser.readJsonArray();

        assertThat(result.values().size(), is(2));
        JsonArray first = result.get(0, JsonNull.instance()).asArray();
        JsonArray second = result.get(1, JsonNull.instance()).asArray();
        assertThat(first.get(0, JsonNull.instance()).asNumber().intValue(), is(1));
        assertThat(first.get(1, JsonNull.instance()).asNumber().intValue(), is(2));
        assertThat(second.get(0, JsonNull.instance()).asNumber().intValue(), is(3));
        assertThat(second.get(1, JsonNull.instance()).asNumber().intValue(), is(4));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseObjectWithArray() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeObjectStart();
            gen.write("data", JsonArray.createStrings(List.of("a", "b", "c")));
            gen.write("count", 3);
            gen.writeObjectEnd();
        });
        JsonParser parser = createParser(smileData);
        JsonObject result = parser.readJsonObject();

        JsonArray data = result.arrayValue("data").orElseThrow();
        assertThat(data.values().size(), is(3));
        assertThat(data.get(0, JsonNull.instance()).asString().value(), is("a"));
        assertThat(data.get(1, JsonNull.instance()).asString().value(), is("b"));
        assertThat(data.get(2, JsonNull.instance()).asString().value(), is("c"));
        assertThat(result.intValue("count").orElseThrow(), is(3));
        assertThat(parser.hasNext(), is(false));
    }

    /*
     * Spec: "High-level format".
     * Rule: higher-level read APIs must preserve the same token meaning as the typed scalar/object/array reads.
     */
    @Test
    public void testParseJsonValueString() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write("test string"));
        JsonParser parser = createParser(smileData);
        JsonValue result = parser.readJsonValue();

        assertThat(result.type(), is(JsonValueType.STRING));
        assertThat(result.asString().value(), is("test string"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseJsonValueNumber() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write(123.45));
        JsonParser parser = createParser(smileData);
        JsonValue result = parser.readJsonValue();

        assertThat(result.type(), is(JsonValueType.NUMBER));
        assertThat(result.asNumber().doubleValue(), is(123.45));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseJsonValueBoolean() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write(true));
        JsonParser parser = createParser(smileData);
        JsonValue result = parser.readJsonValue();

        assertThat(result.type(), is(JsonValueType.BOOLEAN));
        assertThat(result.asBoolean().value(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseJsonValueNull() throws Exception {
        byte[] smileData = generateSmileBytes(JsonGenerator::writeNull);
        JsonParser parser = createParser(smileData);
        JsonValue result = parser.readJsonValue();

        assertThat(result.type(), is(JsonValueType.NULL));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseJsonValueObject() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeObjectStart();
            gen.write("key", "value");
            gen.writeObjectEnd();
        });
        JsonParser parser = createParser(smileData);
        JsonValue result = parser.readJsonValue();

        assertThat(result.type(), is(JsonValueType.OBJECT));
        assertThat(result.asObject().stringValue("key").orElseThrow(), is("value"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseJsonValueArray() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            gen.write(1);
            gen.write(2);
            gen.write(3);
            gen.writeArrayEnd();
        });
        JsonParser parser = createParser(smileData);
        JsonValue result = parser.readJsonValue();

        assertThat(result.type(), is(JsonValueType.ARRAY));
        assertThat(result.asArray().values().size(), is(3));
        assertThat(parser.hasNext(), is(false));
    }

    /*
     * Spec: "Low-level Format".
     * Rule: Smile text payloads are decoded as raw UTF-8 bytes before higher-level coercions such as `readChar()`.
     */
    @Test
    public void testReadChar() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write("A"));
        JsonParser parser = createParser(smileData);
        char result = parser.readChar();

        assertThat(result, is('A'));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadCharBackslash() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write("\\"));
        JsonParser parser = createParser(smileData);
        char result = parser.readChar();

        assertThat(result, is('\\'));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadCharUnicodeSingleCharacter() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write("é"));
        JsonParser parser = createParser(smileData);
        char result = parser.readChar();

        assertThat(result, is('é'));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadCharRejectsMultiChar() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write("AB"));
        JsonParser parser = createParser(smileData);

        assertThrows(JsonException.class, parser::readChar);
    }

    /*
     * Spec: "High-level format" and "Low-level Format".
     * Rule: malformed framing or truncated payloads are not valid Smile documents and must fail decoding.
     */
    @Test
    public void testParseInvalidData() {
        byte[] invalidData = new byte[] {1, 2, 3, 4, 5};
        assertThrows(JsonException.class, () -> createParser(invalidData));
    }

    @Test
    public void testParseTruncatedData() {
        byte[] truncatedData = new byte[] {0x3A, 0x29, 0x0A, 0x00, SmileConstants.TOKEN_INT32};
        JsonParser parser = createParser(truncatedData);
        assertThrows(JsonException.class, parser::readInt);
    }

    @Test
    public void testParseLargeString() throws Exception {
        StringBuilder largeString = new StringBuilder();
        int size = 10000;
        for (int i = 0; i < size; i++) {
            largeString.append((char) ('a' + (i % 26)));
        }
        String testString = largeString.toString();

        byte[] smileData = generateSmileBytes(gen -> gen.write(testString));
        JsonParser parser = createParser(smileData);
        String result = parser.readString();

        assertThat(result.length(), is(size));
        assertThat(result, is(testString));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseLargeArray() throws Exception {
        int size = 1000;
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            for (int i = 0; i < size; i++) {
                gen.write(i);
            }
            gen.writeArrayEnd();
        });
        JsonParser parser = createParser(smileData);
        JsonArray result = parser.readJsonArray();

        assertThat(result.values().size(), is(size));
        for (int i = 0; i < size; i++) {
            assertThat(result.get(i, JsonNull.instance()).asNumber().intValue(), is(i));
        }
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadByteBoundariesAndOverflow() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            gen.write(Byte.MIN_VALUE);
            gen.write(Byte.MAX_VALUE);
            gen.write((int) Byte.MAX_VALUE + 1);
            gen.writeArrayEnd();
        });

        JsonParser parser = createParser(smileData);
        assertThat(parser.currentByte(), is((byte) '['));
        assertThat(parser.nextToken(), is((byte) '1'));
        assertThat(parser.readByte(), is(Byte.MIN_VALUE));
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '1'));
        assertThat(parser.readByte(), is(Byte.MAX_VALUE));
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '1'));
        assertThrows(JsonException.class, parser::readByte);
    }

    @Test
    public void testReadShortBoundariesAndOverflow() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            gen.write(Short.MIN_VALUE);
            gen.write(Short.MAX_VALUE);
            gen.write((int) Short.MAX_VALUE + 1);
            gen.writeArrayEnd();
        });

        JsonParser parser = createParser(smileData);
        assertThat(parser.nextToken(), is((byte) '1'));
        assertThat(parser.readShort(), is(Short.MIN_VALUE));
        parser.nextToken();
        assertThat(parser.nextToken(), is((byte) '1'));
        assertThat(parser.readShort(), is(Short.MAX_VALUE));
        parser.nextToken();
        assertThat(parser.nextToken(), is((byte) '1'));
        assertThrows(JsonException.class, parser::readShort);
    }

    @Test
    public void testReadFloatFromLongPrecisionLossFails() throws Exception {
        long value = (1L << 40) + 1; // encoded as int64 and cannot be represented exactly as float
        byte[] smileData = generateSmileBytes(gen -> gen.write(value));
        JsonParser parser = createParser(smileData);

        assertThrows(JsonException.class, parser::readFloat);
    }

    @Test
    public void testSkipNestedValueAndContinue() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeObjectStart();
            gen.writeKey("skipMe");
            gen.writeObjectStart();
            gen.write("a", 1);
            gen.write("b", 2);
            gen.writeObjectEnd();
            gen.write("keep", "ok");
            gen.writeObjectEnd();
        });

        JsonParser parser = createParser(smileData);
        assertThat(parser.currentByte(), is((byte) '{'));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("skipMe"));
        assertThat(parser.nextToken(), is((byte) ':'));
        assertThat(parser.nextToken(), is((byte) '{'));
        parser.skip();
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("keep"));
        assertThat(parser.nextToken(), is((byte) ':'));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("ok"));
        assertThat(parser.nextToken(), is((byte) '}'));
    }

    @Test
    public void testSkipBooleanAndNullValuesAndContinue() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            gen.write(true);
            gen.writeNull();
            gen.write("ok");
            gen.writeArrayEnd();
        });

        JsonParser parser = createParser(smileData);
        assertThat(parser.currentByte(), is((byte) '['));
        assertThat(parser.nextToken(), is((byte) 't'));
        parser.skip();
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) 'n'));
        parser.skip();
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("ok"));
        assertThat(parser.nextToken(), is((byte) ']'));
    }

    @Test
    public void testMarkResetAndClearMark() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            gen.write(10);
            gen.write(20);
            gen.writeArrayEnd();
        });

        JsonParser parser = createParser(smileData);
        parser.mark();
        JsonArray firstRead = parser.readJsonArray();
        assertThat(firstRead.values().size(), is(2));

        parser.resetToMark();
        assertThrows(IllegalStateException.class, parser::resetToMark);
        parser.mark();
        JsonArray secondRead = parser.readJsonArray();
        assertThat(secondRead.values().size(), is(2));
        assertThat(secondRead.get(0, JsonNull.instance()).asNumber().intValue(), is(10));
        assertThat(secondRead.get(1, JsonNull.instance()).asNumber().intValue(), is(20));

        parser.clearMark();
        assertThrows(IllegalStateException.class, parser::resetToMark);
    }

    @Test
    public void testMarkCannotBeSetTwiceWithoutConsuming() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            gen.write(1);
            gen.writeArrayEnd();
        });

        JsonParser parser = createParser(smileData);
        parser.mark();

        assertThrows(IllegalStateException.class, parser::mark);
    }

    @Test
    public void testMarkResetRestoresSharedValueStrings() throws Exception {
        SmileConfig config = SmileConfig.builder()
                .sharedValueStrings(true)
                .build();
        byte[] smileData = generateSmileBytes(config, gen -> {
            gen.writeArrayStart();
            gen.write("a");
            gen.write("b");
            gen.write("c");
            gen.write("d");
            gen.write("e");
            gen.write("e");
            gen.writeArrayEnd();
        });

        JsonParser parser = createParser(smileData);
        assertThat(parser.currentByte(), is((byte) '['));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("a"));
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("b"));
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        parser.mark();
        assertThat(parser.readString(), is("c"));
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("d"));

        parser.resetToMark();

        assertThat(parser.readString(), is("c"));
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("d"));
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("e"));
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("e"));
        assertThat(parser.nextToken(), is((byte) ']'));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testMarkResetRestoresSharedKeyStrings() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            gen.writeObjectStart();
            gen.write("a", 1);
            gen.writeObjectEnd();
            gen.writeObjectStart();
            gen.write("b", 2);
            gen.writeObjectEnd();
            gen.writeObjectStart();
            gen.write("c", 3);
            gen.writeObjectEnd();
            gen.writeObjectStart();
            gen.write("d", 4);
            gen.writeObjectEnd();
            gen.writeObjectStart();
            gen.write("e", 5);
            gen.writeObjectEnd();
            gen.writeObjectStart();
            gen.write("e", 6);
            gen.writeObjectEnd();
            gen.writeArrayEnd();
        });

        JsonParser parser = createParser(smileData);
        assertThat(parser.currentByte(), is((byte) '['));
        assertThat(parser.nextToken(), is((byte) '{'));
        assertObjectValue(parser.readJsonObject(), "a", 1);
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '{'));
        assertObjectValue(parser.readJsonObject(), "b", 2);
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '{'));
        parser.mark();
        assertObjectValue(parser.readJsonObject(), "c", 3);
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '{'));
        assertObjectValue(parser.readJsonObject(), "d", 4);

        parser.resetToMark();

        assertObjectValue(parser.readJsonObject(), "c", 3);
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '{'));
        assertObjectValue(parser.readJsonObject(), "d", 4);
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '{'));
        assertObjectValue(parser.readJsonObject(), "e", 5);
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '{'));
        assertObjectValue(parser.readJsonObject(), "e", 6);
        assertThat(parser.nextToken(), is((byte) ']'));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadBinaryOnNonBinaryTokenFails() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write("not-binary"));
        JsonParser parser = createParser(smileData);

        assertThrows(JsonException.class, parser::readBinary);
    }

    /*
     * Spec: "Shared value Strings".
     * Rule: shared-value references must resolve correctly for short refs, long refs, high-bit variants, and reset
     * after the 1024-entry window fills.
     */
    @Test
    public void testParseSharedValueShortReference() {
        byte[] smileData = new byte[] {
                0x3A, 0x29, 0x0A, 0x03,
                (byte) 0xF8,
                0x45, 's', 'h', 'a', 'r', 'e', 'd',
                0x01,
                (byte) 0xF9
        };

        JsonParser parser = createParser(smileData);
        JsonArray result = parser.readJsonArray();

        assertThat(result.get(0, JsonNull.instance()).asString().value(), is("shared"));
        assertThat(result.get(1, JsonNull.instance()).asString().value(), is("shared"));
    }

    /*
     * Spec: "Token class: Misc; binary / text / structure markers" and "High-level format".
     * Rule: parsers must decode 7-bit-safe binary by default and honor the header-gated `"raw binary"` token `0xFD`.
     */
    @Test
    public void testParseBinary7Bit() throws Exception {
        byte[] expected = new byte[] {1, 2, 3, 4, 5, 6, 7};
        byte[] smileData = generateSmileBytes(gen -> gen.writeBinary(expected));

        JsonParser parser = createParser(smileData);
        byte[] result = parser.readBinary();

        assertArrayEquals(expected, result);
    }

    @Test
    public void testParseBinaryRawWhenEnabled() {
        byte[] expected = new byte[] {10, 20, 30, 40, 50};
        byte[] smileData = new byte[] {
                0x3A, 0x29, 0x0A, 0x05,
                (byte) 0xFD,
                (byte) 0x85,
                10, 20, 30, 40, 50
        };

        JsonParser parser = createParser(smileData);
        byte[] result = parser.readBinary();

        assertArrayEquals(expected, result);
    }

    @Test
    public void testParseBinaryRawWhenDisabledFails() {
        byte[] smileData = new byte[] {
                0x3A, 0x29, 0x0A, 0x01,
                (byte) 0xFD,
                (byte) 0x81,
                42
        };

        JsonParser parser = createParser(smileData);

        assertThrows(JsonException.class, parser::readBinary);
    }

    /*
     * Spec: "Shared key name Strings".
     * Rule: shared-key references must resolve correctly for short refs, long refs, high-bit variants, and reset
     * after the 1024-entry window fills.
     */
    @Test
    public void testParseSharedKeyShortReference() {
        byte[] smileData = new byte[] {
                0x3A, 0x29, 0x0A, 0x03,
                (byte) 0xFA,
                (byte) 0x83, 'n', 'a', 'm', 'e',
                0x44, 'f', 'i', 'r', 's', 't',
                0x40,
                0x45, 's', 'e', 'c', 'o', 'n', 'd',
                (byte) 0xFB
        };

        JsonParser parser = createParser(smileData);
        JsonObject result = parser.readJsonObject();

        assertThat(result.stringValue("name").orElseThrow(), is("second"));
    }

    @Test
    public void testParseSharedValueLongReference() {
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x3A);
        bytes.add((byte) 0x29);
        bytes.add((byte) 0x0A);
        bytes.add((byte) 0x03);
        bytes.add((byte) 0xF8);
        for (int i = 0; i <= 64; i++) {
            String value = "v" + i;
            byte[] utf = value.getBytes();
            bytes.add((byte) (0x40 | (utf.length - 1)));
            for (byte b : utf) {
                bytes.add(b);
            }
        }
        bytes.add((byte) 0xEC);
        bytes.add((byte) 0x40);
        bytes.add((byte) 0xF9);

        byte[] smileData = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            smileData[i] = bytes.get(i);
        }

        JsonParser parser = createParser(smileData);
        JsonArray result = parser.readJsonArray();
        assertThat(result.get(65, JsonNull.instance()).asString().value(), is("v64"));
    }

    @Test
    public void testParseSharedKeyLongReference() {
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x3A);
        bytes.add((byte) 0x29);
        bytes.add((byte) 0x0A);
        bytes.add((byte) 0x03);
        bytes.add((byte) 0xFA);

        for (int i = 0; i <= 64; i++) {
            String key = "k" + i;
            byte[] keyBytes = key.getBytes();
            bytes.add((byte) (0x80 | (keyBytes.length - 1)));
            for (byte b : keyBytes) {
                bytes.add(b);
            }
            bytes.add((byte) 0x40);
            bytes.add((byte) ('a' + (i % 26)));
        }

        bytes.add((byte) 0x30);
        bytes.add((byte) 0x40);
        bytes.add((byte) 0x43);
        bytes.add((byte) 'l');
        bytes.add((byte) 'a');
        bytes.add((byte) 's');
        bytes.add((byte) 't');

        bytes.add((byte) 0xFB);

        byte[] smileData = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            smileData[i] = bytes.get(i);
        }

        JsonParser parser = createParser(smileData);
        JsonObject result = parser.readJsonObject();
        assertThat(result.stringValue("k64").orElseThrow(), is("last"));
    }

    @Test
    public void testParseSharedValueLongReferenceHighBits() {
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x3A);
        bytes.add((byte) 0x29);
        bytes.add((byte) 0x0A);
        bytes.add((byte) 0x03);
        bytes.add((byte) 0xF8);
        for (int i = 0; i <= 256; i++) {
            String value = "v" + i;
            byte[] utf = value.getBytes();
            bytes.add((byte) (0x40 | (utf.length - 1)));
            for (byte b : utf) {
                bytes.add(b);
            }
        }
        bytes.add((byte) 0xED);
        bytes.add((byte) 0x00);
        bytes.add((byte) 0xF9);

        byte[] smileData = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            smileData[i] = bytes.get(i);
        }

        JsonParser parser = createParser(smileData);
        JsonArray result = parser.readJsonArray();
        assertThat(result.get(257, JsonNull.instance()).asString().value(), is("v256"));
    }

    @Test
    public void testParseSharedKeyLongReferenceHighBits() {
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x3A);
        bytes.add((byte) 0x29);
        bytes.add((byte) 0x0A);
        bytes.add((byte) 0x03);
        bytes.add((byte) 0xFA);

        for (int i = 0; i <= 256; i++) {
            String key = "k" + i;
            byte[] keyBytes = key.getBytes();
            bytes.add((byte) (0x80 | (keyBytes.length - 1)));
            for (byte b : keyBytes) {
                bytes.add(b);
            }
            bytes.add((byte) 0x40);
            bytes.add((byte) 'a');
        }

        bytes.add((byte) 0x31);
        bytes.add((byte) 0x00);
        bytes.add((byte) 0x42);
        bytes.add((byte) 'e');
        bytes.add((byte) 'n');
        bytes.add((byte) 'd');
        bytes.add((byte) 0xFB);

        byte[] smileData = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            smileData[i] = bytes.get(i);
        }

        JsonParser parser = createParser(smileData);
        JsonObject result = parser.readJsonObject();
        assertThat(result.stringValue("k256").orElseThrow(), is("end"));
    }

    @Test
    public void testSharedValueReferencesResetAfter1024Entries() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            for (int i = 0; i < 1024; i++) {
                gen.write("v" + i);
            }
            gen.write("v0");
            gen.writeArrayEnd();
        });

        JsonParser parser = createParser(smileData);
        JsonArray result = parser.readJsonArray();

        assertThat(result.values().size(), is(1025));
        assertThat(result.get(0, JsonNull.instance()).asString().value(), is("v0"));
        assertThat(result.get(1024, JsonNull.instance()).asString().value(), is("v0"));
    }

    @Test
    public void testSharedKeyReferencesResetAfter1024Entries() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeObjectStart();
            for (int i = 0; i < 1024; i++) {
                gen.write("k" + i, i);
            }
            gen.write("k0", 999);
            gen.writeObjectEnd();
        });

        JsonParser parser = createParser(smileData);
        JsonObject result = parser.readJsonObject();

        assertThat(result.intValue("k0").orElseThrow(), is(999));
        assertThat(result.intValue("k1023").orElseThrow(), is(1023));
    }

    /*
     * Spec: "Low-level Format", "Token classes: Tiny ASCII, Short ASCII", and
     * "Token classes: Tiny Unicode, Short Unicode".
     * Rule: Smile strings are raw UTF-8 payloads, so control characters are preserved and malformed ASCII/UTF-8 byte
     * sequences are rejected.
     */
    @Test
    public void testReadJsonStringPreservesRawUtf8AndControlCharacters() throws Exception {
        String value = "line1\nline2\\n";
        byte[] smileData = generateSmileBytes(gen -> gen.write(value));

        JsonParser parser = createParser(smileData);
        JsonString result = parser.readJsonString();

        assertThat(result.value(), is(value));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testAsciiTokenRejectsNonAsciiByte() {
        byte[] smileData = new byte[] {0x3A, 0x29, 0x0A, 0x01, 0x40, (byte) 0xC3};

        JsonParser parser = createParser(smileData);
        assertThrows(JsonException.class, parser::readString);
    }

    @Test
    public void testUnicodeTokenRejectsMalformedUtf8() {
        byte[] smileData = new byte[] {0x3A, 0x29, 0x0A, 0x01, (byte) 0x80, (byte) 0xC3, 0x28};

        JsonParser parser = createParser(smileData);
        assertThrows(JsonException.class, parser::readString);
    }

    @Test
    public void testReadStringAsHashRejectsMalformedUtf8WhenSharedKeysDisabled() {
        byte[] smileData = new byte[] {0x3A, 0x29, 0x0A, 0x00, (byte) 0xFA, (byte) 0xC0, (byte) 0xC3, 0x28,
                (byte) 0xC2, (byte) 0xFB};

        JsonParser parser = createParser(smileData);
        assertThat(parser.currentByte(), is((byte) '{'));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThrows(JsonException.class, parser::readStringAsHash);
    }

    @Test
    public void testReadStringAsHashEmptyString() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write(""));

        JsonParser parser = createParser(smileData);
        int hash = parser.readStringAsHash();

        assertThat(hash, is(0x811c9dc5));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadStringAsHashRegistersSharedRawUtf8Value() throws Exception {
        SmileConfig config = SmileConfig.builder()
                .sharedValueStrings(true)
                .build();
        String value = "line1\nline2\\n";
        byte[] smileData = generateSmileBytes(config, gen -> {
            gen.writeArrayStart();
            gen.write(value);
            gen.write(value);
            gen.writeArrayEnd();
        });

        JsonParser parser = createParser(smileData);
        assertThat(parser.currentByte(), is((byte) '['));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readStringAsHash(), is(fnv1aHashUtf8(value)));
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is(value));
        assertThat(parser.nextToken(), is((byte) ']'));
    }

    @Test
    public void testSkipEmptyArrayAndContinue() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            gen.writeArrayStart();
            gen.writeArrayEnd();
            gen.write(1);
            gen.writeArrayEnd();
        });

        JsonParser parser = createParser(smileData);
        assertThat(parser.currentByte(), is((byte) '['));
        assertThat(parser.nextToken(), is((byte) '['));
        parser.skip();
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '1'));
        assertThat(parser.readInt(), is(1));
        assertThat(parser.nextToken(), is((byte) ']'));
    }

    @Test
    public void testSkipStringRejectsMalformedUtf8() {
        byte[] smileData = new byte[] {0x3A, 0x29, 0x0A, 0x01, (byte) 0xF8, (byte) 0x80, (byte) 0xC3, 0x28,
                (byte) 0xF9};

        JsonParser parser = createParser(smileData);
        assertThat(parser.currentByte(), is((byte) '['));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThrows(JsonException.class, parser::skip);
    }

    /*
     * Spec: "High-level format".
     * Rule: the optional top-level end marker is consumable framing, not another logical value or separator.
     */
    @Test
    public void testTopLevelArrayEndMarkerDoesNotExposeExtraComma() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> {
            gen.writeArrayStart();
            gen.write(1);
            gen.writeArrayEnd();
        });
        byte[] framed = new byte[smileData.length + 1];
        System.arraycopy(smileData, 0, framed, 0, smileData.length);
        framed[framed.length - 1] = SmileConstants.END_OF_CONTENT;

        JsonParser parser = createParser(framed);
        JsonArray result = parser.readJsonArray();

        assertThat(result.values().size(), is(1));
        assertThat(parser.hasNext(), is(false));
        assertThrows(JsonException.class, parser::nextToken);
    }

    /*
     * Spec: "Tokens: key mode", "Token class: Simple literals, numbers", and
     * "Token class: Misc; binary / text / structure markers".
     * Rule: reserved tokens in the wrong mode, invalid VInt terminators, stray close markers, and truncated raw-binary
     * payloads must be rejected.
     */
    @Test
    public void testReservedKeyLongSharedTokenInValueModeFails() {
        byte[] smileData = new byte[] {0x3A, 0x29, 0x0A, 0x01, 0x30};
        assertThrows(JsonException.class, () -> createParser(smileData));
    }

    @Test
    public void testStrayEndArrayFails() {
        byte[] smileData = new byte[] {0x3A, 0x29, 0x0A, 0x01, (byte) 0xF9};
        assertThrows(JsonException.class, () -> createParser(smileData));
    }

    @Test
    public void testInvalidVIntFinalByteFails() {
        byte[] smileData = new byte[] {0x3A, 0x29, 0x0A, 0x01, 0x24, (byte) 0xC0};

        JsonParser parser = createParser(smileData);
        assertThrows(JsonException.class, parser::readInt);
    }

    @Test
    public void testRawBinaryTruncatedPayloadFails() {
        byte[] smileData = new byte[] {0x3A, 0x29, 0x0A, 0x05, (byte) 0xFD, (byte) 0x81};

        JsonParser parser = createParser(smileData);
        assertThrows(JsonException.class, parser::readBinary);
    }

    /*
     * Spec: "Shared value Strings", "Shared key name Strings", and "Avoiding references 0x??FE and 0x??FF".
     * Rule: 65-byte Unicode values are not shareable, and long shared references with forbidden low bytes are invalid.
     */
    @Test
    public void testShortUnicode65ByteValueIsNotShareable() {
        String value = "é".repeat(32) + "a";
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        assertThat(utf8.length, is(65));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x3A);
        baos.write(0x29);
        baos.write(0x0A);
        baos.write(0x03);
        baos.write(SmileConstants.TOKEN_START_ARRAY);
        baos.write(SmileConstants.VALUE_SHORT_UNICODE_PREFIX
                           | (utf8.length - SmileConstants.VALUE_SHORT_UNICODE_LENGTH_ADD));
        baos.writeBytes(utf8);
        baos.write(SmileConstants.VALUE_SHARED_SHORT_MIN);
        baos.write(SmileConstants.TOKEN_END_ARRAY);

        JsonParser parser = createParser(baos.toByteArray());
        assertThrows(JsonException.class, parser::readJsonArray);
    }

    @Test
    public void testInvalidLongSharedValueReferenceLowByte() {
        byte[] smileData = new byte[] {0x3A, 0x29, 0x0A, 0x03, (byte) 0xEC, (byte) 0xFE};
        JsonParser parser = createParser(smileData);
        assertThrows(JsonException.class, parser::readString);
    }

    @Test
    public void testInvalidLongSharedKeyReferenceLowByte() {
        byte[] smileData = new byte[] {0x3A, 0x29, 0x0A, 0x03, (byte) 0xFA, 0x30, (byte) 0xFE};
        JsonParser parser = createParser(smileData);
        assertThrows(JsonException.class, parser::readJsonObject);
    }

    private static int fnv1aHashUtf8(String value) {
        int hash = 0x811c9dc5;
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= current & 0xFF;
            hash *= 0x01000193;
        }
        return hash;
    }

    private static void assertObjectValue(JsonObject object, String key, int value) {
        assertThat(object.keys().size(), is(1));
        assertThat(object.intValue(key).orElseThrow(), is(value));
    }
}
