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

import io.helidon.http.Status;

import jakarta.json.JsonValue;

abstract class JsonRpcResponseImpl implements JsonRpcResponse {

    private Integer id;
    private JsonValue result;
    private JsonRpcError error;
    private Status status = Status.OK_200;

    @Override
    public JsonRpcResponse id(int id) {
        this.id = id;
        return this;
    }

    @Override
    public JsonRpcResponse result(JsonValue result) {
        this.result = result;
        return this;
    }

    @Override
    public JsonRpcResponse error(JsonRpcError error) {
        this.error = error;
        return this;
    }

    @Override
    public JsonRpcResponse status(int status) {
        this.status = Status.create(status);
        return this;
    }

    @Override
    public JsonRpcResponse result(Object object) {
        result = JsonUtil.jsonbToJsonp(object);
        return this;
    }

    @Override
    public Optional<Integer> id() {
        return Optional.ofNullable(id);
    }

    @Override
    public Optional<JsonValue> result() {
        return Optional.ofNullable(result);
    }


    @Override
    public Optional<JsonRpcError> error() {
        return Optional.ofNullable(error);
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public abstract void send();
}
