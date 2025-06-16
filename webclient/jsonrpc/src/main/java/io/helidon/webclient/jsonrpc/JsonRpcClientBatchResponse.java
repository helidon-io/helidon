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

import io.helidon.webclient.api.HttpClientResponse;

/**
 * A representation for a JSON-RPC batch response.
 */
public interface JsonRpcClientBatchResponse extends Iterable<JsonRpcClientResponse>, HttpClientResponse {

    /**
     * Number of responses in this batch.
     *
     * @return the size
     */
    int size();

    /**
     * Get a single response by index.
     *
     * @param index the index
     * @return the response
     * @throws java.lang.IndexOutOfBoundsException if index is out of bounds
     */
    JsonRpcClientResponse get(int index);
}
