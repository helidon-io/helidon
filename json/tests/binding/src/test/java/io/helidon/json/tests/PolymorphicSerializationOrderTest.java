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

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
class PolymorphicSerializationOrderTest {

    private final JsonBinding binding;

    PolymorphicSerializationOrderTest(JsonBinding binding) {
        this.binding = binding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void preservesLeafTypeWhenSuperclassIsRegisteredFirst(BindingMethod method) {
        String json = method.serialize(binding, brownCat(), Animal.class);

        assertThat(json, containsString("\"@type\":\"brown\""));
        assertThat(json, containsString("\"shade\":\"chocolate\""));
        assertBrownCat(method.deserialize(binding, json, Animal.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void preservesLeafTypeWhenLeafIsRegisteredFirst(BindingMethod method) {
        String json = method.serialize(binding, brownCat(), LeafFirst.class);

        assertThat(json, containsString("\"@type\":\"brown\""));
        assertThat(json, containsString("\"shade\":\"chocolate\""));
        assertBrownCat(method.deserialize(binding, json, LeafFirst.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void retainsAliasesForTheSameSubtype(BindingMethod method) {
        for (String alias : List.of("brown", "chocolate")) {
            String json = "{\"@type\":\"" + alias + "\",\"name\":\"Milo\",\"lives\":9,\"shade\":\"chocolate\"}";
            assertBrownCat(method.deserialize(binding, json, Animal.class));
        }
        assertThat(method.serialize(binding, brownCat(), Animal.class), containsString("\"@type\":\"brown\""));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void preservesAnIntermediateInstance(BindingMethod method) {
        Cat expected = new Cat();
        expected.name = "Milo";
        expected.lives = 9;
        String json = method.serialize(binding, expected, Animal.class);

        assertThat(json, containsString("\"@type\":\"cat\""));
        Animal actual = method.deserialize(binding, json, Animal.class);
        assertThat(actual.getClass(), is(Cat.class));
        assertThat(actual.name, is("Milo"));
        assertThat(((Cat) actual).lives, is(9));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void ordersThreeOverlappingTypesWithAnUnrelatedTypeBetweenThem(BindingMethod method) {
        DeepDescendant expected = new DeepDescendant();
        expected.first = "one";
        expected.second = "two";
        expected.third = "three";
        String json = method.serialize(binding, expected, Interleaved.class);

        assertThat(json, containsString("\"@type\":\"deep\""));
        Interleaved actual = method.deserialize(binding, json, Interleaved.class);
        assertThat(actual, instanceOf(DeepDescendant.class));
        DeepDescendant result = (DeepDescendant) actual;
        assertThat(result.first, is("one"));
        assertThat(result.second, is("two"));
        assertThat(result.third, is("three"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void retainsUnrelatedSubtypes(BindingMethod method) {
        Unrelated expected = new Unrelated();
        expected.value = "other";
        String json = method.serialize(binding, expected, Interleaved.class);

        assertThat(json, containsString("\"@type\":\"unrelated\""));
        Interleaved actual = method.deserialize(binding, json, Interleaved.class);
        assertThat(actual, instanceOf(Unrelated.class));
        assertThat(((Unrelated) actual).value, is("other"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void recognizesAParameterizedSuperclassWhenOrderingSubtypes(BindingMethod method) {
        GenericChild expected = new GenericChild();
        expected.value = "generic";
        expected.number = 42;
        String json = method.serialize(binding, expected, GenericRoot.class);

        assertThat(json, containsString("\"@type\":\"child\""));
        GenericRoot actual = method.deserialize(binding, json, GenericRoot.class);
        assertThat(actual, instanceOf(GenericChild.class));
        assertThat(((GenericChild) actual).value, is("generic"));
        assertThat(((GenericChild) actual).number, is(42));
    }

    private static BrownCat brownCat() {
        BrownCat value = new BrownCat();
        value.name = "Milo";
        value.lives = 9;
        value.shade = "chocolate";
        return value;
    }

    private static void assertBrownCat(Object actual) {
        assertThat(actual, instanceOf(BrownCat.class));
        BrownCat value = (BrownCat) actual;
        assertThat(value.name, is("Milo"));
        assertThat(value.lives, is(9));
        assertThat(value.shade, is("chocolate"));
    }

    @Json.Entity
    @Json.Subtype(value = BrownCat.class, alias = "brown")
    @Json.Subtype(value = Cat.class, alias = "cat")
    interface LeafFirst {
    }

    @Json.Entity
    @Json.Subtype(value = Ancestor.class, alias = "ancestor")
    @Json.Subtype(value = Unrelated.class, alias = "unrelated")
    @Json.Subtype(value = Descendant.class, alias = "descendant")
    @Json.Subtype(value = DeepDescendant.class, alias = "deep")
    interface Interleaved {
    }

    @Json.Entity
    @Json.Subtype(value = GenericParent.class, alias = "parent")
    @Json.Subtype(value = GenericChild.class, alias = "child")
    interface GenericRoot {
    }

    @Json.Entity
    @Json.Subtype(value = Cat.class, alias = "cat")
    @Json.Subtype(value = BrownCat.class, alias = "brown")
    @Json.Subtype(value = BrownCat.class, alias = "chocolate")
    static class Animal {
        public String name;
    }

    @Json.Entity
    static class Cat extends Animal implements LeafFirst {
        public int lives;
    }

    @Json.Entity
    static class BrownCat extends Cat {
        public String shade;
    }

    @Json.Entity
    static class Ancestor implements Interleaved {
        public String first;
    }

    @Json.Entity
    static class Descendant extends Ancestor {
        public String second;
    }

    @Json.Entity
    static class DeepDescendant extends Descendant {
        public String third;
    }

    @Json.Entity
    static class Unrelated implements Interleaved {
        public String value;
    }

    @Json.Entity
    static class GenericParent<T> implements GenericRoot {
        public T value;
    }

    @Json.Entity
    static class GenericChild extends GenericParent<String> {
        public int number;
    }
}
