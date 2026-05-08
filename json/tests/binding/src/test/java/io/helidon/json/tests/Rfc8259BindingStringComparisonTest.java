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
public class Rfc8259BindingStringComparisonTest {

    private final JsonBinding jsonBinding;

    Rfc8259BindingStringComparisonTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    /**
     * RFC 8259 §8.3
     * Quote: "Implementations that compare strings with escaped characters unconverted may incorrectly find that \"a\\b\" and \"a\\u005Cb\" are not equal."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.3
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testEquivalentEscapedStringFormsDeserializeToEqualJavaStrings(BindingMethod bindingMethod) {
        String reverseSolidusEscaped = bindingMethod.deserialize(jsonBinding, "\"a\\\\b\"", String.class);
        String unicodeEscaped = bindingMethod.deserialize(jsonBinding, "\"a\\u005Cb\"", String.class);

        assertAll(
                () -> assertThat(reverseSolidusEscaped, is("a\\b")),
                () -> assertThat(unicodeEscaped, is("a\\b")),
                () -> assertThat(reverseSolidusEscaped, is(unicodeEscaped))
        );
    }

    /**
     * RFC 8259 §8.3
     * Quote: "Software implementations are typically required to test names of object members for equality."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.3
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testBeanPropertyNamesMatchEscapedObjectMemberNames(BindingMethod bindingMethod) {
        PropertyNameBean bean = bindingMethod.deserialize(jsonBinding,
                                                          "{\"val\\u0075e\":\"alpha\",\"na\\u006De\":\"beta\"}",
                                                          PropertyNameBean.class);

        assertAll(
                () -> assertThat(bean.value(), is("alpha")),
                () -> assertThat(bean.name(), is("beta"))
        );
    }

    /**
     * RFC 8259 §8.3
     * Quote: "Software implementations are typically required to test names of object members for equality."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.3
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testMapKeysMaterializeUsingDecodedCodeUnits(BindingMethod bindingMethod) {
        GenericType<Map<String, Integer>> type = new GenericType<>() { };
        Map<String, Integer> values = bindingMethod.deserialize(jsonBinding,
                                                                "{\"a\\u005Cb\":1,\"\\uD83D\\uDE00\":2}",
                                                                type);

        assertAll(
                () -> assertThat(values.containsKey("a\\b"), is(true)),
                () -> assertThat(values.get("a\\b"), is(1)),
                () -> assertThat(values.containsKey("😀"), is(true)),
                () -> assertThat(values.get("😀"), is(2))
        );
    }

    /**
     * RFC 8259 §8.3
     * Quote: "Implementations that transform the textual representation into sequences of Unicode code units and then perform the comparison numerically, code unit by code unit, are interoperable"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.3
     */
    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testStringPropertiesMaterializeAsDecodedUnicodeCodeUnits(BindingMethod bindingMethod) {
        String expectedEscapedValue = "\"\\\n\t\r" + '\u0001';
        StringValueBean bean = bindingMethod.deserialize(
                jsonBinding,
                "{\"value\":\"\\\"\\\\\\n\\t\\r\\u0001\",\"reverseSolidus\":\"a\\u005Cb\",\"emoji\":\"\\uD83D\\uDE00\"}",
                StringValueBean.class);

        assertAll(
                () -> assertThat(bean.value(), is(expectedEscapedValue)),
                () -> assertThat(bean.reverseSolidus(), is("a\\b")),
                () -> assertThat(bean.emoji(), is("😀"))
        );
    }

    @Json.Entity
    record PropertyNameBean(String value, String name) {

    }

    @Json.Entity
    record StringValueBean(String value, String reverseSolidus, String emoji) {

    }
}
