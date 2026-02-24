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
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

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

    @Test
    void testPolymorphism() {
        Dog dog = new Dog();
        dog.name("Alik");

        String json = jsonBinding.serialize(dog, Animal.class);
        assertThat(json, is("{\"@type\":\"dog\",\"name\":\"Alik\"}"));

        Animal deserialize = jsonBinding.deserialize(json, Animal.class);
        assertThat(deserialize, instanceOf(Dog.class));
        assertThat(((Dog) deserialize).name(), is("Alik"));
    }

    @Test
    void testPolymorphismEmptyJson() {
        Animal deserialize = jsonBinding.deserialize("{}", Animal.class);
        assertThat(deserialize, instanceOf(Bird.class));
    }

    @Test
    void testPolymorphismMissingTypeNotFirstPropertyFallsBackToDefault() {
        // Current generated poly converter only considers @type if it is the first property.
        Animal deserialize = jsonBinding.deserialize("{\"name\":\"X\"}", Animal.class);
        assertThat(deserialize, instanceOf(Bird.class));
    }

    @Test
    void testPolymorphismUnknownAliasFallsBackToDefault() {
        assertThrows(JsonException.class,
                     () -> jsonBinding.deserialize("{\"@type\":\"unicorn\"}", Animal.class));
    }

    @Test
    void testPolymorphismNullAliasFallsBackToDefault() {
        // The poly converter expects the discriminator value to be a JSON string.
        assertThrows(JsonException.class,
                     () -> jsonBinding.deserialize("{\"@type\":null}", Animal.class));
    }

    @Test
    void testPolymorphismAliasPresentButNotAStringThrows() {
        // The poly converter expects the discriminator value to be a JSON string.
        assertThrows(JsonException.class,
                     () -> jsonBinding.deserialize("{\"@type\":123}", Animal.class));
    }

    @Test
    void testPolymorphismTypeValueCaseSensitivityFallsBackToDefault() {
        assertThrows(JsonException.class,
                     () -> jsonBinding.deserialize("{\"@type\":\"DOG\",\"name\":\"Alik\"}", Animal.class));
    }

    @Test
    void testSerializeAsPolymorphicIncludesDiscriminatorForKnownSubtype() {
        Cat cat = new Cat();
        cat.color("black");
        String json = jsonBinding.serialize(cat, Animal.class);
        assertThat(json, is("{\"@type\":\"cat\",\"color\":\"black\"}"));
    }

    @Test
    void testSerializeNonListedSubtypeUsesDefaultSerializerWithoutDiscriminator() {
        Bird bird = new Bird();
        String json = jsonBinding.serialize(bird, Animal.class);

        // Bird is not listed in @Json.TypeInfo 'value', so poly converter falls back to default serializer
        // which (for Bird) does not write @type.
        assertThat(json, is("{}"));
    }

    @Test
    void testPolymorphismRoundTripDogAsAnimal() {
        Dog dog = new Dog();
        dog.name("Alik");

        String json = jsonBinding.serialize(dog, Animal.class);
        Animal deserialize = jsonBinding.deserialize(json, Animal.class);

        assertThat(deserialize, instanceOf(Dog.class));
        assertThat(((Dog) deserialize).name(), is("Alik"));
    }

    @Test
    void testPolymorphismListOfAnimals() {
        String json = "[" +
                "{\"@type\":\"dog\",\"name\":\"Alik\"}," +
                "{\"@type\":\"cat\",\"color\":\"black\"}," +
                "{}" +
                "]";

        List<Animal> animals = jsonBinding.deserialize(json, new io.helidon.common.GenericType<List<Animal>>() { });
        assertThat(animals.get(0), instanceOf(Dog.class));
        assertThat(((Dog) animals.get(0)).name(), is("Alik"));
        assertThat(animals.get(1), instanceOf(Cat.class));
        assertThat(((Cat) animals.get(1)).color(), is("black"));
        assertThat(animals.get(2), instanceOf(Bird.class));
    }

    @Test
    void testNestedPolymorphicProperty() {
        String json = "{\"animal\":{\"@type\":\"dog\",\"name\":\"Alik\"}}";

        Zoo zoo = jsonBinding.deserialize(json, Zoo.class);
        assertThat(zoo.animal(), instanceOf(Dog.class));
        assertThat(((Dog) zoo.animal()).name(), is("Alik"));

        // Also verify serialize uses the poly converter for the nested property
        String serialized = jsonBinding.serialize(zoo);
        assertThat(serialized, containsString("\"animal\":{\"@type\":\"dog\""));
        assertThat(serialized, containsString("\"name\":\"Alik\""));
    }

    @Test
    void testNoDefaultImplementationMissingTypeThrows() {
        // With no defaultSubtype, missing discriminator should be an error.
        assertThrows(Exception.class, () -> jsonBinding.deserialize("{}", Vehicle.class));
    }

    @Test
    void testNoDefaultImplementationUnknownAliasThrows() {
        assertThrows(Exception.class, () -> jsonBinding.deserialize("{\"@type\":\"boat\"}", Vehicle.class));
    }

    @Test
    void testNoDefaultImplementationKnownAliasWorks() {
        Vehicle deserialize = jsonBinding.deserialize("{\"@type\":\"car\",\"make\":\"Volvo\"}", Vehicle.class);
        assertThat(deserialize, instanceOf(Car.class));
        assertThat(((Car) deserialize).make(), is("Volvo"));

        // serialization through base type uses discriminator
        String json = jsonBinding.serialize(deserialize, Vehicle.class);
        assertThat(json, containsString("\"@type\":\"car\""));
        assertThat(json, containsString("\"make\":\"Volvo\""));
    }

    @Test
    void testCustomTypeInfoKeyIsUsedForDiscriminator() {
        String json = "{\"kind\":\"circle\",\"radius\":2}";
        Shape deserialize = jsonBinding.deserialize(json, Shape.class);
        assertThat(deserialize, instanceOf(Circle.class));
        assertThat(((Circle) deserialize).radius(), is(2));

        String serialized = jsonBinding.serialize(deserialize, Shape.class);
        assertThat(serialized, containsString("\"kind\":\"circle\""));
        assertThat(serialized, not(containsString("\"@type\":")));
    }

    @Test
    void testPolymorphismSwitchStructureType1() {
        ManyType1 obj = new ManyType1();
        obj.value("test1");

        String json = jsonBinding.serialize(obj, ManyTypes.class);
        assertThat(json, is("{\"@type\":\"type1\",\"value\":\"test1\"}"));

        ManyTypes deserialize = jsonBinding.deserialize(json, ManyTypes.class);
        assertThat(deserialize, instanceOf(ManyType1.class));
        assertThat(((ManyType1) deserialize).value(), is("test1"));
    }

    @Test
    void testPolymorphismSwitchStructureType10() {
        ManyType10 obj = new ManyType10();
        obj.value("test10");

        String json = jsonBinding.serialize(obj, ManyTypes.class);
        assertThat(json, is("{\"@type\":\"type10\",\"value\":\"test10\"}"));

        ManyTypes deserialize = jsonBinding.deserialize(json, ManyTypes.class);
        assertThat(deserialize, instanceOf(ManyType10.class));
        assertThat(((ManyType10) deserialize).value(), is("test10"));
    }

    @Test
    void testPolymorphismSwitchStructureEmptyJsonFallsToDefault() {
        ManyTypes deserialize = jsonBinding.deserialize("{}", ManyTypes.class);
        assertThat(deserialize, instanceOf(ManyTypeDefault.class));
    }

    @Test
    void testPolymorphismSwitchStructureUnknownAliasThrows() {
        assertThrows(JsonException.class,
                     () -> jsonBinding.deserialize("{\"@type\":\"unknown\"}", ManyTypes.class));
    }

    @Test
    void testPolymorphismSwitchStructureMissingTypeNotFirstPropertyFallsToDefault() {
        ManyTypes deserialize = jsonBinding.deserialize("{\"value\":\"X\"}", ManyTypes.class);
        assertThat(deserialize, instanceOf(ManyTypeDefault.class));
    }

    @Test
    void testPolymorphismSwitchStructureCaseSensitivity() {
        assertThrows(JsonException.class,
                     () -> jsonBinding.deserialize("{\"@type\":\"TYPE1\",\"value\":\"test\"}", ManyTypes.class));
    }

    @Test
    void testPolymorphismSwitchStructureList() {
        String json = "[" +
                "{\"@type\":\"type1\",\"value\":\"val1\"}," +
                "{\"@type\":\"type5\",\"value\":\"val5\"}," +
                "{\"@type\":\"type10\",\"value\":\"val10\"}," +
                "{}" +
                "]";

        List<ManyTypes> list = jsonBinding.deserialize(json, new GenericType<List<ManyTypes>>() { });
        assertThat(list.get(0), instanceOf(ManyType1.class));
        assertThat(((ManyType1) list.get(0)).value(), is("val1"));
        assertThat(list.get(1), instanceOf(ManyType5.class));
        assertThat(((ManyType5) list.get(1)).value(), is("val5"));
        assertThat(list.get(2), instanceOf(ManyType10.class));
        assertThat(((ManyType10) list.get(2)).value(), is("val10"));
        assertThat(list.get(3), instanceOf(ManyTypeDefault.class));
    }

    @Test
    void testPolymorphismSwitchStructureNested() {
        String json = "{\"manyType\":{\"@type\":\"type2\",\"value\":\"nested\"}}";

        ManyTypesContainer container = jsonBinding.deserialize(json, ManyTypesContainer.class);
        assertThat(container.manyType(), instanceOf(ManyType2.class));
        assertThat(((ManyType2) container.manyType()).value(), is("nested"));

        String serialized = jsonBinding.serialize(container);
        assertThat(serialized, containsString("\"manyType\":{\"@type\":\"type2\""));
        assertThat(serialized, containsString("\"value\":\"nested\""));
    }

    @Test
    void testPolymorphismSwitchStructureNullAlias() {
        assertThrows(JsonException.class,
                     () -> jsonBinding.deserialize("{\"@type\":null}", ManyTypes.class));
    }

    @Test
    void testPolymorphismSwitchStructureNumericAlias() {
        assertThrows(JsonException.class,
                     () -> jsonBinding.deserialize("{\"@type\":42}", ManyTypes.class));
    }

    @Test
    void testPolymorphismSwitchStructureAllTypes() {
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
            String json = jsonBinding.serialize(types[i], ManyTypes.class);
            assertThat(json, containsString("\"@type\":\"" + aliases[i] + "\""));
            assertThat(json, containsString("\"value\":\"" + (i + 1) + "\""));

            ManyTypes deserialized = jsonBinding.deserialize(json, ManyTypes.class);
            assertThat(deserialized.getClass().getSimpleName(), is("ManyType" + (i + 1)));
        }
    }

    @Test
    void testMultipleLevelPolymorphismConcreteA1() {
        ConcreteA1 obj = new ConcreteA1();
        obj.data("testA1");

        String json = jsonBinding.serialize(obj, InterfaceA.class);
        assertThat(json, is("{\"@type\":\"a1\",\"data\":\"testA1\"}"));

        InterfaceA deserialize = jsonBinding.deserialize(json, InterfaceA.class);
        assertThat(deserialize, instanceOf(ConcreteA1.class));
        assertThat(((ConcreteA1) deserialize).data(), is("testA1"));
    }

    @Test
    void testMultipleLevelPolymorphismConcreteB1AsA() {
        ConcreteB1 obj = new ConcreteB1();
        obj.nestedData("testB1");

        // When serialized as InterfaceA, it should include both @type discriminators
        String json = jsonBinding.serialize(obj, InterfaceA.class);
        assertThat(json, containsString("\"@type\":\"b\""));
        assertThat(json, containsString("\"@type2\":\"b1\""));
        assertThat(json, containsString("\"nestedData\":\"testB1\""));

        InterfaceA deserialize = jsonBinding.deserialize(json, InterfaceA.class);
        assertThat(deserialize, instanceOf(ConcreteB1.class));
        assertThat(((ConcreteB1) deserialize).nestedData(), is("testB1"));
    }

    @Test
    void testMultipleLevelPolymorphismConcreteB2AsB() {
        ConcreteB2 obj = new ConcreteB2();
        obj.nestedData("testB2");

        String json = jsonBinding.serialize(obj, InterfaceB.class);
        assertThat(json, is("{\"@type\":\"b\",\"@type2\":\"b2\",\"nestedData\":\"testB2\"}"));

        InterfaceA deserialize = jsonBinding.deserialize(json, InterfaceA.class);
        assertThat(deserialize, instanceOf(ConcreteB2.class));
        assertThat(((ConcreteB2) deserialize).nestedData(), is("testB2"));
    }

    @Test
    void testMultipleLevelPolymorphismEmptyJsonFallsToADefault() {
        InterfaceA deserialize = jsonBinding.deserialize("{}", InterfaceA.class);
        assertThat(deserialize, instanceOf(ConcreteADefault.class));
    }

    @Test
    void testMultipleLevelPolymorphismEmptyJsonFallsToBDefault() {
        InterfaceB deserialize = jsonBinding.deserialize("{}", InterfaceB.class);
        assertThat(deserialize, instanceOf(ConcreteBDefault.class));
    }

    @Test
    void testMultipleLevelPolymorphismUnknownAlias() {
        assertThrows(JsonException.class,
                     () -> jsonBinding.deserialize("{\"@type\":\"unknown\"}", InterfaceA.class));
    }

    @Test
    void testMultipleLevelPolymorphismNestedInContainer() {
        String json = "{\"interfaceA\":{\"@type\":\"b\",\"@type2\":\"b1\",\"nestedData\":\"nested\"}}";

        InterfaceAContainer container = jsonBinding.deserialize(json, InterfaceAContainer.class);
        assertThat(container.interfaceA(), instanceOf(ConcreteB1.class));
        assertThat(((ConcreteB1) container.interfaceA()).nestedData(), is("nested"));

        // Test serialization
        ConcreteB1 b1 = new ConcreteB1();
        b1.nestedData("test");
        InterfaceAContainer container2 = new InterfaceAContainer();
        container2.interfaceA(b1);

        String serialized = jsonBinding.serialize(container2);
        assertThat(serialized, containsString("\"interfaceA\":{\"@type\":\"b\""));
        assertThat(serialized, containsString("\"@type2\":\"b1\""));
        assertThat(serialized, containsString("\"nestedData\":\"test\""));
    }

    @Test
    void testPolymorphismWithArrays() {
        String json = "[{\"@type\":\"dog\",\"name\":\"Rex\"},{\"@type\":\"cat\",\"color\":\"black\"}]";

        Animal[] animals = jsonBinding.deserialize(json, Animal[].class);
        assertThat(animals[0], instanceOf(Dog.class));
        assertThat(((Dog) animals[0]).name(), is("Rex"));
        assertThat(animals[1], instanceOf(Cat.class));
        assertThat(((Cat) animals[1]).color(), is("black"));
    }

    @Test
    void testPolymorphismWithMaps() {
        String json = "{\"animal1\":{\"@type\":\"dog\",\"name\":\"Buddy\"},\"animal2\":{\"@type\":\"cat\",\"color\":\"white\"}}";

        java.util.Map<String, Animal> animalMap = jsonBinding.deserialize(json,
                                                                          new GenericType<java.util.Map<String, Animal>>() { });
        assertThat(animalMap.get("animal1"), instanceOf(Dog.class));
        assertThat(((Dog) animalMap.get("animal1")).name(), is("Buddy"));
        assertThat(animalMap.get("animal2"), instanceOf(Cat.class));
        assertThat(((Cat) animalMap.get("animal2")).color(), is("white"));
    }

    @Test
    void testPolymorphismMalformedJson() {
        // Test various malformed JSON cases
        assertThrows(JsonException.class,
                     () -> jsonBinding.deserialize("{\"@type\":\"dog\"", Animal.class)); // Unclosed object
        assertThrows(JsonException.class,
                     () -> jsonBinding.deserialize("{\"@type\":", Animal.class)); // Incomplete
        assertThrows(JsonException.class,
                     () -> jsonBinding.deserialize("[{\"@type\":\"dog\"}", Animal.class)); // Unclosed array
    }

    @Test
    void testPolymorphismDuplicateTypeKeys() {
        // Test what happens with duplicate @type keys (should use first one)
        String json = "{\"@type\":\"dog\",\"@type\":\"cat\",\"name\":\"Test\"}";

        Animal deserialize = jsonBinding.deserialize(json, Animal.class);
        assertThat(deserialize, instanceOf(Dog.class)); // Should use first @type
        assertThat(((Dog) deserialize).name(), is("Test"));
    }

    @Test
    void testPolymorphismWithHashCollisionAliases() {
        // Test polymorphism with aliases that have conflicting FNV1a hashes
        // "costarring" and "liquid" both have FNV1a hash 0x89C62E45
        HashCollisionParent obj1 = new HashCollisionType1();
        obj1.value("test1");

        String json1 = jsonBinding.serialize(obj1, HashCollisionParent.class);
        assertThat(json1, is("{\"@type\":\"costarring\",\"value\":\"test1\"}"));

        HashCollisionParent deserialize1 = jsonBinding.deserialize(json1, HashCollisionParent.class);
        assertThat(deserialize1, instanceOf(HashCollisionType1.class));
        assertThat(deserialize1.value(), is("test1"));

        // Test the second colliding alias
        HashCollisionParent obj2 = new HashCollisionType2();
        obj2.value("test2");

        String json2 = jsonBinding.serialize(obj2, HashCollisionParent.class);
        assertThat(json2, is("{\"@type\":\"liquid\",\"value\":\"test2\"}"));

        HashCollisionParent deserialize2 = jsonBinding.deserialize(json2, HashCollisionParent.class);
        assertThat(deserialize2, instanceOf(HashCollisionType2.class));
        assertThat(deserialize2.value(), is("test2"));
    }

    @Test
    void testPolymorphismWithMultipleHashCollisions() {
        // Test with multiple colliding aliases: "declinate" and "macallums" both have FNV1a hash 0x0BF8B80D
        HashCollisionParent obj1 = new HashCollisionType3();
        obj1.value("test3");

        String json1 = jsonBinding.serialize(obj1, HashCollisionParent.class);
        assertThat(json1, is("{\"@type\":\"declinate\",\"value\":\"test3\"}"));

        HashCollisionParent deserialize1 = jsonBinding.deserialize(json1, HashCollisionParent.class);
        assertThat(deserialize1, instanceOf(HashCollisionType3.class));
        assertThat(deserialize1.value(), is("test3"));

        HashCollisionParent obj2 = new HashCollisionType4();
        obj2.value("test4");

        String json2 = jsonBinding.serialize(obj2, HashCollisionParent.class);
        assertThat(json2, is("{\"@type\":\"macallums\",\"value\":\"test4\"}"));

        HashCollisionParent deserialize2 = jsonBinding.deserialize(json2, HashCollisionParent.class);
        assertThat(deserialize2, instanceOf(HashCollisionType4.class));
        assertThat(deserialize2.value(), is("test4"));
    }

    @Test
    void testClassInheritance() {
        ChildClass childClass = new ChildClass();
        childClass.valueParent("parent");
        childClass.valueChild("child");
        String json = jsonBinding.serialize(childClass, ParentClass.class);
        assertThat(json, is("{\"@type\":\"childclass\",\"valueParent\":\"parent\",\"valueChild\":\"child\"}"));

        ParentClass deserialized = jsonBinding.deserialize(json, ParentClass.class);
        assertThat(deserialized, instanceOf(ChildClass.class));
        assertThat(deserialized.valueParent(), is("parent"));
        assertThat(((ChildClass) deserialized).valueChild(), is("child"));
    }

    @Test
    void testClassInheritanceFallback() {
        ParentClass childClass = new ParentClass();
        childClass.valueParent("parent");
        String json = jsonBinding.serialize(childClass, ParentClass.class);
        assertThat(json, is("{\"valueParent\":\"parent\"}"));

        ParentClass deserialized = jsonBinding.deserialize(json, ParentClass.class);
        assertThat(deserialized, instanceOf(ParentClass.class));
        assertThat(deserialized.valueParent(), is("parent"));
    }

    @Json.Entity
    @Json.Polymorphic(defaultSubtype = Bird.class)
    @Json.Subtype(Dog.class)
    @Json.Subtype(Cat.class)
    interface Animal {
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
    @Json.Subtype(Car.class)
    @Json.Subtype(Bike.class)
    interface Vehicle {
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
    @Json.Polymorphic(key = "kind")
    @Json.Subtype(Circle.class)
    @Json.Subtype(Square.class)
    interface Shape {
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

    // Test interface with more than 9 subtypes to trigger switch structure in generated code
    @Json.Entity
    @Json.Polymorphic(defaultSubtype = ManyTypeDefault.class)
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
    @Json.Polymorphic(defaultSubtype = ConcreteADefault.class)
    @Json.Subtype(alias = "a1", value = ConcreteA1.class)
    @Json.Subtype(alias = "b", value = InterfaceB.class)
    interface InterfaceA {
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
    @Json.Polymorphic(defaultSubtype = ConcreteBDefault.class, key = "@type2")
    @Json.Subtype(alias = "b1", value = ConcreteB1.class)
    @Json.Subtype(alias = "b2", value = ConcreteB2.class)
    interface InterfaceB extends InterfaceA {
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
