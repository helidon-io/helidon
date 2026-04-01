/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;

/**
 * An implementation of a JSON-RPC error.
 */
class JsonRpcErrorImpl implements JsonRpcError {

    private final JsonObject error;

    JsonRpcErrorImpl(JsonObject error) {
        this.error = Objects.requireNonNull(error);
    }

    @Override
    public int code() {
        return error.intValue("code")
                .orElseThrow(() -> new IllegalStateException("Missing JSON-RPC error code"));
    }

    @Override
    public String message() {
        return error.stringValue("message")
                .orElseThrow(() -> new IllegalStateException("Missing JSON-RPC error message"));
    }

    @Override
    public Optional<JsonValue> data() {
        return error.value("data");
    }

    @Override
    public <T> Optional<T> dataAs(Class<T> type) {
        return data().map(it -> JsonUtil.jsonToObject(it, type));
    }

    @Override
    public JsonObject asJsonObject() {
        return error;
    }
}
