/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.docs.se;

import java.math.BigDecimal;
import java.util.List;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonString;
import io.helidon.json.Generator;
import io.helidon.json.JsonValue;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.binding.JsonConverter;
import io.helidon.json.binding.JsonDeserializer;
import io.helidon.json.binding.JsonSerializer;
import io.helidon.common.GenericType;
import io.helidon.json.binding.Order;

@SuppressWarnings("ALL")
class JsonSnippets {

    // Stub variables
    java.io.OutputStream outputStream = null;

    void snippet_1() {
        // tag::snippet_1[]
        JsonBinding binding = JsonBinding.create();

        // Deserialize JSON string to object
        Person person = binding.deserialize("{\"name\":\"John\",\"age\":30}", Person.class);

        // Serialize object to JSON string
        String json = binding.serialize(person);
        // end::snippet_1[]
    }

    void snippet_2() {
        // tag::snippet_2[]
        @Json.Entity
        class Person {
            private String name;
            private int age;

            // getters and setters
        }
        // end::snippet_2[]
    }

    void snippet_3() {
        // tag::snippet_3[]
        @Json.Entity
        class Person {
            private String firstName;

            @Json.Property("last_name")
            private String lastName;

            // getters and setters
        }
        // end::snippet_3[]
    }

    void snippet_4() {
        // tag::snippet_4[]
        @Json.Entity
        class Person {
            private String firstName;
            private String lastName;

            @Json.Property("fullName")
            public String getDisplayName() {
                return firstName + " " + lastName;
            }

            // getters and setters
        }
        // end::snippet_4[]
    }

    void snippet_5() {
        // tag::snippet_5[]
        @Json.Entity
        class Person {
            private String name;
            private int age;

            @Json.Ignore
            private String password;

            // getters and setters
        }
        // end::snippet_5[]
    }

    void snippet_6() {
        // tag::snippet_6[]
        @Json.Entity
        class Person {
            private String name;
            private transient String temp;
            private String data;

            // getters and setters
        }
        // end::snippet_6[]
    }

    void snippet_7() {
        // tag::snippet_7[]
        @Json.Entity
        class Person {
            private String firstName;
            private String lastName;

            @Json.Ignore
            public String getFirstName() {
                return firstName;
            }

            // other getters and setters
        }
        // end::snippet_7[]
    }

    void snippet_10() {
        // tag::snippet_10[]
        @Json.Entity
        class Person {
            @Json.Required
            private String name;

            private Integer age; // optional

            // getters and setters
        }
        // end::snippet_10[]
    }

    void snippet_11() {
        // tag::snippet_11[]
        @Json.Entity
        class PersonDefault {
            private String name;
            private Integer age;

            // getters and setters
        }
        // end::snippet_11[]
    }

    void snippet_12() {
        // tag::snippet_12[]
        @Json.Entity
        @Json.SerializeNulls
        class PersonWithNulls {
            private String name;
            private Integer age;

            // getters and setters
        }
        // end::snippet_12[]
    }

    void snippet_13() {
        // tag::snippet_13[]
        @Json.Entity
        class PersonSelective {
            private String name;

            @Json.SerializeNulls
            private Integer age;

            private String city;

            // getters and setters
        }
        // end::snippet_13[]
    }

    void snippet_14() {
        // tag::snippet_14[]
        @Json.Entity
        @Json.SerializeNulls
        class PersonMixed {
            private String name;
            private Integer age;

            @Json.SerializeNulls(false)
            private String city;

            // getters and setters
        }
        // end::snippet_14[]
    }

    void snippet_15() {
        // tag::snippet_15[]
        @Json.Entity
        class Person {
            private final String name;
            private final int age;

            @Json.Creator
            public Person(String name, int age) {
                this.name = name;
                this.age = age;
            }

            // getters
        }
        // end::snippet_15[]
    }

    // tag::snippet_16[]
    @Json.Entity
    static class PersonWithCreator {
        private final String name;

        private PersonWithCreator(String name) {
            this.name = name;
        }

        @Json.Creator
        public static PersonWithCreator create(String name) {
            return new PersonWithCreator(name);
        }
    }
    // end::snippet_16[]

    void snippet_17() {
        // tag::snippet_17[]
        @Json.Entity
        @Json.PropertyOrder(Order.ALPHABETICAL)
        class Person {
            private String name;
            private int age;
            private String city;

            // getters and setters
        }
        // end::snippet_17[]
    }

    void snippet_18() {
        // tag::snippet_18[]
        @Json.Entity
        class PersonDefault {
            private String zebra;
            private String alpha;
            private String beta;
        }
        // end::snippet_18[]
    }

    void snippet_19() {
        // tag::snippet_19[]
        @Json.Entity
        @Json.PropertyOrder(Order.ALPHABETICAL)
        class PersonAlphabetical {
            private String zebra;
            private String alpha;
            private String beta;
        }
        // end::snippet_19[]
    }

    void snippet_21() {
        // tag::snippet_21[]
        @Json.Entity
        @Json.PropertyOrder(Order.REVERSE_ALPHABETICAL)
        class PersonReverse {
            private String alpha;
            private String beta;
            private String zebra;
        }
        // end::snippet_21[]
    }

    void snippet_22() {
        // tag::snippet_22[]
        class CustomDeserializer implements JsonDeserializer<MyType> {
            @Override
            public MyType deserialize(JsonParser parser) {
                // custom deserialization logic
                return null;
            }

            @Override
            public GenericType<MyType> type() {
                return GenericType.create(MyType.class);
            }
        }

        @Json.Entity
        class CustomType {

            @Json.Deserializer(CustomDeserializer.class)
            private MyType value;

            // getters and setters
        }
        // end::snippet_22[]
    }

    // tag::snippet_23[]
    class PersonBuilder {
        private String name;
        private int age;

        public PersonBuilder name(String name) {
            this.name = name;
            return this;
        }

        public PersonBuilder age(int age) {
            this.age = age;
            return this;
        }

        public PersonWithBuilder build() {
            return new PersonWithBuilder(this);
        }
    }

    @Json.Entity
    @Json.BuilderInfo(PersonBuilder.class)
    class PersonWithBuilder {
        private final String name;
        private final int age;

        public PersonWithBuilder(PersonBuilder personBuilder) {
            this.name = personBuilder.name;
            this.age = personBuilder.age;
        }

        // getters
    }
    // end::snippet_23[]

    void snippet_24() {
        // tag::snippet_24[]
        @Json.Entity
        @Json.FailOnUnknown
        class StrictPerson {
            private String name;

            // getters and setters
        }
        // end::snippet_24[]
    }

    void snippet_25() {
        // tag::snippet_25[]
        @Json.Entity
        class Person {
            private String name;
            private int age;

            // getters and setters
        }

        JsonBinding binding = JsonBinding.create();
        Person person = binding.deserialize("{\"name\":\"John\",\"age\":30}", Person.class);
        String json = binding.serialize(person);
        // end::snippet_25[]
    }

    void snippet_26() {
        // tag::snippet_26[]
        // Custom JsonSerializer
        class PersonSerializer implements JsonSerializer<Person> {
            @Override
            public void serialize(Generator generator, Person person, boolean writeNulls) {
                generator.writeObjectStart();
                generator.write("name", person.getName());
                generator.write("age", person.getAge());
                generator.writeObjectEnd();
            }

            @Override
            public GenericType<Person> type() {
                return GenericType.create(Person.class);
            }
        }
        // end::snippet_26[]
    }

    void snippet_27() {
        // tag::snippet_27[]
        // Custom JsonDeserializer
        class PersonDeserializer implements JsonDeserializer<Person> {
            @Override
            public Person deserialize(JsonParser parser) {
                JsonObject object = parser.readJsonObject();

                String name = object.stringValue("name").orElse("");
                int age = object.intValue("age", 0);

                return new Person(name, age);
            }

            @Override
            public GenericType<Person> type() {
                return GenericType.create(Person.class);
            }
        }
        // end::snippet_27[]
    }

    void snippet_28() {
        // tag::snippet_28[]
        class CustomConverter implements JsonConverter<MyType> {
            @Override
            public void serialize(Generator generator, MyType instance, boolean writeNulls) {
                // custom serialization logic
            }

            @Override
            public MyType deserialize(JsonParser parser) {
                // custom deserialization logic
                return null;
            }

            @Override
            public GenericType<MyType> type() {
                return GenericType.create(MyType.class);
            }
        }

        @Json.Entity
        class CustomType {
            @Json.Converter(CustomConverter.class)
            private MyType value;

            // getters and setters
        }
        // end::snippet_28[]
    }

    void snippet_29() {
        // tag::snippet_29[]
        JsonParser parser = JsonParser.create("{\"name\":\"John\",\"age\":30}");

        JsonObject object = parser.readJsonObject();

        String name = object.stringValue("name", "DefaultName"); // "John"
        int age = object.intValue("age", 0); // 30
        // end::snippet_29[]
    }

    void snippet_30() {
        // tag::snippet_30[]
        Generator generator = Generator.create(outputStream);

        generator.writeObjectStart();
        generator.write("name", "John");
        generator.write("age", 30);
        generator.writeObjectEnd();
        // end::snippet_30[]
    }

    void snippet_31() {
        // tag::snippet_31[]
        Generator generator = Generator.create(outputStream);

        generator.writeObjectStart();
        generator.writeKey("person");
        generator.writeObjectStart();
        generator.write("name", "John");
        generator.write("age", 30);
        generator.writeObjectEnd();

        generator.writeKey("hobbies");
        generator.writeArrayStart();
        generator.write("reading");
        generator.write("coding");
        generator.writeArrayEnd();
        generator.writeObjectEnd();
        // end::snippet_31[]
    }

    void snippet_32() {
        // tag::snippet_32[]
        JsonObject person = JsonObject.builder()
            .set("name", "John")
            .set("age", 30)
            .set("active", true)
            .build();

        String name = person.stringValue("name", "");
        int age = person.intValue("age", 0);
        boolean active = person.booleanValue("active", false);

        // Nested objects
        JsonObject address = JsonObject.builder()
            .set("street", "123 Main St")
            .set("city", "Springfield")
            .build();

        JsonObject personWithAddress = JsonObject.builder()
            .set("name", "John")
            .set("address", address)
            .build();
        // end::snippet_32[]
    }

    void snippet_33() {
        // tag::snippet_33[]
        JsonArray hobbies = JsonArray.createStrings(List.of("reading", "coding", "gaming"));
        JsonArray numbers = JsonArray.createNumbers(List.of(
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3")));

        // Access elements - JsonArray doesn't provide direct indexed access
        // Use values() to get the list and then access elements
        List<JsonValue> hobbyValues = hobbies.values();
        List<JsonValue> numberValues = numbers.values();
        // end::snippet_33[]
    }

    void snippet_34() {
        // tag::snippet_34[]
        JsonString name = JsonString.create("John Doe");
        String value = name.value(); // "John Doe"

        // From parser
        JsonParser parser = JsonParser.create("\"Hello World\"");
        JsonString greeting = parser.readJsonString();
        // end::snippet_34[]
    }

    void snippet_35() {
        // tag::snippet_35[]
        JsonNumber age = JsonNumber.create(new BigDecimal("30"));
        int intValue = age.intValue();
        double doubleValue = age.doubleValue();
        BigDecimal bigDecimalValue = age.bigDecimalValue();

        // From parser
        JsonParser parser = JsonParser.create("123.45");
        JsonNumber number = parser.readJsonNumber();
        // end::snippet_35[]
    }

    void snippet_36() {
        // tag::snippet_36[]
        JsonBoolean active = JsonBoolean.create(true);
        boolean value = active.value(); // true

        JsonBoolean inactive = JsonBoolean.FALSE; // Predefined constants
        // end::snippet_36[]
    }

    void snippet_37() {
        // tag::snippet_37[]
        JsonNull nullValue = JsonNull.instance();

        // In collections
        JsonArray array = JsonArray.create(List.of(
            JsonString.create("value1"),
            JsonNull.instance(), // null value
            JsonString.create("value3")));
        // end::snippet_37[]
    }

    // Stub classes for compilation
    static class Person {
        private String name;
        private int age;

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public int getAge() { return age; }
    }

    record MyType() {
    }
}
