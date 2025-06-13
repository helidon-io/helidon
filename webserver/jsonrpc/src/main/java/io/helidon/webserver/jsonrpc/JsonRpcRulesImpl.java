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

import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of JSON-RPC rules.
 */
class JsonRpcRulesImpl implements JsonRpcRules {

    private final Map<String, JsonRpcHandlers> rules = new HashMap<>();

    @Override
    public JsonRpcRules register(String pathPattern, JsonRpcHandlers handlers) {
        rules.put(pathPattern, handlers);
        return this;
    }

    @Override
    public JsonRpcRules register(String pathPattern, String method, JsonRpcHandler handler) {
        JsonRpcHandlers handlers = JsonRpcHandlers.create(method, handler);
        rules.put(pathPattern, handlers);
        return this;
    }

    Map<String, JsonRpcHandlers> rulesMap() {
        return rules;
    }
}
