/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.http;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.ServerRequestHeaders;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;

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
     * The content of the {@link io.helidon.common.http.Http.Header#HOST} header
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
    void header(Http.HeaderValue header);
}
