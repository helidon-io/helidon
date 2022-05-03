/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
import java.util.Map;

import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.webclient.spi.WebClientService;

/**
 * Request to SPI {@link WebClientService} that supports modification of the outgoing request.
 */
public interface WebClientServiceRequest {
    /**
     * Returns an HTTP request method. See also {@link io.helidon.common.http.Http.Method HTTP standard methods} utility class.
     *
     * @return an HTTP method
     * @see io.helidon.common.http.Http.Method
     */
    Http.Method method();

    /**
     * Returns an HTTP version from the request line.
     * <p>
     * See {@link Http.Version HTTP Version} enumeration for supported versions.
     * <p>
     * If communication starts as a {@code HTTP/1.1} with {@code h2c} upgrade, then it will be automatically
     * upgraded and this method returns {@code HTTP/2.0}.
     *
     * @return an HTTP version
     */
    Http.Version version();

    /**
     * Returns a Request-URI (or alternatively path) as defined in request line.
     *
     * @return a request URI
     */
    URI uri();

    /**
     * Returns an encoded query string without leading '?' character.
     *
     * @return an encoded query string
     */
    String query();

    /**
     * Returns query parameters.
     *
     * @return an parameters representing query parameters
     */
    UriQuery queryParams();

    /**
     * Returns a path which was accepted by matcher in actual routing. It is path without a context root
     * of the routing.
     * <p>
     * Use {@link io.helidon.common.uri.UriPath#absolute()} method to obtain absolute request URI path representation.
     * <p>
     * Returned {@link io.helidon.common.uri.UriPath} also provides access to path template parameters.
     * An absolute path then provides access to
     * all (including) context parameters if any. In case of conflict between parameter names, most recent value is returned.
     *
     * @return a path
     */
    UriPath path();

    /**
     * Returns a decoded request URI fragment without leading hash '#' character.
     *
     * @return a decoded URI fragment
     */
    String fragment();

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
     * Schema of the request uri.
     *
     * This will not match schema returned by {@link WebClientServiceRequest#uri()}
     * if changed by {@link WebClientServiceRequest#schema(String schema)}
     *
     * @return schema of the request
     */
    String schema();

    /**
     * Set new schema of the request.
     *
     * @param schema new request schema
     */
    void schema(String schema);

    /**
     * Host of the request uri.
     *
     * This will not match host returned by {@link WebClientServiceRequest#uri()}
     * if changed by {@link WebClientServiceRequest#host(String host)}
     *
     * @return host of the request
     */
    String host();

    /**
     * Set new host of the request.
     *
     * @param host new request host
     */
    void host(String host);

    /**
     * Port of the request uri.
     *
     * This will not match port returned by {@link WebClientServiceRequest#uri()}
     * if changed by {@link WebClientServiceRequest#port(int port)}
     *
     * @return port of the request
     */
    int port();

    /**
     * Set new port of the request.
     *
     * @param port new request port
     */
    void port(int port);

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
