/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.context.Context;
import io.helidon.common.http.HashParameters;
import io.helidon.common.http.Http;
import io.helidon.common.http.Parameters;

/**
 * Implementation of the {@link WebClientServiceRequest} interface.
 */
class WebClientServiceRequestImpl implements WebClientServiceRequest {

    private final WebClientRequestHeaders headers;
    private final Context context;
    private final Http.RequestMethod method;
    private final Http.Version version;
    private final URI uri;
    private final String query;
    private final Parameters queryParams;
    private final Path path;
    private final String fragment;
    private final Map<String, String> parameters;
    private final CompletionStage<WebClientServiceRequest> sent;
    private final CompletableFuture<WebClientServiceResponse> responseReceived;
    private final CompletableFuture<WebClientServiceResponse> complete;

    WebClientServiceRequestImpl(WebClientRequestBuilderImpl requestBuilder,
                                CompletionStage<WebClientServiceRequest> sent,
                                CompletableFuture<WebClientServiceResponse> responseReceived,
                                CompletableFuture<WebClientServiceResponse> complete) {
        this.headers = requestBuilder.headers();
        this.context = requestBuilder.context();
        this.method = requestBuilder.method();
        this.version = requestBuilder.httpVersion();
        this.uri = requestBuilder.uri();
        this.query = requestBuilder.query();
        this.responseReceived = responseReceived;
        this.queryParams = queryParams();
        this.path = requestBuilder.path();
        this.fragment = requestBuilder.fragment();
        this.parameters = new HashMap<>(requestBuilder.properties());
        this.sent = sent;
        this.complete = complete;
    }

    @Override
    public WebClientRequestHeaders headers() {
        return headers;
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public CompletionStage<WebClientServiceRequest> whenSent() {
        return sent;
    }

    @Override
    public CompletionStage<WebClientServiceResponse> whenResponseReceived() {
        return responseReceived;
    }

    @Override
    public CompletionStage<WebClientServiceResponse> whenComplete() {
        return complete;
    }

    @Override
    public Map<String, String> properties() {
        return parameters;
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
        return uri;
    }

    @Override
    public String query() {
        return query;
    }

    @Override
    public Parameters queryParams() {
        return queryParams;
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public String fragment() {
        return fragment;
    }
}
