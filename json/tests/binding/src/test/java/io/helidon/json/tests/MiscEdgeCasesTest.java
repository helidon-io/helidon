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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.helidon.common.GenericType;
import io.helidon.json.JsonException;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class MiscEdgeCasesTest {

    private final JsonBinding jsonBinding;

    MiscEdgeCasesTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testNullObjectSerialization() {
        String json = jsonBinding.serialize(null);
        assertThat(json, is("null"));
    }

    @Test
    public void testNullDeserialization() {
        TestBean deserialized = jsonBinding.deserialize("null", TestBean.class);
        assertThat(deserialized, is(nullValue()));
    }

    @Test
    public void testEmptyJsonObject() {
        String json = "{}";
        EmptyBean bean = jsonBinding.deserialize(json, EmptyBean.class);
        assertThat(bean, is(instanceOf(EmptyBean.class)));
    }

    @Test
    public void testEmptyArrayDeserialization() {
        GenericType<List<String>> listType = new GenericType<>() { };
        List<String> list = jsonBinding.deserialize("[]", listType);
        assertThat(list.size(), is(0));
    }

    @Test
    public void testEmptyMapDeserialization() {
        GenericType<Map<String, String>> mapType = new GenericType<>() { };
        Map<String, String> map = jsonBinding.deserialize("{}", mapType);
        assertThat(map.size(), is(0));
    }

    @Test
    public void testInvalidJsonThrowsException() {
        assertThrows(JsonException.class, () -> jsonBinding.deserialize("{invalid", TestBean.class));
        assertThrows(JsonException.class, () -> jsonBinding.deserialize("[invalid", List.class));
    }

    @Test
    public void testArraysWithNulls() {
        String[] arrayWithNulls = {"first", null, "third"};
        String json = jsonBinding.serialize(arrayWithNulls);
        assertThat(json, is("[\"first\",null,\"third\"]"));

        String[] deserialized = jsonBinding.deserialize(json, String[].class);
        assertThat(deserialized[0], is("first"));
        assertThat(deserialized[1], is(nullValue()));
        assertThat(deserialized[2], is("third"));
    }

    @Test
    public void testNestedNullsInObjects() {
        NestedBean bean = new NestedBean();
        bean.setNested(null);
        bean.setValue("test");

        String json = jsonBinding.serialize(bean);
        assertThat(json, is("{\"value\":\"test\"}"));

        NestedBean deserialized = jsonBinding.deserialize("{\"nested\":null,\"value\":\"test\"}", NestedBean.class);
        assertThat(deserialized.getNested(), is(nullValue()));
        assertThat(deserialized.getValue(), is("test"));
    }

    @Test
    public void testEmptyCollections() {
        CollectionBean bean = new CollectionBean();
        bean.setEmptyList(Collections.emptyList());
        bean.setEmptyMap(Collections.emptyMap());

        String json = jsonBinding.serialize(bean);
        assertThat(json, is("{\"emptyList\":[],\"emptyMap\":{}}"));

        CollectionBean deserialized = jsonBinding.deserialize(json, CollectionBean.class);
        assertThat(deserialized.getEmptyList().size(), is(0));
        assertThat(deserialized.getEmptyMap().size(), is(0));
    }

    @Test
    public void testDeserializeFromInputStream() throws IOException {
        String jsonData = "{\"value\":\"stream test\"}";
        try (InputStream inputStream = new ByteArrayInputStream(jsonData.getBytes(StandardCharsets.UTF_8))) {
            TestBean bean = jsonBinding.deserialize(inputStream, TestBean.class);
            assertThat(bean.getValue(), is("stream test"));
        }
    }

    @Test
    public void testDeserializeFromInputStreamWithBufferSize() throws IOException {
        String jsonData = "{\"value\":\"buffered stream test\"}";
        try (InputStream inputStream = new ByteArrayInputStream(jsonData.getBytes(StandardCharsets.UTF_8))) {
            TestBean bean = jsonBinding.deserialize(inputStream, 1024, TestBean.class);
            assertThat(bean.getValue(), is("buffered stream test"));
        }
    }

    @Test
    public void testBooleanPrimitives() {
        boolean[] booleans = {true, false, true};
        String json = jsonBinding.serialize(booleans);
        assertThat(json, is("[true,false,true]"));

        boolean[] deserialized = jsonBinding.deserialize(json, boolean[].class);
        assertThat(deserialized[0], is(true));
        assertThat(deserialized[1], is(false));
        assertThat(deserialized[2], is(true));
    }

    @Test
    public void testCharPrimitives() {
        char[] chars = {'a', 'b', 'c'};
        String json = jsonBinding.serialize(chars);
        assertThat(json, is("[\"a\",\"b\",\"c\"]"));

        char[] deserialized = jsonBinding.deserialize(json, char[].class);
        assertThat(deserialized[0], is('a'));
        assertThat(deserialized[1], is('b'));
        assertThat(deserialized[2], is('c'));
    }

    @Json.Entity
    static class TestBean {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class EmptyBean {
        // Empty bean with no fields
    }

    @Json.Entity
    static class NestedBean {
        private TestBean nested;
        private String value;

        public TestBean getNested() {
            return nested;
        }

        public void setNested(TestBean nested) {
            this.nested = nested;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class CollectionBean {
        private List<String> emptyList;
        private Map<String, String> emptyMap;

        public List<String> getEmptyList() {
            return emptyList;
        }

        public void setEmptyList(List<String> emptyList) {
            this.emptyList = emptyList;
        }

        public Map<String, String> getEmptyMap() {
            return emptyMap;
        }

        public void setEmptyMap(Map<String, String> emptyMap) {
            this.emptyMap = emptyMap;
        }
    }
}
