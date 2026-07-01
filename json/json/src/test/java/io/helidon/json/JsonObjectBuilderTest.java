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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonObjectBuilderTest {

    @Test
    void shouldSetAdditionalNumericValues() {
        BigInteger bigInteger = new BigInteger("123456789012345678901234567890");
        String expectedJson = "{\"positiveByte\":7,"
                + "\"negativeByte\":-7,"
                + "\"narrowedByte\":-7,"
                + "\"short\":1024,"
                + "\"long\":1234567890123,"
                + "\"float\":1.25,"
                + "\"bigInteger\":123456789012345678901234567890}";

        JsonObject result = JsonObject.builder()
                .set("positiveByte", (byte) 7)
                .set("negativeByte", (byte) -7)
                .set("narrowedByte", (byte) 249)
                .set("short", (short) 1024)
                .set("long", 1234567890123L)
                .set("float", 1.25F)
                .set("bigInteger", bigInteger)
                .build();

        assertThat(result.toString(), is(expectedJson));
        assertThat(result.byteValue("positiveByte").orElseThrow(), is((byte) 7));
        assertThat(result.byteValue("negativeByte").orElseThrow(), is((byte) -7));
        assertThat(result.byteValue("narrowedByte").orElseThrow(), is((byte) -7));
        assertThat(result.shortValue("short").orElseThrow(), is((short) 1024));
        assertThat(result.longValue("long").orElseThrow(), is(1234567890123L));
        assertThat(result.floatValue("float").orElseThrow(), is(1.25F));
        assertThat(result.bigIntegerValue("bigInteger").orElseThrow(), is(bigInteger));
    }

    @Test
    void shouldRoundTripAdditionalNumericValuesAsJsonValue() {
        BigInteger bigInteger = new BigInteger("123456789012345678901234567890");
        JsonObject original = JsonObject.builder()
                .set("positiveByte", (byte) 7)
                .set("negativeByte", (byte) -7)
                .set("narrowedByte", (byte) 249)
                .set("short", (short) 1024)
                .set("long", 1234567890123L)
                .set("float", 1.25F)
                .set("bigInteger", bigInteger)
                .build();

        JsonParser parser = JsonParser.create(original.toString());
        JsonValue parsedValue = parser.readJsonValue();
        assertThat(parsedValue.type(), is(JsonValueType.OBJECT));

        JsonObject parsedObject = parsedValue.asObject();
        assertThat(parsedObject.byteValue("positiveByte").orElseThrow(), is((byte) 7));
        assertThat(parsedObject.byteValue("negativeByte").orElseThrow(), is((byte) -7));
        assertThat(parsedObject.byteValue("narrowedByte").orElseThrow(), is((byte) -7));
        assertThat(parsedObject.shortValue("short").orElseThrow(), is((short) 1024));
        assertThat(parsedObject.longValue("long").orElseThrow(), is(1234567890123L));
        assertThat(parsedObject.floatValue("float").orElseThrow(), is(1.25F));
        assertThat(parsedObject.bigIntegerValue("bigInteger").orElseThrow(), is(bigInteger));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    void shouldReturnDefaultAdditionalNumericValues() {
        JsonObject object = JsonObject.builder().build();
        BigInteger bigInteger = new BigInteger("123456789012345678901234567890");

        assertThat(object.byteValue("byte", (byte) 7), is((byte) 7));
        assertThat(object.shortValue("short", (short) 1024), is((short) 1024));
        assertThat(object.longValue("long", 1234567890123L), is(1234567890123L));
        assertThat(object.floatValue("float", 1.25F), is(1.25F));
        assertThat(object.bigIntegerValue("bigInteger", bigInteger), is(bigInteger));
    }

    @Test
    void shouldAcceptSubtypeListsForArrayValues() {
        List<JsonString> values = List.of(JsonString.create("Ada"), JsonString.create("Bob"));

        JsonObject result = JsonObject.builder()
                .setValues("names", values)
                .build();

        assertThat(result.toString(), is("{\"names\":[\"Ada\",\"Bob\"]}"));
    }

    @Test
    void shouldCopyValuesFromExistingObject() {
        JsonObject source = JsonObject.builder()
                .set("name", "Ada")
                .set("active", true)
                .build();

        JsonObject result = JsonObject.builder()
                .from(source)
                .build();

        assertThat(result.toString(), is("{\"name\":\"Ada\",\"active\":true}"));
    }

    @Test
    void shouldAllowOverridesAfterFrom() {
        JsonObject source = JsonObject.builder()
                .set("name", "Ada")
                .set("active", true)
                .build();

        JsonObject result = JsonObject.builder()
                .from(source)
                .set("active", false)
                .set("team", "json")
                .build();

        assertThat(result.toString(), is("{\"name\":\"Ada\",\"active\":false,\"team\":\"json\"}"));
    }

    @Test
    void shouldRejectNullSource() {
        assertThrows(NullPointerException.class, () -> JsonObject.builder().from(null));
    }

    @ParameterizedTest
    @MethodSource("jsonObjects")
    void shouldRejectNullLookupKeys(JsonObject object) {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> object.containsKey(null)),
                () -> assertThrows(NullPointerException.class, () -> object.value(null)),
                () -> assertThrows(NullPointerException.class, () -> object.value(null, JsonNull.instance())),
                () -> assertThrows(NullPointerException.class, () -> object.booleanValue(null)),
                () -> assertThrows(NullPointerException.class, () -> object.booleanValue(null, false)),
                () -> assertThrows(NullPointerException.class, () -> object.objectValue(null)),
                () -> assertThrows(NullPointerException.class, () -> object.objectValue(null, JsonObject.empty())),
                () -> assertThrows(NullPointerException.class, () -> object.stringValue(null)),
                () -> assertThrows(NullPointerException.class, () -> object.stringValue(null, "default")),
                () -> assertThrows(NullPointerException.class, () -> object.byteValue(null)),
                () -> assertThrows(NullPointerException.class, () -> object.byteValue(null, (byte) 1)),
                () -> assertThrows(NullPointerException.class, () -> object.shortValue(null)),
                () -> assertThrows(NullPointerException.class, () -> object.shortValue(null, (short) 1)),
                () -> assertThrows(NullPointerException.class, () -> object.intValue(null)),
                () -> assertThrows(NullPointerException.class, () -> object.intValue(null, 1)),
                () -> assertThrows(NullPointerException.class, () -> object.longValue(null)),
                () -> assertThrows(NullPointerException.class, () -> object.longValue(null, 1L)),
                () -> assertThrows(NullPointerException.class, () -> object.floatValue(null)),
                () -> assertThrows(NullPointerException.class, () -> object.floatValue(null, 1F)),
                () -> assertThrows(NullPointerException.class, () -> object.doubleValue(null)),
                () -> assertThrows(NullPointerException.class, () -> object.doubleValue(null, 1D)),
                () -> assertThrows(NullPointerException.class, () -> object.bigIntegerValue(null)),
                () -> assertThrows(NullPointerException.class, () -> object.bigIntegerValue(null, BigInteger.ONE)),
                () -> assertThrows(NullPointerException.class, () -> object.numberValue(null)),
                () -> assertThrows(NullPointerException.class, () -> object.numberValue(null, BigDecimal.ONE)),
                () -> assertThrows(NullPointerException.class, () -> object.arrayValue(null)),
                () -> assertThrows(NullPointerException.class, () -> object.arrayValue(null, JsonArray.empty())));
    }

    @Test
    void shouldRejectNullKeysDuringConstruction() {
        LinkedHashMap<String, JsonValue> values = new LinkedHashMap<>();
        values.put(null, JsonNull.instance());

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> JsonObject.create(values)),
                () -> assertThrows(NullPointerException.class, () -> JsonObject.builder().setNull(null)));
    }

    private static Stream<JsonObject> jsonObjects() {
        return Stream.of(
                JsonObject.builder().set("value", 1).build(),
                JsonParser.create("{\"value\":1}").readJsonObject());
    }
}
