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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class ObjectConverterTest {

    private final JsonBinding jsonBinding;

    ObjectConverterTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testDeserializeStringToObject() {
        Object result = jsonBinding.deserialize("\"hello world\"", Object.class);
        assertThat(result, instanceOf(String.class));
        assertThat(result, is("hello world"));
    }

    @Test
    public void testDeserializeObjectToObject() {
        Object result = jsonBinding.deserialize("{\"key\":\"value\"}", Object.class);
        assertThat(result, instanceOf(JsonObject.class));
        JsonObject jsonObject = (JsonObject) result;
        assertThat(jsonObject.stringValue("key").isPresent(), is(true));
        assertThat(jsonObject.stringValue("key").orElseThrow(), is("value"));
    }

    @Test
    public void testDeserializeArrayToObject() {
        Object result = jsonBinding.deserialize("[\"item1\", \"item2\"]", Object.class);
        assertThat(result, instanceOf(Object[].class));
        Object[] array = (Object[]) result;
        assertThat(array.length, is(2));
        assertThat(array[0], is("item1"));
        assertThat(array[1], is("item2"));
    }

    @Test
    public void testDeserializeBooleanTrueToObject() {
        Object result = jsonBinding.deserialize("true", Object.class);
        assertThat(result, instanceOf(Boolean.class));
        assertThat(result, is(true));
    }

    @Test
    public void testDeserializeBooleanFalseToObject() {
        Object result = jsonBinding.deserialize("false", Object.class);
        assertThat(result, instanceOf(Boolean.class));
        assertThat(result, is(false));
    }

    @Test
    public void testDeserializeIntegerNumberToObject() {
        Object result = jsonBinding.deserialize("42", Object.class);
        assertThat(result, instanceOf(Double.class));
        assertThat(result, is(42.0));
    }

    @Test
    public void testDeserializeFloatNumberToObject() {
        Object result = jsonBinding.deserialize("3.14", Object.class);
        assertThat(result, instanceOf(Double.class));
        assertThat(result, is(3.14));
    }

    @Test
    public void testDeserializeNegativeNumberToObject() {
        Object result = jsonBinding.deserialize("-123", Object.class);
        assertThat(result, instanceOf(Double.class));
        assertThat(result, is(-123.0));
    }

    @Test
    public void testDeserializeScientificNotationToObject() {
        Object result = jsonBinding.deserialize("1.23e4", Object.class);
        assertThat(result, instanceOf(Double.class));
        assertThat(result, is(12300.0));
    }

    @Test
    public void testDeserializeNull() {
        Object value = jsonBinding.deserialize("null", Object.class);
        assertThat(value, nullValue());
    }

    @Test
    public void testSerializeString() {
        String original = "test string";
        String json = jsonBinding.serialize(original, Object.class);
        assertThat(json, is("\"test string\""));
    }

    @Test
    public void testSerializeInteger() {
        Integer original = 123;
        String json = jsonBinding.serialize(original, Object.class);
        assertThat(json, is("123"));
    }

    @Test
    public void testSerializeBoolean() {
        Boolean original = true;
        String json = jsonBinding.serialize(original, Object.class);
        assertThat(json, is("true"));
    }

    @Test
    public void testSerializeArray() {
        String[] original = {"a", "b", "c"};
        String json = jsonBinding.serialize(original, Object.class);
        assertThat(json, is("[\"a\",\"b\",\"c\"]"));
    }

    @Test
    public void testSerializeJsonObject() {
        JsonObject original = JsonObject.builder().set("key", "value").build();
        String json = jsonBinding.serialize(original, Object.class);
        assertThat(json, is("{\"key\":\"value\"}"));
    }

    @Test
    public void testDeserializeEmptyObject() {
        Object result = jsonBinding.deserialize("{}", Object.class);
        assertThat(result, instanceOf(JsonObject.class));
        JsonObject jsonObject = (JsonObject) result;
        assertThat(jsonObject.size(), is(0));
    }

    @Test
    public void testDeserializeEmptyArray() {
        Object result = jsonBinding.deserialize("[]", Object.class);
        assertThat(result, instanceOf(Object[].class));
        Object[] array = (Object[]) result;
        assertThat(array.length, is(0));
    }

    @Test
    public void testDeserializeNestedStructures() {
        Object result = jsonBinding.deserialize("{\"array\": [1, 2, 3], \"object\": {\"nested\": true}}", Object.class);
        assertThat(result, instanceOf(JsonObject.class));
        JsonObject jsonObject = (JsonObject) result;
        assertThat(jsonObject.containsKey("array"), is(true));
        assertThat(jsonObject.containsKey("object"), is(true));
    }
}