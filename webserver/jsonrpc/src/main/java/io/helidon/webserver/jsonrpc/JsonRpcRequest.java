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

import io.helidon.jsonrpc.core.JsonRpcParams;
import io.helidon.webserver.http.HttpRequest;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * A representation of a JSON-RPC request.
 */
public interface JsonRpcRequest extends HttpRequest {

    /**
     * The request version. Always "2.0".
     *
     * @return the request version
     */
    String version();

    /**
     * The JSON-RPC request method name.
     *
     * @return the request method
     */
    String rpcMethod();

    /**
     * The JSON-RPC request ID, if present.
     *
     * @return an optional request ID
     */
    Optional<JsonValue> rpcId();

    /**
     * The params associated with the request. If omitted in the request, then
     * internally initialized using {@link JsonValue#EMPTY_JSON_OBJECT}.
     *
     * @return the params
     */
    JsonRpcParams params();

    /**
     * Get a complete request as a JSON object.
     *
     * @return a JSON object that represents the request
     */
    JsonObject asJsonObject();
}
