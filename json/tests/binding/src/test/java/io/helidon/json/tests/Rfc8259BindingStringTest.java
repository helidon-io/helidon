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

import java.util.LinkedHashMap;
import java.util.Map;

import io.helidon.common.GenericType;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

@Testing.Test
public class Rfc8259BindingStringTest {

    private final JsonBinding jsonBinding;

    Rfc8259BindingStringTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks, except for the characters that MUST be escaped: quotation mark, reverse solidus, and the control characters (U+0000 through U+001F)."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testWritesStringValuesWithMandatoryEscapes(BindingMethod bindingMethod) {
        String value = "\"\\\n\t\r" + '\u0001';

        String json = bindingMethod.serialize(jsonBinding, value);
        assertThat(json, is("\"\\\"\\\\\\n\\t\\r\\u0001\""));
        assertThat(bindingMethod.deserialize(jsonBinding, json, String.class), is(value));
    }

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks, except for the characters that MUST be escaped: quotation mark, reverse solidus, and the control characters (U+0000 through U+001F)."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testWritesObjectNamesAndStringValuesWithMandatoryEscapes(BindingMethod bindingMethod) {
        String key = "k\"\\\n\t\r" + '\u0001';
        String value = "v\"\\\n\t\r" + '\u0001';
        Map<String, String> map = new LinkedHashMap<>();
        GenericType<Map<String, String>> type = new GenericType<>() { };

        map.put(key, value);

        String json = bindingMethod.serialize(jsonBinding, map, type);
        assertThat(json, is("{\"k\\\"\\\\\\n\\t\\r\\u0001\":\"v\\\"\\\\\\n\\t\\r\\u0001\"}"));
        assertThat(bindingMethod.deserialize(jsonBinding, json, type).get(key), is(value));
    }

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks, except for the characters that MUST be escaped: quotation mark, reverse solidus, and the control characters (U+0000 through U+001F)."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testWritesCharValuesWithMandatoryEscapes(BindingMethod bindingMethod) {
        assertAll(
                () -> runRootCharScenario(bindingMethod, '"', "\"\\\"\""),
                () -> runRootCharScenario(bindingMethod, '\\', "\"\\\\\""),
                () -> runRootCharScenario(bindingMethod, '\n', "\"\\n\""),
                () -> runRootCharScenario(bindingMethod, '\t', "\"\\t\""),
                () -> runRootCharScenario(bindingMethod, '\r', "\"\\r\""),
                () -> runRootCharScenario(bindingMethod, '\u0001', "\"\\u0001\"")
        );
    }

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks, except for the characters that MUST be escaped: quotation mark, reverse solidus, and the control characters (U+0000 through U+001F)."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testWritesCharacterObjectMembersWithMandatoryEscapes(BindingMethod bindingMethod) {
        assertAll(
                () -> runCharacterPropertyScenario(bindingMethod, '"', "{\"value\":\"\\\"\"}"),
                () -> runCharacterPropertyScenario(bindingMethod, '\\', "{\"value\":\"\\\\\"}"),
                () -> runCharacterPropertyScenario(bindingMethod, '\n', "{\"value\":\"\\n\"}"),
                () -> runCharacterPropertyScenario(bindingMethod, '\t', "{\"value\":\"\\t\"}"),
                () -> runCharacterPropertyScenario(bindingMethod, '\r', "{\"value\":\"\\r\"}"),
                () -> runCharacterPropertyScenario(bindingMethod, '\u0001', "{\"value\":\"\\u0001\"}")
        );
    }

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks,"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testWritesUnicodeStringDataAsEquivalentCharacters(BindingMethod bindingMethod) {
        String value = "€😀žluťoučký";
        String json = bindingMethod.serialize(jsonBinding, value);

        assertThat(bindingMethod.deserialize(jsonBinding, json, String.class), is(value));
    }

    private void runRootCharScenario(BindingMethod bindingMethod, char value, String expectedJson) {
        String json = bindingMethod.serialize(jsonBinding, value);
        assertThat(json, is(expectedJson));
        assertThat(bindingMethod.deserialize(jsonBinding, json, char.class), is(value));
    }

    private void runCharacterPropertyScenario(BindingMethod bindingMethod, char value, String expectedJson) {
        CharacterBean bean = new CharacterBean(value);
        String json = bindingMethod.serialize(jsonBinding, bean);

        assertThat(json, is(expectedJson));
        assertThat(bindingMethod.deserialize(jsonBinding, json, CharacterBean.class), is(bean));
    }

    @Json.Entity
    record CharacterBean(Character value) {

    }
}
