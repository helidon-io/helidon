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

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.tls.Tls;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriInfo;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpException;
import io.helidon.http.HttpMediaType;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

/**
 * An implementation of JSON-RPC client request.
 */
class JsonRpcClientRequestImpl implements JsonRpcClientRequest {

    private final Http1ClientRequest delegate;
    private final JsonRpcClientBatchRequest batch;

    private JsonValue rpcId;
    private String rpcMethod;
    private Map<String, JsonValue> namedParams;
    private List<JsonValue> arrayParams;

    JsonRpcClientRequestImpl(Http1Client http1Client, String rpcMethod) {
        this(http1Client, rpcMethod, null);
    }

    JsonRpcClientRequestImpl(Http1Client http1Client, String rpcMethod, JsonRpcClientBatchRequest batch) {
        Objects.requireNonNull(http1Client, "delegate is null");
        this.delegate = http1Client.post();
        this.rpcMethod = rpcMethod;
        this.batch = batch;
    }

    @Override
    public JsonRpcClientRequest rpcMethod(String rpcMethod) {
        this.rpcMethod = rpcMethod;
        return this;
    }

    @Override
    public JsonRpcClientRequest rpcId(JsonValue value) {
        rpcId = value;
        return this;
    }

    @Override
    public JsonRpcClientRequest param(String name, JsonValue value) {
        if (arrayParams != null) {
            throw new IllegalStateException("Cannot mixed named and array params");
        }
        if (namedParams == null) {
            namedParams = new HashMap<>();
        }
        namedParams.put(name, value);
        return this;
    }

    @Override
    public JsonRpcClientRequest addParam(JsonValue value) {
        if (namedParams != null) {
            throw new IllegalStateException("Cannot mixed named and array params");
        }
        if (arrayParams == null) {
            arrayParams = new ArrayList<>();
        }
        arrayParams.add(value);
        return this;
    }

    @Override
    public JsonObject asJsonObject() {
        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("jsonrpc", "2.0");
        if (rpcId != null) {
            builder.add("id", rpcId);
        }
        builder.add("method", rpcMethod);
        if (namedParams != null) {
            JsonObjectBuilder namedBuilder = Json.createObjectBuilder();
            for (Map.Entry<String, JsonValue> entry : namedParams.entrySet()) {
                namedBuilder.add(entry.getKey(), entry.getValue());
            }
            builder.add("params", namedBuilder.build());
        } else if (arrayParams != null) {
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            for (JsonValue value : arrayParams) {
                arrayBuilder.add(value);
            }
            builder.add("params", arrayBuilder.build());
        } else {
            builder.add("params", Json.createObjectBuilder().build());
        }
        return builder.build();
    }

    @Override
    public JsonRpcClientResponse submit() {
        if (rpcMethod == null) {
            throw new IllegalStateException("rpcMethod is null");
        }
        HttpClientResponse res = delegate.header(HeaderNames.CONTENT_TYPE, MediaTypes.APPLICATION_JSON_VALUE)
                .header(HeaderNames.ACCEPT, MediaTypes.APPLICATION_JSON_VALUE)
                .submit(asJsonObject());
        return new JsonRpcClientResponseImpl(res);
    }

    @Override
    public JsonRpcClientBatchRequest addToBatch() {
        if (batch == null) {
            throw new IllegalStateException("batch is null");
        }
        batch.add(this);
        return batch;
    }

    @Override
    public JsonRpcClientRequest uri(String uri) {
        delegate.uri(uri);
        return this;
    }

    @Override
    public JsonRpcClientRequest path(String uri) {
        delegate.path(uri);
        return this;
    }

    @Override
    public JsonRpcClientRequest tls(Tls tls) {
        delegate.tls(tls);
        return this;
    }

    @Override
    public JsonRpcClientRequest proxy(Proxy proxy) {
        delegate.proxy(proxy);
        return this;
    }

    @Override
    public JsonRpcClientRequest uri(URI uri) {
        delegate.uri(uri);
        return this;
    }

    @Override
    public JsonRpcClientRequest uri(ClientUri uri) {
        delegate.uri(uri);
        return this;
    }

    @Override
    public JsonRpcClientRequest header(Header header) {
        delegate.header(header);
        return this;
    }

    @Override
    public JsonRpcClientRequest header(HeaderName name, String... values) {
        delegate.header(name, values);
        return this;
    }

    @Override
    public JsonRpcClientRequest header(HeaderName name, List<String> values) {
        delegate.header(name, values);
        return this;
    }

    @Override
    public JsonRpcClientRequest headers(Headers headers) {
        delegate.headers(headers);
        return this;
    }

    @Override
    public JsonRpcClientRequest headers(Consumer<ClientRequestHeaders> headersConsumer) {
        delegate.headers(headersConsumer);
        return this;
    }

    @Override
    public JsonRpcClientRequest accept(HttpMediaType... accepted) {
        delegate.accept(accepted);
        return this;
    }

    @Override
    public JsonRpcClientRequest accept(MediaType... acceptedTypes) {
        delegate.accept(acceptedTypes);
        return this;
    }

    @Override
    public JsonRpcClientRequest contentType(MediaType contentType) {
        delegate.contentType(contentType);
        return this;
    }

    @Override
    public JsonRpcClientRequest pathParam(String name, String value) {
        delegate.pathParam(name, value);
        return this;
    }

    @Override
    public JsonRpcClientRequest queryParam(String name, String... values) {
        delegate.queryParam(name, values);
        return this;
    }

    @Override
    public JsonRpcClientRequest fragment(String fragment) {
        delegate.fragment(fragment);
        return this;
    }

    @Override
    public JsonRpcClientRequest fragment(UriFragment fragment) {
        delegate.fragment(fragment);
        return this;
    }

    @Override
    public JsonRpcClientRequest followRedirects(boolean followRedirects) {
        delegate.followRedirects(followRedirects);
        return this;
    }

    @Override
    public JsonRpcClientRequest maxRedirects(int maxRedirects) {
        delegate.maxRedirects(maxRedirects);
        return this;
    }

    @Override
    public boolean followRedirects() {
        return delegate.followRedirects();
    }

    @Override
    public int maxRedirects() {
        return delegate.maxRedirects();
    }

    @Override
    public HttpClientResponse request() {
        return delegate.request();
    }

    @Override
    public ClientRequestHeaders headers() {
        return delegate.headers();
    }

    @Override
    public <E> ClientResponseTyped<E> request(Class<E> type) {
        return delegate.request(type);
    }

    @Override
    public <E> E requestEntity(Class<E> type) throws HttpException {
        return delegate.requestEntity(type);
    }

    @Override
    public HttpClientResponse submit(Object entity) {
        return delegate.submit(entity);
    }

    @Override
    public <T> ClientResponseTyped<T> submit(Object entity, Class<T> requestedType) {
        return delegate.submit(entity, requestedType);
    }

    @Override
    public HttpClientResponse outputStream(OutputStreamHandler outputStreamConsumer) {
        return delegate.outputStream(outputStreamConsumer);
    }

    @Override
    public <T> ClientResponseTyped<T> outputStream(OutputStreamHandler outputStreamConsumer, Class<T> requestedType) {
        return delegate.outputStream(outputStreamConsumer, requestedType);
    }

    @Override
    public UriInfo resolvedUri() {
        return delegate.resolvedUri();
    }

    @Override
    public JsonRpcClientRequest connection(ClientConnection connection) {
        delegate.connection(connection);
        return this;
    }

    @Override
    public JsonRpcClientRequest skipUriEncoding(boolean skip) {
        delegate.skipUriEncoding(skip);
        return this;
    }

    @Override
    public JsonRpcClientRequest property(String propertyName, String propertyValue) {
        delegate.property(propertyName, propertyValue);
        return this;
    }

    @Override
    public JsonRpcClientRequest keepAlive(boolean keepAlive) {
        delegate.keepAlive(keepAlive);
        return this;
    }

    @Override
    public JsonRpcClientRequest readTimeout(Duration readTimeout) {
        delegate.readTimeout(readTimeout);
        return this;
    }

    @Override
    public JsonRpcClientRequest readContinueTimeout(Duration readContinueTimeout) {
        delegate.readContinueTimeout(readContinueTimeout);
        return this;
    }

    @Override
    public JsonRpcClientRequest sendExpectContinue(boolean sendExpectContinue) {
        delegate.sendExpectContinue(sendExpectContinue);
        return this;
    }
}
