/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.harness;

import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * JSON values.
 */
public class JsonValues {

    /**
     * Get a {@link JsonValue} as a {@link String}.
     *
     * @param value value
     * @return String
     * @throws IllegalArgumentException if the value type cannot be converted
     */
    public static String asString(JsonValue value) {
        return switch (value.getValueType()) {
            case STRING -> ((JsonString) value).getString();
            case NULL -> null;
            default -> throw new IllegalArgumentException(String.format(
                    "Cannot convert JSON value to String, value: '%s', type: %s",
                    value, value.getValueType()));
        };
    }

    /**
     * Get a {@link JsonValue} as a {@code long}.
     *
     * @param value value
     * @return long
     * @throws IllegalArgumentException if the value type cannot be converted
     */
    public static long asLong(JsonValue value) {
        return switch (value.getValueType()) {
            case NUMBER -> ((JsonNumber) value).longValue();
            case STRING -> Long.parseLong(((JsonString) value).getString());
            case TRUE -> 1;
            case FALSE -> 0;
            default -> throw new IllegalArgumentException(String.format(
                    "Cannot convert JSON value to long, value: '%s', type: %s",
                    value, value.getValueType()));
        };
    }
}
