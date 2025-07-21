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
package io.helidon.webserver.jsonrpc;

import java.util.Optional;

import io.helidon.http.Status;
import io.helidon.jsonrpc.core.JsonRpcError;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * A representation of a JSON-RPC response.
 */
public interface JsonRpcResponse extends ServerResponse {

    /**
     * Set a JSON-RPC ID for this response.
     *
     * @param rpcId the ID
     * @return this response
     */
    JsonRpcResponse rpcId(JsonValue rpcId);

    /**
     * Set a JSON-RPC ID for this response as an int.
     *
     * @param rpcId the ID
     * @return this response
     */
    default JsonRpcResponse rpcId(int rpcId) {
        return rpcId(Json.createValue(rpcId));
    }

    /**
     * Set a JSON-RPC ID for this response as a string.
     *
     * @param rpcId the ID
     * @return this response
     */
    default JsonRpcResponse rpcId(String rpcId) {
        return rpcId(Json.createValue(rpcId));
    }

    /**
     * Set a result for this response as a JSON value.
     *
     * @param result the result
     * @return this response
     * @see #error()
     */
    JsonRpcResponse result(JsonValue result);

    /**
     * Set a result as an arbitrary object that can be mapped to JSON. This
     * method will serialize the parameter using JSONB.
     *
     * @param object the object
     * @return this response
     * @throws jakarta.json.JsonException if an error occurs during serialization
     * @see #error()
     */
    JsonRpcResponse result(Object object);

    /**
     * Set a JSON-RPC error on this response with a code and a message.
     *
     * @param code the error code
     * @param message the error message
     * @return this response
     * @see #result()
     */
    JsonRpcResponse error(int code, String message);

    /**
     * Set a JSON-RPC error on this response with a code, a message and
     * some associated data.
     *
     * @param code the error code
     * @param message the error message
     * @param data the data
     * @return this response
     * @see #result()
     */
    JsonRpcResponse error(int code, String message, JsonValue data);

    /**
     * Set an HTTP status for the underlying response. Normally this will be
     * set by Helidon, but this method allows to override the default values.
     * The default value is {@link io.helidon.http.Status#OK_200_CODE}.
     *
     * @param status the status
     * @return this response
     */
    JsonRpcResponse status(int status);

    /**
     * Set an HTTP status for the underlying response. Normally this will be
     * set by Helidon, but this method allows to override the default values.
     * The default value is {@link io.helidon.http.Status#OK_200}.
     *
     * @param status the status
     * @return this response
     */
    default JsonRpcResponse status(Status status) {
        return status(status.code());
    }

    /**
     * Get the JSON-RPC ID set on this response.
     *
     * @return the ID
     */
    Optional<JsonValue> rpcId();

    /**
     * Get the result set on this response.
     *
     * @return the result as a JSON value
     */
    Optional<JsonValue> result();

    /**
     * Get an error set on this response.
     *
     * @return the error
     */
    Optional<JsonRpcError> error();

    /**
     * Get the status set on this response.
     *
     * @return the status
     */
    Status status();

    /**
     * Send this response over the wire to the client. This method blocks
     * until the response is delivered.
     */
    void send();

    /**
     * Get a complete response as a JSON object.
     *
     * @return a JSON object that represents the response
     */
    JsonObject asJsonObject();
}
