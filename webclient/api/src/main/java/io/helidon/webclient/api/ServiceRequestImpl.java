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

package io.helidon.webclient.api;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import io.helidon.common.context.Context;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Method;

class ServiceRequestImpl implements WebClientServiceRequest {
    private final Map<String, String> properties;
    private final String protocolId;
    private final ClientUri uri;
    private final Method method;
    private final ClientRequestHeaders headers;
    private final Context context;
    private final CompletionStage<WebClientServiceResponse> whenComplete;
    private final CompletionStage<WebClientServiceRequest> whenSent;

    private String requestId;

    ServiceRequestImpl(ClientUri uri,
                       Method method,
                       String protocolId,
                       ClientRequestHeaders headers,
                       Context context,
                       String requestId,
                       CompletionStage<WebClientServiceResponse> whenComplete,
                       CompletionStage<WebClientServiceRequest> whenSent,
                       Map<String, String> properties) {
        this.uri = uri;
        this.method = method;
        this.protocolId = protocolId;
        this.headers = headers;
        this.context = context;
        this.requestId = requestId;
        this.whenComplete = whenComplete;
        this.whenSent = whenSent;
        this.properties = new HashMap<>(properties);
    }

    @Override
    public ClientUri uri() {
        return uri;
    }

    @Override
    public Method method() {
        return method;
    }

    @Override
    public String protocolId() {
        return protocolId;
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
}
