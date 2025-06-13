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

/**
 * The JSON-RPC rules on which handlers can be registered.
 */
public interface JsonRpcRules {

    /**
     * Register JSON-RPC handlers on a given path pattern.
     *
     * @param pathPattern the path pattern
     * @param handlers the handlers
     * @return the rules instance
     */
    JsonRpcRules register(String pathPattern, JsonRpcHandlers handlers);

    /**
     * Register a single JSON-RPC handler given a method and path pattern.
     *
     * @param pathPattern the path pattern
     * @param method the method name
     * @param handler the handler
     * @return the rules instance
     */
    JsonRpcRules register(String pathPattern, String method, JsonRpcHandler handler);
}
