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
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExceptionReportingTest {

    private static final JsonBinding HELIDON = Services.get(JsonBinding.class);

    @Test
    public void testUnexpectedStringEnd() {
        assertThrows(JsonException.class, () -> HELIDON.deserialize("{\"data", TestData.class));
        assertThrows(JsonException.class, () -> HELIDON.deserialize("{\"data\":\"something}", TestData.class));
    }

    @Test
    public void testUnexpectedJsonValue() {
        assertThrows(JsonException.class, () -> HELIDON.deserialize("{\"data\":myValue}", TestData.class));
    }

    @Test
    public void testTooLargeNumbers() {
        String testValue = "1".repeat(20);
        assertThrows(JsonException.class, () -> HELIDON.deserialize(testValue, byte.class));
        assertThrows(JsonException.class, () -> HELIDON.deserialize(testValue, short.class));
        assertThrows(JsonException.class, () -> HELIDON.deserialize(testValue, int.class));
        assertThrows(JsonException.class, () -> HELIDON.deserialize(testValue, long.class));
        assertThat(HELIDON.deserialize(testValue, float.class), is(Float.parseFloat(testValue)));
        assertThat(HELIDON.deserialize(testValue, double.class), is(Double.parseDouble(testValue)));
    }

    @Json.Entity
    record TestData(String data) {
    }

}
