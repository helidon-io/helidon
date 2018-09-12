/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.spi;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Flow;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;


/**
 * Bare (minimal) representation of HTTP Request. Used by {@link WebServer WebServer} implementations to invoke
 * a {@link Routing Routing}.
 */
public interface BareRequest {

    /**
     * Gets actual {@link WebServer} instance.
     *
     * @return Actual {@code WebServer} instance.
     */
    WebServer getWebServer();

    /**
     * Gets an HTTP request method.
     *
     * @return an HTTP method.
     */
    Http.RequestMethod getMethod();

    /**
     * Gets an HTTP version from the request line such as {@code HTTP/1.1}.
     *
     * @return HTTP version.
     */
    Http.Version getVersion();

    /**
     * Gets a Request-URI (or alternatively path) as defined in request line.
     *
     * @return a request URI
     */
    URI getUri();

    /**
     * Gets the Internet Protocol (IP) address of the interface on which the request was received.
     *
     * @return an address.
     */
    String getLocalAddress();

    /**
     * Returns the Internet Protocol (IP) port number of the interface on which the request was received.
     *
     * @return an integer specifying the port number.
     */
    int getLocalPort();

    /**
     * Gets the Internet Protocol (IP) address of the client or last proxy that sent the request.
     *
     * @return The address of the client that sent the request.
     */
    String getRemoteAddress();

    /**
     * Returns the Internet Protocol (IP) source port of the client or last proxy that sent the request.
     *
     * @return The port number.
     */
    int getRemotePort();

    /**
     * Gets an indicating whether this request was made using a secure channel, such as HTTPS.
     *
     * @return {@code true} if the request was made using a secure channel.
     */
    boolean isSecure();

    /**
     * Gets http request headers.
     *
     * @return representing http headers.
     */
    Map<String, List<String>> getHeaders();

    /**
     * Gets the Flow Publisher that allows a subscription for request body chunks.
     *
     * @return the publisher
     */
    Flow.Publisher<DataChunk> bodyPublisher();

    /**
     * A unique correlation ID that is associated with this request and its associated response.
     *
     * @return a unique correlation ID associated with this request and its response
     */
    long requestId();
}
