/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common.mapper;

import java.util.List;
import java.util.Objects;

import io.helidon.common.GenericType;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValueProviderTest {
    @Test
    void testEmpty() {
        String name = "QueryParam(query)";
        Value<Object> empty = Value.empty(name);

        ValueProvider provider = ValueProvider.empty(name);

        assertThat(provider.name(), is(name));
        assertThat(provider.asBoolean(), is(empty));
        assertThat(provider.asDouble(), is(empty));
        assertThat(provider.asInt(), is(empty));
        assertThat(provider.asLong(), is(empty));
        assertThat(provider.asString(), is(empty));
        assertThat(provider.asList(String.class), is(empty));
        assertThat(provider.as(String.class), is(empty));
        assertThat(provider.as(ValueProvider.STRING_TYPE), is(empty));
    }

    @Test
    void testValue() {
        GenericType<List<MyType>> myListType = new GenericType<>() { };
        MapperManager manager = MapperManager.builder()
                .addMapper(Boolean::parseBoolean, String.class, Boolean.class)
                .addMapper(Double::parseDouble, String.class, Double.class)
                .addMapper(Integer::parseInt, String.class, Integer.class)
                .addMapper(Long::parseLong, String.class, Long.class)
                .addMapper(MyType::new, String.class, MyType.class)
                .addMapper(string -> List.of(new MyType(string)), ValueProvider.STRING_TYPE, myListType)
                .build();

        String name = "Answer";
        String value = "42";
        MyType myTypeValue = new MyType("42");
        ValueProvider provider = ValueProvider.create(manager, name, value);

        assertThat(provider.name(), is(name));
        assertThat(provider.asBoolean().get(), is(false));
        assertThat(provider.asDouble().get(), is(42.0));
        assertThat(provider.asInt().get(), is(42));
        assertThat(provider.asLong().get(), is(42L));
        assertThat(provider.asString().get(), is(value));
        assertThat(provider.asList(String.class).get(), is(List.of(value)));
        assertThat(provider.as(String.class).get(), is(value));
        assertThat(provider.as(ValueProvider.STRING_TYPE).get(), is(value));
        assertThat(provider.as(MyType.class).get(), is(myTypeValue));
        assertThat(provider.as(myListType).get(), is(List.of(myTypeValue)));
        assertThat(provider.as(CharSequence.class).get(), is(value));
    }

    @Test
    void testList() {
        GenericType<List<MyType>> myListType = new GenericType<>() { };
        MapperManager manager = MapperManager.builder()
                .addMapper(Boolean::parseBoolean, String.class, Boolean.class)
                .addMapper(Double::parseDouble, String.class, Double.class)
                .addMapper(Integer::parseInt, String.class, Integer.class)
                .addMapper(Long::parseLong, String.class, Long.class)
                .addMapper(MyType::new, String.class, MyType.class)
                .addMapper(string -> List.of(new MyType(string)), ValueProvider.STRING_TYPE, myListType)
                .build();

        String name = "Answer";
        String value = "42,42,41,43";
        ValueProvider provider = ValueProvider.create(manager, name, value);

        assertThat(provider.name(), is(name));
        assertThat(provider.asBoolean().get(), is(false));
        assertThrows(MapperException.class, () -> provider.asDouble().get());
        assertThrows(MapperException.class, () -> provider.asInt().get());
        assertThrows(MapperException.class, () -> provider.asLong().get());
        assertThat(provider.asString().get(), is(value));
        assertThat(provider.asList(String.class).get(), hasItems("42", "42", "41", "43"));
        assertThat(provider.as(String.class).get(), is(value));
        assertThat(provider.as(ValueProvider.STRING_TYPE).get(), is(value));
        assertThat(provider.as(MyType.class).get(), is(new MyType(value)));
        assertThat(provider.as(myListType).get(), hasItems(new MyType(value)));
        assertThat(provider.as(CharSequence.class).get(), is(value));
    }

    static final class MyType {
        private final String message;

        MyType(String message) {
            this.message = message;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MyType myType = (MyType) o;
            return message.equals(myType.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(message);
        }

        @Override
        public String toString() {
            return message;
        }
    }
}
