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

import io.helidon.json.JsonException;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class EnumTest {

    private final JsonBinding jsonBinding;

    EnumTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testRootEnumProcessingParameterized(BindingMethod bindingMethod) {
        String expected = "\"VALUE1\"";
        String json = bindingMethod.serialize(jsonBinding, TestEnum.VALUE1);
        assertThat(json, is(expected));

        TestEnum testEnum = bindingMethod.deserialize(jsonBinding, expected, TestEnum.class);
        assertThat(testEnum, is(TestEnum.VALUE1));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testEnumInObjectParameterized(BindingMethod bindingMethod) {
        String expected = "{\"enumValue\":\"VALUE2\"}";
        String json = bindingMethod.serialize(jsonBinding, new RecordWithEnum(TestEnum.VALUE2));
        assertThat(json, is(expected));

        RecordWithEnum recordWithEnum = bindingMethod.deserialize(jsonBinding, expected, RecordWithEnum.class);
        assertThat(recordWithEnum.enumValue, is(TestEnum.VALUE2));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testEnumInObjectAsNullParameterized(BindingMethod bindingMethod) {
        String json = bindingMethod.serialize(jsonBinding, new RecordWithEnum(null));
        assertThat(json, is("{}"));

        String expected = "{\"enumValue\":null}";
        RecordWithEnum recordWithEnum = bindingMethod.deserialize(jsonBinding, expected, RecordWithEnum.class);
        assertThat(recordWithEnum.enumValue, is(nullValue()));
        recordWithEnum = bindingMethod.deserialize(jsonBinding, "{}", RecordWithEnum.class);
        assertThat(recordWithEnum.enumValue, is(nullValue()));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testInvalidEnumValueParameterized(BindingMethod bindingMethod) {
        assertThrows(JsonException.class, () -> bindingMethod.deserialize(jsonBinding, "\"INVALID\"", TestEnum.class));
    }

    enum TestEnum {
        VALUE1,
        VALUE2,
        VALUE3
    }

    @Json.Entity
    record RecordWithEnum(TestEnum enumValue) {
    }
}
