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

import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;

/**
 * An implementation of {@link io.helidon.jsonrpc.core.JsonRpcParams}.
 */
class JsonRpcParamsImpl implements JsonRpcParams {

    private final JsonValue params;

    JsonRpcParamsImpl(JsonValue params) {
        this.params = Objects.requireNonNull(params);
    }

    @Override
    public JsonValue asJsonValue() {
        return params;
    }

    @Override
    public JsonObject asJsonObject() {
        return params.asObject();
    }

    @Override
    public JsonArray asJsonArray() {
        return params.asArray();
    }

    @Override
    public JsonValue get(String name) {
        return find(name)
                .orElseThrow(() -> new IllegalArgumentException("Unable to find param " + name));
    }

    @Override
    public String getString(String name) {
        return get(name).asString().value();
    }

    @Override
    public Optional<JsonValue> find(String name) {
        return asJsonObject().value(name);
    }

    @Override
    public JsonValue get(int index) {
        return asJsonArray().get(index)
                .orElseThrow(() -> new IndexOutOfBoundsException("Unable to find param at index " + index));
    }

    @Override
    public String getString(int index) {
        return get(index).asString().value();
    }

    @Override
    public Optional<JsonValue> find(int index) {
        return asJsonArray().get(index);
    }

    @Override
    public <T> T as(Class<T> type) {
        return JsonUtil.jsonToObject(asJsonValue(), type);
    }
}
