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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@Testing.Test
public class DoubleTest {

    private final JsonBinding jsonBinding;

    DoubleTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDoubleSerializationParameterized(BindingMethod bindingMethod) {
        DoubleModel model = new DoubleModel(123.456, 456.789);

        String expected = "{\"object\":123.456,\"primitive\":456.789}";
        assertThat(bindingMethod.serialize(jsonBinding, model), is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDoubleDeserializationFromDoubleAsStringValueParameterized(BindingMethod bindingMethod) {
        DoubleModel doubleModel = bindingMethod.deserialize(jsonBinding,
                                                            "{\"object\":\"123.456\",\"primitive\":\"456.789\"}",
                                                            DoubleModel.class);
        assertThat(doubleModel.object, is(123.456));
        assertThat(doubleModel.primitive, is(456.789));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testDoubleDeserializationFromDoubleRawValueParameterized(BindingMethod bindingMethod) {
        DoubleModel doubleModel = bindingMethod.deserialize(jsonBinding,
                                                            "{\"object\":123.456,\"primitive\":456.789}",
                                                            DoubleModel.class);
        assertThat(doubleModel.object, is(123.456));
        assertThat(doubleModel.primitive, is(456.789));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRawDoublesParameterized(BindingMethod bindingMethod) {
        Double value = bindingMethod.deserialize(jsonBinding, "123.456", Double.class);
        assertThat(value, is(123.456));
        value = bindingMethod.deserialize(jsonBinding, "123.456", double.class);
        assertThat(value, is(123.456));
        value = bindingMethod.deserialize(jsonBinding, "\"123.456\"", Double.class);
        assertThat(value, is(123.456));
        value = bindingMethod.deserialize(jsonBinding, "\"123.456\"", double.class);
        assertThat(value, is(123.456));
        value = bindingMethod.deserialize(jsonBinding, "null", Double.class);
        assertThat(value, is(nullValue()));
        value = bindingMethod.deserialize(jsonBinding, "null", double.class);
        assertThat(value, is(0.0));

        String serialized = bindingMethod.serialize(jsonBinding, 123.456);
        assertThat(serialized, is("123.456"));
        serialized = bindingMethod.serialize(jsonBinding, Double.valueOf(123.456));
        assertThat(serialized, is("123.456"));
    }

    @Json.Entity
    record DoubleModel(Double object, double primitive) {

    }
}
