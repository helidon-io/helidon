/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.concurrent.CompletionStage;

import io.helidon.common.context.Context;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Method;

/**
 * Request to SPI {@link io.helidon.webclient.spi.WebClientService} that supports modification of the outgoing request.
 */
public interface WebClientServiceRequest {
    /**
     * URI helper for this client request.
     *
     * @return URI helper
     */
    ClientUri uri();

    /**
     * Returns an HTTP request method. See also {@link io.helidon.http.Method HTTP standard methods} utility class.
     *
     * @return an HTTP method
     * @see io.helidon.http.Method
     */
    Method method();

    /**
     * Returns an HTTP protocol ID, mapped to a specific version. This is the same ID that is used for ALPN
     * (protocol negotiation) - {@code http/1.1} for HTTP/1.1 and {@code h2} for HTTP/2.
     * <p>
     * If communication starts as a {@code HTTP/1.1} with {@code h2c} upgrade, then it will be automatically
     * upgraded and this method returns {@code HTTP/2.0}.
     *
     * @return an HTTP version
     */
    String protocolId();

    /**
     * Configured request headers.
     *
     * @return headers (mutable)
     */
    ClientRequestHeaders headers();

    /**
     * Registry that can be used to propagate information from server (e.g. security context, tracing spans etc.).
     *
     * @return registry propagated by the user
     */
    Context context();

    /**
     * Request id which will be used in logging messages.
     *
     * @return current request id
     */
    String requestId();

    /**
     * Set new request id. This id is used in logging messages.
     *
     * @param requestId new request id
     */
    void requestId(String requestId);

    /**
     * Completes when the request part of this request is done (e.g. we have sent all headers and bytes).
     *
     * @return completion stage that finishes when we fully send request (including entity) to server
     */
    CompletionStage<WebClientServiceRequest> whenSent();

    /**
     * Completes when the full processing of this request is done (e.g. we have received a full response).
     *
     * @return completion stage that finishes when we receive and fully read response from the server
     */
    CompletionStage<WebClientServiceResponse> whenComplete();

    /**
     * Properties configured by user when creating this client request.
     *
     * @return properties that were configured (mutable)
     */
    Map<String, String> properties();
}
