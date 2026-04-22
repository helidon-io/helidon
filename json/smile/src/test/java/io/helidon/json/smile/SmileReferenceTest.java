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
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonValue;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileParser;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Reference tests comparing Helidon Smile implementation with Jackson Smile.
 * These tests ensure Helidon's Smile format output is compatible and correct
 * by comparing against the reference Jackson implementation.
 *
 * <p>These are interoperability checks, not the primary byte-level spec assertions. Where grouped below,
 * the comment quotes the Smile spec section title that the compatibility slice corresponds to.</p>
 */
class SmileReferenceTest {

    private static final SmileFactory JACKSON_SMILE_FACTORY = SmileFactory.builder().build();

    /*
     * Spec area: "Token class: Simple literals, numbers" and "Token classes: Tiny ASCII, Short ASCII".
     * Cross-check: Helidon should emit the same on-wire representation as Jackson for the scalar token families.
     */
    @Test
    public void testNullReference() {
        byte[] jacksonBytes =
                generateJacksonSmileBytes(com.fasterxml.jackson.dataformat.smile.SmileGenerator::writeNull);
        byte[] helidonBytes = generateHelidonSmileBytes(JsonGenerator::writeNull);

        // Compare the encoded bytes
        assertThat("Null encoding should match Jackson", Arrays.equals(jacksonBytes, helidonBytes), is(true));
    }

    @Test
    public void testBooleanReference() {
        // Test true
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> gen.writeBoolean(true));
        byte[] helidonBytes = generateHelidonSmileBytes(gen -> gen.write(true));

        assertThat("Boolean true encoding should match Jackson", Arrays.equals(jacksonBytes, helidonBytes), is(true));

        // Test false
        byte[] jacksonBytesFalse = generateJacksonSmileBytes(gen -> gen.writeBoolean(false));
        byte[] helidonBytesFalse = generateHelidonSmileBytes(gen -> gen.write(false));

        assertThat("Boolean false encoding should match Jackson",
                   Arrays.equals(jacksonBytesFalse, helidonBytesFalse),
                   is(true));
    }

    @Test
    public void testEmptyStringReference() {
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> gen.writeString(""));
        byte[] helidonBytes = generateHelidonSmileBytes(gen -> gen.write(""));

        assertThat("Empty string encoding should match Jackson", Arrays.equals(jacksonBytes, helidonBytes), is(true));
    }

    @Test
    public void testSimpleStringReference() {
        String testString = "Hello World";
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> gen.writeString(testString));
        byte[] helidonBytes = generateHelidonSmileBytes(gen -> gen.write(testString));

        assertThat("Simple string encoding should match Jackson", Arrays.equals(jacksonBytes, helidonBytes), is(true));
    }

    @Test
    public void testUnicodeStringReference() {
        String testString = "Hello 世界 € 😀";
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> gen.writeString(testString));
        byte[] helidonBytes = generateHelidonSmileBytes(gen -> gen.write(testString));

        assertThat("Unicode string encoding should match Jackson", Arrays.equals(jacksonBytes, helidonBytes), is(true));
    }

    /*
     * Spec area: "Token class: Simple literals, numbers".
     * Cross-check: integral, floating-point, and big-number encodings stay compatible with Jackson's Smile codec.
     */
    @Test
    public void testIntegerReference() {
        int testValue = 12345;
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> gen.writeNumber(testValue));
        byte[] helidonBytes = generateHelidonSmileBytes(gen -> gen.write(testValue));

        assertThat("Integer encoding should match Jackson", Arrays.equals(jacksonBytes, helidonBytes), is(true));
    }

    @Test
    public void testLongReference() {
        long testValue = 123456789012345L;
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> gen.writeNumber(testValue));
        byte[] helidonBytes = generateHelidonSmileBytes(gen -> gen.write(testValue));

        assertThat("Long encoding should match Jackson", Arrays.equals(jacksonBytes, helidonBytes), is(true));
    }

    @Test
    public void testDoubleReference() {
        double testValue = 123.456789;
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> gen.writeNumber(testValue));
        byte[] helidonBytes = generateHelidonSmileBytes(gen -> gen.write(testValue));

        assertThat("Double encoding should match Jackson", Arrays.equals(jacksonBytes, helidonBytes), is(true));
    }

    @Test
    public void testBigIntegerReference() {
        BigInteger testValue = new BigInteger("1234567890123456789012345678901234567890");
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> gen.writeNumber(testValue));
        byte[] helidonBytes = generateHelidonSmileBytes(gen -> gen.write(testValue));

        assertThat("BigInteger encoding should match Jackson", Arrays.equals(jacksonBytes, helidonBytes), is(true));
    }

    @Test
    public void testBigDecimalReference() {
        BigDecimal testValue = new BigDecimal("12345678901234567890.1234567890");
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> gen.writeNumber(testValue));
        byte[] helidonBytes = generateHelidonSmileBytes(gen -> gen.write(testValue));

        assertThat("BigDecimal encoding should match Jackson", Arrays.equals(jacksonBytes, helidonBytes), is(true));
    }

    /*
     * Spec area: "High-level format".
     * Cross-check: structural start/end markers and nesting should match the reference implementation.
     */
    @Test
    public void testSimpleArrayReference() {
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> {
            gen.writeStartArray();
            gen.writeNumber(1);
            gen.writeString("hello");
            gen.writeBoolean(true);
            gen.writeEndArray();
        });

        byte[] helidonBytes = generateHelidonSmileBytes(gen -> {
            gen.writeArrayStart();
            gen.write(1);
            gen.write("hello");
            gen.write(true);
            gen.writeArrayEnd();
        });

        assertThat("Simple array encoding should match Jackson", Arrays.equals(jacksonBytes, helidonBytes), is(true));
    }

    /*
     * Spec area: "Tokens: key mode".
     * Cross-check: object key/value alternation and key-token selection should match Jackson.
     */
    @Test
    public void testSimpleObjectReference() {
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> {
            gen.writeStartObject();
            gen.writeStringField("key", "value");
            gen.writeNumberField("number", 42);
            gen.writeEndObject();
        });

        byte[] helidonBytes = generateHelidonSmileBytes(gen -> {
            gen.writeObjectStart();
            gen.write("key", "value");
            gen.write("number", 42);
            gen.writeObjectEnd();
        });

        assertThat("Simple object encoding should match Jackson", helidonBytes, is(jacksonBytes));
    }

    @Test
    public void testNestedStructureReference() {
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> {
            gen.writeStartObject();
            gen.writeFieldName("items");
            gen.writeStartArray();
            gen.writeString("first");
            gen.writeStartObject();
            gen.writeBooleanField("flag", true);
            gen.writeNumberField("count", 7);
            gen.writeEndObject();
            gen.writeEndArray();
            gen.writeEndObject();
        });

        byte[] helidonBytes = generateHelidonSmileBytes(gen -> {
            gen.writeObjectStart();
            gen.writeKey("items");
            gen.writeArrayStart();
            gen.write("first");
            gen.writeObjectStart();
            gen.write("flag", true);
            gen.write("count", 7);
            gen.writeObjectEnd();
            gen.writeArrayEnd();
            gen.writeObjectEnd();
        });

        assertThat("Nested structure encoding should match Jackson", helidonBytes, is(jacksonBytes));
    }

    /*
     * Spec area: "High-level format".
     * Cross-check: the first three header bytes are the fixed Smile signature bytes.
     */
    @Test
    public void testHeaderValidation() {
        byte[] helidonBytes = generateHelidonSmileBytes(gen -> gen.write("test"));

        // Check that the first 4 bytes match the Smile header
        assertThat("First byte should be :", helidonBytes[0], is((byte) 0x3A));
        assertThat("Second byte should be )", helidonBytes[1], is((byte) 0x29));
        assertThat("Third byte should be linefeed", helidonBytes[2], is((byte) 0x0A));
        // Fourth byte contains version/flags
    }

    /*
     * Spec area: "High-level format" and "Low-level Format".
     * Cross-check: both codecs can parse each other's Smile payloads without losing token semantics.
     */
    @Test
    public void testRoundTripCompatibility() {
        // Generate with Helidon
        String originalValue = "Hello 世界 123";
        byte[] helidonBytes = generateHelidonSmileBytes(gen -> gen.write(originalValue));

        // Parse with Jackson
        String jacksonParsed;
        try (SmileParser jacksonParser = JACKSON_SMILE_FACTORY.createParser(helidonBytes)) {
            jacksonParser.nextToken(); // Skip to first token
            jacksonParsed = jacksonParser.getText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat("Jackson should parse Helidon output correctly", jacksonParsed, is(originalValue));

        // Generate with Jackson
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> {
            try {
                gen.writeString(originalValue);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Parse with Helidon
        JsonParser helidonParser = io.helidon.json.smile.SmileParser.create(jacksonBytes);
        String helidonParsed = helidonParser.readString();
        assertThat("Helidon should parse Jackson output correctly", helidonParsed, is(originalValue));
    }

    @Test
    public void testJacksonSharedValueStringsParsedByHelidon() {
        SmileFactory factory = SmileFactory.builder()
                .enable(com.fasterxml.jackson.dataformat.smile.SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (com.fasterxml.jackson.dataformat.smile.SmileGenerator gen = factory.createGenerator(baos)) {
            gen.writeStartArray();
            gen.writeString("shared-value");
            gen.writeString("shared-value");
            gen.writeString("shared-value");
            gen.writeEndArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        byte[] jacksonBytes = baos.toByteArray();

        // Header bit 1 indicates shared value strings enabled.
        assertThat((jacksonBytes[3] & 0x02) != 0, is(true));

        JsonParser parser = io.helidon.json.smile.SmileParser.create(jacksonBytes);
        JsonArray result = parser.readJsonArray();

        assertThat(result.values().size(), is(3));
        assertThat(result.get(0, JsonNull.instance()).asString().value(), is("shared-value"));
        assertThat(result.get(1, JsonNull.instance()).asString().value(), is("shared-value"));
        assertThat(result.get(2, JsonNull.instance()).asString().value(), is("shared-value"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testJacksonSharedKeyNamesParsedByHelidon() {
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> {
            gen.writeStartArray();

            gen.writeStartObject();
            gen.writeStringField("shared-key", "first");
            gen.writeNumberField("idx", 1);
            gen.writeEndObject();

            gen.writeStartObject();
            gen.writeStringField("shared-key", "second");
            gen.writeNumberField("idx", 2);
            gen.writeEndObject();

            gen.writeStartObject();
            gen.writeStringField("shared-key", "third");
            gen.writeNumberField("idx", 3);
            gen.writeEndObject();

            gen.writeEndArray();
        });

        JsonParser parser = io.helidon.json.smile.SmileParser.create(jacksonBytes);
        JsonArray result = parser.readJsonArray();

        assertThat(result.values().size(), is(3));
        List<JsonValue> values = result.values();

        assertThat(values.get(0).asObject().stringValue("shared-key").orElseThrow(), is("first"));
        assertThat(values.get(1).asObject().stringValue("shared-key").orElseThrow(), is("second"));
        assertThat(values.get(2).asObject().stringValue("shared-key").orElseThrow(), is("third"));
        assertThat(values.get(0).asObject().intValue("idx").orElseThrow(), is(1));
        assertThat(values.get(1).asObject().intValue("idx").orElseThrow(), is(2));
        assertThat(values.get(2).asObject().intValue("idx").orElseThrow(), is(3));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testHelidonSharedValueStringsParsedByJackson() {
        SmileConfig config = SmileConfig.builder()
                .sharedValueStrings(true)
                .build();

        byte[] helidonBytes = generateHelidonSmileBytes(config, gen -> {
            gen.writeArrayStart();
            gen.write("shared-value");
            gen.write("shared-value");
            gen.write("shared-value");
            gen.writeArrayEnd();
        });

        try (SmileParser parser = JACKSON_SMILE_FACTORY.createParser(helidonBytes)) {
            assertThat(parser.nextToken(), is(JsonToken.START_ARRAY));
            assertThat(parser.nextToken(), is(JsonToken.VALUE_STRING));
            assertThat(parser.getText(), is("shared-value"));
            assertThat(parser.nextToken(), is(JsonToken.VALUE_STRING));
            assertThat(parser.getText(), is("shared-value"));
            assertThat(parser.nextToken(), is(JsonToken.VALUE_STRING));
            assertThat(parser.getText(), is("shared-value"));
            assertThat(parser.nextToken(), is(JsonToken.END_ARRAY));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testHelidonSharedKeyNamesParsedByJackson() {
        byte[] helidonBytes = generateHelidonSmileBytes(gen -> {
            gen.writeArrayStart();

            gen.writeObjectStart();
            gen.write("shared-key", "first");
            gen.write("idx", 1);
            gen.writeObjectEnd();

            gen.writeObjectStart();
            gen.write("shared-key", "second");
            gen.write("idx", 2);
            gen.writeObjectEnd();

            gen.writeObjectStart();
            gen.write("shared-key", "third");
            gen.write("idx", 3);
            gen.writeObjectEnd();

            gen.writeArrayEnd();
        });

        try (SmileParser parser = JACKSON_SMILE_FACTORY.createParser(helidonBytes)) {
            assertThat(parser.nextToken(), is(JsonToken.START_ARRAY));

            assertObject(parser, "first", 1);
            assertObject(parser, "second", 2);
            assertObject(parser, "third", 3);

            assertThat(parser.nextToken(), is(JsonToken.END_ARRAY));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testHelidonRawBinaryParsedByJackson() {
        SmileConfig config = SmileConfig.builder()
                .rawBinaryEnabled(true)
                .build();
        byte[] payload = new byte[] {1, 2, 3, 4, 5, (byte) 0xFE, (byte) 0xFF};

        byte[] helidonBytes = generateHelidonSmileBytes(config, gen -> gen.writeBinary(payload));

        try (SmileParser parser = JACKSON_SMILE_FACTORY.createParser(helidonBytes)) {
            assertThat(parser.nextToken(), is(JsonToken.VALUE_EMBEDDED_OBJECT));
            assertThat(parser.getBinaryValue(), is(payload));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testJacksonBigNumbersParsedByHelidon() {
        BigInteger bigInteger = new BigInteger("1234567890123456789012345678901234567890");
        BigDecimal bigDecimal = new BigDecimal("98765432109876543210.0123456789");

        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> {
            gen.writeStartArray();
            gen.writeNumber(bigInteger);
            gen.writeNumber(bigDecimal);
            gen.writeEndArray();
        });

        JsonParser parser = io.helidon.json.smile.SmileParser.create(jacksonBytes);
        JsonArray result = parser.readJsonArray();

        assertThat(result.values().size(), is(2));
        assertThat(result.get(0, JsonNull.instance()).asNumber().bigDecimalValue().toBigIntegerExact(), is(bigInteger));
        assertThat(result.get(1, JsonNull.instance()).asNumber().bigDecimalValue(), is(bigDecimal));
    }

    @Test
    public void testJacksonRawBinaryParsedByHelidon() {
        SmileFactory factory = SmileFactory.builder()
                .enable(com.fasterxml.jackson.dataformat.smile.SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT)
                .disable(com.fasterxml.jackson.dataformat.smile.SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT)
                .build();
        byte[] payload = new byte[] {9, 8, 7, 6, 5, (byte) 0xFE, (byte) 0xFF};

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (com.fasterxml.jackson.dataformat.smile.SmileGenerator gen = factory.createGenerator(baos)) {
            gen.writeBinary(payload);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        JsonParser parser = io.helidon.json.smile.SmileParser.create(baos.toByteArray());
        assertThat(parser.readBinary(), is(payload));
    }

    @Test
    public void testJacksonEndMarkerAcceptedByHelidon() {
        byte[] jacksonBytes = generateJacksonSmileBytes(gen -> gen.writeString("done"));
        byte[] withEndMarker = Arrays.copyOf(jacksonBytes, jacksonBytes.length + 1);
        withEndMarker[withEndMarker.length - 1] = (byte) 0xFF;

        JsonParser parser = io.helidon.json.smile.SmileParser.create(withEndMarker);
        assertThat(parser.readString(), is("done"));
        assertThat(parser.hasNext(), is(false));
    }

    // Helper method to generate Jackson Smile bytes
    private byte[] generateJacksonSmileBytes(JacksonConsumer writer) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (com.fasterxml.jackson.dataformat.smile.SmileGenerator generator =
                     JACKSON_SMILE_FACTORY.createGenerator(baos)) {
            writer.accept(generator);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    private interface JacksonConsumer{

        void accept(com.fasterxml.jackson.dataformat.smile.SmileGenerator smileGenerator) throws IOException;

    }

    // Helper method to generate Helidon Smile bytes
    private byte[] generateHelidonSmileBytes(Consumer<JsonGenerator> writer) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(baos)) {
            writer.accept(generator);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    private byte[] generateHelidonSmileBytes(SmileConfig config, Consumer<JsonGenerator> writer) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(baos, config)) {
            writer.accept(generator);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    private static void assertObject(SmileParser parser,
                                     String expectedSharedKeyValue,
                                     int expectedIdx) throws IOException {
        assertThat(parser.nextToken(), is(JsonToken.START_OBJECT));
        assertThat(parser.nextToken(), is(JsonToken.FIELD_NAME));
        assertThat(parser.currentName(), is("shared-key"));
        assertThat(parser.nextToken(), is(JsonToken.VALUE_STRING));
        assertThat(parser.getText(), is(expectedSharedKeyValue));
        assertThat(parser.nextToken(), is(JsonToken.FIELD_NAME));
        assertThat(parser.currentName(), is("idx"));
        assertThat(parser.nextToken(), is(JsonToken.VALUE_NUMBER_INT));
        assertThat(parser.getIntValue(), is(expectedIdx));
        assertThat(parser.nextToken(), is(JsonToken.END_OBJECT));
    }
}
