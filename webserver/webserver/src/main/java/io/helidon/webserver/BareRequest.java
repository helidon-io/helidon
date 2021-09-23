/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;

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
    WebServer webServer();

    /**
     * Gets an HTTP request method.
     *
     * @return an HTTP method.
     */
    Http.RequestMethod method();

    /**
     * Gets an HTTP version from the request line such as {@code HTTP/1.1}.
     *
     * @return HTTP version.
     */
    Http.Version version();

    /**
     * Gets a Request-URI (or alternatively path) as defined in request line.
     *
     * @return a request URI
     */
    URI uri();

    /**
     * Gets the Internet Protocol (IP) address of the interface on which the request was received.
     *
     * @return an address.
     */
    String localAddress();

    /**
     * Returns the Internet Protocol (IP) port number of the interface on which the request was received.
     *
     * @return an integer specifying the port number.
     */
    int localPort();

    /**
     * Gets the Internet Protocol (IP) address of the client or last proxy that sent the request.
     *
     * @return The address of the client that sent the request.
     */
    String remoteAddress();

    /**
     * Returns the Internet Protocol (IP) source port of the client or last proxy that sent the request.
     *
     * @return The port number.
     */
    int remotePort();

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
    Map<String, List<String>> headers();

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

    /**
     * Request to close the connection and report success or failure asynchronously with returned single.
     * After connection is closed it is not possible to use it again.
     *
     * @return Single completed when connection is closed.
     */
    Single<Void> closeConnection();
}
