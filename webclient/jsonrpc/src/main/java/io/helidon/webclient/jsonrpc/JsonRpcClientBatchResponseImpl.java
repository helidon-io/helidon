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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.spi.Source;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

/**
 * A representation of JSON-RPC client batch response.
 */
class JsonRpcClientBatchResponseImpl implements JsonRpcClientBatchResponse {
    private static final JsonArray EMPTY_JSON_ARRAY = Json.createArrayBuilder().build();

    private final HttpClientResponse delegate;
    private JsonArray jsonArray;
    private List<JsonRpcClientResponse> responses;

    JsonRpcClientBatchResponseImpl(HttpClientResponse delegate) {
        this.delegate = delegate;
    }

    @Override
    public int size() {
        return asJsonArray().size();
    }

    @Override
    public JsonRpcClientResponse get(int index) {
        if (responses == null) {
            responses = new ArrayList<>();
            JsonArray array = asJsonArray();
            for (jakarta.json.JsonValue jsonValue : array) {
                JsonObject object = jsonValue.asJsonObject();
                responses.add(new JsonRpcClientResponseImpl(delegate, object));
            }
        }
        return responses.get(index);
    }

    @Override
    public Iterator<JsonRpcClientResponse> iterator() {
        JsonArray array = asJsonArray();
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return array.size() > index;
            }

            @Override
            public JsonRpcClientResponse next() {
                return get(index++);
            }
        };
    }

    public JsonArray asJsonArray() {
        if (jsonArray == null) {
            ClientResponseHeaders headers = delegate.headers();
            if (headers.contains(HeaderNames.CONTENT_TYPE)) {
                Optional<String> contentType = headers.first(HeaderNames.CONTENT_TYPE);
                if (contentType.isEmpty()
                        || !contentType.get().equalsIgnoreCase(MediaTypes.APPLICATION_JSON_VALUE)) {
                    throw new IllegalStateException("Response contains invalid Content-Type header");
                }
                jsonArray = delegate.entity().as(JsonArray.class);
            } else {
                jsonArray = EMPTY_JSON_ARRAY;
            }
        }
        return jsonArray;
    }

    @Override
    public ReadableEntity entity() {
        return delegate.entity();
    }

    @Override
    public InputStream inputStream() {
        return delegate.inputStream();
    }

    @Override
    public <T> T as(Class<T> type) {
        return delegate.as(type);
    }

    @Override
    public <T extends Source<?>> void source(GenericType<T> sourceType, T source) {
        delegate.source(sourceType, source);
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
