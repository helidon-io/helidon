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
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;

@Testing.Test
public class GenericFieldsTest {

    private final JsonBinding jsonBinding;

    GenericFieldsTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testClassWithGenericListField() {
        ClassWithListField obj = new ClassWithListField();
        obj.setName("test");
        obj.setValues(List.of("a", "b", "c"));

        String expected = "{\"name\":\"test\",\"values\":[\"a\",\"b\",\"c\"]}";
        String json = jsonBinding.serialize(obj);
        assertThat(json, is(expected));

        ClassWithListField deserialized = jsonBinding.deserialize(json, ClassWithListField.class);
        assertThat(deserialized.getName(), is("test"));
        assertThat(deserialized.getValues(), is(List.of("a", "b", "c")));
    }

    @Test
    public void testClassWithGenericMapField() {
        ClassWithMapField obj = new ClassWithMapField();
        obj.setId("123");
        obj.setProperties(Map.of("key1", "value1", "key2", "value2"));

        String expected = "{\"id\":\"123\",\"properties\":{\"key1\":\"value1\",\"key2\":\"value2\"}}";
        String expected2 = "{\"id\":\"123\",\"properties\":{\"key2\":\"value2\",\"key1\":\"value1\"}}";
        String json = jsonBinding.serialize(obj);
        assertThat(json, isOneOf(expected, expected2));

        ClassWithMapField deserialized = jsonBinding.deserialize(json, ClassWithMapField.class);
        assertThat(deserialized.getId(), is("123"));
        assertThat(deserialized.getProperties().get("key1"), is("value1"));
        assertThat(deserialized.getProperties().get("key2"), is("value2"));
    }

    @Test
    public void testClassWithGenericSetField() {
        ClassWithSetField obj = new ClassWithSetField();
        obj.setTitle("example");
        obj.setTags(Set.of("tag1", "tag2", "tag3"));

        String json = jsonBinding.serialize(obj);

        ClassWithSetField deserialized = jsonBinding.deserialize(json, ClassWithSetField.class);
        assertThat(deserialized.getTitle(), is("example"));
        assertThat(deserialized.getTags(), is(Set.of("tag1", "tag2", "tag3")));
    }

    @Test
    public void testClassWithNestedGenericFields() {
        String expected = "{\"data\":{\"list\":[\"x\"],\"set\":[1]}}";
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("list", List.of("x"));
        map.put("set", Set.of(1));
        ClassWithNestedGenerics obj = new ClassWithNestedGenerics();
        obj.setData(map);

        String json = jsonBinding.serialize(obj);

        assertThat(json, is(expected));
    }

    @Test
    public void testClassWithMixedFieldTypes() {
        ClassWithMixedFields obj = new ClassWithMixedFields();
        obj.setId("user123");
        obj.setName("John Doe");
        obj.setAge(30);
        obj.setTags(List.of("admin", "premium"));
        LinkedHashMap<String, String> settings = new LinkedHashMap<>();
        settings.put("theme", "dark");
        settings.put("notifications", "enabled");
        obj.setSettings(settings);

        String expected = "{\"id\":\"user123\",\"name\":\"John Doe\",\"age\":30,\"tags\":[\"admin\",\"premium\"],"
                + "\"settings\":{\"theme\":\"dark\",\"notifications\":\"enabled\"}}";
        String json = jsonBinding.serialize(obj);
        assertThat(json, is(expected));

        ClassWithMixedFields deserialized = jsonBinding.deserialize(json, ClassWithMixedFields.class);
        assertThat(deserialized.getId(), is("user123"));
        assertThat(deserialized.getName(), is("John Doe"));
        assertThat(deserialized.getAge(), is(30));
        assertThat(deserialized.getTags(), is(List.of("admin", "premium")));
        assertThat(deserialized.getSettings().get("theme"), is("dark"));
        assertThat(deserialized.getSettings().get("notifications"), is("enabled"));
    }

    @Json.Entity
    static class ClassWithListField {
        private String name;
        private List<String> values;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getValues() {
            return values;
        }

        public void setValues(List<String> values) {
            this.values = values;
        }
    }

    @Json.Entity
    static class ClassWithMapField {
        private String id;
        private Map<String, String> properties;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }
    }

    @Json.Entity
    static class ClassWithSetField {
        private String title;
        private Set<String> tags;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Set<String> getTags() {
            return tags;
        }

        public void setTags(Set<String> tags) {
            this.tags = tags;
        }
    }

    @Json.Entity
    static class ClassWithNestedGenerics {
        private Map<String, Object> data;

        public Map<String, Object> getData() {
            return data;
        }

        public void setData(Map<String, Object> data) {
            this.data = data;
        }
    }

    @Json.Entity
    static class ClassWithMixedFields {
        private String id;
        private String name;
        private int age;
        private List<String> tags;
        private Map<String, String> settings;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public Map<String, String> getSettings() {
            return settings;
        }

        public void setSettings(Map<String, String> settings) {
            this.settings = settings;
        }
    }
}
