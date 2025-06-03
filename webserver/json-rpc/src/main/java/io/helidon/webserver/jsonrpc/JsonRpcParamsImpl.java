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

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

class JsonRpcParamsImpl implements JsonRpcParams {

    private final JsonStructure params;

    JsonRpcParamsImpl(JsonStructure params) {
        this.params = params;
    }

    @Override
    public boolean isPresent() {
        return params != null;
    }

    @Override
    public JsonObject asJsonObject() {
        return params.asJsonObject();
    }

    @Override
    public JsonValue get(String name) {
        JsonValue value = asJsonObject().get(name);
        if (value == null) {
            throw new IllegalArgumentException("Unable to find param " + name);
        }
        return value;
    }

    @Override
    public String getString(String name) {
        return ((JsonString) get(name)).getString();
    }

    @Override
    public Optional<JsonValue> optionalGet(String name) {
        return Optional.ofNullable(asJsonObject().get(name));
    }

    @Override
    public JsonArray asJsonArray() {
        return params.asJsonArray();
    }

    @Override
    public JsonValue get(int index) {
        return asJsonArray().get(index);
    }

    @Override
    public String getString(int index) {
        return ((JsonString) get(index)).getString();
    }

    @Override
    public Optional<JsonValue> optionalGet(int index) {
        JsonArray array = asJsonArray();
        if (index >= 0 && index < array.size()) {
            return Optional.of(array.get(index));
        }
        return Optional.empty();
    }

    @Override
    public <T> T as(Class<T> type) throws Exception {
        return JsonUtil.jsonpToJsonb(params, type);
    }

    @Override
    public <T> T getAs(String name, Class<T> type) throws Exception {
        return JsonUtil.jsonpToJsonb(get(name), type);
    }

    @Override
    public <T> T getAs(int index, Class<T> type) throws Exception {
        return JsonUtil.jsonpToJsonb(get(index), type);
    }
}
