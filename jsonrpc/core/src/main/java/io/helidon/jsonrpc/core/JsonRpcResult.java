/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
package io.helidon.jsonrpc.core;

import java.util.Optional;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;

/**
 * A representation of a JSON-RPC response result.
 */
public interface JsonRpcResult {

    /**
     * Create an instance from a JSON value.
     *
     * @param result the value
     * @return a new instance of this class
     */
    static JsonRpcResult create(JsonValue result) {
        return new JsonRpcResultImpl(result);
    }

    /**
     * Access a response result as a JSON object.
     *
     * @return a JSON object
     * @throws io.helidon.json.JsonException if not a JSON object
     */
    JsonObject asJsonObject();

    /**
     * Access a response result as a JSON array.
     *
     * @return a JSON array
     * @throws io.helidon.json.JsonException if not a JSON array
     */
    JsonArray asJsonArray();

    /**
     * Access a response result as a JSON value.
     *
     * @return a JSON value
     */
    JsonValue asJsonValue();

    /**
     * Get a JSON object property value as a JSON value.
     *
     * @param name property name
     * @return the property value
     * @throws io.helidon.json.JsonException if not a JSON object
     * @throws IllegalArgumentException if the property does not exist
     */
    JsonValue get(String name);

    /**
     * Get a JSON object property value as a string.
     *
     * @param name property name
     * @return the property value as a string
     * @throws io.helidon.json.JsonException if not a JSON object or value not a string
     * @throws IllegalArgumentException if the property does not exist
     */
    String getString(String name);

    /**
     * Get a JSON object property value as a JSON value, if present.
     *
     * @param name property name
     * @return an optional property value
     * @throws io.helidon.json.JsonException if not a JSON object
     */
    Optional<JsonValue> find(String name);

    /**
     * Get a JSON array value by index as a JSON value.
     *
     * @param index the index
     * @return the JSON value
     * @throws io.helidon.json.JsonException if not a JSON array
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    JsonValue get(int index);

    /**
     * Get a JSON array value by index as a string.
     *
     * @param index the index
     * @return the property value as a string
     * @throws io.helidon.json.JsonException if not a JSON array or value not a string
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    String getString(int index);

    /**
     * Get a JSON array value by index as a JSON value, if present.
     *
     * @param index the index
     * @return the optional property value
     * @throws io.helidon.json.JsonException if not a JSON array
     */
    Optional<JsonValue> find(int index);

    /**
     * Access a response result as a Java object. This method binds the result
     * using Helidon JSON binding.
     *
     * @param type the bean class
     * @param <T>  the bean type
     * @return an instance of the bean type
     * @throws io.helidon.json.binding.JsonBindingException if an error occurs during mapping
     */
    <T> T as(Class<T> type);
}
