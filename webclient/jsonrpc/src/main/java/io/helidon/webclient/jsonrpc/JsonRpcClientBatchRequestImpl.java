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

import java.util.ArrayList;
import java.util.List;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;

/**
 * An implementation of a JSON-RPC client batch request.
 */
class JsonRpcClientBatchRequestImpl implements JsonRpcClientBatchRequest {

    private final Http1Client http1Client;
    private final Http1ClientRequest delegate;
    private final List<JsonRpcClientRequest> requests = new ArrayList<>();

    JsonRpcClientBatchRequestImpl(Http1Client http1Client, String path) {
        this.http1Client = http1Client;
        this.delegate = http1Client.post(path);
    }

    @Override
    public JsonRpcClientRequest rpcMethod(String rpcMethod) {
        return new JsonRpcClientRequestImpl(http1Client, rpcMethod, this);
    }

    @Override
    public JsonRpcClientBatchRequest add(JsonRpcClientRequest request) {
        requests.add(request);
        return this;
    }

    @Override
    public JsonRpcClientBatchResponse submit() {
        HttpClientResponse res = delegate.header(HeaderNames.CONTENT_TYPE, MediaTypes.APPLICATION_JSON_VALUE)
                .header(HeaderNames.ACCEPT, MediaTypes.APPLICATION_JSON_VALUE)
                .submit(asJsonArray());
        return new JsonRpcClientBatchResponseImpl(res);
    }

    @Override
    public JsonArray asJsonArray() {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        requests.forEach(request -> arrayBuilder.add(request.asJsonObject()));
        return  arrayBuilder.build();
    }
}
