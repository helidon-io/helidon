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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.helidon.common.GenericType;
import io.helidon.json.JsonException;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.binding.JsonDeserializer;
import io.helidon.json.smile.SmileGenerator;
import io.helidon.json.smile.SmileParser;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
class PolymorphicOrderTest {

    private final JsonBinding binding;

    PolymorphicOrderTest(JsonBinding binding) {
        this.binding = binding;
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void preservesPropertiesBeforeAndAfterDiscriminator(Input input) {
        String name = "\"name\":\"example\"";
        String count = "\"count\":7";
        String kind = "\"kind\":\"sample\"";
        for (String properties : List.of(kind + "," + name + "," + count,
                                         name + "," + kind + "," + count,
                                         name + "," + count + "," + kind)) {
            Base value = binding.deserialize(input.parser("{" + properties + "}"), Base.class);
            assertThat(value, is(new Sample("example", 7, null)));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void preservesLargeNestedPrefixAndFollowingArrayElement(Input input) {
        String name = "x".repeat(32_768);
        String first = "{\"name\":\"" + name + "\",\"nested\":{\"kind\":\"unrelated\",\"numbers\":[1,2,3]},"
                + "\"kind\":\"sample\",\"count\":9}";
        String second = "{\"count\":4,\"kind\":\"sample\",\"name\":\"next\"}";
        List<Base> values = binding.deserialize(input.parser("[" + first + "," + second + "]"),
                                               new GenericType<List<Base>>() { });
        assertThat(values, is(List.of(new Sample(name, 9, new Nested("unrelated", List.of(1, 2, 3))),
                                     new Sample("next", 4, null))));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void defaultIsUsedOnlyWhenDiscriminatorIsAbsent(Input input) {
        assertThat(binding.deserialize(input.parser("{\"name\":\"default\"}"), Base.class),
                   is(new DefaultValue("default")));
        for (String alias : List.of("null", "42", "true", "{}", "[]", "\"unknown\"")) {
            assertThrows(JsonException.class,
                         () -> binding.deserialize(input.parser("{\"name\":\"example\",\"kind\":" + alias + "}"),
                                                   Base.class));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void strictUnknownPropertiesRemainErrors(Input input) {
        assertThrows(JsonException.class,
                     () -> binding.deserialize(input.parser("{\"unknown\":1,\"kind\":\"sample\"}"), Base.class));
        assertThrows(JsonException.class,
                     () -> binding.deserialize(input.parser("{\"name\":\"x\",\"kind\":\"sample\",\"unknown\":1}"),
                                               Base.class));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void supportsDefaultOnlyPolymorphism(Input input) {
        assertThat(binding.deserialize(input.parser("{\"name\":\"default\"}"), DefaultOnly.class),
                   is(new DefaultOnlyValue("default")));
        assertThat(binding.deserialize(input.parser("{}"), DefaultOnly.class), is(new DefaultOnlyValue(null)));
        for (String json : List.of("{\"@type\":\"unknown\"}", "{\"name\":\"x\",\"@type\":\"unknown\"}")) {
            assertThrows(JsonException.class, () -> binding.deserialize(input.parser(json), DefaultOnly.class));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void discriminatorMetadataDoesNotCollideWithPropertyNames(Input input) {
        String properties = "\"polymorphic\":\"first\"";
        for (String json : List.of("{\"@type\":\"child\"," + properties + "}",
                                   "{" + properties + ",\"@type\":\"child\"}")) {
            PropertyNameParent value = binding.deserialize(input.parser(json), PropertyNameParent.class);
            assertThat(value, instanceOf(PropertyNameChild.class));
            assertThat(value.polymorphic, is("first"));
        }
        properties = "\"polymorphicKey\":\"second\",\"polymorphicKey_1\":\"third\"";
        for (String json : List.of("{\"@type\":\"child\"," + properties + "}",
                                   "{" + properties + ",\"@type\":\"child\"}")) {
            KeyPropertyParent value = binding.deserialize(input.parser(json), KeyPropertyParent.class);
            assertThat(value, instanceOf(KeyPropertyChild.class));
            assertThat(value.polymorphicKey, is("second"));
            assertThat(value.polymorphicKey_1, is("third"));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void matchesEscapedKeysAndResolvesKeyHashCollisions(Input input) {
        assertThat(binding.deserialize(input.parser("{\"name\":\"x\",\"k\\u0069nd\":\"sample\"}"), Base.class),
                   is(new Sample("x", 0, null)));
        assertThat(binding.deserialize(input.parser("{\"liquid\":0,\"name\":\"x\",\"costarring\":\"chosen\"}"),
                                       CollisionBase.class), is(new CollisionValue("x")));
        JsonException missing = assertThrows(JsonException.class,
                                             () -> binding.deserialize(input.parser("{\"liquid\":\"chosen\"}"),
                                                                       CollisionBase.class));
        assertThat(missing.getMessage(), containsString("Missing property containing alias"));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void resolvesAliasCollisionsAfterPrefix(Input input) {
        List<String> aliases = List.of("costarring", "liquid", "declinate", "macallums");
        List<Class<?>> types = List.of(PolymorphismSupportTest.HashCollisionType1.class,
                                       PolymorphismSupportTest.HashCollisionType2.class,
                                       PolymorphismSupportTest.HashCollisionType3.class,
                                       PolymorphismSupportTest.HashCollisionType4.class);
        for (int i = 0; i < aliases.size(); i++) {
            String alias = aliases.get(i);
            PolymorphismSupportTest.HashCollisionParent value = binding.deserialize(
                    input.parser("{\"value\":\"" + alias + "\",\"@type\":\"" + alias + "\"}"),
                    PolymorphismSupportTest.HashCollisionParent.class);
            assertThat(value.value(), is(alias));
            assertThat(value, instanceOf(types.get(i)));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void supportsManyAliasesAndConcretePolymorphicParents(Input input) {
        PolymorphismSupportTest.ManyTypes many = binding.deserialize(
                input.parser("{\"value\":\"tenth\",\"@type\":\"type10\"}"), PolymorphismSupportTest.ManyTypes.class);
        assertThat(many, instanceOf(PolymorphismSupportTest.ManyType10.class));
        assertThat(((PolymorphismSupportTest.ManyType10) many).value(), is("tenth"));

        PolymorphismSupportTest.ParentClass parent = binding.deserialize(
                input.parser("{\"valueParent\":\"parent\",\"valueChild\":\"child\",\"@type\":\"childclass\"}"),
                PolymorphismSupportTest.ParentClass.class);
        assertThat(parent, instanceOf(PolymorphismSupportTest.ChildClass.class));
        assertThat(parent.valueParent(), is("parent"));
        assertThat(((PolymorphismSupportTest.ChildClass) parent).valueChild(), is("child"));
    }

    @ParameterizedTest
    @EnumSource(value = Input.class, names = {"ARRAY", "STREAM", "SHORT_READ_STREAM"})
    void rejectsMalformedObjectsWithLateDiscriminators(Input input) {
        for (String json : List.of("{\"name\" \"x\",\"kind\":\"sample\"}",
                                   "{\"name\":\"x\" \"kind\":\"sample\"}",
                                   "{\"name\":\"x\",\"kind\" \"sample\"}",
                                   "{\"name\":\"x\",\"kind\":\"sample\",}",
                                   "{\"name\":\"x\",\"kind\":\"sample\"",
                                   "{\"name\":\"x\",\"kind\":\"sample\" \"count\":7}",
                                   "{\"count\":1e,\"kind\":\"sample\"}",
                                   "{\"count\":[1,],\"kind\":\"sample\"}")) {
            assertThrows(JsonException.class, () -> binding.deserialize(input.parser(json), Base.class), json);
        }
    }

    @ParameterizedTest
    @EnumSource(value = Input.class, names = {"SMILE_ARRAY", "SMILE_STREAM"})
    void preservesSmileBinaryPrefix(Input input) {
        byte[] expected = new byte[8192];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) i;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(output)) {
            generator.writeObjectStart()
                    .writeBinary("data", expected)
                    .write("kind", "binary")
                    .write("tail", 9)
                    .writeObjectEnd();
        }
        byte[] bytes = output.toByteArray();
        JsonParser parser = input == Input.SMILE_ARRAY ? SmileParser.create(bytes)
                : SmileParser.create(new ByteArrayInputStream(bytes), 7);
        BinaryBase result = binding.deserialize(parser, BinaryBase.class);
        assertThat(result, instanceOf(BinaryValue.class));
        assertThat(((BinaryValue) result).data(), is(expected));
        assertThat(((BinaryValue) result).tail(), is(9));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void resolvesMultipleLevelsInEitherDiscriminatorOrder(Input input) {
        for (String json : List.of("{\"nestedData\":\"x\",\"@type2\":\"b1\",\"@type\":\"b\"}",
                                   "{\"@type2\":\"b1\",\"@type\":\"b\",\"nestedData\":\"x\"}",
                                   "{\"@type\":\"b\",\"nestedData\":\"x\",\"@type2\":\"b1\"}")) {
            PolymorphismSupportTest.InterfaceA value = binding.deserialize(input.parser(json),
                                                                          PolymorphismSupportTest.InterfaceA.class);
            assertThat(value, instanceOf(PolymorphismSupportTest.ConcreteB1.class));
            assertThat(((PolymorphismSupportTest.ConcreteB1) value).nestedData(), is("x"));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void customDeserializerReceivesCompleteObjectWithoutDiscriminator(Input input) {
        JsonBinding custom = JsonBinding.builder()
                .addDeserializer(new JsonDeserializer<Sample>() {
                    @Override
                    public Sample deserialize(JsonParser parser) {
                        JsonObject value = parser.readJsonObject();
                        assertThat(value.size(), is(2));
                        return new Sample(value.stringValue("name").orElseThrow(), value.intValue("count").orElseThrow(), null);
                    }

                    @Override
                    public GenericType<Sample> type() {
                        return GenericType.create(Sample.class);
                    }
                })
                .build();
        for (String json : List.of("{\"kind\":\"sample\",\"name\":\"x\",\"count\":2}",
                                   "{\"name\":\"x\",\"kind\":\"sample\",\"count\":2}",
                                   "{\"name\":\"x\",\"count\":2,\"kind\":\"sample\"}")) {
            assertThat(custom.deserialize(input.parser(json), Base.class), is(new Sample("x", 2, null)));
        }
    }

    enum Input {
        ARRAY,
        STREAM,
        SHORT_READ_STREAM,
        VALUE,
        SMILE_ARRAY,
        SMILE_STREAM;

        JsonParser parser(String json) {
            return switch (this) {
            case ARRAY -> JsonParser.create(json);
            case STREAM -> JsonParser.create(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), 7);
            case SHORT_READ_STREAM -> JsonParser.create(new ShortReadStream(json.getBytes(StandardCharsets.UTF_8)), 7);
            case VALUE -> JsonParser.create(JsonParser.create(json).readJsonValue());
            case SMILE_ARRAY, SMILE_STREAM -> {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (JsonGenerator generator = SmileGenerator.create(output)) {
                    generator.write(JsonParser.create(json).readJsonValue());
                }
                byte[] bytes = output.toByteArray();
                yield this == SMILE_ARRAY ? SmileParser.create(bytes)
                        : SmileParser.create(new ByteArrayInputStream(bytes), 7);
            }
            };
        }
    }

    @Json.Entity
    @Json.Polymorphic(key = "kind", value = DefaultValue.class)
    @Json.Subtype(value = Sample.class, alias = "sample")
    interface Base {
    }

    @Json.Entity
    @Json.Polymorphic(key = "costarring")
    @Json.Subtype(value = CollisionValue.class, alias = "chosen")
    interface CollisionBase {
    }

    @Json.Entity
    @Json.Polymorphic(key = "kind")
    @Json.Subtype(value = BinaryValue.class, alias = "binary")
    interface BinaryBase {
    }

    @Json.Entity
    @Json.Polymorphic(DefaultOnlyValue.class)
    interface DefaultOnly {
    }

    static final class ShortReadStream extends ByteArrayInputStream {
        ShortReadStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            return super.read(target, offset, Math.min(length, 1));
        }
    }

    @Json.Entity
    @Json.Subtype(value = PropertyNameChild.class, alias = "child")
    static class PropertyNameParent {
        public String polymorphic;
    }

    @Json.Entity
    static class PropertyNameChild extends PropertyNameParent {
    }

    @Json.Entity
    @Json.Subtype(value = KeyPropertyChild.class, alias = "child")
    static class KeyPropertyParent {
        public String polymorphicKey;
        public String polymorphicKey_1;
    }

    @Json.Entity
    static class KeyPropertyChild extends KeyPropertyParent {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record Sample(String name, int count, Nested nested) implements Base {
    }

    @Json.Entity
    record Nested(String kind, List<Integer> numbers) {
    }

    @Json.Entity
    record DefaultValue(String name) implements Base {
    }

    @Json.Entity
    record CollisionValue(String name) implements CollisionBase {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record BinaryValue(byte[] data, int tail) implements BinaryBase {
    }

    @Json.Entity
    record DefaultOnlyValue(String name) implements DefaultOnly {
    }
}
