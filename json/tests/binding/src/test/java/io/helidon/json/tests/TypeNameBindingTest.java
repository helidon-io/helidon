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

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Testing.Test
class TypeNameBindingTest {
    private final JsonBinding binding;

    TypeNameBindingTest(JsonBinding binding) {
        this.binding = binding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void sameSimpleNamesRetainDistinctConverters(BindingMethod method) {
        var expected = new Pair(new Left.Value("left"), new Right.Value(42));
        String json = "{\"left\":{\"text\":\"left\"},\"right\":{\"number\":42}}";
        assertThat(method.serialize(binding, expected), is(json));
        assertThat(method.deserialize(binding, json, Pair.class), is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void nestedGenericTypesRetainTheirArguments(BindingMethod method) {
        Box<List<Left.Value>> left = new Box<>();
        left.setValue(List.of(new Left.Value("left")));
        Box<List<Right.Value>> right = new Box<>();
        right.setValue(List.of(new Right.Value(42)));
        var expected = new Nested(left, right);
        String json = "{\"left\":{\"value\":[{\"text\":\"left\"}]},\"right\":{\"value\":[{\"number\":42}]}}";
        assertThat(method.serialize(binding, expected), is(json));
        Nested actual = method.deserialize(binding, json, Nested.class);
        assertAll(() -> assertThat(actual.left().getValue(), is(left.getValue())),
                  () -> assertThat(actual.right().getValue(), is(right.getValue())));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void multidimensionalArraysRetainTheirComponentTypes(BindingMethod method) {
        var expected = new Matrix(new Left.Value[][] {{new Left.Value("left")}},
                                  new Right.Value[][] {{new Right.Value(42)}},
                                  new int[][] {{1, 2}, {3}});
        String json = "{\"left\":[[{\"text\":\"left\"}]],\"right\":[[{\"number\":42}]],\"numbers\":[[1,2],[3]]}";
        assertThat(method.serialize(binding, expected), is(json));
        Matrix actual = method.deserialize(binding, json, Matrix.class);
        assertAll(() -> assertThat(actual.left(), is(expected.left())),
                  () -> assertThat(actual.right(), is(expected.right())),
                  () -> assertThat(actual.numbers(), is(expected.numbers())));
    }

    @Json.Entity
    record Pair(Left.Value left, Right.Value right) {
    }

    @Json.Entity
    record Nested(Box<List<Left.Value>> left, Box<List<Right.Value>> right) {
    }

    @Json.Entity
    record Matrix(Left.Value[][] left, Right.Value[][] right, int[][] numbers) {
    }

    @Json.Entity
    static class Box<T> {
        private T value;

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }
    }

    static class Left {
        @Json.Entity
        record Value(String text) {
        }
    }

    static class Right {
        @Json.Entity
        record Value(int number) {
        }
    }
}
