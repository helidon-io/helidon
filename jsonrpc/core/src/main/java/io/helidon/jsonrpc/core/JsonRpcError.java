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

import java.util.Objects;
import java.util.Optional;

import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import static io.helidon.jsonrpc.core.JsonUtil.JSON_BUILDER_FACTORY;

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
     * Create an instance from a JSON object.
     *
     * @param error the object
     * @return a new instance of this class
     */
    static JsonRpcError create(JsonObject error) {
        return new JsonRpcErrorImpl(error);
    }

    /**
     * Create an instance from a code and a message.
     *
     * @param code    the error code
     * @param message the message
     * @return a new instance of this class
     */
    static JsonRpcError create(int code, String message) {
        Objects.requireNonNull(message, "message is null");
        JsonObjectBuilder builder = JSON_BUILDER_FACTORY.createObjectBuilder();
        builder.add("code", code);
        builder.add("message", message);
        return new JsonRpcErrorImpl(builder.build());
    }

    /**
     * Create an instance from a code, a message and data.
     *
     * @param code    the error code
     * @param message the message
     * @param data    the associated data
     * @return a new instance of this class
     */
    static JsonRpcError create(int code, String message, JsonValue data) {
        Objects.requireNonNull(message, "message is null");
        Objects.requireNonNull(data, "data is null");
        JsonObjectBuilder builder = JSON_BUILDER_FACTORY.createObjectBuilder();
        builder.add("code", code);
        builder.add("message", message);
        builder.add("data", data);
        return new JsonRpcErrorImpl(builder.build());
    }

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
