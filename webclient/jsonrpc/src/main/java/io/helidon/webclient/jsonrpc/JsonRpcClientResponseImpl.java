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
package io.helidon.webclient.jsonrpc;

import java.util.Objects;
import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.media.ReadableEntity;
import io.helidon.jsonrpc.core.JsonRpcError;
import io.helidon.jsonrpc.core.JsonRpcResult;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientResponse;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import static io.helidon.jsonrpc.core.JsonUtil.JSON_BUILDER_FACTORY;

/**
 * An implementation of JSON-RPC client response.
 */
class JsonRpcClientResponseImpl implements JsonRpcClientResponse {
    private static final JsonObject EMPTY_JSON_OBJECT = JSON_BUILDER_FACTORY.createObjectBuilder().build();

    private final HttpClientResponse delegate;
    private JsonObject jsonObject;

    JsonRpcClientResponseImpl(HttpClientResponse delegate) {
        this(delegate, null);
    }

    JsonRpcClientResponseImpl(HttpClientResponse delegate, JsonObject jsonObject) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is null");
        this.jsonObject = jsonObject;
    }

    @Override
    public Optional<JsonValue> rpcId() {
        JsonValue id = asJsonObject().get("id");
        return Optional.ofNullable(id);
    }

    @Override
    public Optional<JsonRpcResult> result() {
        JsonValue result = asJsonObject().get("result");
        return result == null ? Optional.empty() : Optional.of(JsonRpcResult.create(result));
    }

    @Override
    public Optional<JsonRpcError> error() {
        try {
            JsonObject error = asJsonObject().getJsonObject("error");
            return Optional.ofNullable(error == null ? null : JsonRpcError.create(error));
        } catch (ClassCastException e) {
            return Optional.empty();
        }
    }

    @Override
    public JsonObject asJsonObject() {
        if (jsonObject == null) {
            ClientResponseHeaders headers = delegate.headers();
            if (headers.contains(HeaderNames.CONTENT_TYPE)) {
                Optional<String> contentType = headers.first(HeaderNames.CONTENT_TYPE);
                if (contentType.isEmpty()
                        || !contentType.get().equalsIgnoreCase(MediaTypes.APPLICATION_JSON_VALUE)) {
                    throw new IllegalStateException("Response contains invalid Content-Type header");
                }
                jsonObject = delegate.entity().as(JsonObject.class);
            } else {
                jsonObject = EMPTY_JSON_OBJECT;
            }
        }
        return jsonObject;
    }

    @Override
    public ReadableEntity entity() {
        return delegate.entity();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public Status status() {
        return delegate.status();
    }

    @Override
    public ClientResponseHeaders headers() {
        return delegate.headers();
    }

    @Override
    public ClientResponseTrailers trailers() {
        return delegate.trailers();
    }

    @Override
    public ClientUri lastEndpointUri() {
        return delegate.lastEndpointUri();
    }
}
