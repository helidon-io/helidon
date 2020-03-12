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

import java.util.concurrent.CompletionStage;

import io.helidon.common.context.Context;
import io.helidon.common.http.HttpRequest;
import io.helidon.common.http.Parameters;
import io.helidon.webclient.spi.WebClientService;

/**
 * Request to SPI {@link WebClientService} that supports modification of the outgoing request.
 */
public interface WebClientServiceRequest extends HttpRequest {
    /**
     * Configured request headers.
     *
     * @return headers (mutable)
     */
    WebClientRequestHeaders headers();

    /**
     * Registry that can be used to propagate information from server (e.g. security context, tracing spans etc.).
     *
     * @return registry propagated by the user
     */
    Context context();

    /**
     * Completes when the request part of this request is done (e.g. we have sent all headers and bytes).
     *
     * @return completion stage that finishes when we fully send request (including entity) to server
     */
    CompletionStage<WebClientServiceRequest> whenSent();

    /**
     * Completes when the response headers has been received, but entity has not been processed yet.
     *
     * @return completion stage that finishes when we received headers
     */
    CompletionStage<WebClientServiceResponse> whenResponseReceived();

    /**
     * Completes when the full processing of this request is done (e.g. we have received a full response).
     *
     * @return completion stage that finishes when we receive and fully read response from the server
     */
    CompletionStage<WebClientServiceResponse> whenComplete();

    /**
     * Properties configured by user when creating this client request.
     *
     * @return properties that were configured
     * @see WebClientRequestBuilder#property(String, String...)
     */
    Parameters properties();

}
