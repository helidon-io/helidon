/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.service.registry.Services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class GenericClassesTest {

    private static final JsonBinding HELIDON = Services.get(JsonBinding.class);

//    @Test
//    public void testGenericContainerClass() {
//        Container<String> container = new Container<>();
//        container.setValue("hello");
//
//        String expected = "{\"value\":\"hello\"}";
//        String json = HELIDON.serialize(container);
//        assertThat(json, is(expected));
//
//        GenericType<Container<String>> type = new GenericType<>() { };
//        Container<String> deserialized = HELIDON.deserialize(json, type);
//        assertThat(deserialized.getValue(), is("hello"));
//    }
//
//    @Test
//    public void testGenericPairClass() {
//        Pair<String, Integer> pair = new Pair<>();
//        pair.setFirst("key");
//        pair.setSecond(42);
//
//        String expected = "{\"first\":\"key\",\"second\":42}";
//        String json = HELIDON.serialize(pair);
//        assertThat(json, is(expected));
//
//        GenericType<Pair<String, Integer>> type = new GenericType<Pair<String, Integer>>() { };
//        Pair<String, Integer> deserialized = HELIDON.deserialize(json, type);
//        assertThat(deserialized.getFirst(), is("key"));
//        assertThat(deserialized.getSecond(), is(42));
//    }
//
//    @Test
//    public void testNestedGenericClasses() {
//        Container<Pair<String, Integer>> nested = new Container<>();
//        Pair<String, Integer> innerPair = new Pair<>();
//        innerPair.setFirst("nested");
//        innerPair.setSecond(123);
//        nested.setValue(innerPair);
//
//        String expected = "{\"value\":{\"first\":\"nested\",\"second\":123}}";
//        String json = HELIDON.serialize(nested);
//        assertThat(json, is(expected));
//
//        GenericType<Container<Pair<String, Integer>>> type = new GenericType<>() { };
//        Container<Pair<String, Integer>> deserialized = HELIDON.deserialize(json, type);
//        assertThat(deserialized.getValue().getFirst(), is("nested"));
//        assertThat(deserialized.getValue().getSecond(), is(123));
//    }
//
//    @Test
//    public void testGenericTripleClass() {
//        Triple<String, Integer, Boolean> triple = new Triple<>();
//        triple.setFirst("test");
//        triple.setSecond(100);
//        triple.setThird(true);
//
//        String expected = "{\"first\":\"test\",\"second\":100,\"third\":true}";
//        String json = HELIDON.serialize(triple);
//        assertThat(json, is(expected));
//
//        GenericType<Triple<String, Integer, Boolean>> type = new GenericType<>() { };
//        Triple<String, Integer, Boolean> deserialized = HELIDON.deserialize(json, type);
//        assertThat(deserialized.getFirst(), is("test"));
//        assertThat(deserialized.getSecond(), is(100));
//        assertThat(deserialized.getThird(), is(true));
//    }
//
//    @Test
//    public void testGenericClassWithMixedFields() {
//        GenericClassWithMixedFields<String> obj = new GenericClassWithMixedFields<>();
//        obj.setId("item123");
//        obj.setName("Test Item");
//        obj.setValue("generic value");
//        obj.setTags(List.of("important", "featured"));
//
//        String expected = "{\"id\":\"item123\","
//                + "\"name\":\"Test Item\","
//                + "\"value\":\"generic value\","
//                + "\"tags\":[\"important\",\"featured\"]}";
//        String json = HELIDON.serialize(obj);
//        assertThat(json, is(expected));
//
//        GenericType<GenericClassWithMixedFields<String>> type = new GenericType<>() { };
//        GenericClassWithMixedFields<String> deserialized = HELIDON.deserialize(json, type);
//        assertThat(deserialized.getId(), is("item123"));
//        assertThat(deserialized.getName(), is("Test Item"));
//        assertThat(deserialized.getValue(), is("generic value"));
//        assertThat(deserialized.getTags(), is(List.of("important", "featured")));
//    }

    static class SuperParentClass<T, U> {

        private T superParentField;
        private U secondSuperParentField;

        public T superParentField() {
            return superParentField;
        }

        public void superParentField(T superParentField) {
            this.superParentField = superParentField;
        }

        public U secondSuperParentField() {
            return secondSuperParentField;
        }

        public void secondSuperParentField(U secondSuperParentField) {
            this.secondSuperParentField = secondSuperParentField;
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

        public int childField() {
            return childField;
        }

        public void childField(int childField) {
            this.childField = childField;
        }
    }

//    @Json.Entity
//    static class Container<T> {
//        private T value;
//
//        public T getValue() {
//            return value;
//        }
//
//        public void setValue(T value) {
//            this.value = value;
//        }
//    }
//
//    @Json.Entity
//    static class Pair<A, B> {
//        private A first;
//        private B second;
//
//        public A getFirst() {
//            return first;
//        }
//
//        public void setFirst(A first) {
//            this.first = first;
//        }
//
//        public B getSecond() {
//            return second;
//        }
//
//        public void setSecond(B second) {
//            this.second = second;
//        }
//    }
//
//    @Json.Entity
//    static class Triple<A, B, C> {
//        private A first;
//        private B second;
//        private C third;
//
//        public A getFirst() {
//            return first;
//        }
//
//        public void setFirst(A first) {
//            this.first = first;
//        }
//
//        public B getSecond() {
//            return second;
//        }
//
//        public void setSecond(B second) {
//            this.second = second;
//        }
//
//        public C getThird() {
//            return third;
//        }
//
//        public void setThird(C third) {
//            this.third = third;
//        }
//    }
//
//    @Json.Entity
//    static class GenericClassWithMixedFields<T> {
//        private String id;
//        private String name;
//        private T value;
//        private List<String> tags;
//
//        public String getId() {
//            return id;
//        }
//
//        public void setId(String id) {
//            this.id = id;
//        }
//
//        public String getName() {
//            return name;
//        }
//
//        public void setName(String name) {
//            this.name = name;
//        }
//
//        public T getValue() {
//            return value;
//        }
//
//        public void setValue(T value) {
//            this.value = value;
//        }
//
//        public List<String> getTags() {
//            return tags;
//        }
//
//        public void setTags(List<String> tags) {
//            this.tags = tags;
//        }
//    }
}
