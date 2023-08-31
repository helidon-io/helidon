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
package io.helidon.microprofile.openapi;

import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import io.smallrye.openapi.runtime.scanner.AnnotationScannerExtension;
import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Extension we want SmallRye's OpenAPI implementation to use for parsing the JSON content in Extension annotations.
 */
class JsonpAnnotationScannerExtension implements AnnotationScannerExtension {

    private static final System.Logger LOGGER = System.getLogger(JsonpAnnotationScannerExtension.class.getName());
    private static final JsonReaderFactory JSON_READER_FACTORY = Json.createReaderFactory(Collections.emptyMap());
    private static final Representer MISSING_FIELD_TOLERANT_REPRESENTER;

    static {
        MISSING_FIELD_TOLERANT_REPRESENTER = new Representer(new DumperOptions());
        MISSING_FIELD_TOLERANT_REPRESENTER.getPropertyUtils().setSkipMissingProperties(true);
    }

    @Override
    public Object parseExtension(String key, String value) {
        try {
            return parseValue(value);
        } catch (Exception ex) {
            LOGGER.log(System.Logger.Level.ERROR,
                       String.format("Error parsing extension key: %s, value: %s", key, value),
                       ex);
            return null;
        }
    }

    @Override
    public Object parseValue(String value) {
        // Inspired by SmallRye's JsonUtil#parseValue method.
        if (value == null || value.isBlank()) {
            return null;
        }

        value = value.trim();

        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.valueOf(value);
        }

        // See if we should parse the value fully.
        switch (value.charAt(0)) {
            case '{', '[', '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                try {
                    JsonReader reader = JSON_READER_FACTORY.createReader(new StringReader(value));
                    JsonValue jsonValue = reader.readValue();
                    // readValue will truncate the input to convert to a number if it can. Make sure the value is the same length
                    // as the original.
                    if (jsonValue.getValueType().equals(JsonValue.ValueType.NUMBER)
                            && value.length() != jsonValue.toString().length()) {
                        return value;
                    }

                    return convertJsonValue(jsonValue);
                } catch (Exception ex) {
                    LOGGER.log(System.Logger.Level.ERROR,
                               String.format("Error parsing JSON value: %s", value),
                               ex);
                    throw ex;
                }
            }
            default -> {
            }
        }

        // Treat as JSON string.
        return value;
    }

    @Override
    public Schema parseSchema(String jsonSchema) {
        return OpenApiParser.parse(OpenApiHelper.types(),
                                   Schema.class,
                                   new StringReader(jsonSchema),
                                   MISSING_FIELD_TOLERANT_REPRESENTER);
    }

    private static Object convertJsonValue(JsonValue jsonValue) {
        return switch (jsonValue.getValueType()) {
            case ARRAY -> jsonValue.asJsonArray()
                    .stream()
                    .map(JsonpAnnotationScannerExtension::convertJsonValue)
                    .toList();
            case FALSE -> Boolean.FALSE;
            case TRUE -> Boolean.TRUE;
            case NULL -> null;
            case STRING -> ((JsonString) jsonValue).getString();
            case NUMBER -> ((JsonNumber) jsonValue).numberValue();
            case OBJECT -> jsonValue.asJsonObject()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> convertJsonValue(entry.getValue())));
        };
    }
}
