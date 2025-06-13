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

import java.util.Objects;
import java.util.Optional;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

/**
 * An implementation of a JSON-RPC error.
 */
public class JsonRpcErrorImpl implements JsonRpcError {

    private final JsonObject error;

    /**
     * Create an instance from a JSON object.
     *
     * @param error the object
     * @return a new instance of this class
     */
    public static JsonRpcErrorImpl create(JsonObject error) {
        return new JsonRpcErrorImpl(error);
    }

    /**
     * Create an instance from a code and a message.
     *
     * @param code    the error code
     * @param message the message
     * @return a new instance of this class
     */
    public static JsonRpcErrorImpl create(int code, String message) {
        return create(code, message, null);
    }

    /**
     * Create an instance from a code, a message and data.
     *
     * @param code    the error code
     * @param message the message
     * @param data    the associated data or {@code null}
     * @return a new instance of this class
     */
    public static JsonRpcErrorImpl create(int code, String message, JsonValue data) {
        Objects.requireNonNull(message, "message is null");
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("code", code);
        builder.add("message", message);
        if (data != null) {
            builder.add("data", data);
        }
        return new JsonRpcErrorImpl(builder.build());
    }

    private JsonRpcErrorImpl(JsonObject error) {
        this.error = Objects.requireNonNull(error);
    }

    @Override
    public int code() {
        return error.getInt("code");
    }

    @Override
    public String message() {
        return error.getString("message");
    }

    @Override
    public Optional<JsonValue> data() {
        return Optional.ofNullable(error.get("data"));
    }

    @Override
    public <T> Optional<T> dataAs(Class<T> type) {
        JsonValue data = error.get("data");
        return data == null ? Optional.empty()
                : Optional.of(JsonUtil.jsonpToJsonb(data.asJsonObject(), type));
    }

    @Override
    public JsonObject asJsonObject() {
        return error;
    }
}
