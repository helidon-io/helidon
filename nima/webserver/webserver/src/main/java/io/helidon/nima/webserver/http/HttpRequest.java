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

import io.helidon.common.http.HeadersServerRequest;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpPrologue;
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
    HeadersServerRequest headers();

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
     * The authority requested (host and optional port). This is authority as may be obtained from
     * {@link io.helidon.common.http.Http.Header#HOST} (or HTTP/2 authority pseudo-header),
     * {@link io.helidon.common.http.Http.Header#FORWARDED},
     * {@link io.helidon.common.http.Http.Header#X_FORWARDED_HOST}
     *  - depending on server configuration
     * Authority can be used for redirections.
     * The underlying headers are not modified, so if you need access to the actual requested host, you can still
     * use the {@link Http.Header#HOST}.
     *
     * @return authority of this request
     */
    String usedAuthority();

    /**
     * The protocol used in the original request.
     * This is obtained from socket information, {@link io.helidon.common.http.Http.Header#FORWARDED},
     * or {@link io.helidon.common.http.Http.Header#X_FORWARDED_PROTO}.
     * Protocol can be used for redirections.
     * @return used protocol
     */
    String usedProtocol();

    /**
     * Replace (or set) a request header.
     * This may be useful in filters, or on re-routing.
     *
     * @param header header to set
     */
    void header(Http.HeaderValue header);
}
