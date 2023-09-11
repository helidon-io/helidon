/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.Header;
import io.helidon.http.HttpPrologue;
import io.helidon.http.ServerRequestHeaders;

/**
 * HTTP Request.
 * Used for all HTTP versions.
 */
public interface HttpRequest {
    /**
     * Prologue of the request.
     *
     * @return HTTP prologue
     */
    HttpPrologue prologue();

    /**
     * Headers of the request.
     *
     * @return HTTP headers
     */
    ServerRequestHeaders headers();

    /**
     * Path of the request, with methods to get path parameters.
     *
     * @return HTTP path
     */
    UriPath path();

    /**
     * Query of the request.
     *
     * @return HTTP query
     */
    UriQuery query();

    /**
     * Peer info of the remote peer.
     *
     * @return remote peer info
     */
    PeerInfo remotePeer();

    /**
     * Peer info of the local side.
     *
     * @return local peer info
     */
    PeerInfo localPeer();

    /**
     * The content of the {@link io.helidon.http.HeaderNames#HOST} header
     * or {@code authority} pseudo header (HTTP/2).
     *
     * @return authority of this request
     */
    String authority();

    /**
     * Replace (or set) a request header.
     * This may be useful in filters, or on re-routing.
     *
     * @param header header to set
     */
    void header(Header header);

    /**
     * Request ID on this connection.
     * In HTTP/1, this is a number between 0 and {@link Integer#MAX_VALUE}. When the number of requests reaches maximum, it
     * starts again from 0.
     * In HTTP/2, this is the stream id.
     *
     * @return id of this request on a connection
     */
    int id();


    /**
     * URI as requested by the originating client (to the best of our ability to compute it).
     * By default, the URI is from the {@link io.helidon.http.HeaderNames#HOST} header on the current request.
     * If requested URI discovery is enabled by configuration, additional headers (such as {@link io.helidon.http.HeaderNames#FORWARDED})
     * may be used to derive the originally-requested URI.
     *
     * @return uri info that can be used for redirects
     */
    UriInfo requestedUri();
}
