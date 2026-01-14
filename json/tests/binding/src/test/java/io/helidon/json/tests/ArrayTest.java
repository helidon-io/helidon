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

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
public class ArrayTest {

    private final JsonBinding jsonBinding;

    ArrayTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testOneDimensionPrimitiveArray() {
        int[] expectedArray = {1, 2, 3};
        OneDimensionPrimitiveArray recordWithArray = new OneDimensionPrimitiveArray(expectedArray);
        String serializedJson = jsonBinding.serialize(recordWithArray);
        assertThat(serializedJson, is("{\"intArray\":[1,2,3]}"));

        OneDimensionPrimitiveArray deserialized = jsonBinding.deserialize(serializedJson, OneDimensionPrimitiveArray.class);
        assertThat(deserialized, notNullValue());
        assertThat(deserialized.intArray(), is(expectedArray));
    }

    @Test
    public void testTwoDimensionPrimitiveArray() {
        int[][] expectedArray = {{1, 2, 3}, {4, 5}, {7}};
        TwoDimensionPrimitiveArray recordWithArray = new TwoDimensionPrimitiveArray(expectedArray);
        String serializedJson = jsonBinding.serialize(recordWithArray);
        assertThat(serializedJson, is("{\"intArray\":[[1,2,3],[4,5],[7]]}"));

        TwoDimensionPrimitiveArray deserialized = jsonBinding.deserialize(serializedJson, TwoDimensionPrimitiveArray.class);
        assertThat(deserialized, notNullValue());
        assertThat(deserialized.intArray(), is(expectedArray));
    }

    @Test
    public void testOneDimensionReferenceTypeArray() {
        String[] expectedArray = {"Hi", "Hello"};
        OneDimensionReferenceTypeArray recordWithArray = new OneDimensionReferenceTypeArray(expectedArray);
        String serializedJson = jsonBinding.serialize(recordWithArray);
        assertThat(serializedJson, is("{\"stringArray\":[\"Hi\",\"Hello\"]}"));

        OneDimensionReferenceTypeArray deserialized = jsonBinding.deserialize(serializedJson,
                                                                              OneDimensionReferenceTypeArray.class);
        assertThat(deserialized, notNullValue());
        assertThat(deserialized.stringArray(), is(expectedArray));
    }

    @Test
    public void testTwoDimensionReferenceTypeArray() {
        String[][] expectedArray = {{"Hi", "Hello"}, {"Test", "value", "is here"}};
        TwoDimensionReferenceTypeArray recordWithArray = new TwoDimensionReferenceTypeArray(expectedArray);
        String serializedJson = jsonBinding.serialize(recordWithArray);
        assertThat(serializedJson, is("{\"stringArray\":[[\"Hi\",\"Hello\"],[\"Test\",\"value\",\"is here\"]]}"));

        TwoDimensionReferenceTypeArray deserialized = jsonBinding.deserialize(serializedJson,
                                                                              TwoDimensionReferenceTypeArray.class);
        assertThat(deserialized, notNullValue());
        assertThat(deserialized.stringArray(), is(expectedArray));
    }

    @Test
    public void testCharArray() {
        char[] expectedArray = {'a', 'b', 'c'};
        String serializedJson = jsonBinding.serialize(expectedArray);
        assertThat(serializedJson, is("[\"a\",\"b\",\"c\"]"));

        char[] deserialized = jsonBinding.deserialize(serializedJson, char[].class);
        assertThat(deserialized, notNullValue());
        assertThat(deserialized, is(expectedArray));
    }

    @Json.Entity
    record OneDimensionPrimitiveArray(int[] intArray) {
    }

    @Json.Entity
    record TwoDimensionPrimitiveArray(int[][] intArray) {
    }

    @Json.Entity
    record OneDimensionReferenceTypeArray(String[] stringArray) {
    }

    @Json.Entity
    record TwoDimensionReferenceTypeArray(String[][] stringArray) {
    }

}
