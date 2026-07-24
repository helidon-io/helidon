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

package io.helidon.openapi;

import io.helidon.common.Api;
import io.helidon.json.JsonException;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;
import io.helidon.json.schema.spi.JsonSchemaProvider;
import io.helidon.openapi.spi.OpenApiDocumentSource;

/**
 * Base class for generated OpenAPI document sources.
 */
@Api.Internal
public abstract class OpenApiSourceBase implements OpenApiDocumentSource {
    static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

    /**
     * Constructor with no side effects.
     */
    @Api.Internal
    protected OpenApiSourceBase() {
    }

    /**
     * Create a schema reference object.
     *
     * @param name schema name
     * @return schema reference JSON object
     */
    @Api.Internal
    protected static JsonObject schemaRef(String name) {
        return JsonObject.builder()
                .set("$ref", SCHEMA_REF_PREFIX + name)
                .build();
    }

    /**
     * Create a simple schema object for a JSON type.
     *
     * @param type JSON schema type
     * @return schema JSON object
     */
    @Api.Internal
    protected static JsonObject schema(String type) {
        return JsonObject.builder()
                .set("type", type)
                .build();
    }

    /**
     * Create an array schema object.
     *
     * @param items array item schema
     * @return array schema JSON object
     */
    @Api.Internal
    protected static JsonObject arraySchema(JsonObject items) {
        return JsonObject.builder()
                .set("type", "array")
                .set("items", items)
                .build();
    }

    /**
     * Create an OpenAPI example value from annotation text.
     *
     * @param value annotation value
     * @return parsed JSON value, or a JSON string if the value is not JSON
     */
    @Api.Internal
    protected static JsonValue exampleValue(String value) {
        String stripped = value.strip();
        if (!stripped.isEmpty()) {
            try {
                JsonParser parser = JsonParser.create(stripped);
                JsonValue result = parser.readJsonValue();
                if (!parser.hasNext()) {
                    return result;
                }
            } catch (JsonException ignored) {
                // Annotation values are strings by default; JSON parsing is an opt-in convenience.
            }
        }
        return JsonString.create(value);
    }

    /**
     * Create an OpenAPI extension value from resolved annotation text.
     *
     * @param name extension name
     * @param value resolved annotation value
     * @param parseValue whether to parse the value as JSON
     * @return extension JSON value
     * @throws IllegalArgumentException if parsing is enabled and the value is not exactly one valid JSON value
     */
    @Api.Internal
    protected static JsonValue extensionValue(String name, String value, boolean parseValue) {
        if (!parseValue) {
            return JsonString.create(value);
        }
        try {
            JsonParser parser = JsonParser.create(value.strip());
            JsonValue result = parser.readJsonValue();
            if (!parser.hasNext()) {
                return result;
            }
        } catch (JsonException e) {
            throw new IllegalArgumentException("OpenAPI extension " + name
                                                       + " must contain exactly one valid JSON value", e);
        }
        throw new IllegalArgumentException("OpenAPI extension " + name
                                                   + " must contain exactly one valid JSON value");
    }

    /**
     * Add a component schema from a JSON schema provider to the OpenAPI document.
     *
     * @param document OpenAPI document builder
     * @param provider JSON schema provider
     * @param name schema name
     */
    @Api.Internal
    protected static void componentSchema(OpenApiDocument.Builder document, JsonSchemaProvider provider, String name) {
        document.components(components -> components.schema(name, provider.schema().generateObjectNoKeywords()));
    }
}
