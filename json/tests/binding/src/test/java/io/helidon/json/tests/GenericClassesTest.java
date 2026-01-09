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

import java.util.List;

import io.helidon.common.GenericType;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testing.Test
public class GenericClassesTest {

    private final JsonBinding jsonBinding;

    GenericClassesTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testGenericContainerClass() {
        Container<String> container = new Container<>();
        container.setValue("hello");

        String expected = "{\"value\":\"hello\"}";
        String json = jsonBinding.serialize(container);
        assertThat(json, is(expected));

        GenericType<Container<String>> type = new GenericType<>() { };
        Container<String> deserialized = jsonBinding.deserialize(json, type);
        assertThat(deserialized.getValue(), is("hello"));
    }

    @Test
    public void testGenericPairClass() {
        Pair<String, Integer> pair = new Pair<>();
        pair.setFirst("key");
        pair.setSecond(42);

        String expected = "{\"first\":\"key\",\"second\":42}";
        String json = jsonBinding.serialize(pair);
        assertThat(json, is(expected));

        GenericType<Pair<String, Integer>> type = new GenericType<>() { };
        Pair<String, Integer> deserialized = jsonBinding.deserialize(json, type);
        assertThat(deserialized.getFirst(), is("key"));
        assertThat(deserialized.getSecond(), is(42));
    }

    @Test
    public void testNestedGenericClasses() {
        Container<Pair<String, Integer>> nested = new Container<>();
        Pair<String, Integer> innerPair = new Pair<>();
        innerPair.setFirst("nested");
        innerPair.setSecond(123);
        nested.setValue(innerPair);

        String expected = "{\"value\":{\"first\":\"nested\",\"second\":123}}";
        String json = jsonBinding.serialize(nested);
        assertThat(json, is(expected));

        GenericType<Container<Pair<String, Integer>>> type = new GenericType<>() { };
        Container<Pair<String, Integer>> deserialized = jsonBinding.deserialize(json, type);
        assertThat(deserialized.getValue().getFirst(), is("nested"));
        assertThat(deserialized.getValue().getSecond(), is(123));
    }

    @Test
    public void testGenericTripleClass() {
        Triple<String, Integer, Boolean> triple = new Triple<>();
        triple.setFirst("test");
        triple.setSecond(100);
        triple.setThird(true);

        String expected = "{\"first\":\"test\",\"second\":100,\"third\":true}";
        String json = jsonBinding.serialize(triple);
        assertThat(json, is(expected));

        GenericType<Triple<String, Integer, Boolean>> type = new GenericType<>() { };
        Triple<String, Integer, Boolean> deserialized = jsonBinding.deserialize(json, type);
        assertThat(deserialized.getFirst(), is("test"));
        assertThat(deserialized.getSecond(), is(100));
        assertThat(deserialized.getThird(), is(true));
    }

    @Test
    public void testGenericClassWithMixedFields() {
        GenericClassWithMixedFields<String> obj = new GenericClassWithMixedFields<>();
        obj.setId("item123");
        obj.setName("Test Item");
        obj.setValue("generic value");
        obj.setTags(List.of("important", "featured"));

        String expected = "{\"id\":\"item123\","
                + "\"name\":\"Test Item\","
                + "\"value\":\"generic value\","
                + "\"tags\":[\"important\",\"featured\"]}";
        String json = jsonBinding.serialize(obj);
        assertThat(json, is(expected));

        GenericType<GenericClassWithMixedFields<String>> type = new GenericType<>() { };
        GenericClassWithMixedFields<String> deserialized = jsonBinding.deserialize(json, type);
        assertThat(deserialized.getId(), is("item123"));
        assertThat(deserialized.getName(), is("Test Item"));
        assertThat(deserialized.getValue(), is("generic value"));
        assertThat(deserialized.getTags(), is(List.of("important", "featured")));
    }

    @Test
    public void testChildClassWithGenericInheritance() {
        ChildClass child = new ChildClass();
        child.superParentField(42);
        child.secondSuperParentField("inherited value");
        child.superParentList(List.of("item1", "item2"));
        child.parentField("parent value");
        child.childField(100);

        String expected = "{\"superParentField\":42,"
                + "\"secondSuperParentField\":\"inherited value\","
                + "\"superParentList\":[\"item1\",\"item2\"],"
                + "\"parentField\":\"parent value\","
                + "\"childField\":100}";
        String json = jsonBinding.serialize(child);
        assertThat(json, is(expected));

        ChildClass deserialized = jsonBinding.deserialize(json, ChildClass.class);
        assertThat(deserialized.superParentField(), is(42));
        assertThat(deserialized.secondSuperParentField(), is("inherited value"));
        assertThat(deserialized.superParentList(), is(List.of("item1", "item2")));
        assertThat(deserialized.parentField(), is("parent value"));
        assertThat(deserialized.childField(), is(100));
    }

    static class SuperParentClass<T, U> {

        private T superParentField;
        private U secondSuperParentField;
        private List<U> superParentList;

        T superParentField() {
            return superParentField;
        }

        void superParentField(T superParentField) {
            this.superParentField = superParentField;
        }

        U secondSuperParentField() {
            return secondSuperParentField;
        }

        void secondSuperParentField(U secondSuperParentField) {
            this.secondSuperParentField = secondSuperParentField;
        }

        List<U> superParentList() {
            return superParentList;
        }

        void superParentList(List<U> superParentList) {
            this.superParentList = superParentList;
        }
    }

    static class ParentClass<T> extends SuperParentClass<Integer, T> {

        private T parentField;

        T parentField() {
            return parentField;
        }

        void parentField(T parentField) {
            this.parentField = parentField;
        }

    }

    @Json.Entity
    static class ChildClass extends ParentClass<String> {

        private int childField;

        int childField() {
            return childField;
        }

        void childField(int childField) {
            this.childField = childField;
        }
    }

    @Json.Entity
    static class Container<T> {
        private T value;

        T getValue() {
            return value;
        }

        void setValue(T value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class Pair<A, B> {
        private A first;
        private B second;

        A getFirst() {
            return first;
        }

        void setFirst(A first) {
            this.first = first;
        }

        B getSecond() {
            return second;
        }

        void setSecond(B second) {
            this.second = second;
        }
    }

    @Json.Entity
    static class Triple<A, B, C> {
        private A first;
        private B second;
        private C third;

        A getFirst() {
            return first;
        }

        void setFirst(A first) {
            this.first = first;
        }

        B getSecond() {
            return second;
        }

        void setSecond(B second) {
            this.second = second;
        }

        C getThird() {
            return third;
        }

        void setThird(C third) {
            this.third = third;
        }
    }

    @Json.Entity
    static class GenericClassWithMixedFields<T> {
        private String id;
        private String name;
        private T value;
        private List<String> tags;

        String getId() {
            return id;
        }

        void setId(String id) {
            this.id = id;
        }

        String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }

        T getValue() {
            return value;
        }

        void setValue(T value) {
            this.value = value;
        }

        List<String> getTags() {
            return tags;
        }

        void setTags(List<String> tags) {
            this.tags = tags;
        }
    }
}
