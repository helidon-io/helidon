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
package io.helidon.openapi;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class SimpleOpenApiManagerTest {

    private static final String HELLO_BASE64 = Base64.getEncoder().encodeToString("Hello".getBytes(StandardCharsets.UTF_8));

    @Test
    void testJsonFormatting() {
        SimpleOpenApiManager manager = new SimpleOpenApiManager();
        String raw = "plain-boolean: true\n"
                      + "quoted-boolean: \"true\"\n"
                      + "integer: 100\n"
                      + "float: 1.1\n"
                      + "binary: !!binary \"" + HELLO_BASE64 + "\"\n";
        String formatted = manager.format(raw, OpenApiFormat.JSON);
        JsonReader reader = Json.createReader(new StringReader(formatted));
        JsonObject jsonObject = reader.readObject();
        assertThat(jsonObject.getBoolean("plain-boolean"), is(true));
        assertThat(jsonObject.getString("quoted-boolean"), is("true"));
        assertThat(jsonObject.getInt("integer"), is(100));
        assertThat(jsonObject.getJsonNumber("float").doubleValue(), is(1.1D));
        assertThat(jsonObject.getString("binary"), is(HELLO_BASE64));
    }

    @Test
    void testYamlFormatting() {
        SimpleOpenApiManager manager = new SimpleOpenApiManager();
        String raw = "{"
                      + "\"plain-boolean\": true,"
                      + "\"quoted-boolean\": \"true\","
                      + "\"integer\": 100,"
                      + "\"float\": 1.1,"
                      + "\"binary\": \"" + HELLO_BASE64 + "\""
                      + "}";
        String formatted = manager.format(raw, OpenApiFormat.YAML);
        Yaml yaml = new Yaml();
        Map<String, ?> yamlObject = yaml.load(formatted);
        assertThat(yamlObject.get("plain-boolean"), is(true));
        assertThat(yamlObject.get("quoted-boolean"), is("true"));
        assertThat(yamlObject.get("integer"), is(100));
        assertThat(yamlObject.get("float"), is(1.1D));
        assertThat(yamlObject.get("binary"), is(HELLO_BASE64));
    }
}
