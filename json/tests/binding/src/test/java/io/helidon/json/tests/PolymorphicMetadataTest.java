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

import io.helidon.json.JsonException;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.tests.PolymorphicOrderTest.Input;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
class PolymorphicMetadataTest {

    private final JsonBinding binding;

    PolymorphicMetadataTest(JsonBinding binding) {
        this.binding = binding;
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void directSubtypeAcceptsDiscriminatorInAnyPosition(Input input) {
        for (String json : List.of("{\"kind\":\"sample\",\"name\":\"example\",\"count\":7}",
                                   "{\"name\":\"example\",\"kind\":\"sample\",\"count\":7}",
                                   "{\"name\":\"example\",\"count\":7,\"kind\":\"sample\"}")) {
            assertThat(binding.deserialize(input.parser(json), PolymorphicOrderTest.Sample.class),
                       is(new PolymorphicOrderTest.Sample("example", 7, null)));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void directSubtypeAcceptsEscapedMetadataAndMissingMetadata(Input input) {
        for (String json : List.of("{\"name\":\"example\",\"k\\u0069nd\":\"s\\u0061mple\",\"count\":7}",
                                   "{\"name\":\"example\",\"count\":7}")) {
            assertThat(binding.deserialize(input.parser(json), PolymorphicOrderTest.Sample.class),
                       is(new PolymorphicOrderTest.Sample("example", 7, null)));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void emittedMetadataRoundTripsThroughConcreteAndBaseTypes(Input input) {
        PolymorphicOrderTest.Sample expected = new PolymorphicOrderTest.Sample("example", 7, null);
        String json = binding.serialize(expected);
        assertThat(binding.deserialize(input.parser(json), PolymorphicOrderTest.Sample.class), is(expected));
        assertThat(binding.deserialize(input.parser(json), PolymorphicOrderTest.Base.class), is(expected));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void explicitSubtypeIgnoresDiscriminatorValue(Input input) {
        for (String value : List.of("\"other\"", "null", "42", "true", "{\"nested\":[1,2]}", "[\"other\",{}]")) {
            for (String json : List.of("{\"kind\":" + value + ",\"name\":\"example\",\"count\":7}",
                                       "{\"name\":\"example\",\"kind\":" + value + ",\"count\":7}",
                                       "{\"name\":\"example\",\"count\":7,\"kind\":" + value + "}")) {
                assertThat(binding.deserialize(input.parser(json), PolymorphicOrderTest.Sample.class),
                           is(new PolymorphicOrderTest.Sample("example", 7, null)));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Input.class, names = {"ARRAY", "STREAM", "SHORT_READ_STREAM"})
    void polymorphicSelectionKeepsFirstDiscriminatorWhenItAppearsLater(Input input) {
        for (String json : List.of("{\"name\":\"example\",\"kind\":\"sample\",\"kind\":\"other\",\"count\":7}",
                                   "{\"name\":\"example\",\"kind\":\"sample\",\"count\":7,\"kind\":\"other\"}")) {
            assertThat(binding.deserialize(input.parser(json), PolymorphicOrderTest.Base.class),
                       is(new PolymorphicOrderTest.Sample("example", 7, null)));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Input.class, names = {"ARRAY", "STREAM", "SHORT_READ_STREAM", "VALUE"})
    void ignoredMetadataStillRequiresValidJson(Input input) {
        for (String json : List.of("{\"kind\":{\"broken\":},\"name\":\"example\"}",
                                   "{\"kind\":[1,],\"name\":\"example\"}",
                                   "{\"name\":\"example\",\"kind\":\"unterminated}",
                                   "{\"kind\":[\"line\nbreak\"]}",
                                   "{\"kind\":\"\\u12G4\"}")) {
            assertThrows(JsonException.class,
                         () -> binding.deserialize(input.parser(json), PolymorphicOrderTest.Sample.class));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void directSubtypeKeepsUnknownPropertyErrors(Input input) {
        for (String json : List.of("{\"kind\":\"sample\",\"unknown\":1}",
                                   "{\"unknown\":1,\"kind\":\"sample\"}")) {
            JsonException exception = assertThrows(JsonException.class,
                                                  () -> binding.deserialize(input.parser(json),
                                                                            PolymorphicOrderTest.Sample.class));
            assertThat(exception.getMessage(), containsString("Unknown properties"));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void recognizesMetadataWithoutDataProperties(Input input) {
        assertThat(binding.deserialize(input.parser("{\"kind\":\"empty\"}"), Empty.class), is(new Empty()));
        assertThat(binding.deserialize(input.parser("{}"), Empty.class), is(new Empty()));
        assertThat(binding.deserialize(input.parser("{\"kind\":\"other\"}"), Empty.class), is(new Empty()));
        assertThrows(JsonException.class,
                     () -> binding.deserialize(input.parser("{\"kind\":\"empty\",\"unknown\":1}"), Empty.class));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void acceptsEveryRegisteredAliasAndPreservesSerializationAlias(Input input) {
        Aliased expected = new Aliased("example");
        for (String alias : List.of("first", "second")) {
            String json = "{\"value\":\"example\",\"kind\":\"" + alias + "\"}";
            assertThat(binding.deserialize(input.parser(json), Aliased.class), is(expected));
            assertThat(binding.deserialize(input.parser(json), AliasBase.class), is(expected));
        }
        assertThat(binding.serialize(expected), is("{\"kind\":\"first\",\"value\":\"example\"}"));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void ignoresKnownMetadataWhenUnknownPropertiesAreAllowed(Input input) {
        assertThat(binding.deserialize(input.parser("{\"unknown\":1,\"kind\":\"first\",\"value\":\"example\"}"),
                                       Aliased.class), is(new Aliased("example")));
        for (String alias : List.of("\"other\"", "null", "42", "true", "{}", "[]")) {
            String json = "{\"unknown\":1,\"kind\":" + alias + ",\"value\":\"example\"}";
            assertThat(binding.deserialize(input.parser(json), Aliased.class), is(new Aliased("example")));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void explicitSubtypeIgnoresInheritedMetadataAtEveryLevel(Input input) {
        for (String json : List.of("{\"nestedData\":\"example\",\"@type2\":\"b1\",\"@type\":\"b\"}",
                                   "{\"@type\":\"b\",\"nestedData\":\"example\",\"@type2\":\"b1\"}",
                                   "{\"nestedData\":\"example\"}")) {
            PolymorphismSupportTest.ConcreteB1 value = binding.deserialize(input.parser(json),
                                                                          PolymorphismSupportTest.ConcreteB1.class);
            assertThat(value.nestedData(), is("example"));
        }
        for (String json : List.of("{\"nestedData\":\"example\",\"@type2\":\"b1\",\"@type\":\"a1\"}",
                                   "{\"@type\":\"b\",\"nestedData\":\"example\",\"@type2\":\"b2\"}")) {
            PolymorphismSupportTest.ConcreteB1 value = binding.deserialize(input.parser(json),
                                                                          PolymorphismSupportTest.ConcreteB1.class);
            assertThat(value.nestedData(), is("example"));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void explicitSubtypeIgnoresAnAliasForAnotherSubtype(Input input) {
        for (String alias : List.of("costarring", "liquid")) {
            PolymorphismSupportTest.HashCollisionType1 value = binding.deserialize(
                    input.parser("{\"value\":\"example\",\"@type\":\"" + alias + "\"}"),
                    PolymorphismSupportTest.HashCollisionType1.class);
            assertThat(value.value(), is("example"));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void keepsMetadataSeparateFromNormalizedPropertyNames(Input input) {
        for (String json : List.of("{\"týpe-key\":\"sämple\",\"type_key\":\"example\"}",
                                   "{\"type_key\":\"example\",\"t\\u00fdpe-key\":\"s\\u00e4mple\"}")) {
            assertThat(binding.deserialize(input.parser(json), Normalized.class), is(new Normalized("example")));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void explicitPropertyKeepsItsRequiredAndCustomDeserializerContracts(Input input) {
        assertThat(binding.deserialize(input.parser("{\"kind\":\"data\"}"), ExplicitProperty.class),
                   is(new ExplicitProperty("data_deserialized")));
        JsonException exception = assertThrows(JsonException.class,
                                              () -> binding.deserialize(input.parser("{}"), ExplicitProperty.class));
        assertThat(exception.getMessage(), containsString("required"));
    }

    @Json.Entity
    @Json.Polymorphic(key = "kind")
    @Json.Subtype(value = Empty.class, alias = "empty")
    interface EmptyBase {
    }

    @Json.Entity
    @Json.Polymorphic(key = "kind")
    @Json.Subtype(value = Aliased.class, alias = "first")
    @Json.Subtype(value = Aliased.class, alias = "second")
    interface AliasBase {
    }

    @Json.Entity
    @Json.Polymorphic(key = "týpe-key")
    @Json.Subtype(value = Normalized.class, alias = "sämple")
    interface NormalizedBase {
    }

    @Json.Entity
    @Json.Polymorphic(key = "kind")
    @Json.Subtype(value = ExplicitProperty.class, alias = "metadata")
    interface ExplicitPropertyBase {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record Empty() implements EmptyBase {
    }

    @Json.Entity
    @Json.FailOnUnknown(false)
    record Aliased(String value) implements AliasBase {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record Normalized(@Json.Property("type_key") String value) implements NormalizedBase {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record ExplicitProperty(@Json.Property("kind")
                            @Json.Required
                            @Json.Deserializer(CustomConverterTest.StringConverter.class) String value)
            implements ExplicitPropertyBase {
    }
}
