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

package io.helidon.json.tests;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.common.GenericType;
import io.helidon.json.JsonException;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonValue;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.smile.SmileParser;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class EnumTest {

    private final JsonBinding jsonBinding;

    EnumTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRootEnumProcessingParameterized(BindingMethod bindingMethod) {
        String expected = "\"VALUE1\"";
        String json = bindingMethod.serialize(jsonBinding, TestEnum.VALUE1);
        assertThat(json, is(expected));

        TestEnum testEnum = bindingMethod.deserialize(jsonBinding, expected, TestEnum.class);
        assertThat(testEnum, is(TestEnum.VALUE1));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testEnumInObjectParameterized(BindingMethod bindingMethod) {
        String expected = "{\"enumValue\":\"VALUE2\"}";
        String json = bindingMethod.serialize(jsonBinding, new RecordWithEnum(TestEnum.VALUE2));
        assertThat(json, is(expected));

        RecordWithEnum recordWithEnum = bindingMethod.deserialize(jsonBinding, expected, RecordWithEnum.class);
        assertThat(recordWithEnum.enumValue, is(TestEnum.VALUE2));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testEnumInObjectAsNullParameterized(BindingMethod bindingMethod) {
        String json = bindingMethod.serialize(jsonBinding, new RecordWithEnum(null));
        assertThat(json, is("{}"));

        String expected = "{\"enumValue\":null}";
        RecordWithEnum recordWithEnum = bindingMethod.deserialize(jsonBinding, expected, RecordWithEnum.class);
        assertThat(recordWithEnum.enumValue, is(nullValue()));
        recordWithEnum = bindingMethod.deserialize(jsonBinding, "{}", RecordWithEnum.class);
        assertThat(recordWithEnum.enumValue, is(nullValue()));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testInvalidEnumValueParameterized(BindingMethod bindingMethod) {
        assertThrows(JsonException.class, () -> bindingMethod.deserialize(jsonBinding, "\"INVALID\"", TestEnum.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void distinguishesDeclaredHashCollisions(BindingMethod method) {
        for (CollidingEnum value : CollidingEnum.values()) {
            String json = '"' + value.name() + '"';
            assertThat(method.serialize(jsonBinding, value), is(json));
            assertThat(method.deserialize(jsonBinding, json, CollidingEnum.class), is(value));
        }
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsUnknownHashCollisions(BindingMethod method) {
        // Both decoded strings have the same FNV-1a hash as "project".
        for (String name : new String[] {"unknown-IpUce6", "unknown-IpUce\\u0036"}) {
            assertThrows(JsonException.class,
                         () -> method.deserialize(jsonBinding, '"' + name + '"', SingleEnum.class));
        }
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void matchesEscapedAndUnicodeNames(BindingMethod method) {
        assertThat(method.deserialize(jsonBinding, "\"costar\\u0072ing\"", CollidingEnum.class), is(CollidingEnum.costarring));
        for (UnicodeEnum value : UnicodeEnum.values()) {
            String json = method.serialize(jsonBinding, value);
            assertThat(method.deserialize(jsonBinding, json, UnicodeEnum.class), is(value));
        }
        assertThat(method.deserialize(jsonBinding, "\"CAF\\u00c9\"", UnicodeEnum.class), is(UnicodeEnum.CAFÉ));
        assertThat(method.deserialize(jsonBinding, "\"\\u53d8\\u91cf\"", UnicodeEnum.class), is(UnicodeEnum.变量));
        assertThat(method.deserialize(jsonBinding, "\"\\uD801\\uDC00\"", UnicodeEnum.class), is(UnicodeEnum.𐐀));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void preservesArrayElementsAndNulls(BindingMethod method) {
        CollidingEnum[] values = method.deserialize(jsonBinding,
                                                    "[\"costarring\",null,\"liquid\",\"costar\\u0072ing\"]",
                                                    CollidingEnum[].class);
        assertThat(values, arrayContaining(CollidingEnum.costarring, null, CollidingEnum.liquid, CollidingEnum.costarring));
        assertThat(method.serialize(jsonBinding, values), is("[\"costarring\",null,\"liquid\",\"costarring\"]"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void matchesUsingGenericType(BindingMethod method) {
        GenericType<CollidingEnum> type = GenericType.create(CollidingEnum.class);
        assertThat(method.deserialize(jsonBinding, "\"costarring\"", type), is(CollidingEnum.costarring));
        assertThat(method.deserialize(jsonBinding, "\"liquid\"", type), is(CollidingEnum.liquid));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsUnknownNamesAndNonStrings(BindingMethod method) {
        for (String json : new String[] {"\"value1\"", "\"VALUE1x\"", "\"\"", "1", "true", "{}", "[]",
                "\"VALUE1\\q\"", "\"VALUE1\\uD800\""}) {
            assertThrows(JsonException.class, () -> method.deserialize(jsonBinding, json, TestEnum.class), json);
        }
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void handlesEmptyEnumsAndNullValues(BindingMethod method) {
        assertThrows(JsonException.class, () -> method.deserialize(jsonBinding, "\"UNKNOWN\"", EmptyEnum.class));
        assertThat(method.deserialize(jsonBinding, "null", EmptyEnum.class), nullValue());
        assertThat(method.deserialize(jsonBinding, "null", CollidingEnum.class), nullValue());
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void usesDeclaredNameInsteadOfToString(BindingMethod method) {
        assertThat(method.serialize(jsonBinding, DisplayEnum.VALUE), is("\"VALUE\""));
        assertThat(method.deserialize(jsonBinding, "\"VALUE\"", DisplayEnum.class), is(DisplayEnum.VALUE));
        JsonException exception = assertThrows(JsonException.class,
                                              () -> method.deserialize(jsonBinding, "\"display\"", DisplayEnum.class));
        assertThat(exception.getMessage(), containsString("VALUE"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void keepsEnumTypesSeparateAcrossBindings(BindingMethod method) {
        JsonBinding otherBinding = JsonBinding.create();
        assertThat(method.deserialize(jsonBinding, "\"VALUE1\"", TestEnum.class), is(TestEnum.VALUE1));
        assertThat(method.deserialize(otherBinding, "\"VALUE1\"", OtherEnum.class), is(OtherEnum.VALUE1));
        assertThat(method.deserialize(otherBinding, "\"VALUE2\"", TestEnum.class), is(TestEnum.VALUE2));
        assertThat(method.deserialize(jsonBinding, "\"VALUE2\"", OtherEnum.class), is(OtherEnum.VALUE2));
    }

    @ParameterizedTest
    @EnumSource(ParserInput.class)
    void matchesCompleteNamesWithAlternateParsers(ParserInput input) {
        JsonParser parser = input.parser("[\"costarring\",\"liquid\",\"costar\\u0072ing\"]", jsonBinding);
        assertThat(jsonBinding.deserialize(parser, CollidingEnum[].class),
                   arrayContaining(CollidingEnum.costarring, CollidingEnum.liquid, CollidingEnum.costarring));
        assertThat(jsonBinding.deserialize(input.parser("\"CAF\\u00c9\"", jsonBinding), UnicodeEnum.class), is(UnicodeEnum.CAFÉ));
        assertThat(jsonBinding.deserialize(input.parser("\"\\uD801\\uDC00\"", jsonBinding), UnicodeEnum.class),
                   is(UnicodeEnum.𐐀));
        JsonParser unknown = input.parser("\"unknown-IpUce6\"", jsonBinding);
        assertThrows(JsonException.class, () -> jsonBinding.deserialize(unknown, SingleEnum.class));
    }

    enum ParserInput {
        SMALL_STREAM,
        JSON_VALUE,
        SMILE_ARRAY,
        SMILE_STREAM;

        JsonParser parser(String json, JsonBinding binding) {
            if (this == SMALL_STREAM) {
                return JsonParser.create(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), 6);
            }
            JsonValue value = JsonParser.create(json).readJsonValue();
            if (this == JSON_VALUE) {
                return JsonParser.create(value);
            }
            byte[] smile = SmileBindingSupport.serializeSmile(binding, value);
            return this == SMILE_ARRAY ? SmileParser.create(smile) : SmileParser.create(new ByteArrayInputStream(smile), 8);
        }
    }

    // These distinct, valid Java enum names have the same FNV-1a hash.
    enum CollidingEnum {
        costarring,
        liquid
    }

    enum SingleEnum {
        project
    }

    enum UnicodeEnum {
        CAFÉ,
        变量,
        𐐀
    }

    enum EmptyEnum {
    }

    enum DisplayEnum {
        VALUE;

        @Override
        public String toString() {
            return "display";
        }
    }

    enum TestEnum {
        VALUE1,
        VALUE2,
        VALUE3
    }

    enum OtherEnum {
        VALUE1,
        VALUE2,
        VALUE3
    }

    @Json.Entity
    record RecordWithEnum(TestEnum enumValue) {
    }
}
