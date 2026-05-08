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
public class IntegerTest {

    private final JsonBinding jsonBinding;

    IntegerTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testIntegerSerializationParameterized(BindingMethod bindingMethod) {
        IntegerModel model = new IntegerModel(123, 456);

        String expected = "{\"object\":123,\"primitive\":456}";
        assertThat(bindingMethod.serialize(jsonBinding, model), is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testIntegerDeserializationFromIntegerAsStringValueParameterized(BindingMethod bindingMethod) {
        IntegerModel integerModel = bindingMethod.deserialize(jsonBinding,
                                                              "{\"object\":\"123\",\"primitive\":\"456\"}",
                                                              IntegerModel.class);
        assertThat(integerModel.object, is(123));
        assertThat(integerModel.primitive, is(456));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testIntegerDeserializationFromIntegerRawValueParameterized(BindingMethod bindingMethod) {
        IntegerModel integerModel = bindingMethod.deserialize(jsonBinding,
                                                              "{\"object\":123,\"primitive\":456}",
                                                              IntegerModel.class);
        assertThat(integerModel.object, is(123));
        assertThat(integerModel.primitive, is(456));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRawIntegersParameterized(BindingMethod bindingMethod) {
        Integer value = bindingMethod.deserialize(jsonBinding, "123", Integer.class);
        assertThat(value, is(123));
        value = bindingMethod.deserialize(jsonBinding, "123", int.class);
        assertThat(value, is(123));
        value = bindingMethod.deserialize(jsonBinding, "\"123\"", Integer.class);
        assertThat(value, is(123));
        value = bindingMethod.deserialize(jsonBinding, "\"123\"", int.class);
        assertThat(value, is(123));
        value = bindingMethod.deserialize(jsonBinding, "null", Integer.class);
        assertThat(value, is(nullValue()));
        value = bindingMethod.deserialize(jsonBinding, "null", int.class);
        assertThat(value, is(0));

        String serialized = bindingMethod.serialize(jsonBinding, 123);
        assertThat(serialized, is("123"));
        serialized = bindingMethod.serialize(jsonBinding, Integer.valueOf(123));
        assertThat(serialized, is("123"));
    }

    @Json.Entity
    record IntegerModel(Integer object, int primitive) {

    }
}
