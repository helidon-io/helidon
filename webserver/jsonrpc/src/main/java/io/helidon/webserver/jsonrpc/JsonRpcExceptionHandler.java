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

/**
 * An exception handler that can be registered to map exceptions thrown in method
 * handlers to {@code JsonRpcError}s.
 */
@FunctionalInterface
public interface JsonRpcExceptionHandler extends ServerLifecycle {

    /**
     * Handler for exceptions thrown in JSON-RPC method handlers.
     *
     * @param req the server request
     * @param res the server response
     * @param throwable the throwable thrown by the method handler
     * @return an optional JSON-RPC error to be returned to the client. If the returned
     *         optional is empty, then an error with code {@link JsonRpcError#INTERNAL_ERROR}
     *         is sent instead.
     * @throws Exception if an unexpected condition is found
     */
    Optional<JsonRpcError> handle(JsonRpcRequest req, JsonRpcResponse res, Throwable throwable) throws Exception;
}
