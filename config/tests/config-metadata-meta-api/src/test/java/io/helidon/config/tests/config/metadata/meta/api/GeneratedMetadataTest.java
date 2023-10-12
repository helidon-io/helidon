/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.config.tests.config.metadata.meta.api;

import java.io.IOException;
import java.io.InputStream;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.fail;

class GeneratedMetadataTest {
    @Test
    void testMetadata() throws IOException {
        String location = "/META-INF/helidon/config-metadata.json";

        JsonArray modules;
        try (InputStream in = GeneratedMetadataTest.class.getResourceAsStream(location)) {
            assertThat("The resource " + location + " must be generated during annotation processing",
                       in,
                       notNullValue());

            // root is array
            JsonReader reader = Json.createReader(in);
            modules = reader.readArray();
        }

        assertThat(modules, hasSize(1));
        JsonObject module = modules.getJsonObject(0);
        assertThat(module.getString("module"), is("io.helidon.config.tests.config.metadata.meta.api"));
        JsonArray types = module.getJsonArray("types");
        assertThat("Two types should be generated: AbstractBuilder, and MyBuilder",
                   types,
                   hasSize(2));

        JsonObject builder = null;
        JsonObject abstractBuilder = null;
        for (JsonValue type : types) {
            JsonObject jsonObject = type.asJsonObject();
            if (jsonObject.getString("annotatedType").equals(MyBuilder.class.getName())) {
                builder = jsonObject;
            } else if (jsonObject.getString("annotatedType").equals(AbstractBuilder.class.getName())) {
                abstractBuilder = jsonObject;
            }
        }
        assertThat("Type should be generated for builder implementation (MyBuilder)", builder, notNullValue());
        assertThat("Type should be generated for abstract builder (AbstractBuilder)", abstractBuilder, notNullValue());

        // now we have both builder and abstract builder, we can validate fields
        validateBuilder(builder);
        validateAbstractBuilder(abstractBuilder);
    }

    private void validateBuilder(JsonObject builder) {
        assertThat(builder.getString("description"), is("builder"));
        assertThat(builder.getString("type"), is(MyTarget.class.getName()));

        JsonArray inherits = builder.getJsonArray("inherits");
        assertThat("Builder must inherit from AbstractBuilder", inherits, hasSize(1));
        assertThat(inherits.getString(0), is(AbstractBuilder.class.getName()));

        JsonArray producers = builder.getJsonArray("producers");
        assertThat("Producers must contain build method", producers, hasSize(1));
        assertThat(producers.getString(0), is(MyBuilder.class.getName() + "#build()"));

        JsonArray options = builder.getJsonArray("options");
        assertThat("There should be two options - message and type", options, hasSize(2));
        JsonObject message = null;
        JsonObject type = null;
        for (JsonValue jsonValue : options) {
            JsonObject jsonObject = jsonValue.asJsonObject();
            switch (jsonObject.getString("key")) {
            case "message":
                message = jsonObject;
                break;
            case "type":
                type = jsonObject;
                break;
            default:
                fail("Unexpected option discovered: " + jsonObject.getString("key"));
            }
        }

        assertThat(message.getString("description"), is("message description"));
        assertThat(message.getString("key"), is("message"));
        assertThat(message.getString("method"), is(MyBuilder.class.getName() + "#message(java.lang.String)"));

        assertThat(type.getString("description"), is("type description"));
        assertThat(type.getString("key"), is("type"));
        assertThat(type.getString("type"), is("java.lang.Integer"));
        assertThat(type.getString("method"), is(MyBuilder.class.getName() + "#type(int)"));

        assertThat(type.getString("defaultValue"), is("42"));
        JsonArray allowedValues = type.getJsonArray("allowedValues");
        assertThat("There should be two default values defined", allowedValues, hasSize(2));
        JsonObject the42 = null;
        JsonObject the0 = null;
        for (JsonValue allowedValue : allowedValues) {
            JsonObject jsonObject = allowedValue.asJsonObject();
            switch(jsonObject.getString("value")) {
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
    }

    private void validateAbstractBuilder(JsonObject abstractBuilder) {
        assertThat(abstractBuilder.getString("description"), is("abstract builder"));
        assertThat(abstractBuilder.getString("type"), is(AbstractBuilder.class.getName()));
        JsonArray options = abstractBuilder.getJsonArray("options");
        assertThat("There should be a single option - abstract-message", options, hasSize(1));
        JsonObject option = options.getJsonObject(0);
        assertThat(option.getString("description"), is("abstract description"));
        assertThat(option.getString("key"), is("abstract-message"));
        assertThat(option.getString("method"), is(AbstractBuilder.class.getName() + "#abstractMessage(java.lang.String)"));
    }
}
