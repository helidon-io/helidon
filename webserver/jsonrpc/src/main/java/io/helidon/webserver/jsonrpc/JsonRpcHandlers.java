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
import java.util.Optional;

/**
 * Aggregates zero or more JSON-RPC handlers based on JSON-RPC method names.
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
     * @return a map of method names to handlers
     */
    Map<String, JsonRpcHandler> handlersMap() {
        return handlers;
    }

    /**
     * Get access to the error handler, if registered.
     *
     * @return the error handler or {@code null}
     */
    Optional<JsonRpcErrorHandler> errorHandler() {
        return Optional.ofNullable(errorHandler);
    }

    /**
     * Merge these JSON-RPC handlers with another instance. The error and method
     * handlers in {@code other} shall take precedence over those in this instance.
     * Returns a new instance since this class is immutable.
     *
     * @param other the other instance
     * @return newly created JSON-RPC handler
     */
    JsonRpcHandlers merge(JsonRpcHandlers other) {
        JsonRpcHandlers.Builder builder = JsonRpcHandlers.builder();
        if (other.errorHandler != null) {
            builder.errorHandler(other.errorHandler);   // other overrides
        } else if (errorHandler != null) {
            builder.errorHandler(errorHandler);
        }
        handlers.forEach(builder::method);
        other.handlers.forEach(builder::method);        // other overrides
        return builder.build();
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
        public Builder errorHandler(JsonRpcErrorHandler handler) {
            Objects.requireNonNull(handler);
            errorHandler = handler;
            return this;
        }
    }
}
