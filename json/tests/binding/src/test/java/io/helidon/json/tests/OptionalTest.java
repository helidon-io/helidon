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
import io.helidon.json.binding.Json;
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

    @Test
    void testEmptyOptionalInObject() {
        OptionalInObject obj = new OptionalInObject("test", Optional.empty());
        String expected = "{\"name\":\"test\"}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalInObject deserialized = jsonBinding.deserialize(expected, OptionalInObject.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testOptionalInObject() {
        OptionalInObject obj = new OptionalInObject("test", Optional.of("value"));
        String expected = "{\"name\":\"test\",\"optionalField\":\"value\"}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalInObject deserialized = jsonBinding.deserialize(expected, OptionalInObject.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testEmptyOptionalIntInObject() {
        OptionalIntInObject obj = new OptionalIntInObject("test", OptionalInt.empty());
        String expected = "{\"name\":\"test\"}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalIntInObject deserialized = jsonBinding.deserialize(expected, OptionalIntInObject.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testOptionalIntInObject() {
        OptionalIntInObject obj = new OptionalIntInObject("test", OptionalInt.of(42));
        String expected = "{\"name\":\"test\",\"optionalField\":42}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalIntInObject deserialized = jsonBinding.deserialize(expected, OptionalIntInObject.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testEmptyOptionalLongInObject() {
        OptionalLongInObject obj = new OptionalLongInObject("test", OptionalLong.empty());
        String expected = "{\"name\":\"test\"}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalLongInObject deserialized = jsonBinding.deserialize(expected, OptionalLongInObject.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testOptionalLongInObject() {
        OptionalLongInObject obj = new OptionalLongInObject("test", OptionalLong.of(42L));
        String expected = "{\"name\":\"test\",\"optionalField\":42}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalLongInObject deserialized = jsonBinding.deserialize(expected, OptionalLongInObject.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testEmptyOptionalDoubleInObject() {
        OptionalDoubleInObject obj = new OptionalDoubleInObject("test", OptionalDouble.empty());
        String expected = "{\"name\":\"test\"}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalDoubleInObject deserialized = jsonBinding.deserialize(expected, OptionalDoubleInObject.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testOptionalDoubleInObject() {
        OptionalDoubleInObject obj = new OptionalDoubleInObject("test", OptionalDouble.of(42.5));
        String expected = "{\"name\":\"test\",\"optionalField\":42.5}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalDoubleInObject deserialized = jsonBinding.deserialize(expected, OptionalDoubleInObject.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testEmptyOptionalInObjectWithSerializeNulls() {
        OptionalInObjectWithSerializeNulls obj = new OptionalInObjectWithSerializeNulls("test", Optional.empty());
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalInObjectWithSerializeNulls deserialized = jsonBinding.deserialize(expected, OptionalInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testOptionalInObjectWithSerializeNulls() {
        OptionalInObjectWithSerializeNulls obj = new OptionalInObjectWithSerializeNulls("test", Optional.of("value"));
        String expected = "{\"name\":\"test\",\"optionalField\":\"value\"}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalInObjectWithSerializeNulls deserialized = jsonBinding.deserialize(expected, OptionalInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testEmptyOptionalIntInObjectWithSerializeNulls() {
        OptionalIntInObjectWithSerializeNulls obj = new OptionalIntInObjectWithSerializeNulls("test", OptionalInt.empty());
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalIntInObjectWithSerializeNulls deserialized = jsonBinding.deserialize(expected, OptionalIntInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testOptionalIntInObjectWithSerializeNulls() {
        OptionalIntInObjectWithSerializeNulls obj = new OptionalIntInObjectWithSerializeNulls("test", OptionalInt.of(42));
        String expected = "{\"name\":\"test\",\"optionalField\":42}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalIntInObjectWithSerializeNulls deserialized = jsonBinding.deserialize(expected, OptionalIntInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testEmptyOptionalLongInObjectWithSerializeNulls() {
        OptionalLongInObjectWithSerializeNulls obj = new OptionalLongInObjectWithSerializeNulls("test", OptionalLong.empty());
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalLongInObjectWithSerializeNulls deserialized = jsonBinding.deserialize(expected, OptionalLongInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testOptionalLongInObjectWithSerializeNulls() {
        OptionalLongInObjectWithSerializeNulls obj = new OptionalLongInObjectWithSerializeNulls("test", OptionalLong.of(42L));
        String expected = "{\"name\":\"test\",\"optionalField\":42}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalLongInObjectWithSerializeNulls deserialized = jsonBinding.deserialize(expected, OptionalLongInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testEmptyOptionalDoubleInObjectWithSerializeNulls() {
        OptionalDoubleInObjectWithSerializeNulls obj = new OptionalDoubleInObjectWithSerializeNulls("test", OptionalDouble.empty());
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalDoubleInObjectWithSerializeNulls deserialized = jsonBinding.deserialize(expected, OptionalDoubleInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testOptionalDoubleInObjectWithSerializeNulls() {
        OptionalDoubleInObjectWithSerializeNulls obj = new OptionalDoubleInObjectWithSerializeNulls("test", OptionalDouble.of(42.5));
        String expected = "{\"name\":\"test\",\"optionalField\":42.5}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalDoubleInObjectWithSerializeNulls deserialized = jsonBinding.deserialize(expected, OptionalDoubleInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @Test
    void testNullOptionalInObject() {
        OptionalInObject obj = new OptionalInObject("test", null);
        String expected = "{\"name\":\"test\"}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalInObject deserialized = jsonBinding.deserialize(expected, OptionalInObject.class);
        assertThat(deserialized, is(new OptionalInObject("test", Optional.empty())));
    }

    @Test
    void testNullOptionalIntInObject() {
        OptionalIntInObject obj = new OptionalIntInObject("test", null);
        String expected = "{\"name\":\"test\"}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalIntInObject deserialized = jsonBinding.deserialize(expected, OptionalIntInObject.class);
        assertThat(deserialized, is(new OptionalIntInObject("test", OptionalInt.empty())));
    }

    @Test
    void testNullOptionalLongInObject() {
        OptionalLongInObject obj = new OptionalLongInObject("test", null);
        String expected = "{\"name\":\"test\"}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalLongInObject deserialized = jsonBinding.deserialize(expected, OptionalLongInObject.class);
        assertThat(deserialized, is(new OptionalLongInObject("test", OptionalLong.empty())));
    }

    @Test
    void testNullOptionalDoubleInObject() {
        OptionalDoubleInObject obj = new OptionalDoubleInObject("test", null);
        String expected = "{\"name\":\"test\"}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalDoubleInObject deserialized = jsonBinding.deserialize(expected, OptionalDoubleInObject.class);
        assertThat(deserialized, is(new OptionalDoubleInObject("test", OptionalDouble.empty())));
    }

    @Test
    void testNullOptionalInObjectWithSerializeNulls() {
        OptionalInObjectWithSerializeNulls obj = new OptionalInObjectWithSerializeNulls("test", null);
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalInObjectWithSerializeNulls deserialized = jsonBinding.deserialize(expected, OptionalInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(new OptionalInObjectWithSerializeNulls("test", Optional.empty())));
    }

    @Test
    void testNullOptionalIntInObjectWithSerializeNulls() {
        OptionalIntInObjectWithSerializeNulls obj = new OptionalIntInObjectWithSerializeNulls("test", null);
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalIntInObjectWithSerializeNulls deserialized = jsonBinding.deserialize(expected, OptionalIntInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(new OptionalIntInObjectWithSerializeNulls("test", OptionalInt.empty())));
    }

    @Test
    void testNullOptionalLongInObjectWithSerializeNulls() {
        OptionalLongInObjectWithSerializeNulls obj = new OptionalLongInObjectWithSerializeNulls("test", null);
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalLongInObjectWithSerializeNulls deserialized = jsonBinding.deserialize(expected, OptionalLongInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(new OptionalLongInObjectWithSerializeNulls("test", OptionalLong.empty())));
    }

    @Test
    void testNullOptionalDoubleInObjectWithSerializeNulls() {
        OptionalDoubleInObjectWithSerializeNulls obj = new OptionalDoubleInObjectWithSerializeNulls("test", null);
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = jsonBinding.serialize(obj);

        assertThat(serialized, is(expected));

        OptionalDoubleInObjectWithSerializeNulls deserialized = jsonBinding.deserialize(expected, OptionalDoubleInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(new OptionalDoubleInObjectWithSerializeNulls("test", OptionalDouble.empty())));
    }


    @Json.Entity
    public record OptionalInObject(String name, Optional<String> optionalField) {}

    @Json.Entity
    public record OptionalIntInObject(String name, OptionalInt optionalField) {}

    @Json.Entity
    public record OptionalLongInObject(String name, OptionalLong optionalField) {}

    @Json.Entity
    public record OptionalDoubleInObject(String name, OptionalDouble optionalField) {}

    @Json.Entity
    @Json.SerializeNulls
    public record OptionalInObjectWithSerializeNulls(String name, Optional<String> optionalField) {}

    @Json.Entity
    @Json.SerializeNulls
    public record OptionalIntInObjectWithSerializeNulls(String name, OptionalInt optionalField) {}

    @Json.Entity
    @Json.SerializeNulls
    public record OptionalLongInObjectWithSerializeNulls(String name, OptionalLong optionalField) {}

    @Json.Entity
    @Json.SerializeNulls
    public record OptionalDoubleInObjectWithSerializeNulls(String name, OptionalDouble optionalField) {}

}
