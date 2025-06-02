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

public class JsonRpcError {

    private final int code;
    private final String message;
    private final Object data;

    protected JsonRpcError(Builder builder) {
        this.code = builder.code;
        this.message = builder.message;
        this.data = builder.data;
    }

    public static JsonRpcError.Builder builder() {
        return new Builder();
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public Optional<Object> data() {
        return Optional.ofNullable(data);
    }

    // -- getters used for JSONB serialization

    public int getCode() {
        return code();
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data();
    }

    /**
     * Builder for {@link io.helidon.webserver.jsonrpc.JsonRpcError}.
     */
    public static class Builder implements io.helidon.common.Builder<JsonRpcError.Builder, JsonRpcError> {

        private int code;
        private String message = "Error processing request";
        private Object data;

        private Builder() {
        }

        @Override
        public JsonRpcError build() {
            return new JsonRpcError(this);
        }

        public Builder code(int code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder data(Object data) {
            this.data = data;
            return this;
        }
    }

}
