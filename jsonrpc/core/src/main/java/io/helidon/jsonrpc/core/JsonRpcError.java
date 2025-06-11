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

package io.helidon.jsonrpc.core;

import java.util.Optional;

import jakarta.json.JsonValue;

/**
 * A representation of a JSON-RPC error. Includes bean-like getter methods for
 * JSON serialization to work.
 */
public class JsonRpcError {

    /**
     * Invalid JSON was received by the server.
     */
    public static final int PARSE_ERROR = -32700;

    /**
     * The JSON sent is not a valid Request object.
     */
    public static final int INVALID_REQUEST = -32600;

    /**
     * The method does not exist or is not available.
     */
    public static final int METHOD_NOT_FOUND = -32601;

    /**
     * Invalid method parameter(s).
     */
    public static final int INVALID_PARAMS = -32602;

    /**
     * Internal JSON-RPC error.
     */
    public static final int INTERNAL_ERROR = -32603;

    private final int code;
    private final String message;
    private final JsonValue data;

    protected JsonRpcError(Builder builder) {
        this.code = builder.code;
        this.message = builder.message;
        this.data = builder.data;
    }

    /**
     * Get a builder for this class.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the error code.
     *
     * @return the error code.
     */
    public int code() {
        return code;
    }

    /**
     * Get the message for this error.
     *
     * @return the message
     */
    public String message() {
        return message;
    }

    /**
     * Get the data associated with this error, if defined.
     *
     * @return optional data
     */
    public Optional<Object> data() {
        return Optional.ofNullable(data);
    }

    // -- getters used for JSONB serialization

    /**
     * Get the error code. For serialization.
     *
     * @return the error code.
     */
    public int getCode() {
        return code();
    }

    /**
     * Get the message for this error. For serialization.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Get the data associated with this error.
     *
     * @return optional data or {@code null} if not defined
     */
    public Object getData() {
        return data();
    }

    /**
     * Builder for {@link io.helidon.jsonrpc.core.JsonRpcError}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, JsonRpcError> {

        private int code = Integer.MAX_VALUE;
        private String message = "Error processing request";
        private JsonValue data;

        private Builder() {
        }

        @Override
        public JsonRpcError build() {
            if (code == Integer.MAX_VALUE) {
                throw new IllegalStateException("An error code is required");
            }
            return new JsonRpcError(this);
        }

        /**
         * Update the error code in this builder.
         *
         * @param code the error code
         * @return this builder
         */
        public Builder code(int code) {
            this.code = code;
            return this;
        }

        /**
         * Update the error message in this builder.
         *
         * @param message the error message
         * @return this builder
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * Update the error data in this builder.
         *
         * @param data the error data
         * @return this builder
         */
        public Builder data(JsonValue data) {
            this.data = data;
            return this;
        }

        /**
         * Update the error data in this builder.
         *
         * @param data the error data as an object
         * @return this builder
         */
        public Builder data(Object data) {
            this.data = JsonUtil.jsonbToJsonp(data);
            return this;
        }
    }
}
