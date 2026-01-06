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
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class DoubleTest {

    private static final JsonBinding HELIDON = Services.get(JsonBinding.class);

    @Test
    public void testDoubleSerialization() {
        DoubleModel model = new DoubleModel(123.456, 456.789);

        String expected = "{\"object\":123.456,\"primitive\":456.789}";
        assertThat(HELIDON.serialize(model), is(expected));
    }

    @Test
    public void testDoubleDeserializationFromDoubleAsStringValue() {
        DoubleModel doubleModel = HELIDON.deserialize("{\"object\":\"123.456\",\"primitive\":\"456.789\"}", DoubleModel.class);
        assertThat(doubleModel.object, is(123.456));
        assertThat(doubleModel.primitive, is(456.789));
    }

    @Test
    public void testDoubleDeserializationFromDoubleRawValue() {
        DoubleModel doubleModel = HELIDON.deserialize("{\"object\":123.456,\"primitive\":456.789}", DoubleModel.class);
        assertThat(doubleModel.object, is(123.456));
        assertThat(doubleModel.primitive, is(456.789));
    }

    @Test
    public void testRawDoubles() {
        Double value = HELIDON.deserialize("123.456", Double.class);
        assertThat(value, is(123.456));
        value = HELIDON.deserialize("123.456", double.class);
        assertThat(value, is(123.456));
        value = HELIDON.deserialize("\"123.456\"", Double.class);
        assertThat(value, is(123.456));
        value = HELIDON.deserialize("\"123.456\"", double.class);
        assertThat(value, is(123.456));
        value = HELIDON.deserialize("null", Double.class);
        assertThat(value, is(nullValue()));
        value = HELIDON.deserialize("null", double.class);
        assertThat(value, is(0.0));

        String serialized = HELIDON.serialize(123.456);
        assertThat(serialized, is("123.456"));
        serialized = HELIDON.serialize(Double.valueOf(123.456));
        assertThat(serialized, is("123.456"));
    }

    @Json.Entity
    record DoubleModel(Double object, double primitive) {

    }

}
