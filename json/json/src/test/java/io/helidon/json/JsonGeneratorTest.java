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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Base64;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for JsonGenerator functionality.
 * Tests basic generation, object/array structures, different data types,
 * and error conditions.
 */
class JsonGeneratorTest {

    private static final int LARGE_VALUE_LENGTH = 16_384;
    private static final JsonKey ESCAPED_KEY = JsonKey.create("na\"me\\\n\u20AC\uD83D\uDE00");

    // Basic value tests
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteString(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write("hello");
        }

        assertThat(target.generatedJson(), is("\"hello\""));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteInt(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(42);
        }

        assertThat(target.generatedJson(), is("42"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteLong(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(123456789L);
        }

        assertThat(target.generatedJson(), is("123456789"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteFloat(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(3.14f);
        }

        assertThat(target.generatedJson(), is("3.14"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteDouble(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(2.71828);
        }

        assertThat(target.generatedJson(), is("2.71828"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteBooleanTrue(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(true);
        }

        assertThat(target.generatedJson(), is("true"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteBooleanFalse(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(false);
        }

        assertThat(target.generatedJson(), is("false"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteNull(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeNull();
        }

        assertThat(target.generatedJson(), is("null"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteBinaryAsRootValue(GeneratorMethod generatorMethod) throws Exception {
        byte[] value = "my-test-value".getBytes();
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeBinary(value);
        }

        assertThat(target.generatedJson(), is("\"" + Base64.getEncoder().encodeToString(value) + "\""));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteBinaryAsObjectField(GeneratorMethod generatorMethod) throws Exception {
        byte[] value = "hello".getBytes();
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .writeBinary("payload", value)
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(), is("{\"payload\":\"" + Base64.getEncoder().encodeToString(value) + "\"}"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteEmptyBinaryAsObjectField(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .writeBinary("payload", new byte[0])
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(), is("{\"payload\":\"\"}"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteChar(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write('A');
        }

        assertThat(target.generatedJson(), is("\"A\""));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteByte(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write((byte) 127);
        }

        assertThat(target.generatedJson(), is("127"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteShort(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write((short) 32767);
        }

        assertThat(target.generatedJson(), is("32767"));
    }

    // Object tests
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteSimpleObject(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .write("name", "John")
                    .write("age", 30)
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(), is("{\"name\":\"John\",\"age\":30}"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteNestedObject(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .writeKey("person")
                    .writeObjectStart()
                    .write("name", "John")
                    .write("age", 30)
                    .writeObjectEnd()
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(), is("{\"person\":{\"name\":\"John\",\"age\":30}}"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testPrettyPrintObject(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createPrettyGenerator()) {
            generator.writeObjectStart()
                    .write("name", "John")
                    .writeKey("address")
                    .writeObjectStart()
                    .write("city", "Prague")
                    .writeObjectEnd()
                    .writeObjectEnd();
        }

        String expected = """
                {
                   "name": "John",
                   "address": {
                      "city": "Prague"
                   }
                }""";
        assertThat(target.generatedJson(), is(expected));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteObjectWithJsonKey(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .writeKey(ESCAPED_KEY)
                    .write("value")
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(), is("{\"na\\\"me\\\\\\n€\\uD83D\\uDE00\":\"value\"}"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWritePrecomputedKeyAlias(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .writePrecomputedKey(ESCAPED_KEY)
                    .write("value")
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(), is("{\"na\\\"me\\\\\\n€\\uD83D\\uDE00\":\"value\"}"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteObjectWithJsonKeyOverloads(GeneratorMethod generatorMethod) throws Exception {
        byte[] payload = "hello".getBytes();

        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .write(JsonKey.create("name"), "John")
                    .write(JsonKey.create("age"), 30)
                    .write(JsonKey.create("count"), 1234567890123L)
                    .write(JsonKey.create("ratio"), 3.5f)
                    .write(JsonKey.create("score"), 7.25d)
                    .write(JsonKey.create("active"), true)
                    .write(JsonKey.create("initial"), 'J')
                    .write(JsonKey.create("amount"), new BigDecimal("12.50"))
                    .write(JsonKey.create("id"), new BigInteger("12345678901234567890"))
                    .write(JsonKey.create("json"), JsonBoolean.TRUE)
                    .write(JsonKey.create("nickname"), (String) null)
                    .writeBinary(JsonKey.create("payload"), payload)
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(), is("{\"name\":\"John\",\"age\":30,\"count\":1234567890123,"
                                                       + "\"ratio\":3.5,\"score\":7.25,\"active\":true,"
                                                       + "\"initial\":\"J\",\"amount\":12.50,"
                                                       + "\"id\":12345678901234567890,\"json\":true,"
                                                       + "\"nickname\":null,\"payload\":\""
                                                       + Base64.getEncoder().encodeToString(payload) + "\"}"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteObjectWithBoolean(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .write("active", true)
                    .write("deleted", false)
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(), is("{\"active\":true,\"deleted\":false}"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteObjectWithNull(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .write("name", "John")
                    .write("nickname", (String) null)
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(), is("{\"name\":\"John\",\"nickname\":null}"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteObjectWithJsonNumberValue(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .write("id", JsonNumber.create(new BigDecimal("1")))
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(), is("{\"id\":1}"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteBigDecimalAsNumber(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(new BigDecimal("12345678901234567890.123456789"));
        }

        assertThat(target.generatedJson(), is("12345678901234567890.123456789"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteBigIntegerAsNumber(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(new BigInteger("123456789012345678901234567890"));
        }

        assertThat(target.generatedJson(), is("123456789012345678901234567890"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteObjectWithNestedJsonObjectValue(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        JsonObject nested = JsonObject.builder()
                .set("code", JsonNumber.create(new BigDecimal("7")))
                .build();

        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .write("error", nested)
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(), is("{\"error\":{\"code\":7}}"));
    }

    // Array tests
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteSimpleArray(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeArrayStart()
                    .write("a")
                    .write("b")
                    .write("c")
                    .writeArrayEnd();
        }

        assertThat(target.generatedJson(), is("[\"a\",\"b\",\"c\"]"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteMixedArray(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeArrayStart()
                    .write("string")
                    .write(42)
                    .write(true)
                    .writeNull()
                    .writeArrayEnd();
        }

        assertThat(target.generatedJson(), is("[\"string\",42,true,null]"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteNestedArray(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeArrayStart()
                    .writeArrayStart()
                    .write(1)
                    .write(2)
                    .writeArrayEnd()
                    .writeArrayStart()
                    .write(3)
                    .write(4)
                    .writeArrayEnd()
                    .writeArrayEnd();
        }

        assertThat(target.generatedJson(), is("[[1,2],[3,4]]"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testPrettyPrintArray(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createPrettyGenerator()) {
            generator.writeArrayStart()
                    .write("a")
                    .writeObjectStart()
                    .write("active", true)
                    .writeObjectEnd()
                    .writeArrayEnd();
        }

        String expected = """
                [
                   "a",
                   {
                      "active": true
                   }
                ]""";
        assertThat(target.generatedJson(), is(expected));
    }

    // Complex structure tests
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteComplexObject(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .writeKey("users")
                    .writeArrayStart()
                    .writeObjectStart()
                    .write("id", 1)
                    .write("name", "Alice")
                    .writeObjectEnd()
                    .writeObjectStart()
                    .write("id", 2)
                    .write("name", "Bob")
                    .writeObjectEnd()
                    .writeArrayEnd()
                    .write("total", 2)
                    .writeObjectEnd();
        }

        assertThat(target.generatedJson(), is("{\"users\":[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}],\"total\":2}"));
    }

    // Error condition tests
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteKeyOutsideObject(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            assertThrows(JsonException.class, () -> generator.writeKey("key"));
        }
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteJsonKeyOutsideObject(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            assertThrows(JsonException.class, () -> generator.writeKey(JsonKey.create("key")));
        }
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWritePrecomputedKeyOutsideObject(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            assertThrows(JsonException.class, () -> generator.writePrecomputedKey(JsonKey.create("key")));
        }
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteNullStringKey(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart();

            assertThrows(JsonException.class, () -> generator.writeKey((String) null));
        }
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteNullJsonKey(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart();

            assertThrows(JsonException.class, () -> generator.writeKey((JsonKey) null));
        }
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteNullPrecomputedKey(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart();

            assertThrows(JsonException.class, () -> generator.writePrecomputedKey(null));
        }
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteJsonKeyValueOutsideObject(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            assertThrows(JsonException.class, () -> generator.write(JsonKey.create("key"), "value"));
        }
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteNullStringValueKey(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart();

            assertThrows(JsonException.class, () -> generator.write((String) null, "value"));
        }
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteNullJsonKeyValueKey(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart();

            assertThrows(JsonException.class, () -> generator.write((JsonKey) null, "value"));
        }
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteValueWithoutKeyInObject(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart();

            assertThrows(JsonException.class, () -> generator.write("value"));
        }
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteMultipleRootValues(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write("first");

            assertThrows(JsonException.class, () -> generator.write("second"));
        }
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteKeyTwice(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .writeKey("key1");

            assertThrows(JsonException.class, () -> generator.writeKey("key2"));
        }
    }

    // String escaping tests
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteStringWithQuotes(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write("He said \"Hello\"");
        }

        assertThat(target.generatedJson(), is("\"He said \\\"Hello\\\"\""));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteStringWithBackslash(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write("path\\to\\file");
        }

        assertThat(target.generatedJson(), is("\"path\\\\to\\\\file\""));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteStringWithNewline(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write("line1\nline2");
        }

        assertThat(target.generatedJson(), is("\"line1\\nline2\""));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteStringWithControlChars(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write("tab:\tcontrol:\u0001");
        }

        assertThat(target.generatedJson(), is("\"tab:\\tcontrol:\\u0001\""));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteLargeString(GeneratorMethod generatorMethod) throws Exception {
        String value = "x".repeat(LARGE_VALUE_LENGTH);

        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(value);
        }

        assertThat(target.generatedJson(), is('"' + value + '"'));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteLargeEscapedString(GeneratorMethod generatorMethod) throws Exception {
        String value = "prefix-" + "\\\"\n\t".repeat(4_096) + "-suffix";

        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(value);
        }

        String expected = '"' + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                + '"';

        assertThat(target.generatedJson(), is(expected));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteLargeJsonKey(GeneratorMethod generatorMethod) throws Exception {
        String key = "prefix-" + "\\\"\n\t".repeat(4_096) + "-suffix";
        JsonKey jsonKey = JsonKey.create(key);

        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.writeObjectStart()
                    .writeKey(jsonKey)
                    .write(1)
                    .writeObjectEnd();
        }

        String expected = "{\""
                + key.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\t", "\\t")
                + "\":1}";

        assertThat(target.generatedJson(), is(expected));
    }

    // Special number cases
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteZero(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(0);
        }

        assertThat(target.generatedJson(), is("0"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteNegativeNumber(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(-42);
        }

        assertThat(target.generatedJson(), is("-42"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteFloatZero(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(0.0f);
        }

        assertThat(target.generatedJson(), is("0.0"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteDoubleZero(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(0.0);
        }

        assertThat(target.generatedJson(), is("0.0"));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteFloatNaN(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(Float.NaN);
        }

        assertThat(target.generatedJson(), is("\"NaN\""));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteFloatPositiveInfinity(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(Float.POSITIVE_INFINITY);
        }

        assertThat(target.generatedJson(), is("\"Infinity\""));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteFloatNegativeInfinity(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(Float.NEGATIVE_INFINITY);
        }

        assertThat(target.generatedJson(), is("\"-Infinity\""));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteDoubleNaN(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(Double.NaN);
        }

        assertThat(target.generatedJson(), is("\"NaN\""));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteDoubleNegativeInfinity(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(Double.NEGATIVE_INFINITY);
        }
        assertThat(target.generatedJson(), is("\"-Infinity\""));
    }

    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    public void testWriteDoublePositiveInfinity(GeneratorMethod generatorMethod) throws Exception {
        GeneratorMethod.Target target = generatorMethod.createTarget();
        try (JsonGenerator generator = target.createGenerator()) {
            generator.write(Double.POSITIVE_INFINITY);
        }
        assertThat(target.generatedJson(), is("\"Infinity\""));
    }

}
