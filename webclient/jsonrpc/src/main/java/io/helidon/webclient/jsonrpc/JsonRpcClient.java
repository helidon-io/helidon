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

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.webclient.spi.Protocol;

/**
 * A JSON-RPC client.
 */
@RuntimeType.PrototypedBy(JsonRpcClientConfig.class)
public interface JsonRpcClient extends RuntimeType.Api<JsonRpcClientConfig> {

    /**
     * Protocol ID constant for JSON-RPC.
     */
    String PROTOCOL_ID = "jsonrpc";

    /**
     * Protocol to use to obtain an instance of JSON-RPC specific client from
     * {@link io.helidon.webclient.api.WebClient#client(io.helidon.webclient.spi.Protocol)}.
     */
    Protocol<JsonRpcClient, JsonRpcClientProtocolConfig> PROTOCOL = JsonRpcProtocolProvider::new;

    /**
     * A new fluent API builder to customize client setup.
     *
     * @return a new builder
     */
    static JsonRpcClientConfig.Builder builder() {
        return JsonRpcClientConfig.builder();
    }

    /**
     * Create a new instance with custom configuration.
     *
     * @param clientConfig JSON-RPC client configuration
     * @return a new JSON-RPC client
     */
    static JsonRpcClient create(JsonRpcClientConfig clientConfig) {
        return new JsonRpcClientImpl(clientConfig);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param consumer JSON-RPC client configuration
     * @return a new JSON-RPC client
     */
    static JsonRpcClient create(Consumer<JsonRpcClientConfig.Builder> consumer) {
        return create(JsonRpcClientConfig.builder()
                              .update(consumer)
                              .buildPrototype());
    }

    /**
     * Create a new instance with default configuration.
     *
     * @return a new JSON-RPC client
     */
    static JsonRpcClient create() {
        return create(JsonRpcClientConfig.create());
    }

    /**
     * Create a new {@link io.helidon.webclient.jsonrpc.JsonRpcClientRequest} given
     * a JSON-RPC method.
     *
     * @param method the method
     * @return a new JSON-RPC request
     */
    JsonRpcClientRequest rpcMethod(String method);

    /**
     * Create a JSON-RPC batch request and set the path related to the base URI.
     *
     * @param path the path
     * @return new batch request
     */
    JsonRpcClientBatchRequest batch(String path);
}
