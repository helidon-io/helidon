/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.config.tests.config.metadata.builder.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.util.function.Predicate.not;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.fail;

class GeneratedMetadataTest {
    private static JsonObject module;
    private static JsonArray types;

    @BeforeAll
    static void setUpClass() throws IOException {
        String location = "/META-INF/helidon/config-metadata.json";
        JsonArray modules;
        try (InputStream in = GeneratedMetadataTest.class.getResourceAsStream(location)) {
            assertThat("The resource " + location + " must be generated during annotation processing",
                       in,
                       notNullValue());

            // root is array
            JsonReader reader = Json.createReader(in);
            modules = reader.readArray();
            assertThat(modules, hasSize(1));
            module = modules.getJsonObject(0);
            types = module.getJsonArray("types");
        }
    }

    @Test
    void testModuleName() {
        assertThat(module.getString("module"), is("io.helidon.config.tests.config.metadata.builder.api"));
    }

    @Test
    void testTypes() {
        assertThat("Two types should be generated: MyAbstract, and MyTarget",
                   types,
                   hasSize(2));
    }

    @Test
    void TestMyTarget() {
        JsonObject builder = null;
        for (JsonValue type : types) {
            JsonObject jsonObject = type.asJsonObject();
            if (jsonObject.getString("annotatedType").equals(MyTarget.class.getName())) {
                builder = jsonObject;
            }
        }
        assertThat("Type should be generated for MyTargetBlueprint", builder, notNullValue());

        assertThat(builder.getString("description"), is("builder"));
        assertThat(builder.getString("type"), is(MyTarget.class.getName()));

        JsonArray inherits = builder.getJsonArray("inherits");
        assertThat("Builder must inherit from AbstractBuilder", inherits, hasSize(1));
        assertThat(inherits.getString(0), is(MyAbstract.class.getName()));

        JsonArray producersJson = builder.getJsonArray("producers");
        assertThat("Producers must contain create and builder methods", producersJson, hasSize(2));
        List<String> producers = new ArrayList<>();
        producers.add(producersJson.getString(0));
        producers.add(producersJson.getString(1));
        assertThat(producers, hasItems(MyTarget.class.getName() + "#create(io.helidon.config.Config)",
                                       MyTarget.class.getName() + "#builder()"));

        JsonArray options = builder.getJsonArray("options");
        assertThat("There should be three options - message, type, and javadoc", options, hasSize(3));
        JsonObject message = null;
        JsonObject type = null;
        JsonObject javadoc = null;
        for (JsonValue jsonValue : options) {
            JsonObject jsonObject = jsonValue.asJsonObject();
            switch (jsonObject.getString("key")) {
            case "message":
                message = jsonObject;
                break;
            case "type":
                type = jsonObject;
                break;
            case "javadoc":
                javadoc = jsonObject;
                break;
            default:
                fail("Unexpected option discovered: " + jsonObject.getString("key"));
            }
        }

        assertThat("Option \"type\" must be generated", type, notNullValue());
        assertThat("Option \"message\" must be generated", message, notNullValue());
        assertThat("Option \"javadoc\" must be generated", javadoc, notNullValue());

        /*
        Validate message option
         */
        assertThat(message.getString("description"), is("message description"));
        assertThat(message.getString("key"), is("message"));
        assertThat(message.getString("method"), is(MyTarget.Builder.class.getCanonicalName() + "#message(java.lang.String)"));
        assertThat(message.getString("defaultValue"), is("message"));

        /*
        Validate type option
         */
        assertThat(type.getString("description"), is("type description"));
        assertThat(type.getString("key"), is("type"));
        assertThat(type.getString("type"), is("java.lang.Integer"));
        assertThat(type.getString("method"), is(MyTarget.Builder.class.getCanonicalName() + "#type(int)"));
        assertThat(type.getString("defaultValue"), is("42"));
        JsonArray allowedValues = type.getJsonArray("allowedValues");
        assertThat("There should be two default values defined", allowedValues, hasSize(2));
        JsonObject the42 = null;
        JsonObject the0 = null;
        for (JsonValue allowedValue : allowedValues) {
            JsonObject jsonObject = allowedValue.asJsonObject();
            switch (jsonObject.getString("value")) {
            case "42":
                the42 = jsonObject;
                break;
            case "0":
                the0 = jsonObject;
                break;
            default:
                fail("Unexpected allowed value discovered: " + jsonObject.getString("value"));
            }
        }

        assertThat(the42.getString("description"), is("answer"));
        assertThat(the0.getString("description"), is("no answer"));

        /*
        Validate javadoc option
         */
        // Description.
        // `technical`
        // MyTarget.ignored()
        // MyTarget.ignored()
        // CONSTANT
        // Some value
        // See MyTarget.message()
        List<String> descriptionLines = Stream.of(javadoc.getString("description").split("\n"))
                .map(String::trim) // trim spaces
                .filter(not(String::isBlank)) // ignore empty lines
                .collect(Collectors.toUnmodifiableList());
        assertThat(descriptionLines, hasItems(
                "Description.",
                "`technical`", // {@code technical}
                "MyTarget.ignored()", // {@link MyTarget#ignored()}
                "MyTarget.ignored()", // {@linkplain MyTarget#ignored()}
                "CONSTANT", // {@value #CONSTANT}
                "See MyTarget.message()" // @see MyTarget#message()
        ));
    }

    @Test
    void TestMyAbstract() {
        JsonObject builder = null;
        for (JsonValue type : types) {
            JsonObject jsonObject = type.asJsonObject();
            if (jsonObject.getString("annotatedType").equals(MyAbstract.class.getName())) {
                builder = jsonObject;
            }
        }
        assertThat("Type should be generated for MyAbstractBlueprint", builder, notNullValue());
        assertThat(builder.getString("description"), is("abstract builder"));
        assertThat(builder.getString("type"), is(MyAbstract.class.getName()));
        JsonArray options = builder.getJsonArray("options");
        assertThat("There should be a single option - abstract-message", options, hasSize(1));
        JsonObject option = options.getJsonObject(0);
        assertThat(option.getString("description"), is("abstract description"));
        assertThat(option.getString("key"), is("abstract-message"));
        assertThat(option.getString("method"),
                   is(MyAbstract.Builder.class.getCanonicalName() + "#abstractMessage(java.lang.String)"));
    }
}
