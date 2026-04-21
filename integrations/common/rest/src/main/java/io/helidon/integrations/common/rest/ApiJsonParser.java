/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.common.rest;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;
import io.helidon.json.JsonValueType;

/**
 * Helper methods to process a returned JSON.
 */
@SuppressWarnings("helidon:api:incubating")
public abstract class ApiJsonParser {
    private static final Optional<Boolean> EMPTY = Optional.empty();
    private static final Optional<Boolean> PRESENT = Optional.of(true);

    /**
     * Get a string value from a json value.
     *
     * @param value Json value
     * @return string representation of the value.
     * @throws io.helidon.integrations.common.rest.ApiException in case the value is array or object
     */
    protected static String stringValue(JsonValue value) {
        return switch (value.type()) {
        case ARRAY -> throw new ApiException("Cannot create a simple String from an array: " + value);
        case OBJECT -> throw new ApiException("Cannot create a simple String from an object: " + value);
        case STRING -> value.asString().value();
        case BOOLEAN, NUMBER, NULL -> value.toString();
        case UNKNOWN -> throw new ApiException("Unsupported Helidon JSON value type: " + value.type());
        };
    }

    /**
     * Get a string value from a JSON-P value.
     *
     * @param value Json value
     * @return string representation of the value
     * @deprecated use {@link #stringValue(JsonValue)}
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected static String stringValue(jakarta.json.JsonValue value) {
        return stringValue(ApiJsonBuilder.toHelidonJson(value));
    }

    /**
     * Convert a JSON array in the JSON object to a list of strings.
     *
     * @param json JSON object
     * @param name name of the array in the object
     * @return list from the array, or empty if the array does not exist or is null
     */
    protected static List<String> toList(JsonObject json, String name) {
        return isPresent(json, name)
                .map(ignored -> {
                    List<String> result = new LinkedList<>();
                    JsonArray jsonArray = json.arrayValue(name, JsonArray.empty());
                    for (JsonValue jsonValue : jsonArray.values()) {
                        result.add(stringValue(jsonValue));
                    }
                    return List.copyOf(result);
                }).orElseGet(List::of);
    }

    /**
     * Convert a JSON-P array in the JSON object to a list of strings.
     *
     * @param json JSON object
     * @param name name of the array in the object
     * @return list from the array, or empty if the array does not exist or is null
     * @deprecated use {@link #toList(JsonObject, String)}
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected static List<String> toList(jakarta.json.JsonObject json, String name) {
        return isPresent(json, name)
                .map(ignored -> {
                    List<String> result = new LinkedList<>();
                    jakarta.json.JsonArray jsonArray = json.getJsonArray(name);
                    for (jakarta.json.JsonValue jsonValue : jsonArray) {
                        result.add(stringValue(jsonValue));
                    }
                    return List.copyOf(result);
                }).orElseGet(List::of);
    }

    /**
     * Get bytes from a base64 string value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return bytes or empty if the property does not exist or is null
     */
    protected static Optional<byte[]> toBytesBase64(JsonObject json, String name) {
        return isPresent(json, name)
                .flatMap(ignored -> json.stringValue(name))
                .map(it -> Base64.getDecoder().decode(it));
    }

    /**
     * Get bytes from a base64 string value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return bytes or empty if the property does not exist or is null
     * @deprecated use {@link #toBytesBase64(JsonObject, String)}
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected static Optional<byte[]> toBytesBase64(jakarta.json.JsonObject json, String name) {
        return isPresent(json, name)
                .map(ignored -> Base64.getDecoder().decode(json.getString(name)));
    }

    /**
     * Get a child JSON object.
     *
     * @param json JSON object
     * @param name name of the property
     * @return JSON object or empty if the property does not exist or is null
     */
    protected static Optional<JsonObject> toObject(JsonObject json, String name) {
        return isPresent(json, name)
                .flatMap(ignored -> json.objectValue(name));
    }

    /**
     * Get a child JSON-P object.
     *
     * @param json JSON object
     * @param name name of the property
     * @return JSON object or empty if the property does not exist or is null
     * @deprecated use {@link #toObject(JsonObject, String)}
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected static Optional<jakarta.json.JsonObject> toObject(jakarta.json.JsonObject json, String name) {
        return isPresent(json, name)
                .map(ignored -> json.getJsonObject(name));
    }

    /**
     * Get a string value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return string or empty if the property does not exist or is null
     */
    protected static Optional<String> toString(JsonObject json, String name) {
        return isPresent(json, name)
                .flatMap(ignored -> json.stringValue(name));
    }

    /**
     * Get a string value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return string or empty if the property does not exist or is null
     * @deprecated use {@link #toString(JsonObject, String)}
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected static Optional<String> toString(jakarta.json.JsonObject json, String name) {
        return isPresent(json, name)
                .map(ignored -> json.getString(name));
    }

    /**
     * Get an int value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return int or empty if the property does not exist or is null
     */
    protected static Optional<Integer> toInt(JsonObject json, String name) {
        return isPresent(json, name)
                .flatMap(ignored -> json.intValue(name));
    }

    /**
     * Get an int value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return int or empty if the property does not exist or is null
     * @deprecated use {@link #toInt(JsonObject, String)}
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected static Optional<Integer> toInt(jakarta.json.JsonObject json, String name) {
        return isPresent(json, name)
                .map(ignored -> json.getInt(name));
    }

    /**
     * Get a long value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return long or empty if the property does not exist or is null
     */
    protected static Optional<Long> toLong(JsonObject json, String name) {
        return isPresent(json, name)
                .flatMap(ignored -> json.value(name))
                .map(it -> it.asNumber().longValue());
    }

    /**
     * Get a long value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return long or empty if the property does not exist or is null
     * @deprecated use {@link #toLong(JsonObject, String)}
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected static Optional<Long> toLong(jakarta.json.JsonObject json, String name) {
        return isPresent(json, name)
                .map(ignored -> json.getJsonNumber(name).longValue());
    }

    /**
     * Get a double value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return double or empty if the property does not exist or is null
     */
    protected static Optional<Double> toDouble(JsonObject json, String name) {
        return isPresent(json, name)
                .flatMap(ignored -> json.doubleValue(name));
    }

    /**
     * Get a double value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return double or empty if the property does not exist or is null
     * @deprecated use {@link #toDouble(JsonObject, String)}
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected static Optional<Double> toDouble(jakarta.json.JsonObject json, String name) {
        return isPresent(json, name)
                .map(ignored -> json.getJsonNumber(name).doubleValue());
    }

    /**
     * Get a boolean value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return boolean or empty if the property does not exist or is null
     */
    protected static Optional<Boolean> toBoolean(JsonObject json, String name) {
        return isPresent(json, name)
                .flatMap(ignored -> json.booleanValue(name));
    }

    /**
     * Get a boolean value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return boolean or empty if the property does not exist or is null
     * @deprecated use {@link #toBoolean(JsonObject, String)}
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected static Optional<Boolean> toBoolean(jakarta.json.JsonObject json, String name) {
        return isPresent(json, name)
                .map(ignored -> json.getBoolean(name));
    }

    /**
     * Get an {@link java.time.Instant} value.
     *
     * @param json JSON object
     * @param name name of the property
     * @param formatter to use when parsing the string value
     * @return instant or empty if the property does not exist or is null
     */
    protected static Optional<Instant> toInstant(JsonObject json, String name, DateTimeFormatter formatter) {
        return isPresent(json, name)
                .flatMap(ignored -> json.stringValue(name))
                .flatMap(timeString -> {
                    if (timeString.isBlank()) {
                        return Optional.empty();
                    }
                    return Optional.of(Instant.from(formatter.parse(timeString)));
                });
    }

    /**
     * Get an {@link java.time.Instant} value.
     *
     * @param json JSON object
     * @param name name of the property
     * @param formatter to use when parsing the string value
     * @return instant or empty if the property does not exist or is null
     * @deprecated use {@link #toInstant(JsonObject, String, DateTimeFormatter)}
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected static Optional<Instant> toInstant(jakarta.json.JsonObject json, String name, DateTimeFormatter formatter) {
        return isPresent(json, name)
                .flatMap(ignored -> {
                    String timeString = json.getString(name);
                    if (timeString.isBlank()) {
                        return Optional.empty();
                    }
                    return Optional.of(Instant.from(formatter.parse(timeString)));
                });
    }

    /**
     * Get a map value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return map with property key/value pairs, or empty if the property does not exist or is null
     */
    protected static Map<String, String> toMap(JsonObject json, String name) {
        return isPresent(json, name)
                .map(ignored -> {
                    Map<String, String> map = new HashMap<>();

                    JsonObject child = json.objectValue(name)
                            .orElse(JsonObject.empty());
                    child.keysAsStrings()
                            .forEach(key -> map.put(key,
                                                    stringValue(child.value(key)
                                                                        .orElse(JsonNull.instance()))));

                    return Map.copyOf(map);
                }).orElseGet(Map::of);

    }

    /**
     * Get a map value.
     *
     * @param json JSON object
     * @param name name of the property
     * @return map with property key/value pairs, or empty if the property does not exist or is null
     * @deprecated use {@link #toMap(JsonObject, String)}
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected static Map<String, String> toMap(jakarta.json.JsonObject json, String name) {
        return isPresent(json, name)
                .map(ignored -> {
                    Map<String, String> map = new HashMap<>();

                    json.getJsonObject(name)
                            .forEach((key, value) -> map.put(key, stringValue(value)));

                    return Map.copyOf(map);
                }).orElseGet(Map::of);

    }

    /**
     * If the property is present on the JSON object, returns a non-empty optional, otherwise returns an
     * empty.
     *
     * @param json JSON object
     * @param name name of the property
     * @return non-empty optional if the property exists and is not null
     */
    protected static Optional<Boolean> isPresent(JsonObject json, String name) {
        if (!json.containsKey(name)) {
            return EMPTY;
        }
        if (json.value(name).map(it -> it.type() == JsonValueType.NULL).orElse(false)) {
            return EMPTY;
        }
        return PRESENT;
    }

    /**
     * If the property is present on the JSON-P object, returns a non-empty optional.
     *
     * @param json JSON object
     * @param name name of the property
     * @return non-empty optional if the property exists and is not null
     * @deprecated use {@link #isPresent(JsonObject, String)}
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    protected static Optional<Boolean> isPresent(jakarta.json.JsonObject json, String name) {
        if (!json.containsKey(name) || json.isNull(name)) {
            return EMPTY;
        }
        return PRESENT;
    }
}
