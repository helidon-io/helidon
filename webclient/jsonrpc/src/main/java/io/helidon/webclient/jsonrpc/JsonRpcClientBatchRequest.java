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

import jakarta.json.JsonArray;

/**
 * An interface representing a JSON-RPC batch request.
 */
public interface JsonRpcClientBatchRequest {

    /**
     * Start creation of a new {@link io.helidon.webclient.jsonrpc.JsonRpcClientRequest}
     * given a JSON-RPC method. To add a request to the batch, call
     * {@link io.helidon.webclient.jsonrpc.JsonRpcClientRequest#addToBatch()}.
     *
     * @param method the method
     * @return a new JSON-RPC request
     */
    JsonRpcClientRequest rpcMethod(String method);

    /**
     * Submit this request batch and get a response batch.
     *
     * @return a response batch
     */
    JsonRpcClientBatchResponse submit();

    /**
     * Get the complete batch request as a JSON array.
     *
     * @return a JSON array
     */
    JsonArray asJsonArray();
}
