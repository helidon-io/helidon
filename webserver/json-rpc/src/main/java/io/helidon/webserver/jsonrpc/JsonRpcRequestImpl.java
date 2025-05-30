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

import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;

class JsonRpcRequestImpl implements JsonRpcRequest {

    private final JsonObject json;

    JsonRpcRequestImpl(JsonObject json) {
        this.json = json;
    }

    @Override
    public String version() {
        return json.getString("jsonrpc");
    }

    @Override
    public String method() {
        return json.getString("method");
    }

    @Override
    public Optional<Integer> id() {
        return json.containsKey("id")
                ? Optional.of(json.getInt("id"))
                : Optional.empty();
    }

    @Override
    public JsonRpcParams params() {
        return new JsonRpcParamsImpl((JsonStructure) json.get("params"));
    }
}
