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
import java.util.Objects;

/**
 * Aggregates zero or more JSON-RPC handlers in a map using method
 * names as keys.
 */
public class JsonRpcHandlers {

    private final JsonRpcErrorHandler errorHandler;
    private final Map<String, JsonRpcHandler> handlers;

    private JsonRpcHandlers(Builder builder) {
        this.handlers = builder.handlers;
        this.errorHandler = builder.errorHandler;
    }

    /**
     * Return a new builder for this class.
     *
     * @return a new builder
     */
    public static JsonRpcHandlers.Builder builder() {
        return new JsonRpcHandlers.Builder();
    }

    /**
     * Create an instance of this class from a single method and handler pair.
     *
     * @param method the method name
     * @param handler the handler
     * @return a newly created instance of this class
     */
    public static JsonRpcHandlers create(String method, JsonRpcHandler handler) {
        return builder().method(method, handler).build();
    }

    /**
     * Return a map of method names to handles.
     *
     * @return a map
     */
    Map<String, JsonRpcHandler> handlersMap() {
        return handlers;
    }

    /**
     * Get access to the error handler, if registered.
     *
     * @return the error handler or {@code null}
     */
    JsonRpcErrorHandler errorHandler() {
        return errorHandler;
    }

    /**
     * A builder for {@link io.helidon.webserver.jsonrpc.JsonRpcHandlers}.
     */
    public static class Builder implements io.helidon.common.Builder<JsonRpcHandlers.Builder, JsonRpcHandlers> {

        private JsonRpcErrorHandler errorHandler;
        private final Map<String, JsonRpcHandler> handlers = new HashMap<>();

        private Builder() {
        }

        @Override
        public JsonRpcHandlers build() {
            return new JsonRpcHandlers(this);
        }

        /**
         * Add a new method and its handler.
         *
         * @param method  method name
         * @param handler the handler
         * @return this builder
         */
        public Builder method(String method, JsonRpcHandler handler) {
            Objects.requireNonNull(method);
            Objects.requireNonNull(handler);
            handlers.put(method, handler);
            return this;
        }

        /**
         * Register an error handler to process any erroneous requests.
         *
         * @param handler the error handler
         * @return this builder
         */
        public Builder error(JsonRpcErrorHandler handler) {
            errorHandler = handler;
            return this;
        }
    }
}
