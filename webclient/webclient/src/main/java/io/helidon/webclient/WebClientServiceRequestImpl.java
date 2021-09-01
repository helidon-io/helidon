/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.common.http.Parameters;
import io.helidon.common.reactive.Single;

/**
 * Implementation of the {@link WebClientServiceRequest} interface.
 */
class WebClientServiceRequestImpl implements WebClientServiceRequest {

    private final WebClientRequestHeaders headers;
    private final Http.RequestMethod method;
    private final Http.Version version;
    private final Map<String, String> parameters;
    private final CompletableFuture<WebClientServiceRequest> sent;
    private final CompletableFuture<WebClientServiceResponse> responseReceived;
    private final CompletableFuture<WebClientServiceResponse> complete;
    private final WebClientRequestBuilderImpl requestBuilder;
    private String schema;
    private String host;
    private int port;

    WebClientServiceRequestImpl(WebClientRequestBuilderImpl requestBuilder,
                                CompletableFuture<WebClientServiceRequest> sent,
                                CompletableFuture<WebClientServiceResponse> responseReceived,
                                CompletableFuture<WebClientServiceResponse> complete) {
        this.headers = requestBuilder.headers();
        this.method = requestBuilder.method();
        this.version = requestBuilder.httpVersion();
        this.responseReceived = responseReceived;
        this.parameters = requestBuilder.properties();
        this.sent = sent;
        this.complete = complete;
        this.requestBuilder = requestBuilder;
        URI uri = requestBuilder.uri();
        this.schema = uri.getScheme();
        this.host = uri.getHost();
        this.port = uri.getPort();
    }

    @Override
    public WebClientRequestHeaders headers() {
        return headers;
    }

    @Override
    public Context context() {
        return requestBuilder.context();
    }

    @Override
    public long requestId() {
        return requestBuilder.requestId();
    }

    @Override
    public void requestId(long requestId) {
        requestBuilder.requestId(requestId);
    }

    @Override
    public Single<WebClientServiceRequest> whenSent() {
        return Single.create(sent);
    }

    @Override
    public Single<WebClientServiceResponse> whenResponseReceived() {
        return Single.create(responseReceived);
    }

    @Override
    public Single<WebClientServiceResponse> whenComplete() {
        return Single.create(complete);
    }

    @Override
    public Map<String, String> properties() {
        return parameters;
    }

    @Override
    public void schema(String schema) {
        this.schema = schema;
    }

    @Override
    public void host(String host) {
        this.host = host;
    }

    @Override
    public void port(int port) {
        this.port = port;
    }

    @Override
    public void path(String path) {
        requestBuilder.path(path);
    }

    @Override
    public void fragment(String fragment) {
        requestBuilder.fragment(fragment);
    }

    @Override
    public Http.RequestMethod method() {
        return method;
    }

    @Override
    public Http.Version version() {
        return version;
    }

    @Override
    public URI uri() {
        return requestBuilder.uri();
    }

    @Override
    public String query() {
        return requestBuilder.queryFromParams();
    }

    @Override
    public Parameters queryParams() {
        return requestBuilder.queryParams();
    }

    @Override
    public Path path() {
        return requestBuilder.path();
    }

    @Override
    public String fragment() {
        return requestBuilder.fragment();
    }

    @Override
    public String host() {
        return this.host;
    }

    @Override
    public String schema() {
        return this.schema;
    }

    @Override
    public int port() {
        return this.port;
    }

}
