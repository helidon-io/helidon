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

    private final JsonStructure json;

    JsonRpcParamsImpl(JsonStructure json) {
        this.json = json;
    }

    @Override
    public JsonObject asJsonObject() {
        return json.asJsonObject();
    }

    @Override
    public JsonValue get(String name) {
        return asJsonObject().get(name);
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
        return json.asJsonArray();
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
}
