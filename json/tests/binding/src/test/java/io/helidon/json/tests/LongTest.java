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
public class LongTest {

    private final JsonBinding jsonBinding;

    LongTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testLongSerializationParameterized(BindingMethod bindingMethod) {
        LongModel model = new LongModel(123L, 456);

        String expected = "{\"object\":123,\"primitive\":456}";
        assertThat(bindingMethod.serialize(jsonBinding, model), is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testLongDeserializationFromLongAsStringValueParameterized(BindingMethod bindingMethod) {
        LongModel longModel = bindingMethod.deserialize(jsonBinding,
                                                        "{\"object\":\"123\",\"primitive\":\"456\"}",
                                                        LongModel.class);
        assertThat(longModel.object, is(123L));
        assertThat(longModel.primitive, is(456L));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testLongDeserializationFromLongRawValueParameterized(BindingMethod bindingMethod) {
        LongModel longModel = bindingMethod.deserialize(jsonBinding,
                                                        "{\"object\":123,\"primitive\":456}",
                                                        LongModel.class);
        assertThat(longModel.object, is(123L));
        assertThat(longModel.primitive, is(456L));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRawLongsParameterized(BindingMethod bindingMethod) {
        Long value = bindingMethod.deserialize(jsonBinding, "123", Long.class);
        assertThat(value, is(123L));
        value = bindingMethod.deserialize(jsonBinding, "123", long.class);
        assertThat(value, is(123L));
        value = bindingMethod.deserialize(jsonBinding, "\"123\"", Long.class);
        assertThat(value, is(123L));
        value = bindingMethod.deserialize(jsonBinding, "\"123\"", long.class);
        assertThat(value, is(123L));
        value = bindingMethod.deserialize(jsonBinding, "null", Long.class);
        assertThat(value, is(nullValue()));
        value = bindingMethod.deserialize(jsonBinding, "null", long.class);
        assertThat(value, is(0L));

        String serialized = bindingMethod.serialize(jsonBinding, 123L);
        assertThat(serialized, is("123"));
        serialized = bindingMethod.serialize(jsonBinding, Long.valueOf(123L));
        assertThat(serialized, is("123"));
    }

    @Json.Entity
    record LongModel(Long object, long primitive) {

    }
}
