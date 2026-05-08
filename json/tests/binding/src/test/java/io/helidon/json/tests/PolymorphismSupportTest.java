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

import io.helidon.common.GenericType;
import io.helidon.json.JsonException;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.binding.JsonBindingException;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class PolymorphismSupportTest {

    private final JsonBinding jsonBinding;

    PolymorphismSupportTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphism(BindingMethod bindingMethod) {
        Dog dog = new Dog();
        dog.name("Alik");

        String json = bindingMethod.serialize(jsonBinding, dog, Animal.class);
        assertThat(json, is("{\"@type\":\"dog\",\"name\":\"Alik\"}"));

        Animal deserialize = bindingMethod.deserialize(jsonBinding, json, Animal.class);
        assertThat(deserialize, instanceOf(Dog.class));
        assertThat(((Dog) deserialize).name(), is("Alik"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testCyclicSerialization(BindingMethod bindingMethod) {
        Fish fish = new Fish();
        fish.name("Nemo");

        assertThrows(JsonBindingException.class, () -> bindingMethod.serialize(jsonBinding, fish));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismEmptyJson(BindingMethod bindingMethod) {
        Animal deserialize = bindingMethod.deserialize(jsonBinding, "{}", Animal.class);
        assertThat(deserialize, instanceOf(Bird.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismMissingTypeNotFirstPropertyFallsBackToDefault(BindingMethod bindingMethod) {
        // Current generated poly converter only considers @type if it is the first property.
        Animal deserialize = bindingMethod.deserialize(jsonBinding, "{\"name\":\"X\"}", Animal.class);
        assertThat(deserialize, instanceOf(Bird.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismUnknownAliasFallsBackToDefault(BindingMethod bindingMethod) {
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "{\"@type\":\"unicorn\"}", Animal.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismNullAliasFallsBackToDefault(BindingMethod bindingMethod) {
        // The poly converter expects the discriminator value to be a JSON string.
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "{\"@type\":null}", Animal.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismAliasPresentButNotAStringThrows(BindingMethod bindingMethod) {
        // The poly converter expects the discriminator value to be a JSON string.
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "{\"@type\":123}", Animal.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismTypeValueCaseSensitivityFallsBackToDefault(BindingMethod bindingMethod) {
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "{\"@type\":\"DOG\",\"name\":\"Alik\"}", Animal.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testSerializeAsPolymorphicIncludesDiscriminatorForKnownSubtype(BindingMethod bindingMethod) {
        Cat cat = new Cat();
        cat.color("black");
        String json = bindingMethod.serialize(jsonBinding, cat, Animal.class);
        assertThat(json, is("{\"@type\":\"cat\",\"color\":\"black\"}"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testSerializeNonListedSubtypeUsesDefaultSerializerWithoutDiscriminator(BindingMethod bindingMethod) {
        Bird bird = new Bird();
        String json = bindingMethod.serialize(jsonBinding, bird, Animal.class);

        // Bird is not listed in @Json.TypeInfo 'value', so poly converter falls back to default serializer
        // which (for Bird) does not write @type.
        assertThat(json, is("{}"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismRoundTripDogAsAnimal(BindingMethod bindingMethod) {
        Dog dog = new Dog();
        dog.name("Alik");

        String json = bindingMethod.serialize(jsonBinding, dog, Animal.class);
        Animal deserialize = bindingMethod.deserialize(jsonBinding, json, Animal.class);

        assertThat(deserialize, instanceOf(Dog.class));
        assertThat(((Dog) deserialize).name(), is("Alik"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismListOfAnimals(BindingMethod bindingMethod) {
        String json = "[" +
                "{\"@type\":\"dog\",\"name\":\"Alik\"}," +
                "{\"@type\":\"cat\",\"color\":\"black\"}," +
                "{}" +
                "]";

        List<Animal> animals = bindingMethod.deserialize(jsonBinding, json, new GenericType<List<Animal>>() { });
        assertThat(animals.get(0), instanceOf(Dog.class));
        assertThat(((Dog) animals.get(0)).name(), is("Alik"));
        assertThat(animals.get(1), instanceOf(Cat.class));
        assertThat(((Cat) animals.get(1)).color(), is("black"));
        assertThat(animals.get(2), instanceOf(Bird.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testNestedPolymorphicProperty(BindingMethod bindingMethod) {
        String json = "{\"animal\":{\"@type\":\"dog\",\"name\":\"Alik\"}}";

        Zoo zoo = bindingMethod.deserialize(jsonBinding, json, Zoo.class);
        assertThat(zoo.animal(), instanceOf(Dog.class));
        assertThat(((Dog) zoo.animal()).name(), is("Alik"));

        // Also verify serialize uses the poly converter for the nested property
        String serialized = bindingMethod.serialize(jsonBinding, zoo);
        assertThat(serialized, containsString("\"animal\":{\"@type\":\"dog\""));
        assertThat(serialized, containsString("\"name\":\"Alik\""));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testNoDefaultImplementationMissingTypeThrows(BindingMethod bindingMethod) {
        // With no defaultSubtype, missing discriminator should be an error.
        assertThrows(Exception.class, () -> bindingMethod.deserialize(jsonBinding, "{}", Vehicle.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testNoDefaultImplementationUnknownAliasThrows(BindingMethod bindingMethod) {
        assertThrows(Exception.class, () -> bindingMethod.deserialize(jsonBinding, "{\"@type\":\"boat\"}", Vehicle.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testNoDefaultImplementationKnownAliasWorks(BindingMethod bindingMethod) {
        Vehicle deserialize = bindingMethod.deserialize(jsonBinding, "{\"@type\":\"car\",\"make\":\"Volvo\"}", Vehicle.class);
        assertThat(deserialize, instanceOf(Car.class));
        assertThat(((Car) deserialize).make(), is("Volvo"));

        // serialization through base type uses discriminator
        String json = bindingMethod.serialize(jsonBinding, deserialize, Vehicle.class);
        assertThat(json, containsString("\"@type\":\"car\""));
        assertThat(json, containsString("\"make\":\"Volvo\""));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testCustomTypeInfoKeyIsUsedForDiscriminator(BindingMethod bindingMethod) {
        String json = "{\"kind\":\"circle\",\"radius\":2}";
        Shape deserialize = bindingMethod.deserialize(jsonBinding, json, Shape.class);
        assertThat(deserialize, instanceOf(Circle.class));
        assertThat(((Circle) deserialize).radius(), is(2));

        String serialized = bindingMethod.serialize(jsonBinding, deserialize, Shape.class);
        assertThat(serialized, containsString("\"kind\":\"circle\""));
        assertThat(serialized, not(containsString("\"@type\":")));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismSwitchStructureType1(BindingMethod bindingMethod) {
        ManyType1 obj = new ManyType1();
        obj.value("test1");

        String json = bindingMethod.serialize(jsonBinding, obj, ManyTypes.class);
        assertThat(json, is("{\"@type\":\"type1\",\"value\":\"test1\"}"));

        ManyTypes deserialize = bindingMethod.deserialize(jsonBinding, json, ManyTypes.class);
        assertThat(deserialize, instanceOf(ManyType1.class));
        assertThat(((ManyType1) deserialize).value(), is("test1"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismSwitchStructureType10(BindingMethod bindingMethod) {
        ManyType10 obj = new ManyType10();
        obj.value("test10");

        String json = bindingMethod.serialize(jsonBinding, obj, ManyTypes.class);
        assertThat(json, is("{\"@type\":\"type10\",\"value\":\"test10\"}"));

        ManyTypes deserialize = bindingMethod.deserialize(jsonBinding, json, ManyTypes.class);
        assertThat(deserialize, instanceOf(ManyType10.class));
        assertThat(((ManyType10) deserialize).value(), is("test10"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismSwitchStructureEmptyJsonFallsToDefault(BindingMethod bindingMethod) {
        ManyTypes deserialize = bindingMethod.deserialize(jsonBinding, "{}", ManyTypes.class);
        assertThat(deserialize, instanceOf(ManyTypeDefault.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismSwitchStructureUnknownAliasThrows(BindingMethod bindingMethod) {
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "{\"@type\":\"unknown\"}", ManyTypes.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismSwitchStructureMissingTypeNotFirstPropertyFallsToDefault(BindingMethod bindingMethod) {
        ManyTypes deserialize = bindingMethod.deserialize(jsonBinding, "{\"value\":\"X\"}", ManyTypes.class);
        assertThat(deserialize, instanceOf(ManyTypeDefault.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismSwitchStructureCaseSensitivity(BindingMethod bindingMethod) {
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "{\"@type\":\"TYPE1\",\"value\":\"test\"}", ManyTypes.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismSwitchStructureList(BindingMethod bindingMethod) {
        String json = "[" +
                "{\"@type\":\"type1\",\"value\":\"val1\"}," +
                "{\"@type\":\"type5\",\"value\":\"val5\"}," +
                "{\"@type\":\"type10\",\"value\":\"val10\"}," +
                "{}" +
                "]";

        List<ManyTypes> list = bindingMethod.deserialize(jsonBinding, json, new GenericType<List<ManyTypes>>() { });
        assertThat(list.get(0), instanceOf(ManyType1.class));
        assertThat(((ManyType1) list.get(0)).value(), is("val1"));
        assertThat(list.get(1), instanceOf(ManyType5.class));
        assertThat(((ManyType5) list.get(1)).value(), is("val5"));
        assertThat(list.get(2), instanceOf(ManyType10.class));
        assertThat(((ManyType10) list.get(2)).value(), is("val10"));
        assertThat(list.get(3), instanceOf(ManyTypeDefault.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismSwitchStructureNested(BindingMethod bindingMethod) {
        String json = "{\"manyType\":{\"@type\":\"type2\",\"value\":\"nested\"}}";

        ManyTypesContainer container = bindingMethod.deserialize(jsonBinding, json, ManyTypesContainer.class);
        assertThat(container.manyType(), instanceOf(ManyType2.class));
        assertThat(((ManyType2) container.manyType()).value(), is("nested"));

        String serialized = bindingMethod.serialize(jsonBinding, container);
        assertThat(serialized, containsString("\"manyType\":{\"@type\":\"type2\""));
        assertThat(serialized, containsString("\"value\":\"nested\""));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismSwitchStructureNullAlias(BindingMethod bindingMethod) {
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "{\"@type\":null}", ManyTypes.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismSwitchStructureNumericAlias(BindingMethod bindingMethod) {
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "{\"@type\":42}", ManyTypes.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismSwitchStructureAllTypes(BindingMethod bindingMethod) {
        // Test serialization and deserialization for all subtypes
        ManyType1 t1 = new ManyType1();
        t1.value("1");
        ManyType2 t2 = new ManyType2();
        t2.value("2");
        ManyType3 t3 = new ManyType3();
        t3.value("3");
        ManyType4 t4 = new ManyType4();
        t4.value("4");
        ManyType5 t5 = new ManyType5();
        t5.value("5");
        ManyType6 t6 = new ManyType6();
        t6.value("6");
        ManyType7 t7 = new ManyType7();
        t7.value("7");
        ManyType8 t8 = new ManyType8();
        t8.value("8");
        ManyType9 t9 = new ManyType9();
        t9.value("9");
        ManyType10 t10 = new ManyType10();
        t10.value("10");

        ManyTypes[] types = {t1, t2, t3, t4, t5, t6, t7, t8, t9, t10};
        String[] aliases = {"type1", "type2", "type3", "type4", "type5", "type6", "type7", "type8", "type9", "type10"};

        for (int i = 0; i < types.length; i++) {
            String json = bindingMethod.serialize(jsonBinding, types[i], ManyTypes.class);
            assertThat(json, containsString("\"@type\":\"" + aliases[i] + "\""));
            assertThat(json, containsString("\"value\":\"" + (i + 1) + "\""));

            ManyTypes deserialized = bindingMethod.deserialize(jsonBinding, json, ManyTypes.class);
            assertThat(deserialized.getClass().getSimpleName(), is("ManyType" + (i + 1)));
        }
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testMultipleLevelPolymorphismConcreteA1(BindingMethod bindingMethod) {
        ConcreteA1 obj = new ConcreteA1();
        obj.data("testA1");

        String json = bindingMethod.serialize(jsonBinding, obj, InterfaceA.class);
        assertThat(json, is("{\"@type\":\"a1\",\"data\":\"testA1\"}"));

        InterfaceA deserialize = bindingMethod.deserialize(jsonBinding, json, InterfaceA.class);
        assertThat(deserialize, instanceOf(ConcreteA1.class));
        assertThat(((ConcreteA1) deserialize).data(), is("testA1"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testMultipleLevelPolymorphismConcreteB1AsA(BindingMethod bindingMethod) {
        ConcreteB1 obj = new ConcreteB1();
        obj.nestedData("testB1");

        // When serialized as InterfaceA, it should include both @type discriminators
        String json = bindingMethod.serialize(jsonBinding, obj, InterfaceA.class);
        assertThat(json, containsString("\"@type\":\"b\""));
        assertThat(json, containsString("\"@type2\":\"b1\""));
        assertThat(json, containsString("\"nestedData\":\"testB1\""));

        InterfaceA deserialize = bindingMethod.deserialize(jsonBinding, json, InterfaceA.class);
        assertThat(deserialize, instanceOf(ConcreteB1.class));
        assertThat(((ConcreteB1) deserialize).nestedData(), is("testB1"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testMultipleLevelPolymorphismConcreteB2AsB(BindingMethod bindingMethod) {
        ConcreteB2 obj = new ConcreteB2();
        obj.nestedData("testB2");

        String json = bindingMethod.serialize(jsonBinding, obj, InterfaceB.class);
        assertThat(json, is("{\"@type\":\"b\",\"@type2\":\"b2\",\"nestedData\":\"testB2\"}"));

        InterfaceA deserialize = bindingMethod.deserialize(jsonBinding, json, InterfaceA.class);
        assertThat(deserialize, instanceOf(ConcreteB2.class));
        assertThat(((ConcreteB2) deserialize).nestedData(), is("testB2"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testMultipleLevelPolymorphismEmptyJsonFallsToADefault(BindingMethod bindingMethod) {
        InterfaceA deserialize = bindingMethod.deserialize(jsonBinding, "{}", InterfaceA.class);
        assertThat(deserialize, instanceOf(ConcreteADefault.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testMultipleLevelPolymorphismEmptyJsonFallsToBDefault(BindingMethod bindingMethod) {
        InterfaceB deserialize = bindingMethod.deserialize(jsonBinding, "{}", InterfaceB.class);
        assertThat(deserialize, instanceOf(ConcreteBDefault.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testMultipleLevelPolymorphismUnknownAlias(BindingMethod bindingMethod) {
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "{\"@type\":\"unknown\"}", InterfaceA.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testMultipleLevelPolymorphismNestedInContainer(BindingMethod bindingMethod) {
        String json = "{\"interfaceA\":{\"@type\":\"b\",\"@type2\":\"b1\",\"nestedData\":\"nested\"}}";

        InterfaceAContainer container = bindingMethod.deserialize(jsonBinding, json, InterfaceAContainer.class);
        assertThat(container.interfaceA(), instanceOf(ConcreteB1.class));
        assertThat(((ConcreteB1) container.interfaceA()).nestedData(), is("nested"));

        // Test serialization
        ConcreteB1 b1 = new ConcreteB1();
        b1.nestedData("test");
        InterfaceAContainer container2 = new InterfaceAContainer();
        container2.interfaceA(b1);

        String serialized = bindingMethod.serialize(jsonBinding, container2);
        assertThat(serialized, containsString("\"interfaceA\":{\"@type\":\"b\""));
        assertThat(serialized, containsString("\"@type2\":\"b1\""));
        assertThat(serialized, containsString("\"nestedData\":\"test\""));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismWithArrays(BindingMethod bindingMethod) {
        String json = "[{\"@type\":\"dog\",\"name\":\"Rex\"},{\"@type\":\"cat\",\"color\":\"black\"}]";

        Animal[] animals = bindingMethod.deserialize(jsonBinding, json, Animal[].class);
        assertThat(animals[0], instanceOf(Dog.class));
        assertThat(((Dog) animals[0]).name(), is("Rex"));
        assertThat(animals[1], instanceOf(Cat.class));
        assertThat(((Cat) animals[1]).color(), is("black"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismWithMaps(BindingMethod bindingMethod) {
        String json = "{\"animal1\":{\"@type\":\"dog\",\"name\":\"Buddy\"},\"animal2\":{\"@type\":\"cat\",\"color\":\"white\"}}";

        java.util.Map<String, Animal> animalMap = bindingMethod.deserialize(jsonBinding, json,
                                                                            new GenericType<java.util.Map<String, Animal>>() { });
        assertThat(animalMap.get("animal1"), instanceOf(Dog.class));
        assertThat(((Dog) animalMap.get("animal1")).name(), is("Buddy"));
        assertThat(animalMap.get("animal2"), instanceOf(Cat.class));
        assertThat(((Cat) animalMap.get("animal2")).color(), is("white"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismMalformedJson(BindingMethod bindingMethod) {
        // Test various malformed JSON cases
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "{\"@type\":\"dog\"", Animal.class)); // Unclosed object
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "{\"@type\":", Animal.class)); // Incomplete
        assertThrows(JsonException.class,
                     () -> bindingMethod.deserialize(jsonBinding, "[{\"@type\":\"dog\"}", Animal.class)); // Unclosed array
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismDuplicateTypeKeys(BindingMethod bindingMethod) {
        // Test what happens with duplicate @type keys (should use first one)
        String json = "{\"@type\":\"dog\",\"@type\":\"cat\",\"name\":\"Test\"}";

        Animal deserialize = bindingMethod.deserialize(jsonBinding, json, Animal.class);
        assertThat(deserialize, instanceOf(Dog.class)); // Should use first @type
        assertThat(((Dog) deserialize).name(), is("Test"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismWithHashCollisionAliases(BindingMethod bindingMethod) {
        // Test polymorphism with aliases that have conflicting FNV1a hashes
        // "costarring" and "liquid" both have FNV1a hash 0x89C62E45
        HashCollisionParent obj1 = new HashCollisionType1();
        obj1.value("test1");

        String json1 = bindingMethod.serialize(jsonBinding, obj1, HashCollisionParent.class);
        assertThat(json1, is("{\"@type\":\"costarring\",\"value\":\"test1\"}"));

        HashCollisionParent deserialize1 = bindingMethod.deserialize(jsonBinding, json1, HashCollisionParent.class);
        assertThat(deserialize1, instanceOf(HashCollisionType1.class));
        assertThat(deserialize1.value(), is("test1"));

        // Test the second colliding alias
        HashCollisionParent obj2 = new HashCollisionType2();
        obj2.value("test2");

        String json2 = bindingMethod.serialize(jsonBinding, obj2, HashCollisionParent.class);
        assertThat(json2, is("{\"@type\":\"liquid\",\"value\":\"test2\"}"));

        HashCollisionParent deserialize2 = bindingMethod.deserialize(jsonBinding, json2, HashCollisionParent.class);
        assertThat(deserialize2, instanceOf(HashCollisionType2.class));
        assertThat(deserialize2.value(), is("test2"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testPolymorphismWithMultipleHashCollisions(BindingMethod bindingMethod) {
        // Test with multiple colliding aliases: "declinate" and "macallums" both have FNV1a hash 0x0BF8B80D
        HashCollisionParent obj1 = new HashCollisionType3();
        obj1.value("test3");

        String json1 = bindingMethod.serialize(jsonBinding, obj1, HashCollisionParent.class);
        assertThat(json1, is("{\"@type\":\"declinate\",\"value\":\"test3\"}"));

        HashCollisionParent deserialize1 = bindingMethod.deserialize(jsonBinding, json1, HashCollisionParent.class);
        assertThat(deserialize1, instanceOf(HashCollisionType3.class));
        assertThat(deserialize1.value(), is("test3"));

        HashCollisionParent obj2 = new HashCollisionType4();
        obj2.value("test4");

        String json2 = bindingMethod.serialize(jsonBinding, obj2, HashCollisionParent.class);
        assertThat(json2, is("{\"@type\":\"macallums\",\"value\":\"test4\"}"));

        HashCollisionParent deserialize2 = bindingMethod.deserialize(jsonBinding, json2, HashCollisionParent.class);
        assertThat(deserialize2, instanceOf(HashCollisionType4.class));
        assertThat(deserialize2.value(), is("test4"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testClassInheritance(BindingMethod bindingMethod) {
        ChildClass childClass = new ChildClass();
        childClass.valueParent("parent");
        childClass.valueChild("child");
        String json = bindingMethod.serialize(jsonBinding, childClass, ParentClass.class);
        assertThat(json, is("{\"@type\":\"childclass\",\"valueParent\":\"parent\",\"valueChild\":\"child\"}"));

        ParentClass deserialized = bindingMethod.deserialize(jsonBinding, json, ParentClass.class);
        assertThat(deserialized, instanceOf(ChildClass.class));
        assertThat(deserialized.valueParent(), is("parent"));
        assertThat(((ChildClass) deserialized).valueChild(), is("child"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void testClassInheritanceFallback(BindingMethod bindingMethod) {
        ParentClass childClass = new ParentClass();
        childClass.valueParent("parent");
        String json = bindingMethod.serialize(jsonBinding, childClass, ParentClass.class);
        assertThat(json, is("{\"valueParent\":\"parent\"}"));

        ParentClass deserialized = bindingMethod.deserialize(jsonBinding, json, ParentClass.class);
        assertThat(deserialized, instanceOf(ParentClass.class));
        assertThat(deserialized.valueParent(), is("parent"));
    }

    @Json.Entity
    @Json.Polymorphic(Bird.class)
    @Json.Subtype(Dog.class)
    @Json.Subtype(Cat.class)
    interface Animal {
    }

    @Json.Entity
    @Json.Subtype(Car.class)
    @Json.Subtype(Bike.class)
    interface Vehicle {
    }

    @Json.Entity
    @Json.Polymorphic(key = "kind")
    @Json.Subtype(Circle.class)
    @Json.Subtype(Square.class)
    interface Shape {
    }

    // Test interface with more than 9 subtypes to trigger switch structure in generated code
    @Json.Entity
    @Json.Polymorphic(ManyTypeDefault.class)
    @Json.Subtype(value = ManyType1.class, alias = "type1")
    @Json.Subtype(value = ManyType2.class, alias = "type2")
    @Json.Subtype(value = ManyType3.class, alias = "type3")
    @Json.Subtype(value = ManyType4.class, alias = "type4")
    @Json.Subtype(value = ManyType5.class, alias = "type5")
    @Json.Subtype(value = ManyType6.class, alias = "type6")
    @Json.Subtype(value = ManyType7.class, alias = "type7")
    @Json.Subtype(value = ManyType8.class, alias = "type8")
    @Json.Subtype(value = ManyType9.class, alias = "type9")
    @Json.Subtype(value = ManyType10.class, alias = "type10")
    interface ManyTypes {
    }

    @Json.Entity
    @Json.Polymorphic(ConcreteADefault.class)
    @Json.Subtype(alias = "a1", value = ConcreteA1.class)
    @Json.Subtype(alias = "b", value = InterfaceB.class)
    interface InterfaceA {
    }

    @Json.Entity
    @Json.Polymorphic(value = ConcreteBDefault.class, key = "@type2")
    @Json.Subtype(alias = "b1", value = ConcreteB1.class)
    @Json.Subtype(alias = "b2", value = ConcreteB2.class)
    interface InterfaceB extends InterfaceA {
    }

    // Test interfaces and classes for hash collision testing
    @Json.Entity
    @Json.Subtype(alias = "costarring", value = HashCollisionType1.class) // FNV1a hash: 0x89C62E45
    @Json.Subtype(alias = "liquid", value = HashCollisionType2.class)     // FNV1a hash: 0x89C62E45 (collision)
    @Json.Subtype(alias = "declinate", value = HashCollisionType3.class)  // FNV1a hash: 0x0BF8B80D
    @Json.Subtype(alias = "macallums", value = HashCollisionType4.class)  // FNV1a hash: 0x0BF8B80D (collision)
    interface HashCollisionParent {
        String value();

        void value(String value);
    }

    @Json.Entity
    static class Dog implements Animal {

        private String name;

        public String name() {
            return name;
        }

        public void name(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Dog{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }

    @Json.Entity
    static class Cat implements Animal {
        private String color;

        public String color() {
            return color;
        }

        public void color(String color) {
            this.color = color;
        }
    }

    @Json.Entity
    static class Bird implements Animal {
        private String name;

        public String name() {
            return name;
        }

        public void name(String name) {
            this.name = name;
        }
    }

    static class Fish implements Animal {
        private String name;

        public String name() {
            return name;
        }

        public void name(String name) {
            this.name = name;
        }
    }

    @Json.Entity
    static class Zoo {
        private Animal animal;

        public Animal animal() {
            return animal;
        }

        public void animal(Animal animal) {
            this.animal = animal;
        }
    }

    @Json.Entity
    static class ManyTypesContainer {
        private ManyTypes manyType;

        public ManyTypes manyType() {
            return manyType;
        }

        public void manyType(ManyTypes manyType) {
            this.manyType = manyType;
        }
    }

    @Json.Entity
    static class Car implements Vehicle {
        private String make;

        public String make() {
            return make;
        }

        public void make(String make) {
            this.make = make;
        }
    }

    @Json.Entity
    static class Bike implements Vehicle {
        private String brand;

        public String brand() {
            return brand;
        }

        public void brand(String brand) {
            this.brand = brand;
        }
    }

    @Json.Entity
    static class Circle implements Shape {
        private int radius;

        public int radius() {
            return radius;
        }

        public void radius(int radius) {
            this.radius = radius;
        }
    }

    @Json.Entity
    static class Square implements Shape {
        private int side;

        public int side() {
            return side;
        }

        public void side(int side) {
            this.side = side;
        }
    }

    @Json.Entity
    static class ManyType1 implements ManyTypes {
        private String value;

        public String value() {
            return value;
        }

        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class ManyType2 implements ManyTypes {
        private String value;

        public String value() {
            return value;
        }

        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class ManyType3 implements ManyTypes {
        private String value;

        public String value() {
            return value;
        }

        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class ManyType4 implements ManyTypes {
        private String value;

        public String value() {
            return value;
        }

        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class ManyType5 implements ManyTypes {
        private String value;

        public String value() {
            return value;
        }

        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class ManyType6 implements ManyTypes {
        private String value;

        public String value() {
            return value;
        }

        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class ManyType7 implements ManyTypes {
        private String value;

        public String value() {
            return value;
        }

        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class ManyType8 implements ManyTypes {
        private String value;

        public String value() {
            return value;
        }

        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class ManyType9 implements ManyTypes {
        private String value;

        public String value() {
            return value;
        }

        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class ManyType10 implements ManyTypes {
        private String value;

        public String value() {
            return value;
        }

        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class ManyTypeDefault implements ManyTypes {
        private String value;

        public String value() {
            return value;
        }

        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class InterfaceAContainer {
        private InterfaceA interfaceA;

        public InterfaceA interfaceA() {
            return interfaceA;
        }

        public void interfaceA(InterfaceA interfaceA) {
            this.interfaceA = interfaceA;
        }
    }

    @Json.Entity
    static class ConcreteA1 implements InterfaceA {
        private String data;

        public String data() {
            return data;
        }

        public void data(String data) {
            this.data = data;
        }
    }

    @Json.Entity
    static class ConcreteADefault implements InterfaceA {
        private String data;

        public String data() {
            return data;
        }

        public void data(String data) {
            this.data = data;
        }
    }

    @Json.Entity
    static class ConcreteB1 implements InterfaceB {
        private String nestedData;

        public String nestedData() {
            return nestedData;
        }

        public void nestedData(String nestedData) {
            this.nestedData = nestedData;
        }
    }

    @Json.Entity
    static class ConcreteB2 implements InterfaceB {
        private String nestedData;

        public String nestedData() {
            return nestedData;
        }

        public void nestedData(String nestedData) {
            this.nestedData = nestedData;
        }
    }

    @Json.Entity
    static class ConcreteBDefault implements InterfaceB {
        private String nestedData;

        public String nestedData() {
            return nestedData;
        }

        public void nestedData(String nestedData) {
            this.nestedData = nestedData;
        }
    }

    @Json.Entity
    static class HashCollisionType1 implements HashCollisionParent {
        private String value;

        @Override
        public String value() {
            return value;
        }

        @Override
        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class HashCollisionType2 implements HashCollisionParent {
        private String value;

        @Override
        public String value() {
            return value;
        }

        @Override
        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class HashCollisionType3 implements HashCollisionParent {
        private String value;

        @Override
        public String value() {
            return value;
        }

        @Override
        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    static class HashCollisionType4 implements HashCollisionParent {
        private String value;

        @Override
        public String value() {
            return value;
        }

        @Override
        public void value(String value) {
            this.value = value;
        }
    }

    @Json.Entity
    @Json.Subtype(ChildClass.class)
    static class ParentClass {

        private String valueParent;

        public String valueParent() {
            return valueParent;
        }

        public void valueParent(String valueParent) {
            this.valueParent = valueParent;
        }
    }

    @Json.Entity
    static class ChildClass extends ParentClass {

        private String valueChild;

        public String valueChild() {
            return valueChild;
        }

        public void valueChild(String valueChild) {
            this.valueChild = valueChild;
        }
    }
}
