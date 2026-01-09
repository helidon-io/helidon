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
public class LongTest {

    private final JsonBinding jsonBinding;

    LongTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testLongSerialization() {
        LongModel model = new LongModel(123L, 456);

        String expected = "{\"object\":123,\"primitive\":456}";
        assertThat(jsonBinding.serialize(model), is(expected));
    }

    @Test
    public void testLongDeserializationFromLongAsStringValue() {
        LongModel longModel = jsonBinding.deserialize("{\"object\":\"123\",\"primitive\":\"456\"}", LongModel.class);
        assertThat(longModel.object, is(123L));
        assertThat(longModel.primitive, is(456L));
    }

    @Test
    public void testLongDeserializationFromLongRawValue() {
        LongModel longModel = jsonBinding.deserialize("{\"object\":123,\"primitive\":456}", LongModel.class);
        assertThat(longModel.object, is(123L));
        assertThat(longModel.primitive, is(456L));
    }

    @Test
    public void testRawLongs() {
        Long value = jsonBinding.deserialize("123", Long.class);
        assertThat(value, is(123L));
        value = jsonBinding.deserialize("123", long.class);
        assertThat(value, is(123L));
        value = jsonBinding.deserialize("\"123\"", Long.class);
        assertThat(value, is(123L));
        value = jsonBinding.deserialize("\"123\"", long.class);
        assertThat(value, is(123L));
        value = jsonBinding.deserialize("null", Long.class);
        assertThat(value, is(nullValue()));
        value = jsonBinding.deserialize("null", long.class);
        assertThat(value, is(0L));

        String serialized = jsonBinding.serialize(123L);
        assertThat(serialized, is("123"));
        serialized = jsonBinding.serialize(Long.valueOf(123L));
        assertThat(serialized, is("123"));
    }

    @Json.Entity
    record LongModel(Long object, long primitive) {

    }

}
