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

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PrettyPrintTest {

    private static final JsonBinding PRETTY_BINDING = JsonBinding.create(builder -> builder.prettyPrint(true));

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testPrettyPrintPojoSerialize(BindingMethod bindingMethod) {
        Person person = new Person();
        person.setName("John");
        Nested nested = new Nested();
        nested.setActive(true);
        person.setNested(nested);

        String expected = """
                {
                   "name": "John",
                   "nested": {
                      "active": true
                   }
                }""";

        assertThat(bindingMethod.serialize(PRETTY_BINDING, person), is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testPrettyPrintMapSerialize(BindingMethod bindingMethod) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "John");
        map.put("active", true);
        map.put("count", 3);

        String expected = """
                {
                   "name": "John",
                   "active": true,
                   "count": 3
                }""";

        assertThat(bindingMethod.serialize(PRETTY_BINDING, map), is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testPrettyPrintListSerialize(BindingMethod bindingMethod) {
        List<Object> list = List.of("John", 3, true);

        String expected = """
                [
                   "John",
                   3,
                   true
                ]""";

        assertThat(bindingMethod.serialize(PRETTY_BINDING, list), is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testPrettyPrintArraySerialize(BindingMethod bindingMethod) {
        String[] array = {"John", "Jane", "Jack"};

        String expected = """
                [
                   "John",
                   "Jane",
                   "Jack"
                ]""";

        assertThat(bindingMethod.serialize(PRETTY_BINDING, array), is(expected));
    }

    @Json.Entity
    static class Person {
        private String name;
        private Nested nested;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Nested getNested() {
            return nested;
        }

        public void setNested(Nested nested) {
            this.nested = nested;
        }
    }

    @Json.Entity
    static class Nested {
        private boolean active;

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
