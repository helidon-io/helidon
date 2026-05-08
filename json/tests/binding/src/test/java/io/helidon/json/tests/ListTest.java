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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import io.helidon.common.GenericType;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

@Testing.Test
public class ListTest {

    private final JsonBinding jsonBinding;

    ListTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testListSerializationParameterized(BindingMethod bindingMethod) {
        List<String> list = List.of("a", "b", "c");

        String expected = "[\"a\",\"b\",\"c\"]";

        String json = bindingMethod.serialize(jsonBinding, list);
        assertThat(json, is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testListDeserializationParameterized(BindingMethod bindingMethod) {
        List<String> list = List.of("a", "b", "c");

        String json = "[\"a\",\"b\",\"c\"]";

        GenericType<List<String>> type = new GenericType<>() { };
        List<String> deserialized = bindingMethod.deserialize(jsonBinding, json, type);
        assertThat(deserialized, is(list));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testListTypeDeserializationParameterized(BindingMethod bindingMethod) {
        List<String> list = List.of("a", "b", "c");

        String json = "[\"a\",\"b\",\"c\"]";

        GenericType<List<String>> listType = new GenericType<>() { };
        List<String> deserialized = bindingMethod.deserialize(jsonBinding, json, listType);
        assertThat(deserialized, is(list));
        assertThat(deserialized, instanceOf(ArrayList.class));

        GenericType<ArrayList<String>> arrayListType = new GenericType<>() { };
        deserialized = bindingMethod.deserialize(jsonBinding, json, arrayListType);
        assertThat(deserialized, is(list));
        assertThat(deserialized, instanceOf(ArrayList.class));

        GenericType<LinkedList<String>> linkedListType = new GenericType<>() { };
        deserialized = bindingMethod.deserialize(jsonBinding, json, linkedListType);
        assertThat(deserialized, is(list));
        assertThat(deserialized, instanceOf(LinkedList.class));
    }
}
