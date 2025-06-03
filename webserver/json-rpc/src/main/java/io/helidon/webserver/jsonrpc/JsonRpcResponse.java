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

import jakarta.json.JsonValue;

/**
 * A representation of a JSON-RPC response.
 */
public interface JsonRpcResponse {

    /**
     * Set an ID for this response.
     *
     * @param id the ID
     * @return this response
     */
    JsonRpcResponse id(int id);

    /**
     * Set a result for this response as a JSON value.
     *
     * @param result the result
     * @return this response
     */
    JsonRpcResponse result(JsonValue result);

    /**
     * Set a result as an arbitrary bean that can be mapped to JSON. This
     * method will attempt to serialize the parameter using JSONB.
     *
     * @param object the object
     * @return this response
     * @throws Exception if an exception occurs during mapping
     */
    JsonRpcResponse result(Object object) throws Exception;

    /**
     * Set an JSON-RPC error on this response. Will be part of serialized
     * response only if no result has been set.
     *
     * @param error the error
     * @return this response
     * @see #result()
     */
    JsonRpcResponse error(JsonRpcError error);

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
     * Get the ID set on this response.
     *
     * @return the ID
     */
    Optional<Integer> id();

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
}
