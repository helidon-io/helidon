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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import io.helidon.common.GenericType;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Testing.Test
public class MapTest {

    private final JsonBinding jsonBinding;

    MapTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testMapSerialization() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");

        String expected = "{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}";
        assertThat(jsonBinding.serialize(map), is(expected));
    }

    @Test
    public void testMapDeserialization() {
        String json = "{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}";

        GenericType<Map<String, String>> type = new GenericType<>() { };
        Map<String, String> map = jsonBinding.deserialize(json, type);

        assertThat(map, notNullValue());
        assertThat(map, instanceOf(HashMap.class));
        assertThat(map.size(), is(3));
        assertThat(map, hasEntry("key1", "value1"));
        assertThat(map, hasEntry("key2", "value2"));
        assertThat(map, hasEntry("key3", "value3"));
    }

    @Test
    public void testMapSerializationWithNulls() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key1", null);
        map.put("key2", null);
        map.put("key3", null);

        String expected = "{\"key1\":null,\"key2\":null,\"key3\":null}";
        assertThat(jsonBinding.serialize(map), is(expected));
    }

    @Test
    public void testMapDeserializationWithNulls() {
        String json = "{\"key1\":null,\"key2\":null,\"key3\":null}";

        GenericType<Map<String, String>> type = new GenericType<>() { };
        Map<String, String> map = jsonBinding.deserialize(json, type);

        assertThat(map, notNullValue());
        assertThat(map, instanceOf(HashMap.class));
        assertThat(map.size(), is(3));
        assertThat(map, hasEntry("key1", null));
        assertThat(map, hasEntry("key2", null));
        assertThat(map, hasEntry("key3", null));
    }

    @Test
    public void testMapWithIntegerKeys() {
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(1, "one");
        map.put(2, "two");
        map.put(42, "forty-two");

        String expected = "{\"1\":\"one\",\"2\":\"two\",\"42\":\"forty-two\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Integer, String>> type = new GenericType<>() { };
        Map<Integer, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(3));
        assertThat(deserialized, hasEntry(1, "one"));
        assertThat(deserialized, hasEntry(2, "two"));
        assertThat(deserialized, hasEntry(42, "forty-two"));
    }

    @Test
    public void testMapWithLongKeys() {
        Map<Long, String> map = new LinkedHashMap<>();
        map.put(123L, "small");
        map.put(9999999999L, "large");

        String expected = "{\"123\":\"small\",\"9999999999\":\"large\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Long, String>> type = new GenericType<>() { };
        Map<Long, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(123L, "small"));
        assertThat(deserialized, hasEntry(9999999999L, "large"));
    }

    @Test
    public void testMapWithDoubleKeys() {
        Map<Double, String> map = new LinkedHashMap<>();
        map.put(1.5, "one-point-five");
        map.put(3.14159, "pi");

        String expected = "{\"1.5\":\"one-point-five\",\"3.14159\":\"pi\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Double, String>> type = new GenericType<>() { };
        Map<Double, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(1.5, "one-point-five"));
        assertThat(deserialized, hasEntry(3.14159, "pi"));
    }

    @Test
    public void testMapWithBooleanKeys() {
        Map<Boolean, String> map = new LinkedHashMap<>();
        map.put(true, "yes");
        map.put(false, "no");

        String expected = "{\"true\":\"yes\",\"false\":\"no\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Boolean, String>> type = new GenericType<>() { };
        Map<Boolean, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(true, "yes"));
        assertThat(deserialized, hasEntry(false, "no"));
    }

    @Test
    public void testMapWithShortKeys() {
        Map<Short, String> map = new LinkedHashMap<>();
        map.put((short) 100, "hundred");
        map.put((short) 200, "two-hundred");

        String expected = "{\"100\":\"hundred\",\"200\":\"two-hundred\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Short, String>> type = new GenericType<>() { };
        Map<Short, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry((short) 100, "hundred"));
        assertThat(deserialized, hasEntry((short) 200, "two-hundred"));
    }

    @Test
    public void testMapWithByteKeys() {
        Map<Byte, String> map = new LinkedHashMap<>();
        map.put((byte) 10, "ten");
        map.put((byte) 20, "twenty");

        String expected = "{\"10\":\"ten\",\"20\":\"twenty\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Byte, String>> type = new GenericType<>() { };
        Map<Byte, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry((byte) 10, "ten"));
        assertThat(deserialized, hasEntry((byte) 20, "twenty"));
    }

    @Test
    public void testMapWithFloatKeys() {
        Map<Float, String> map = new LinkedHashMap<>();
        map.put(2.5f, "two-point-five");
        map.put(7.77f, "seven-point-seven-seven");

        String expected = "{\"2.5\":\"two-point-five\",\"7.77\":\"seven-point-seven-seven\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Float, String>> type = new GenericType<>() { };
        Map<Float, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(2.5f, "two-point-five"));
        assertThat(deserialized, hasEntry(7.77f, "seven-point-seven-seven"));
    }

    @Test
    public void testMapWithCharacterKeys() {
        Map<Character, String> map = new LinkedHashMap<>();
        map.put('A', "alpha");
        map.put('B', "beta");
        map.put('1', "one");

        String expected = "{\"A\":\"alpha\",\"B\":\"beta\",\"1\":\"one\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Character, String>> type = new GenericType<>() { };
        Map<Character, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(3));
        assertThat(deserialized, hasEntry('A', "alpha"));
        assertThat(deserialized, hasEntry('B', "beta"));
        assertThat(deserialized, hasEntry('1', "one"));
    }

}
