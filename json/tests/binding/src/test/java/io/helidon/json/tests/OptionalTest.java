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

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import io.helidon.common.GenericType;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testing.Test
public class OptionalTest {

    private final JsonBinding jsonBinding;

    OptionalTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    void testEmptyOptional() {
        Optional<Object> optional = Optional.empty();
        String expected = "null";
        String serialized = jsonBinding.serialize(optional);

        assertThat(serialized, is(expected));

        Optional<Object> deserialized = jsonBinding.deserialize(expected, new GenericType<>() { });
        assertThat(deserialized, is(optional));
    }

    @Test
    void testOptional() {
        Optional<String> optional = Optional.of("Hello");
        String expected = "\"Hello\"";

        GenericType<Optional<String>> genericType = new GenericType<>() { };
        String serialized = jsonBinding.serialize(optional, genericType);

        assertThat(serialized, is(expected));

        Optional<String> deserialized = jsonBinding.deserialize(expected, genericType);
        assertThat(deserialized, is(optional));
    }

    @Test
    void testEmptyOptionalInt() {
        OptionalInt optional = OptionalInt.empty();
        String expected = "null";
        String serialized = jsonBinding.serialize(optional);

        assertThat(serialized, is(expected));

        OptionalInt deserialized = jsonBinding.deserialize(expected, OptionalInt.class);
        assertThat(deserialized, is(optional));
    }

    @Test
    void testOptionalInt() {
        OptionalInt optional = OptionalInt.of(123);
        String expected = "123";

        String serialized = jsonBinding.serialize(optional);

        assertThat(serialized, is(expected));

        OptionalInt deserialized = jsonBinding.deserialize(expected, OptionalInt.class);
        assertThat(deserialized, is(optional));
    }

    @Test
    void testEmptyOptionalLong() {
        OptionalLong optional = OptionalLong.empty();
        String expected = "null";
        String serialized = jsonBinding.serialize(optional);

        assertThat(serialized, is(expected));

        OptionalLong deserialized = jsonBinding.deserialize(expected, OptionalLong.class);
        assertThat(deserialized, is(optional));
    }

    @Test
    void testOptionalLong() {
        OptionalLong optional = OptionalLong.of(123);
        String expected = "123";

        String serialized = jsonBinding.serialize(optional);

        assertThat(serialized, is(expected));

        OptionalLong deserialized = jsonBinding.deserialize(expected, OptionalLong.class);
        assertThat(deserialized, is(optional));
    }

    @Test
    void testEmptyOptionalDouble() {
        OptionalDouble optional = OptionalDouble.empty();
        String expected = "null";
        String serialized = jsonBinding.serialize(optional);

        assertThat(serialized, is(expected));

        OptionalDouble deserialized = jsonBinding.deserialize(expected, OptionalDouble.class);
        assertThat(deserialized, is(optional));
    }

    @Test
    void testOptionalDouble() {
        OptionalDouble optional = OptionalDouble.of(123.456);
        String expected = "123.456";

        String serialized = jsonBinding.serialize(optional);

        assertThat(serialized, is(expected));

        OptionalDouble deserialized = jsonBinding.deserialize(expected, OptionalDouble.class);
        assertThat(deserialized, is(optional));
    }

}
