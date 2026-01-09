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
import java.util.Map;

import io.helidon.common.GenericType;
import io.helidon.json.binding.JsonBinding;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class MapTest {

    private static JsonBinding jsonBinding;

    @BeforeAll
    public static void init() {
        jsonBinding = Services.get(JsonBinding.class);
    }

    @Test
    public void testMapSerialization() {
        Map<String, String> map = new HashMap<>();
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
        Map<String, String> map = new HashMap<>();
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

}
