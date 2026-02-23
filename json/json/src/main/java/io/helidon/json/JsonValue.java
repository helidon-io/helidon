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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Base class for all JSON value types in Helidon JSON processing.
 */
public abstract sealed class JsonValue
        permits JsonArray, JsonBoolean, JsonControlValue, JsonNoopValue, JsonNull, JsonNumber, JsonObject, JsonString {

    static final byte[] EMPTY_BYTES = new byte[0];

    /**
     * The JsonValue constructor.
     */
    JsonValue() {
    }

    /**
     * A new fluent API builder to construct an {@link JsonObject}.
     *
     * @return a new builder
     */
    public static JsonObject.Builder objectBuilder() {
        return JsonObject.builder();
    }

    /**
     * Return the type of this JSON value.
     *
     * @return the JsonValueType of this value
     */
    public abstract JsonValueType type();

    /**
     * Write this JSON value to the provided generator.
     *
     * @param generator the generator to write to
     */
    public abstract void toJson(JsonGenerator generator);

    @Override
    public String toString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator.create(baos)
                .write(this)
                .close();
        return baos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Return the byte character that starts this JSON value type.
     *
     * @return the starting byte character ('{', '[', '"', etc.)
     */
    abstract byte jsonStartChar();

    /**
     * Cast this value to a JsonString if it is of string type.
     *
     * @return this value as a JsonString
     * @throws JsonException if this value is not a string
     */
    public JsonString asString() {
        if (type() == JsonValueType.STRING) {
            return (JsonString) this;
        }
        throw new JsonException("Json value is not a string, but rather " + type());
    }

    /**
     * Cast this value to a JsonNumber if it is of number type.
     *
     * @return this value as a JsonNumber
     * @throws JsonException if this value is not a number
     */
    public JsonNumber asNumber() {
        if (type() == JsonValueType.NUMBER) {
            return (JsonNumber) this;
        }
        throw new JsonException("Json value is not a number, but rather " + type());
    }

    /**
     * Cast this value to a JsonObject if it is of object type.
     *
     * @return this value as a JsonObject
     * @throws JsonException if this value is not an object
     */
    public JsonObject asObject() {
        if (type() == JsonValueType.OBJECT) {
            return (JsonObject) this;
        }
        throw new JsonException("Json value is not an object, but rather " + type());
    }

    /**
     * Cast this value to a JsonArray if it is of array type.
     *
     * @return this value as a JsonArray
     * @throws JsonException if this value is not an array
     */
    public JsonArray asArray() {
        if (type() == JsonValueType.ARRAY) {
            return (JsonArray) this;
        }
        throw new JsonException("Json value is not an array, but rather " + type());
    }

    /**
     * Cast this value to a JsonBoolean if it is of boolean type.
     *
     * @return this value as a JsonBoolean
     * @throws JsonException if this value is not a boolean
     */
    public JsonBoolean asBoolean() {
        if (type() == JsonValueType.BOOLEAN) {
            return (JsonBoolean) this;
        }
        throw new JsonException("Json value is not a boolean, but rather " + type());
    }
}
