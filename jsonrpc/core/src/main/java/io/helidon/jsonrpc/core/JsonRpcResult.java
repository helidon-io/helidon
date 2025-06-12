/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * A representation of a JSON-RPC response result.
 */
public interface JsonRpcResult {

    /**
     * Access a response result as a JSON object.
     *
     * @return a JSON object
     * @throws ClassCastException if not a JSON object
     */
    JsonObject asJsonObject();

    /**
     * Access a response result as a JSON array.
     *
     * @return a JSON array
     * @throws ClassCastException if not a JSON array
     */
    JsonArray asJsonArray();

    /**
     * Access a response result as a JSON value.
     *
     * @return a JSON structure
     */
    JsonValue asJsonValue();

    /**
     * Get a property value of a JSON object result.
     *
     * @param name property name
     * @return the property value
     * @throws ClassCastException       if not a JSON object
     * @throws IllegalArgumentException if the property does not exist
     */
    JsonValue get(String name);

    /**
     * Get a property value of a JSON object result as a string.
     *
     * @param name property name
     * @return the property value as a string
     * @throws ClassCastException       if not a JSON object
     * @throws IllegalArgumentException if the property does not exist
     */
    String getString(String name);

    /**
     * Get a property value of a JSON object result as a string, if present.
     *
     * @param name property name
     * @return an optional property value
     * @throws ClassCastException if not a JSON object
     */
    Optional<JsonValue> optionalGet(String name);

    /**
     * Get a JSON value by index from a JSON array.
     *
     * @param index the index
     * @return the JSON value
     * @throws ClassCastException        if not a JSON array
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    JsonValue get(int index);

    /**
     * Get property value as a string by index from a JSON array.
     *
     * @param index the index
     * @return the property value as a string
     * @throws ClassCastException        if not a JSON array
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    String getString(int index);

    /**
     * Get property value as a string by index from a JSON array, if present.
     *
     * @param index the index
     * @return the optional property value as a string
     * @throws ClassCastException        if not a JSON array
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    Optional<JsonValue> optionalGet(int index);

    /**
     * Access a response result as a Java object. This method will attempt to
     * bind the result using JSONB.
     *
     * @param type the bean class
     * @param <T>  the bean type
     * @return an instance of the bean type
     * @throws jakarta.json.bind.JsonbException if an error occurs during mapping
     */
    <T> T as(Class<T> type);
}
