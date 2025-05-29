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

public class JsonRpcHandlers {

    private final Map<String, JsonRpcHandler> handlers;

    private JsonRpcHandlers(Builder builder) {
        this.handlers = builder.handlers;
    }

    public static JsonRpcHandlers.Builder builder() {
        return new JsonRpcHandlers.Builder();
    }

    public Map<String, JsonRpcHandler> handlers() {
        return handlers;
    }

    public static class Builder implements io.helidon.common.Builder<JsonRpcHandlers.Builder, JsonRpcHandlers> {

        private final Map<String, JsonRpcHandler> handlers = new HashMap<>();

        private Builder() {
        }

        @Override
        public JsonRpcHandlers build() {
            return new JsonRpcHandlers(this);
        }

        public Builder method(String method, JsonRpcHandler handler) {
            handlers.put(method, handler);
            return this;
        }
    }
}
