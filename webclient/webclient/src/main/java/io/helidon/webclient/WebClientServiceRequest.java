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
import java.net.URL;
import java.util.Map;

import io.helidon.common.context.Context;
import io.helidon.common.http.HttpRequest;
import io.helidon.common.reactive.Single;
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
     * Request id which will be used in logging messages.
     *
     * @return current request id
     */
    long requestId();

    /**
     * Set new request id. This id is used in logging messages.
     *
     * @param requestId new request id
     */
    void requestId(long requestId);

    /**
     * Completes when the request part of this request is done (e.g. we have sent all headers and bytes).
     *
     * @return completion stage that finishes when we fully send request (including entity) to server
     */
    Single<WebClientServiceRequest> whenSent();

    /**
     * Completes when the response headers has been received, but entity has not been processed yet.
     *
     * @return completion stage that finishes when we received headers
     */
    Single<WebClientServiceResponse> whenResponseReceived();

    /**
     * Completes when the full processing of this request is done (e.g. we have received a full response).
     *
     * @return completion stage that finishes when we receive and fully read response from the server
     */
    Single<WebClientServiceResponse> whenComplete();

    /**
     * Properties configured by user when creating this client request.
     *
     * @return properties that were configured (mutable)
     * @see WebClientRequestBuilder#property(String, String)
     */
    Map<String, String> properties();

    /**
     * Set the new base uri of the request.
     *
     * @param uri new request baseuri
     */
    void uri(URI uri);

    /**
     * Set the new base url of the request.
     *
     * @param url new request base url
     */
    void uri(URL url);

    /**
     * Set the new base uri of the request.
     *
     * @param uri new request base uri
     */
    void uri(String uri);

    /**
     * Set the new path of the request.
     *
     * @param path new request path
     */
    void path(String path);

    /**
     * Set the new fragment of the request.
     *
     * @param fragment new request fragment
     */
    void fragment(String fragment);

}
