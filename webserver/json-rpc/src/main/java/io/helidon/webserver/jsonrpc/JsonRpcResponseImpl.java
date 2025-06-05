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

import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.ServerResponseTrailers;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import static io.helidon.webserver.jsonrpc.JsonUtil.jsonbToJsonp;

abstract class JsonRpcResponseImpl implements JsonRpcResponse {

    private Integer responseId;
    private JsonValue result;
    private JsonRpcError error;
    private Status status = Status.OK_200;

    private final ServerResponse delegate;

    JsonRpcResponseImpl(ServerResponse delegate) {
        this.delegate = delegate;
    }

    @Override
    public JsonRpcResponse jsonId(int id) {
        this.responseId = id;
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
    public Optional<Integer> jsonId() {
        return Optional.ofNullable(responseId);
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
    public ServerResponseHeaders headers() {
        return delegate.headers();
    }

    @Override
    public ServerResponseTrailers trailers() {
        return delegate.trailers();
    }

    @Override
    public abstract void send();

    @Override
    public JsonObject asJsonObject() {
        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("jsonrpc", "2.0");
        if (responseId != null) {
            builder.add("id", responseId);
        }
        if (result != null) {
            builder.add("result", result);
        } else if (error != null) {
            builder.add("error", jsonbToJsonp(error));
        }
        return builder.build();
    }
}
