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
package io.helidon.docs.se.json;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.binding.JsonDeserializer;
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

    void snippet_8() {
        // tag::snippet_8[]
        @Json.Entity
        class Person {
            @Json.Required
            private String name;

            private Integer age; // not required

            // getters and setters
        }
        // end::snippet_8[]
    }

    void snippet_9() {
        // tag::snippet_9[]
        @Json.Entity
        class PersonDefault {
            private String name;
            private Integer age;

            // getters and setters
        }
        // end::snippet_9[]
    }

    void snippet_10() {
        // tag::snippet_10[]
        @Json.Entity
        @Json.SerializeNulls
        class PersonWithNulls {
            private String name;
            private Integer age;

            // getters and setters
        }
        // end::snippet_10[]
    }

    void snippet_11() {
        // tag::snippet_11[]
        @Json.Entity
        class PersonSelective {
            private String name;

            @Json.SerializeNulls
            private String city;

            // getters and setters
        }
        // end::snippet_11[]
    }

    void snippet_12() {
        // tag::snippet_12[]
        @Json.Entity
        @Json.SerializeNulls
        class PersonMixed {
            private String name;

            @Json.SerializeNulls(false)
            private String city;

            // getters and setters
        }
        // end::snippet_12[]
    }

    void snippet_13() {
        // tag::snippet_13[]
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
        // end::snippet_13[]
    }

    // tag::snippet_14[]
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
    // end::snippet_14[]

    void snippet_15() {
        // tag::snippet_15[]
        @Json.Entity
        class PersonDefault {
            private String zebra;
            private String alpha;
            private String beta;
        }
        // end::snippet_15[]
    }

    void snippet_16() {
        // tag::snippet_16[]
        @Json.Entity
        @Json.PropertyOrder(Order.ALPHABETICAL)
        class PersonAlphabetical {
            private String zebra;
            private String alpha;
            private String beta;
        }
        // end::snippet_16[]
    }

    void snippet_17() {
        // tag::snippet_17[]
        @Json.Entity
        @Json.PropertyOrder(Order.REVERSE_ALPHABETICAL)
        class PersonReverse {
            private String alpha;
            private String beta;
            private String zebra;
        }
        // end::snippet_17[]
    }

    void snippet_18() {
        // tag::snippet_18[]
        record MyType() { }

        class CustomDeserializer implements JsonDeserializer<MyType> {
            @Override
            public MyType deserialize(JsonParser parser) {
                // custom deserialization logic
                return new MyType();
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
        // end::snippet_18[]
    }

    // tag::snippet_19[]
    class PersonBuilder {
        private String name;
        private int age;

        PersonBuilder name(String name) {
            this.name = name;
            return this;
        }

        PersonBuilder age(int age) {
            this.age = age;
            return this;
        }

        PersonWithBuilder build() {
            return new PersonWithBuilder(this);
        }
    }

    @Json.Entity
    @Json.BuilderInfo(PersonBuilder.class)
    class PersonWithBuilder {
        private final String name;
        private final int age;

        PersonWithBuilder(PersonBuilder personBuilder) {
            this.name = personBuilder.name;
            this.age = personBuilder.age;
        }

        // getters
    }
    // end::snippet_19[]

    void snippet_20() {
        // tag::snippet_20[]
        @Json.Entity
        @Json.FailOnUnknown
        class StrictPerson {
            private String name;

            // getters and setters
        }
        // end::snippet_20[]
    }

    void snippet_21() {
        // tag::snippet_21[]
        JsonParser parser = JsonParser.create("{\"name\":\"John\",\"age\":30}");

        JsonObject object = parser.readJsonObject();

        String name = object.stringValue("name", "DefaultName"); // "John"
        int age = object.intValue("age", 0); // 30
        // end::snippet_21[]
    }

    void snippet_32() {
        // tag::snippet_32[]
        JsonParser parser = JsonParser.create("{\"name\":\"John\",\"age\":30,\"active\":true}");

        // Manual parsing - check for object start
        byte lastByte = parser.currentByte(); //
        if (lastByte != '{') {
            throw parser.createException("Expected object start");
        }
        lastByte = parser.nextToken(); //Get the next non-empty character

        String name = null;
        int age = 0;
        boolean active = false;
        if (lastByte == '}') {
            //Object end detected
        }

        while (true) {
            // Expect a string token (field name)
            if (lastByte != '"') {
                throw parser.createException("Expected field name");
            }
            String fieldName = parser.readString();

            // Expect a colon after field name
            if (parser.nextToken() != ':') {
                throw parser.createException("Expected ':' after field name");
            }
            parser.nextToken(); // Move to value
            if ("name".equals(fieldName)) {
                name = parser.readString(); //read the value as a String
            } else if ("age".equals(fieldName)) {
                age = parser.readInt(); //read the value as an int
            } else if ("active".equals(fieldName)) {
                active = parser.readBoolean(); //read the value as a boolean
            } else {
                parser.skip(); // Skip unknown fields
            }
            lastByte = parser.nextToken();
            if (lastByte == ',') {
                //Continue reading, if the next token is comma
                lastByte = parser.nextToken();
                continue;
            } else if (lastByte == '}') {
                //Object end detected
                break;
            } else {
                //Unexpected token detected
                throw parser.createException("Expected ',' or '}'", lastByte);
            }
        }
        //Object is fully read now.
        // end::snippet_32[]
    }

    void snippet_22() {
        // tag::snippet_22[]
        JsonGenerator generator = JsonGenerator.create(outputStream);

        generator.writeObjectStart();
        generator.write("name", "John");
        generator.write("age", 30);
        generator.writeObjectEnd();
        // end::snippet_22[]
    }

    void snippet_23() {
        // tag::snippet_23[]
        JsonGenerator generator = JsonGenerator.create(outputStream);

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
        // end::snippet_23[]
    }

    void snippet_24() {
        // tag::snippet_24[]
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
        // end::snippet_24[]
    }

    void snippet_25() {
        // tag::snippet_25[]
        JsonArray hobbies = JsonArray.createStrings(List.of("reading", "coding", "gaming"));
        JsonArray numbers = JsonArray.createNumbers(List.of(
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3")));

        // Access elements - JsonArray doesn't provide direct indexed access
        // Use values() to get the list and then access elements
        List<JsonValue> hobbyValues = hobbies.values();
        List<JsonValue> numberValues = numbers.values();
        // end::snippet_25[]
    }

    void snippet_26() {
        // tag::snippet_26[]
        JsonString name = JsonString.create("John Doe");
        String value = name.value(); // "John Doe"

        // From parser
        JsonParser parser = JsonParser.create("\"Hello World\"");
        JsonString greeting = parser.readJsonString();
        // end::snippet_26[]
    }

    void snippet_27() {
        // tag::snippet_27[]
        JsonNumber age = JsonNumber.create(new BigDecimal("30"));
        int intValue = age.intValue();
        double doubleValue = age.doubleValue();
        BigDecimal bigDecimalValue = age.bigDecimalValue();

        // From parser
        JsonParser parser = JsonParser.create("123.45");
        JsonNumber number = parser.readJsonNumber();
        // end::snippet_27[]
    }

    void snippet_28() {
        // tag::snippet_28[]
        JsonBoolean active = JsonBoolean.create(true);
        boolean value = active.value(); // true

        JsonBoolean inactive = JsonBoolean.FALSE; // Predefined constants
        // end::snippet_28[]
    }

    void snippet_29() {
        // tag::snippet_29[]
        JsonNull nullValue = JsonNull.instance();

        // In collections
        JsonArray array = JsonArray.create(List.of(
            JsonString.create("value1"),
            JsonNull.instance(), // null value
            JsonString.create("value3")));
        // end::snippet_29[]
    }

    void snippet_30() {
        // tag::snippet_30[]
        @Json.Entity
        class PersonDefault {
            private String name;
            private Optional<String> middleName;

            // getters and setters
        }
        // end::snippet_30[]
    }

    void snippet_31() {
        // tag::snippet_31[]
        @Json.Entity
        @Json.SerializeNulls
        class PersonWithNulls {
            private String name;
            private Optional<String> middleName;

            // getters and setters
        }
        // end::snippet_31[]
    }

    // Stub classes for compilation
    record Person(String name, int age) {}
}
