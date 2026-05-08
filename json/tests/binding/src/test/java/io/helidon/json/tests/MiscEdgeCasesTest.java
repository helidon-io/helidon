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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testNullObjectSerializationParameterized(BindingMethod bindingMethod) {
        String json = bindingMethod.serialize(jsonBinding, null);
        assertThat(json, is("null"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testNullDeserializationParameterized(BindingMethod bindingMethod) {
        TestBean deserialized = bindingMethod.deserialize(jsonBinding, "null", TestBean.class);
        assertThat(deserialized, is(nullValue()));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testEmptyJsonObjectParameterized(BindingMethod bindingMethod) {
        String json = "{}";
        EmptyBean bean = bindingMethod.deserialize(jsonBinding, json, EmptyBean.class);
        assertThat(bean, is(instanceOf(EmptyBean.class)));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testEmptyArrayDeserializationParameterized(BindingMethod bindingMethod) {
        GenericType<List<String>> listType = new GenericType<>() { };
        List<String> list = bindingMethod.deserialize(jsonBinding, "[]", listType);
        assertThat(list.size(), is(0));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testEmptyMapDeserializationParameterized(BindingMethod bindingMethod) {
        GenericType<Map<String, String>> mapType = new GenericType<>() { };
        Map<String, String> map = bindingMethod.deserialize(jsonBinding, "{}", mapType);
        assertThat(map.size(), is(0));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testInvalidJsonThrowsExceptionParameterized(BindingMethod bindingMethod) {
        assertThrows(JsonException.class, () -> bindingMethod.deserialize(jsonBinding, "{invalid", TestBean.class));
        assertThrows(JsonException.class, () -> bindingMethod.deserialize(jsonBinding, "[invalid", List.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testArraysWithNullsParameterized(BindingMethod bindingMethod) {
        String[] arrayWithNulls = {"first", null, "third"};
        String json = bindingMethod.serialize(jsonBinding, arrayWithNulls);
        assertThat(json, is("[\"first\",null,\"third\"]"));

        String[] deserialized = bindingMethod.deserialize(jsonBinding, json, String[].class);
        assertThat(deserialized[0], is("first"));
        assertThat(deserialized[1], is(nullValue()));
        assertThat(deserialized[2], is("third"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testNestedNullsInObjectsParameterized(BindingMethod bindingMethod) {
        NestedBean bean = new NestedBean();
        bean.setNested(null);
        bean.setValue("test");

        String json = bindingMethod.serialize(jsonBinding, bean);
        assertThat(json, is("{\"value\":\"test\"}"));

        NestedBean deserialized =
                bindingMethod.deserialize(jsonBinding, "{\"nested\":null,\"value\":\"test\"}", NestedBean.class);
        assertThat(deserialized.getNested(), is(nullValue()));
        assertThat(deserialized.getValue(), is("test"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testEmptyCollectionsParameterized(BindingMethod bindingMethod) {
        CollectionBean bean = new CollectionBean();
        bean.setEmptyList(Collections.emptyList());
        bean.setEmptyMap(Collections.emptyMap());

        String json = bindingMethod.serialize(jsonBinding, bean);
        assertThat(json, is("{\"emptyList\":[],\"emptyMap\":{}}"));

        CollectionBean deserialized = bindingMethod.deserialize(jsonBinding, json, CollectionBean.class);
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

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testBooleanPrimitivesParameterized(BindingMethod bindingMethod) {
        boolean[] booleans = {true, false, true};
        String json = bindingMethod.serialize(jsonBinding, booleans);
        assertThat(json, is("[true,false,true]"));

        boolean[] deserialized = bindingMethod.deserialize(jsonBinding, json, boolean[].class);
        assertThat(deserialized[0], is(true));
        assertThat(deserialized[1], is(false));
        assertThat(deserialized[2], is(true));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testCharPrimitivesParameterized(BindingMethod bindingMethod) {
        char[] chars = {'a', 'b', 'c'};
        String json = bindingMethod.serialize(jsonBinding, chars);
        assertThat(json, is("[\"a\",\"b\",\"c\"]"));

        char[] deserialized = bindingMethod.deserialize(jsonBinding, json, char[].class);
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
