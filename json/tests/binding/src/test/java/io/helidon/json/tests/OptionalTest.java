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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testing.Test
public class OptionalTest {

    private final JsonBinding jsonBinding;

    OptionalTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testEmptyOptional(BindingMethod bindingMethod) {
        Optional<Object> optional = Optional.empty();
        String expected = "null";
        String serialized = bindingMethod.serialize(jsonBinding, optional);

        assertThat(serialized, is(expected));

        Optional<Object> deserialized = bindingMethod.deserialize(jsonBinding, expected, new GenericType<>() { });
        assertThat(deserialized, is(optional));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testOptional(BindingMethod bindingMethod) {
        Optional<String> optional = Optional.of("Hello");
        String expected = "\"Hello\"";

        GenericType<Optional<String>> genericType = new GenericType<>() { };
        String serialized = bindingMethod.serialize(jsonBinding, optional, genericType);

        assertThat(serialized, is(expected));

        Optional<String> deserialized = bindingMethod.deserialize(jsonBinding, expected, genericType);
        assertThat(deserialized, is(optional));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testEmptyOptionalInt(BindingMethod bindingMethod) {
        OptionalInt optional = OptionalInt.empty();
        String expected = "null";
        String serialized = bindingMethod.serialize(jsonBinding, optional);

        assertThat(serialized, is(expected));

        OptionalInt deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalInt.class);
        assertThat(deserialized, is(optional));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testOptionalInt(BindingMethod bindingMethod) {
        OptionalInt optional = OptionalInt.of(123);
        String expected = "123";

        String serialized = bindingMethod.serialize(jsonBinding, optional);

        assertThat(serialized, is(expected));

        OptionalInt deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalInt.class);
        assertThat(deserialized, is(optional));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testEmptyOptionalLong(BindingMethod bindingMethod) {
        OptionalLong optional = OptionalLong.empty();
        String expected = "null";
        String serialized = bindingMethod.serialize(jsonBinding, optional);

        assertThat(serialized, is(expected));

        OptionalLong deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalLong.class);
        assertThat(deserialized, is(optional));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testOptionalLong(BindingMethod bindingMethod) {
        OptionalLong optional = OptionalLong.of(123);
        String expected = "123";

        String serialized = bindingMethod.serialize(jsonBinding, optional);

        assertThat(serialized, is(expected));

        OptionalLong deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalLong.class);
        assertThat(deserialized, is(optional));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testEmptyOptionalDouble(BindingMethod bindingMethod) {
        OptionalDouble optional = OptionalDouble.empty();
        String expected = "null";
        String serialized = bindingMethod.serialize(jsonBinding, optional);

        assertThat(serialized, is(expected));

        OptionalDouble deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalDouble.class);
        assertThat(deserialized, is(optional));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testOptionalDouble(BindingMethod bindingMethod) {
        OptionalDouble optional = OptionalDouble.of(123.456);
        String expected = "123.456";

        String serialized = bindingMethod.serialize(jsonBinding, optional);

        assertThat(serialized, is(expected));

        OptionalDouble deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalDouble.class);
        assertThat(deserialized, is(optional));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testEmptyOptionalInObject(BindingMethod bindingMethod) {
        OptionalInObject obj = new OptionalInObject("test", Optional.empty());
        String expected = "{\"name\":\"test\"}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalInObject deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalInObject.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testOptionalInObject(BindingMethod bindingMethod) {
        OptionalInObject obj = new OptionalInObject("test", Optional.of("value"));
        String expected = "{\"name\":\"test\",\"optionalField\":\"value\"}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalInObject deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalInObject.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testEmptyOptionalIntInObject(BindingMethod bindingMethod) {
        OptionalIntInObject obj = new OptionalIntInObject("test", OptionalInt.empty());
        String expected = "{\"name\":\"test\"}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalIntInObject deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalIntInObject.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testOptionalIntInObject(BindingMethod bindingMethod) {
        OptionalIntInObject obj = new OptionalIntInObject("test", OptionalInt.of(42));
        String expected = "{\"name\":\"test\",\"optionalField\":42}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalIntInObject deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalIntInObject.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testEmptyOptionalLongInObject(BindingMethod bindingMethod) {
        OptionalLongInObject obj = new OptionalLongInObject("test", OptionalLong.empty());
        String expected = "{\"name\":\"test\"}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalLongInObject deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalLongInObject.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testOptionalLongInObject(BindingMethod bindingMethod) {
        OptionalLongInObject obj = new OptionalLongInObject("test", OptionalLong.of(42L));
        String expected = "{\"name\":\"test\",\"optionalField\":42}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalLongInObject deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalLongInObject.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testEmptyOptionalDoubleInObject(BindingMethod bindingMethod) {
        OptionalDoubleInObject obj = new OptionalDoubleInObject("test", OptionalDouble.empty());
        String expected = "{\"name\":\"test\"}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalDoubleInObject deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalDoubleInObject.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testOptionalDoubleInObject(BindingMethod bindingMethod) {
        OptionalDoubleInObject obj = new OptionalDoubleInObject("test", OptionalDouble.of(42.5));
        String expected = "{\"name\":\"test\",\"optionalField\":42.5}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalDoubleInObject deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalDoubleInObject.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testEmptyOptionalInObjectWithSerializeNulls(BindingMethod bindingMethod) {
        OptionalInObjectWithSerializeNulls obj = new OptionalInObjectWithSerializeNulls("test", Optional.empty());
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalInObjectWithSerializeNulls deserialized = bindingMethod.deserialize(jsonBinding, expected,
                                                                                    OptionalInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testOptionalInObjectWithSerializeNulls(BindingMethod bindingMethod) {
        OptionalInObjectWithSerializeNulls obj = new OptionalInObjectWithSerializeNulls("test", Optional.of("value"));
        String expected = "{\"name\":\"test\",\"optionalField\":\"value\"}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalInObjectWithSerializeNulls deserialized = bindingMethod.deserialize(jsonBinding, expected,
                                                                                    OptionalInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testEmptyOptionalIntInObjectWithSerializeNulls(BindingMethod bindingMethod) {
        OptionalIntInObjectWithSerializeNulls obj = new OptionalIntInObjectWithSerializeNulls("test", OptionalInt.empty());
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalIntInObjectWithSerializeNulls deserialized = bindingMethod.deserialize(jsonBinding, expected,
                                                                                       OptionalIntInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testOptionalIntInObjectWithSerializeNulls(BindingMethod bindingMethod) {
        OptionalIntInObjectWithSerializeNulls obj = new OptionalIntInObjectWithSerializeNulls("test", OptionalInt.of(42));
        String expected = "{\"name\":\"test\",\"optionalField\":42}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalIntInObjectWithSerializeNulls deserialized = bindingMethod.deserialize(jsonBinding, expected,
                                                                                       OptionalIntInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testEmptyOptionalLongInObjectWithSerializeNulls(BindingMethod bindingMethod) {
        OptionalLongInObjectWithSerializeNulls obj = new OptionalLongInObjectWithSerializeNulls("test", OptionalLong.empty());
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalLongInObjectWithSerializeNulls deserialized = bindingMethod.deserialize(jsonBinding, expected,
                                                                                        OptionalLongInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testOptionalLongInObjectWithSerializeNulls(BindingMethod bindingMethod) {
        OptionalLongInObjectWithSerializeNulls obj = new OptionalLongInObjectWithSerializeNulls("test", OptionalLong.of(42L));
        String expected = "{\"name\":\"test\",\"optionalField\":42}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalLongInObjectWithSerializeNulls deserialized = bindingMethod.deserialize(jsonBinding, expected,
                                                                                        OptionalLongInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testEmptyOptionalDoubleInObjectWithSerializeNulls(BindingMethod bindingMethod) {
        OptionalDoubleInObjectWithSerializeNulls obj = new OptionalDoubleInObjectWithSerializeNulls("test",
                                                                                                    OptionalDouble.empty());
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalDoubleInObjectWithSerializeNulls deserialized = bindingMethod.deserialize(jsonBinding, expected,
                                                                                          OptionalDoubleInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testOptionalDoubleInObjectWithSerializeNulls(BindingMethod bindingMethod) {
        OptionalDoubleInObjectWithSerializeNulls obj = new OptionalDoubleInObjectWithSerializeNulls("test",
                                                                                                    OptionalDouble.of(42.5));
        String expected = "{\"name\":\"test\",\"optionalField\":42.5}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalDoubleInObjectWithSerializeNulls deserialized = bindingMethod.deserialize(jsonBinding, expected,
                                                                                          OptionalDoubleInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(obj));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testNullOptionalInObject(BindingMethod bindingMethod) {
        OptionalInObject obj = new OptionalInObject("test", null);
        String expected = "{\"name\":\"test\"}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalInObject deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalInObject.class);
        assertThat(deserialized, is(new OptionalInObject("test", Optional.empty())));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testNullOptionalIntInObject(BindingMethod bindingMethod) {
        OptionalIntInObject obj = new OptionalIntInObject("test", null);
        String expected = "{\"name\":\"test\"}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalIntInObject deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalIntInObject.class);
        assertThat(deserialized, is(new OptionalIntInObject("test", OptionalInt.empty())));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testNullOptionalLongInObject(BindingMethod bindingMethod) {
        OptionalLongInObject obj = new OptionalLongInObject("test", null);
        String expected = "{\"name\":\"test\"}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalLongInObject deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalLongInObject.class);
        assertThat(deserialized, is(new OptionalLongInObject("test", OptionalLong.empty())));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testNullOptionalDoubleInObject(BindingMethod bindingMethod) {
        OptionalDoubleInObject obj = new OptionalDoubleInObject("test", null);
        String expected = "{\"name\":\"test\"}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalDoubleInObject deserialized = bindingMethod.deserialize(jsonBinding, expected, OptionalDoubleInObject.class);
        assertThat(deserialized, is(new OptionalDoubleInObject("test", OptionalDouble.empty())));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testNullOptionalInObjectWithSerializeNulls(BindingMethod bindingMethod) {
        OptionalInObjectWithSerializeNulls obj = new OptionalInObjectWithSerializeNulls("test", null);
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalInObjectWithSerializeNulls deserialized = bindingMethod.deserialize(jsonBinding, expected,
                                                                                    OptionalInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(new OptionalInObjectWithSerializeNulls("test", Optional.empty())));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testNullOptionalIntInObjectWithSerializeNulls(BindingMethod bindingMethod) {
        OptionalIntInObjectWithSerializeNulls obj = new OptionalIntInObjectWithSerializeNulls("test", null);
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalIntInObjectWithSerializeNulls deserialized = bindingMethod.deserialize(jsonBinding, expected,
                                                                                       OptionalIntInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(new OptionalIntInObjectWithSerializeNulls("test", OptionalInt.empty())));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testNullOptionalLongInObjectWithSerializeNulls(BindingMethod bindingMethod) {
        OptionalLongInObjectWithSerializeNulls obj = new OptionalLongInObjectWithSerializeNulls("test", null);
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalLongInObjectWithSerializeNulls deserialized = bindingMethod.deserialize(jsonBinding, expected,
                                                                                        OptionalLongInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(new OptionalLongInObjectWithSerializeNulls("test", OptionalLong.empty())));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testNullOptionalDoubleInObjectWithSerializeNulls(BindingMethod bindingMethod) {
        OptionalDoubleInObjectWithSerializeNulls obj = new OptionalDoubleInObjectWithSerializeNulls("test", null);
        String expected = "{\"name\":\"test\",\"optionalField\":null}";
        String serialized = bindingMethod.serialize(jsonBinding, obj);

        assertThat(serialized, is(expected));

        OptionalDoubleInObjectWithSerializeNulls deserialized = bindingMethod.deserialize(jsonBinding, expected,
                                                                                          OptionalDoubleInObjectWithSerializeNulls.class);
        assertThat(deserialized, is(new OptionalDoubleInObjectWithSerializeNulls("test", OptionalDouble.empty())));
    }

    @Json.Entity
    public record OptionalInObject(String name, Optional<String> optionalField) { }

    @Json.Entity
    public record OptionalIntInObject(String name, OptionalInt optionalField) { }

    @Json.Entity
    public record OptionalLongInObject(String name, OptionalLong optionalField) { }

    @Json.Entity
    public record OptionalDoubleInObject(String name, OptionalDouble optionalField) { }

    @Json.Entity
    @Json.SerializeNulls
    public record OptionalInObjectWithSerializeNulls(String name, Optional<String> optionalField) { }

    @Json.Entity
    @Json.SerializeNulls
    public record OptionalIntInObjectWithSerializeNulls(String name, OptionalInt optionalField) { }

    @Json.Entity
    @Json.SerializeNulls
    public record OptionalLongInObjectWithSerializeNulls(String name, OptionalLong optionalField) { }

    @Json.Entity
    @Json.SerializeNulls
    public record OptionalDoubleInObjectWithSerializeNulls(String name, OptionalDouble optionalField) { }
}
