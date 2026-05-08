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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class ExceptionReportingTest {

    private final JsonBinding jsonBinding;

    ExceptionReportingTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testUnexpectedStringEndParameterized(BindingMethod bindingMethod) {
        assertThrows(JsonException.class, () -> bindingMethod.deserialize(jsonBinding, "{\"data", TestData.class));
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "{\"data\":\"something}", TestData.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testUnexpectedJsonValueParameterized(BindingMethod bindingMethod) {
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "{\"data\":myValue}", TestData.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testTooLargeNumbersParameterized(BindingMethod bindingMethod) {
        String testValue = "1".repeat(20);
        assertThrows(JsonException.class, () -> bindingMethod.deserialize(jsonBinding, testValue, byte.class));
        assertThrows(JsonException.class, () -> bindingMethod.deserialize(jsonBinding, testValue, short.class));
        assertThrows(JsonException.class, () -> bindingMethod.deserialize(jsonBinding, testValue, int.class));
        assertThrows(JsonException.class, () -> bindingMethod.deserialize(jsonBinding, testValue, long.class));
        assertThat(bindingMethod.deserialize(jsonBinding, testValue, float.class), is(Float.parseFloat(testValue)));
        assertThat(bindingMethod.deserialize(jsonBinding, testValue, double.class), is(Double.parseDouble(testValue)));
    }

    @Json.Entity
    record TestData(String data) {
    }
}
