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

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * A representation of a JSON-RPC error.
 */
public interface JsonRpcError {

    /**
     * Invalid JSON was received by the server.
     */
    int PARSE_ERROR = -32700;

    /**
     * The JSON sent is not a valid Request object.
     */
    int INVALID_REQUEST = -32600;

    /**
     * The method does not exist or is not available.
     */
    int METHOD_NOT_FOUND = -32601;

    /**
     * Invalid method parameter(s).
     */
    int INVALID_PARAMS = -32602;

    /**
     * Internal JSON-RPC error.
     */
    int INTERNAL_ERROR = -32603;

    /**
     * Get the code for this error.
     *
     * @return the code
     */
    int code();

    /**
     * Get the message for this error.
     *
     * @return the message
     */
    String message();

    /**
     * Get the data associated with this error, if defined.
     *
     * @return optional data
     */
    Optional<JsonValue> data();

    /**
     * Get the data associated with this error as an object, if defined.
     * This method will use JSONB for binding.
     *
     * @param type the bean class
     * @param <T>  the bean type
     * @return optional data
     * @throws ClassCastException if the data is not a JSON object
     * @throws jakarta.json.bind.JsonbException if an error occurs during mapping
     */
    <T> Optional<T> dataAs(Class<T> type);

    /**
     * Access the error as a JSON object.
     *
     * @return a JSON object
     */
    JsonObject asJsonObject();
}
