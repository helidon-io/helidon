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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@Testing.Test
public class FloatTest {

    private final JsonBinding jsonBinding;

    FloatTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testFloatSerialization() {
        FloatModel model = new FloatModel(123.456F, 456.789F);

        String expected = "{\"object\":123.456,\"primitive\":456.789}";
        assertThat(jsonBinding.serialize(model), is(expected));
    }

    @Test
    public void testFloatDeserializationFromFloatAsStringValue() {
        FloatModel floatModel = jsonBinding.deserialize("{\"object\":\"123.456\",\"primitive\":\"456.789\"}", FloatModel.class);
        assertThat(floatModel.object, is(123.456F));
        assertThat(floatModel.primitive, is(456.789F));
    }

    @Test
    public void testFloatDeserializationFromFloatRawValue() {
        FloatModel floatModel = jsonBinding.deserialize("{\"object\":123.456,\"primitive\":456.789}", FloatModel.class);
        assertThat(floatModel.object, is(123.456F));
        assertThat(floatModel.primitive, is(456.789F));
    }

    @Test
    public void testRawFloats() {
        Float value = jsonBinding.deserialize("123.456", Float.class);
        assertThat(value, is(123.456F));
        value = jsonBinding.deserialize("123.456", float.class);
        assertThat(value, is(123.456F));
        value = jsonBinding.deserialize("\"123.456\"", Float.class);
        assertThat(value, is(123.456F));
        value = jsonBinding.deserialize("\"123.456\"", float.class);
        assertThat(value, is(123.456F));
        value = jsonBinding.deserialize("null", Float.class);
        assertThat(value, is(nullValue()));
        value = jsonBinding.deserialize("null", float.class);
        assertThat(value, is(0.0F));

        String serialized = jsonBinding.serialize(123.456F);
        assertThat(serialized, is("123.456"));
        serialized = jsonBinding.serialize(Float.valueOf(123.456F));
        assertThat(serialized, is("123.456"));
    }

    @Json.Entity
    record FloatModel(Float object, float primitive) {

    }

}
