/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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


import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import io.smallrye.openapi.runtime.io.Format;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class TestAdditionalProperties {

    private static SnakeYAMLParserHelper<ExpandedTypeDescription> helper = OpenAPISupport.helper();


    @Test
    void checkParsingBooleanAdditionalProperties() throws IOException {
        OpenAPI openAPI = ParserTest.parse(helper, "/withBooleanAddlProps.yml", OpenAPISupport.OpenAPIMediaType.YAML);
        Schema itemSchema = openAPI.getComponents().getSchemas().get("item");

        Schema additionalPropertiesSchema = itemSchema.getAdditionalPropertiesSchema();
        Boolean additionalPropertiesBoolean = itemSchema.getAdditionalPropertiesBoolean();

        assertThat("Additional properties as schema", additionalPropertiesSchema, is(nullValue()));
        assertThat("Additional properties as boolean", additionalPropertiesBoolean, is(notNullValue()));
        assertThat("Additional properties value", additionalPropertiesBoolean.booleanValue(), is(false));
    }

    @Test
    void checkParsingSchemaAdditionalProperties() throws IOException {
        OpenAPI openAPI = ParserTest.parse(helper, "/withSchemaAddlProps.yml", OpenAPISupport.OpenAPIMediaType.YAML);
        Schema itemSchema = openAPI.getComponents().getSchemas().get("item");

        Schema additionalPropertiesSchema = itemSchema.getAdditionalPropertiesSchema();
        Boolean additionalPropertiesBoolean = itemSchema.getAdditionalPropertiesBoolean();

        assertThat("Additional properties as boolean", additionalPropertiesBoolean, is(nullValue()));
        assertThat("Additional properties as schema", additionalPropertiesSchema, is(notNullValue()));

        Map<String, Schema> additionalProperties = additionalPropertiesSchema.getProperties();
        assertThat("Additional property 'code'", additionalProperties, hasKey("code"));
        assertThat("Additional property 'text'", additionalProperties, hasKey("text"));
    }

    @Test
    void checkWritingSchemaAdditionalProperties() throws IOException {
        OpenAPI openAPI = ParserTest.parse(helper, "/withSchemaAddlProps.yml", OpenAPISupport.OpenAPIMediaType.YAML);
        String document = formatModel(openAPI);

        /*
         * Expected output (although the
               additionalProperties:
        type: object
        properties:
          code:
            type: integer
          text:
            type: string
         */
        Yaml yaml = new Yaml();
        Map<String, Object> model = yaml.load(document);
        Map<String, ?> item = asMap(model, "components", "schemas", "item");

        Object additionalProperties = item.get("additionalProperties");

        assertThat("Additional properties node type", additionalProperties, is(instanceOf(Map.class)));

    }

    private static Map<String, ?> asMap(Map<String, ?> map, String... keys) {
        Map<String, ?> m = map;
        for (String key : keys) {
            m = (Map<String, ?>) m.get(key);
        }
        return m;
    }

    @Test
    void checkWritingBooleanAdditionalProperties() throws IOException {
        OpenAPI openAPI = ParserTest.parse(helper, "/withBooleanAddlProps.yml", OpenAPISupport.OpenAPIMediaType.YAML);
        String document = formatModel(openAPI);

        /*
         * Expected output: additionalProperties: false
         */

        assertThat("Formatted OpenAPI document matches expected pattern",
                   document, containsString("additionalProperties: false"));
    }

    private String formatModel(OpenAPI model) {
        StringWriter sw = new StringWriter();
        Map<Class<?>, ExpandedTypeDescription> implsToTypes = OpenAPISupport.buildImplsToTypes(helper);
        Serializer.serialize(helper.types(), implsToTypes, model, Format.YAML, sw);
        return sw.toString();
    }
}
