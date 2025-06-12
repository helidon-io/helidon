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

import io.helidon.webclient.api.ClientRequest;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * A representation of JSON-RPC client request.
 */
public interface JsonRpcClientRequest extends ClientRequest<JsonRpcClientRequest> {

    /**
     * Set a JSON-RPC method on this request.
     *
     * @param rpcMethod the JSON-RPC method
     * @return this request
     */
    JsonRpcClientRequest rpcMethod(String rpcMethod);

    /**
     * Set a JSON-RPC ID on this request.
     *
     * @param value the ID as a JSON value
     * @return this request
     */
    JsonRpcClientRequest rpcId(JsonValue value);

    /**
     * Set a JSON-RPC ID on this request.
     *
     * @param value the ID as an int
     * @return this request
     */
    default JsonRpcClientRequest rpcId(int value) {
        return rpcId(Json.createValue(value));
    }

    /**
     * Set a JSON-RPC ID on this request.
     *
     * @param value the ID as a string
     * @return this request
     */
    default JsonRpcClientRequest rpcId(String value) {
        return rpcId(Json.createValue(value));
    }

    /**
     * Set a named param on this request.
     *
     * @param name the name
     * @param value the value as a JSON value
     * @return this request
     */
    JsonRpcClientRequest param(String name, JsonValue value);

    /**
     * Set a named param on this request.
     *
     * @param name the name
     * @param value the value as a string
     * @return this request
     */
    default JsonRpcClientRequest param(String name, String value) {
        return param(name, Json.createValue(value));
    }

    /**
     * Set a named param on this request.
     *
     * @param name the name
     * @param value the value as an int
     * @return this request
     */
    default JsonRpcClientRequest param(String name, int value) {
        return param(name, Json.createValue(value));
    }

    /**
     * Set array param value on this request.
     *
     * @param value the value as JSON value
     * @return this request
     */
    JsonRpcClientRequest addParam(JsonValue value);

    /**
     * Set array param value on this request.
     *
     * @param value the value as int value
     * @return this request
     */
    default JsonRpcClientRequest addParam(int value) {
        return addParam(Json.createValue(value));
    }

    /**
     * Set array param value on this request.
     *
     * @param value the value as a string
     * @return this request
     */
    default JsonRpcClientRequest addParam(String value) {
        return addParam(Json.createValue(value));
    }

    /**
     * Submit this request to the server and get a response.
     *
     * @return a response
     */
    JsonRpcClientResponse submit();

    /**
     * Get a complete representation of this request as a JSON object.
     * This method can be useful when running over other transports.
     *
     * @return this request as a JSON object
     */
    JsonObject asJsonObject();

    /**
     * Add this request to the ongoing batch.
     *
     * @return the batch
     * @throws java.lang.IllegalStateException if not part of a batch
     */
    JsonRpcClientBatchRequest addToBatch();
}
