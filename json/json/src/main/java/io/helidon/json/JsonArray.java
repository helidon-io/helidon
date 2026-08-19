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

package io.helidon.json;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.helidon.common.Api;

/**
 * Represents a JSON array value containing an ordered list of non-null JSON values.
 */
@Api.Preview
public final class JsonArray extends JsonValue {

    /**
     * An empty JSON array instance.
     */
    static final JsonArray EMPTY_ARRAY = new JsonArray(List.of());

    private final List<JsonValue> jsonValues;

    private JsonArray(List<JsonValue> jsonValues) {
        this.jsonValues = jsonValues;
    }

    /**
     * Create a JsonArray from a list of JsonValue instances.
     *
     * @param jsonValues the list of non-null JSON values
     * @return a new JsonArray
     */
    public static JsonArray create(List<? extends JsonValue> jsonValues) {
        return new JsonArray(List.copyOf(jsonValues));
    }

    /**
     * Create a JsonArray from an array of JsonValue instances.
     *
     * @param jsonValues the array of JSON values
     * @return a new JsonArray
     */
    public static JsonArray create(JsonValue... jsonValues) {
        return new JsonArray(List.of(jsonValues));
    }

    /**
     * Create a JsonArray from a list of strings.
     *
     * @param values the list of string values
     * @return a new JsonArray containing JsonString values
     */
    public static JsonArray createStrings(List<String> values) {
        List<JsonValue> jsonValues = values.stream()
                .<JsonValue>map(JsonString::create)
                .toList();
        return new JsonArray(jsonValues);
    }

    /**
     * Create a JsonArray from a list of BigDecimal numbers.
     *
     * @param values the list of BigDecimal values
     * @return a new JsonArray containing JsonNumber values
     */
    public static JsonArray createNumbers(List<BigDecimal> values) {
        List<JsonValue> jsonValues = values.stream()
                .<JsonValue>map(JsonNumber::create)
                .toList();
        return new JsonArray(jsonValues);
    }

    /**
     * Create a JsonArray from a list of booleans.
     *
     * @param values the list of boolean values
     * @return a new JsonArray containing JsonBoolean values
     */
    public static JsonArray createBooleans(List<Boolean> values) {
        List<JsonValue> jsonValues = values.stream()
                .<JsonValue>map(JsonBoolean::create)
                .toList();
        return new JsonArray(jsonValues);
    }

    /**
     * Returns the shared empty JSON array instance.
     *
     * @return the empty JSON array
     */
    public static JsonArray empty() {
        return EMPTY_ARRAY;
    }

    static JsonArray createFromOwnedList(List<JsonValue> jsonValues) {
        return new JsonArray(Collections.unmodifiableList(jsonValues));
    }

    /**
     * Return the number of values in this array.
     *
     * @return array size
     */
    public int size() {
        return jsonValues.size();
    }

    /**
     * Return the JsonValue at the specified index as an Optional.
     *
     * @param index the index of the element to return
     * @return an Optional containing the element at the specified position, or empty if out of bounds
     */
    public Optional<JsonValue> get(int index) {
        if (index < 0 || index >= jsonValues.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(jsonValues.get(index));
    }

    /**
     * Return the JsonValue at the specified index, or the default value if the index is out of bounds.
     *
     * @param index the index of the element to return
     * @param defaultValue the value to return if the index is out of bounds
     * @return the element at the specified position, or the default value
     */
    public JsonValue get(int index, JsonValue defaultValue) {
        if (index < 0 || index >= jsonValues.size()) {
            return defaultValue;
        }
        JsonValue jsonValue = jsonValues.get(index);
        return jsonValue == null ? defaultValue : jsonValue;
    }

    /**
     * Return an unmodifiable list of all values in this array.
     *
     * @return an unmodifiable list of JsonValue instances
     */
    public List<JsonValue> values() {
        return jsonValues;
    }

    @Override
    public JsonValueType type() {
        return JsonValueType.ARRAY;
    }

    @Override
    public void toJson(JsonGenerator generator) {
        generator.writeArrayStart();
        for (JsonValue jsonValue : jsonValues) {
            jsonValue.toJson(generator);
        }
        generator.writeArrayEnd();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JsonArray that)) {
            return false;
        }
        if (that == this) {
            return true;
        }
        return jsonValues.equals(that.jsonValues);
    }

    @Override
    public int hashCode() {
        return jsonValues.hashCode();
    }

    @Override
    byte jsonStartChar() {
        return '[';
    }
}
