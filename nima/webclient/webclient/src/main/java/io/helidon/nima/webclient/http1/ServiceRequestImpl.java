/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient.http1;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import io.helidon.common.context.Context;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.webclient.UriHelper;
import io.helidon.nima.webclient.WebClientServiceRequest;
import io.helidon.nima.webclient.WebClientServiceResponse;

class ServiceRequestImpl implements WebClientServiceRequest {
    private final Map<String, String> properties;
    private UriHelper uri;
    private Http.Method method;
    private Http.Version version;
    private UriQueryWriteable query;
    private UriFragment fragment;
    private ClientRequestHeaders headers;
    private Context context;
    private String requestId;
    private CompletionStage<WebClientServiceResponse> whenComplete;
    private CompletionStage<WebClientServiceRequest> whenSent;

    ServiceRequestImpl(UriHelper uri,
                       Http.Method method,
                       Http.Version version,
                       UriQueryWriteable query,
                       UriFragment fragment,
                       ClientRequestHeaders headers,
                       Context context,
                       String requestId,
                       CompletionStage<WebClientServiceResponse> whenComplete,
                       CompletionStage<WebClientServiceRequest> whenSent,
                       Map<String, String> properties) {
        this.uri = uri;
        this.method = method;
        this.version = version;
        this.query = query;
        this.fragment = fragment;
        this.headers = headers;
        this.context = context;
        this.requestId = requestId;
        this.whenComplete = whenComplete;
        this.whenSent = whenSent;
        this.properties = new HashMap<>(properties);
    }

    @Override
    public UriHelper uri() {
        return uri;
    }

    @Override
    public Http.Method method() {
        return method;
    }

    @Override
    public Http.Version version() {
        return version;
    }

     @Override
    public UriQueryWriteable query() {
        return query;
    }

    @Override
    public UriFragment fragment() {
        return fragment;
    }

    @Override
    public ClientRequestHeaders headers() {
        return headers;
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public String requestId() {
        return requestId;
    }

    @Override
    public void requestId(String requestId) {
        this.requestId = requestId;
    }

    @Override
    public CompletionStage<WebClientServiceRequest> whenSent() {
        return whenSent;
    }

    @Override
    public CompletionStage<WebClientServiceResponse> whenComplete() {
        return whenComplete;
    }

    @Override
    public Map<String, String> properties() {
        return properties;
    }

    @Override
    public void fragment(String fragment) {
        this.fragment = UriFragment.createFromDecoded(fragment);
    }
}
