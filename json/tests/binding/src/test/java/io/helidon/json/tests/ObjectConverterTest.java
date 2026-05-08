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

import io.helidon.json.JsonObject;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@Testing.Test
public class ObjectConverterTest {

    private final JsonBinding jsonBinding;

    ObjectConverterTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeStringToObject(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding, "\"hello world\"", Object.class);
        assertThat(result, instanceOf(String.class));
        assertThat(result, is("hello world"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeObjectToObject(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding, "{\"key\":\"value\"}", Object.class);
        assertThat(result, instanceOf(JsonObject.class));
        JsonObject jsonObject = (JsonObject) result;
        assertThat(jsonObject.stringValue("key").isPresent(), is(true));
        assertThat(jsonObject.stringValue("key").orElseThrow(), is("value"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeArrayToObject(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding, "[\"item1\", \"item2\"]", Object.class);
        assertThat(result, instanceOf(Object[].class));
        Object[] array = (Object[]) result;
        assertThat(array.length, is(2));
        assertThat(array[0], is("item1"));
        assertThat(array[1], is("item2"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeBooleanTrueToObject(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding, "true", Object.class);
        assertThat(result, instanceOf(Boolean.class));
        assertThat(result, is(true));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeBooleanFalseToObject(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding, "false", Object.class);
        assertThat(result, instanceOf(Boolean.class));
        assertThat(result, is(false));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeIntegerNumberToObject(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding, "42", Object.class);
        assertThat(result, instanceOf(Double.class));
        assertThat(result, is(42.0));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeFloatNumberToObject(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding, "3.14", Object.class);
        assertThat(result, instanceOf(Double.class));
        assertThat(result, is(3.14));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeNegativeNumberToObject(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding, "-123", Object.class);
        assertThat(result, instanceOf(Double.class));
        assertThat(result, is(-123.0));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeScientificNotationToObject(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding, "1.23e4", Object.class);
        assertThat(result, instanceOf(Double.class));
        assertThat(result, is(12300.0));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeExactIntegerAtInteroperabilityBoundaryToObject(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding, "9007199254740991", Object.class);
        assertThat(result, instanceOf(Double.class));
        assertThat(result, is(9_007_199_254_740_991d));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeIntegerBeyondInteroperabilityBoundaryUsesDoubleSemantics(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding, "9007199254740993", Object.class);
        assertThat(result, instanceOf(Double.class));
        assertThat(result, is(9_007_199_254_740_992d));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeNull(BindingMethod bindingMethod) {
        Object value = bindingMethod.deserialize(jsonBinding, "null", Object.class);
        assertThat(value, nullValue());
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testSerializeString(BindingMethod bindingMethod) {
        String original = "test string";
        String json = bindingMethod.serialize(jsonBinding, original, Object.class);
        assertThat(json, is("\"test string\""));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testSerializeInteger(BindingMethod bindingMethod) {
        Integer original = 123;
        String json = bindingMethod.serialize(jsonBinding, original, Object.class);
        assertThat(json, is("123"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testSerializeBoolean(BindingMethod bindingMethod) {
        Boolean original = true;
        String json = bindingMethod.serialize(jsonBinding, original, Object.class);
        assertThat(json, is("true"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testSerializeArray(BindingMethod bindingMethod) {
        String[] original = {"a", "b", "c"};
        String json = bindingMethod.serialize(jsonBinding, original, Object.class);
        assertThat(json, is("[\"a\",\"b\",\"c\"]"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testSerializeJsonObject(BindingMethod bindingMethod) {
        JsonObject original = JsonObject.builder().set("key", "value").build();
        String json = bindingMethod.serialize(jsonBinding, original, Object.class);
        assertThat(json, is("{\"key\":\"value\"}"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeEmptyObject(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding, "{}", Object.class);
        assertThat(result, instanceOf(JsonObject.class));
        JsonObject jsonObject = (JsonObject) result;
        assertThat(jsonObject.size(), is(0));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeEmptyArray(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding, "[]", Object.class);
        assertThat(result, instanceOf(Object[].class));
        Object[] array = (Object[]) result;
        assertThat(array.length, is(0));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDeserializeNestedStructures(BindingMethod bindingMethod) {
        Object result = bindingMethod.deserialize(jsonBinding,
                                                  "{\"array\": [1, 2, 3], \"object\": {\"nested\": true}}",
                                                  Object.class);
        assertThat(result, instanceOf(JsonObject.class));
        JsonObject jsonObject = (JsonObject) result;
        assertThat(jsonObject.containsKey("array"), is(true));
        assertThat(jsonObject.containsKey("object"), is(true));
    }

}
