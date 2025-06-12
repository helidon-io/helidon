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
package io.helidon.webclient.jsonrpc;

import java.util.Optional;

import io.helidon.jsonrpc.core.JsonRpcError;
import io.helidon.jsonrpc.core.JsonRpcResult;
import io.helidon.webclient.api.HttpClientResponse;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * A representation of a JSON-RPC client response.
 */
public interface JsonRpcClientResponse extends HttpClientResponse {

    /**
     * Get the JSON-RPC ID set on this response.
     *
     * @return the ID
     */
    Optional<JsonValue> rpcId();

    /**
     * Get the result in this response.
     *
     * @return the optional response result
     */
    Optional<JsonRpcResult> result();

    /**
     * Get an error set on this response.
     *
     * @return the optional error
     */
    Optional<JsonRpcError> error();

    /**
     * Get a complete response representation as a JSON object. This method
     * can be useful when running over other transports.
     *
     * @return a JSON object that represents the response
     */
    JsonObject asJsonObject();
}
