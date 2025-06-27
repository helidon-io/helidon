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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An implementation of JSON-RPC rules.
 */
class JsonRpcRulesImpl implements JsonRpcRules, Iterable<JsonRpcRulesImpl.Rule> {

    private final List<Rule> rules = new ArrayList<>();
    private final Map<String, Integer> indexes = new HashMap<>();

    record Rule(String pathPattern, JsonRpcHandlers handlers) {
    }

    @Override
    public JsonRpcRules register(String pathPattern, JsonRpcHandlers handlers) {
        Integer k = indexes.get(pathPattern);
        if (k == null) {
            indexes.put(pathPattern, indexes.size());
            rules.add(new Rule(pathPattern, handlers));
        } else {
            Rule rule = rules.get(k);
            rules.set(k, new Rule(rule.pathPattern, merge(rule.handlers, handlers)));
        }
        return this;
    }

    @Override
    public JsonRpcRules register(String pathPattern, String method, JsonRpcHandler handler) {
        JsonRpcHandlers.Builder builder = JsonRpcHandlers.builder();
        builder.putMethod(method, handler);
        return register(pathPattern, builder.build());
    }

    @Override
    public Iterator<Rule> iterator() {
        return rules.iterator();
    }

    /**
     * Merges two JSON-RPC handlers instances, left and right, with the right
     * taking precedence over the left.
     *
     * @param left the left
     * @param right the right
     * @return newly created handlers instances
     */
    private static JsonRpcHandlers merge(JsonRpcHandlers left, JsonRpcHandlers right) {
        JsonRpcHandlers.Builder builder = JsonRpcHandlers.builder();
        left.errorHandler().ifPresent(builder::errorHandler);
        right.errorHandler().ifPresent(builder::errorHandler);      // overrides
        builder.handlersMap(left.handlersMap());
        builder.handlersMap(right.handlersMap());                   // overrides
        return builder.build();
    }
}
