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
import java.util.logging.Level;
import java.util.stream.Collectors;

import io.smallrye.openapi.runtime.scanner.AnnotationScannerExtension;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Extension we want SmallRye's OpenAPI implementation to use for parsing the JSON content in Extension annotations.
 */
class HelidonAnnotationScannerExtension implements AnnotationScannerExtension {

    private static final System.Logger LOGGER = System.getLogger(HelidonAnnotationScannerExtension.class.getName());

    private static final JsonReaderFactory JSON_READER_FACTORY = Json.createReaderFactory(Collections.emptyMap());

    @Override
    public Object parseExtension(String key, String value) {

        // Inspired by SmallRye's JsonUtil#parseValue method.
        if (value == null) {
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
                return convertJsonValue(jsonValue);
            } catch (Exception ex) {
                LOGGER.log(System.Logger.Level.ERROR,
                           String.format("Error parsing extension key: %s, value: %s", key, value),
                           ex);
            }
        }
        default -> {
        }
        }

        // Treat as JSON string.
        return value;
    }

    private static Object convertJsonValue(JsonValue jsonValue) {
        switch (jsonValue.getValueType()) {
        case ARRAY -> {
            JsonArray jsonArray = jsonValue.asJsonArray();
            return jsonArray.stream()
                    .map(HelidonAnnotationScannerExtension::convertJsonValue)
                    .collect(Collectors.toList());
        }
        case FALSE -> {
            return Boolean.FALSE;
        }
        case TRUE -> {
            return Boolean.TRUE;
        }
        case NULL -> {
            return null;
        }
        case STRING -> {
            return JsonString.class.cast(jsonValue).getString();
        }
        case NUMBER -> {
            JsonNumber jsonNumber = JsonNumber.class.cast(jsonValue);
            return jsonNumber.numberValue();
        }
        case OBJECT -> {
            JsonObject jsonObject = jsonValue.asJsonObject();
            return jsonObject.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> convertJsonValue(entry.getValue())));
        }
        default -> {
            return jsonValue.toString();
        }
        }
    }
}
