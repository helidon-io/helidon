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

import io.helidon.webclient.http1.Http1Client;

class JsonRpcClientImpl implements JsonRpcClient {

    private final Http1Client http1Client;
    private final JsonRpcClientConfig clientConfig;

    JsonRpcClientImpl(JsonRpcClientConfig clientConfig) {
        http1Client = Http1Client.builder()
                .from(clientConfig)
                .build();
        this.clientConfig = clientConfig;
    }

    @Override
    public JsonRpcClientConfig prototype() {
        return clientConfig;
    }

    @Override
    public JsonRpcClientRequest rpcMethod(String rpcMethod) {
        Objects.requireNonNull(rpcMethod, "rpcMethod is null");
        return new JsonRpcClientRequestImpl(http1Client, rpcMethod);
    }

    @Override
    public JsonRpcClientBatchRequest batch(String path) {
        return new JsonRpcClientBatchRequestImpl(http1Client, path);
    }
}
