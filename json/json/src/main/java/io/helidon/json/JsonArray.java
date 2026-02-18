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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a JSON array value containing an ordered list of JSON values.
 */
public final class JsonArray extends JsonValue {

    /**
     * An empty JSON array instance.
     */
    static final JsonArray EMPTY_ARRAY = JsonArray.create(List.of());

    private final List<? extends JsonValue> jsonValues;

    private JsonArray(List<? extends JsonValue> jsonValues) {
        this.jsonValues = jsonValues;
    }

    /**
     * Create a JsonArray from a list of JsonValue instances.
     *
     * @param jsonValues the list of JSON values
     * @return a new JsonArray
     */
    public static JsonArray create(List<? extends JsonValue> jsonValues) {
        return new JsonArray(jsonValues);
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
        List<JsonString> jsonValues = values.stream()
                .map(JsonString::create)
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
        List<JsonNumber> jsonValues = values.stream()
                .map(JsonNumber::create)
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
        List<JsonBoolean> jsonValues = values.stream()
                .map(JsonBoolean::create)
                .toList();
        return new JsonArray(jsonValues);
    }

    /**
     * Return the JsonValue at the specified index as an Optional.
     *
     * @param index the index of the element to return
     * @return an Optional containing the element at the specified position, or empty if out of bounds
     */
    public Optional<JsonValue> get(int index) {
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
        JsonValue jsonValue = jsonValues.get(index);
        return jsonValue == null ? defaultValue : jsonValue;
    }

    /**
     * Return an unmodifiable list of all values in this array.
     *
     * @return an unmodifiable list of JsonValue instances
     */
    public List<JsonValue> values() {
        return List.copyOf(jsonValues);
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
        if (this.jsonValues.size() != that.values().size()) {
            return false;
        }
        for (int i = 0; i < this.jsonValues.size(); i++) {
             if (!this.jsonValues.get(i).equals(that.values().get(i))) {
                 return false;
             }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(jsonValues);
    }

    @Override
    byte jsonStartChar() {
        return '[';
    }
}
