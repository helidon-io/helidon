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

import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Blueprint for JSON-RPC handlers. Each handler must have a method name
 * associated with it.
 */
@Prototype.Blueprint
interface JsonRpcHandlersBlueprint {

    /**
     * Return a map of method names to handles.
     *
     * @return a map of method names to handlers
     */
    @Option.Singular(value = "method", withPrefix = false)
    Map<String, JsonRpcHandler> handlersMap();

    /**
     * Get access to the error handler, if registered.
     *
     * @return the error handler or {@code null}
     */
    Optional<JsonRpcErrorHandler> errorHandler();
}
