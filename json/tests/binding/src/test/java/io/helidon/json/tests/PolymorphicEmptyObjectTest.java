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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
class PolymorphicEmptyObjectTest {

    private final JsonBinding binding;

    PolymorphicEmptyObjectTest(JsonBinding binding) {
        this.binding = binding;
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void emptyObjectCreatesTheConcretePolymorphicType(Input input) {
        Animal animal = binding.deserialize(input.parser("{}"), Animal.class);
        assertThat(animal.getClass(), is(Animal.class));
        assertThat(animal.name, nullValue());

        Cat cat = binding.deserialize(input.parser("{}"), Cat.class);
        assertThat(cat.getClass(), is(Cat.class));
        assertThat(cat.name, nullValue());
        assertThat(cat.lives, is(0));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void selectedIntermediateTypeAcceptsAnEmptyRemainder(Input input) {
        Animal animal = binding.deserialize(input.parser("{\"animalType\":\"cat\"}"), Animal.class);

        assertThat(animal.getClass(), is(Cat.class));
        assertThat(animal.name, nullValue());
        assertThat(((Cat) animal).lives, is(0));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void explicitDefaultTakesPrecedenceOverTheConcreteType(Input input) {
        DefaultParent value = binding.deserialize(input.parser("{}"), DefaultParent.class);

        assertThat(value.getClass(), is(DefaultChild.class));
        assertThat(value.name, nullValue());
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void abstractTypeWithoutDefaultStillRequiresAnAlias(Input input) {
        JsonException exception = assertThrows(JsonException.class,
                                              () -> binding.deserialize(input.parser("{}"),
                                                                        PolymorphicOrderTest.CollisionBase.class));

        assertThat(exception.getMessage(), containsString("Missing property containing alias"));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void concreteFallbackStillChecksRequiredProperties(Input input) {
        JsonException exception = assertThrows(JsonException.class,
                                              () -> binding.deserialize(input.parser("{}"), RequiredParent.class));

        assertThat(exception.getMessage(), containsString("required"));
        assertThat(exception.getMessage(), containsString("name"));
        RequiredParent value = binding.deserialize(input.parser("{\"name\":\"present\"}"), RequiredParent.class);
        assertThat(value.getClass(), is(RequiredParent.class));
        assertThat(value.name, is("present"));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void consecutiveEmptyObjectsPreserveNestedArrayAndFollowingProperty(Input input) {
        String json = "{\"animals\":[{},"
                + "{\"animalType\":\"cat\"},"
                + "{\"animalType\":\"cat\",\"catType\":\"brown\"},"
                + "{\"name\":\"last\",\"animalType\":\"cat\",\"lives\":9}],\"tail\":\"done\"}";

        Zoo zoo = binding.deserialize(input.parser(json), Zoo.class);

        assertThat(zoo.animals().stream().map(Object::getClass).toList(),
                   contains(Animal.class, Cat.class, BrownCat.class, Cat.class));
        assertThat(zoo.animals().get(0).name, nullValue());
        assertThat(zoo.animals().get(1).name, nullValue());
        assertThat(((BrownCat) zoo.animals().get(2)).shade, nullValue());
        assertThat(zoo.animals().get(3).name, is("last"));
        assertThat(((Cat) zoo.animals().get(3)).lives, is(9));
        assertThat(zoo.tail(), is("done"));
    }

    @Json.Entity
    @Json.Polymorphic(key = "animalType")
    @Json.Subtype(value = Cat.class, alias = "cat")
    static class Animal {
        public String name;
    }

    @Json.Entity
    @Json.Polymorphic(key = "catType")
    @Json.Subtype(value = BrownCat.class, alias = "brown")
    static class Cat extends Animal {
        public int lives;
    }

    @Json.Entity
    static class BrownCat extends Cat {
        public String shade;
    }

    @Json.Entity
    @Json.Polymorphic(key = "defaultType", value = DefaultChild.class)
    @Json.Subtype(value = ExplicitChild.class, alias = "explicit")
    static class DefaultParent {
        public String name;
    }

    @Json.Entity
    static class DefaultChild extends DefaultParent {
    }

    @Json.Entity
    static class ExplicitChild extends DefaultParent {
    }

    @Json.Entity
    @Json.Subtype(value = RequiredChild.class, alias = "child")
    static class RequiredParent {
        @Json.Required
        public String name;
    }

    @Json.Entity
    static class RequiredChild extends RequiredParent {
    }

    @Json.Entity
    record Zoo(List<Animal> animals, String tail) {
    }
}
