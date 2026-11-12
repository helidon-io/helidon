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

import java.util.List;

import io.helidon.common.AccessorStyle;
import io.helidon.common.Api;
import io.helidon.json.JsonException;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
@SuppressWarnings(Api.SUPPRESS_INCUBATING)
class PropertyIdentifierTest {
    private final JsonBinding jsonBinding;

    PropertyIdentifierTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void dashedProperty(BindingMethod bindingMethod) {
        DashedProperty entity = new DashedProperty();
        entity.someName("value");
        String json = bindingMethod.serialize(jsonBinding, entity);
        assertThat(json, is("{\"some-name\":\"value\"}"));
        DashedProperty result = bindingMethod.deserialize(jsonBinding, json, DashedProperty.class);
        assertThat(result.someName(), is("value"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void recordPropertiesWithDistinctJsonNames(BindingMethod bindingMethod) {
        var entity = new DistinctNames("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");
        String json = "{\"some-name\":\"a\",\"some_name\":\"b\",\"someName\":\"c\",\"some.name\":\"d\","
                + "\"some name\":\"e\",\"1name\":\"f\",\"class\":\"g\",\"é\":\"h\",\"\":\"i\","
                + "\"foo\":\"j\",\"fooKey\":\"k\"}";
        assertThat(bindingMethod.serialize(jsonBinding, entity), is(json));
        assertThat(bindingMethod.deserialize(jsonBinding, json, DistinctNames.class), is(entity));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void normalizedPropertyNamesRemainDistinct(BindingMethod bindingMethod) {
        var entity = new NormalizedNames("a", "b", "c", "d", "e", "f", "g", "h", "i");
        String json = "{\"e\":\"a\",\"é\":\"b\",\"e\u0301\":\"c\",\"\":\"d\",\"NAME\":\"e\","
                + "\"1name\":\"f\",\"NAME_1name\":\"g\",\"ø\":\"h\",\"_\":\"i\"}";
        assertThat(bindingMethod.serialize(jsonBinding, entity), is(json));
        assertThat(bindingMethod.deserialize(jsonBinding, json, NormalizedNames.class), is(entity));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void genericPropertiesWithSimilarNames(BindingMethod bindingMethod) {
        var entity = new GenericNames(List.of("a"), List.of(1), List.of(true));
        String json = "{\"some-name\":[\"a\"],\"values\":[1],\"Values\":[true]}";
        assertThat(bindingMethod.serialize(jsonBinding, entity), is(json));
        assertThat(bindingMethod.deserialize(jsonBinding, json, GenericNames.class), is(entity));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void genericAndTypeConverterNames(BindingMethod bindingMethod) {
        var entity = new GenericAndPlain(List.of("a"), "b");
        String json = "{\"string\":[\"a\"],\"plain\":\"b\"}";
        assertThat(bindingMethod.serialize(jsonBinding, entity), is(json));
        assertThat(bindingMethod.deserialize(jsonBinding, json, GenericAndPlain.class), is(entity));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void hashFieldsAndPropertyLocals(BindingMethod bindingMethod) {
        var entity = new HashAndLocalNames("a", "b");
        String json = "{\"--\":\"a\",\"---\":\"b\"}";
        assertThat(bindingMethod.serialize(jsonBinding, entity), is(json));
        assertThat(bindingMethod.deserialize(jsonBinding, json, HashAndLocalNames.class), is(entity));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void converterFieldsAndPropertyLocals(BindingMethod bindingMethod) {
        var entity = new ConverterAndLocalNames("a", List.of("b"));
        String json = "{\"deserializer_\":\"a\",\"--\":[\"b\"]}";
        assertThat(bindingMethod.serialize(jsonBinding, entity), is(json));
        assertThat(bindingMethod.deserialize(jsonBinding, json, ConverterAndLocalNames.class), is(entity));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void requiredProperty(BindingMethod bindingMethod) {
        String json = "{\"required-name\":\"value\"}";
        var entity = new RequiredName("value");
        assertThat(bindingMethod.serialize(jsonBinding, entity), is(json));
        assertThat(bindingMethod.deserialize(jsonBinding, json, RequiredName.class), is(entity));
        JsonException exception = assertThrows(JsonException.class,
                () -> bindingMethod.deserialize(jsonBinding, "{\"other\":1}", RequiredName.class));
        assertThat(exception.getMessage(), containsString("required-name"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void customConverters(BindingMethod bindingMethod) {
        var entity = new CustomName("value");
        String json = "{\"custom-name\":\"value_custom_converter\"}";
        assertThat(bindingMethod.serialize(jsonBinding, entity), is(json));
        assertThat(bindingMethod.deserialize(jsonBinding, json, CustomName.class), is(new CustomName("value_deserialized")));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void polymorphicAlias(BindingMethod bindingMethod) {
        var entity = new DashedAlias("value");
        String json = "{\"@type\":\"some-name\",\"some-name\":\"value\"}";
        assertThat(bindingMethod.serialize(jsonBinding, entity, Alias.class), is(json));
        assertThat(bindingMethod.deserialize(jsonBinding, json, Alias.class), is(entity));
        var other = new UnderscoredAlias("other");
        String otherJson = "{\"@type\":\"some_name\",\"some-name\":\"other\"}";
        assertThat(bindingMethod.serialize(jsonBinding, other, Alias.class), is(otherJson));
        assertThat(bindingMethod.deserialize(jsonBinding, otherJson, Alias.class), is(other));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void propertiesAssignedAfterBuilding(BindingMethod bindingMethod) {
        String json = "{\"built-name\":\"a\",\"field-name\":\"b\",\"setter-name\":\"c\"}";
        BuiltNames entity = bindingMethod.deserialize(jsonBinding, json, BuiltNames.class);
        assertThat(entity.built, is("a"));
        assertThat(entity.field, is("b"));
        assertThat(entity.setter(), is("c"));
        assertThat(bindingMethod.serialize(jsonBinding, entity), is(json));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void propertyAndAliasNames(BindingMethod bindingMethod) {
        var entity = new AliasProperty();
        entity.value = "value";
        String json = "{\"alias_name\":\"value\"}";
        assertThat(bindingMethod.serialize(jsonBinding, entity), is(json));
        assertThat(bindingMethod.deserialize(jsonBinding, json, AliasProperty.class).value, is("value"));
    }

    @Json.Entity
    record DistinctNames(@Json.Property("some-name") String dashed,
                         @Json.Property("some_name") String underscored,
                         @Json.Property("someName") String camel,
                         @Json.Property("some.name") String dotted,
                         @Json.Property("some name") String spaced,
                         @Json.Property("1name") String digit,
                         @Json.Property("class") String keyword,
                         @Json.Property("é") String unicode,
                         @Json.Property("") String empty,
                         String foo,
                         String fooKey) {
    }

    @Json.Entity
    record GenericNames(@Json.Property("some-name") List<String> dashed,
                        @Json.Property("values") List<Integer> lower,
                        @Json.Property("Values") List<Boolean> upper) {
    }

    @Json.Entity
    record NormalizedNames(@Json.Property("e") String ascii,
                           @Json.Property("é") String accented,
                           @Json.Property("e\u0301") String decomposed,
                           @Json.Property("") String empty,
                           @Json.Property("NAME") String name,
                           @Json.Property("1name") String digit,
                           @Json.Property("NAME_1name") String prefixedDigit,
                           @Json.Property("ø") String unsupported,
                           @Json.Property("_") String underscore) {
    }

    @Json.Entity
    record GenericAndPlain(@Json.Property("string") List<String> values, String plain) {
    }

    @Json.Entity
    record HashAndLocalNames(@Json.Property("--") String first, @Json.Property("---") String second) {
    }

    @Json.Entity
    record ConverterAndLocalNames(@Json.Property("deserializer_") String plain, @Json.Property("--") List<String> values) {
    }

    @Json.Entity
    record RequiredName(@Json.Required @Json.Property("required-name") String value) {
    }

    @Json.Entity
    record CustomName(@Json.Property("custom-name")
                      @Json.Serializer(CustomConverterTest.StringConverter.class)
                      @Json.Deserializer(CustomConverterTest.StringConverter.class) String value) {
    }

    @Json.Entity
    @Json.Subtype(value = DashedAlias.class, alias = "some-name")
    @Json.Subtype(value = UnderscoredAlias.class, alias = "some_name")
    interface Alias {
    }

    @Json.Entity
    record DashedAlias(@Json.Property("some-name") String value) implements Alias {
    }

    @Json.Entity
    record UnderscoredAlias(@Json.Property("some-name") String value) implements Alias {
    }

    @Json.Entity
    @Json.Subtype(value = AliasPropertyChild.class, alias = "name")
    static class AliasProperty {
        @Json.Property("alias_name")
        public String value;
    }

    @Json.Entity
    static class AliasPropertyChild extends AliasProperty {
    }

    @Json.Entity(accessorStyle = AccessorStyle.RECORD)
    @Json.BuilderInfo(BuiltNames.Builder.class)
    static class BuiltNames {
        @Json.Property("built-name")
        public final String built;
        @Json.Property("field-name")
        public String field;
        @Json.Property("setter-name")
        private String setter;

        private BuiltNames(Builder builder) {
            built = builder.built;
        }

        public String setter() {
            return setter;
        }

        public void setter(String value) {
            setter = value;
        }

        static class Builder {
            private String built;

            public Builder built(String value) {
                built = value;
                return this;
            }

            public BuiltNames build() {
                return new BuiltNames(this);
            }
        }
    }

    @Json.Entity(accessorStyle = AccessorStyle.RECORD)
    static class DashedProperty {
        @Json.Property("some-name")
        private String someName;

        public String someName() {
            return someName;
        }

        public void someName(String value) {
            someName = value;
        }
    }
}
