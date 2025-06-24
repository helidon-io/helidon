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

import io.helidon.jsonrpc.core.JsonRpcError;
import io.helidon.webserver.ServerLifecycle;
import io.helidon.webserver.http.ServerRequest;

import jakarta.json.JsonObject;

/**
 * A JSON-RPC handler that can process invalid requests if registered.
 */
@FunctionalInterface
public interface JsonRpcErrorHandler extends ServerLifecycle {

    /**
     * Handler for a JSON-RPC erroneous request.
     *
     * @param req        the server request
     * @param jsonObject an invalid JSON-RPC request as a JSON object
     * @return an optional JSON-RPC error to be returned to the client. If empty,
     *         then no error is returned.
     * @throws Exception if an unexpected condition is found
     */
    Optional<JsonRpcError> handle(ServerRequest req, JsonObject jsonObject) throws Exception;
}
