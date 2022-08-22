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

import io.helidon.nima.http.media.ReadableEntity;

/**
 * HTTP server request.
 */
public interface ServerRequest extends HttpRequest {
    /**
     * Whether this request was over a secure protocol (TLS).
     * This only returns if the last step was over secure protocol. If a reverse proxy is used, check {@link #usedProtocol()}.
     *
     * @return whether this is a secure request
     */
    boolean isSecure();

    /**
     * Path of the request.
     *
     * @return routed path to obtain parameters from path pattern
     */
    RoutedPath path();

    /**
     * Server request entity.
     *
     * @return entity
     */
    ReadableEntity content();

    /**
     * Identification of the client/server connection socket.
     * The socket id is not guaranteed to be unique across server instances, or between restarts.
     *
     * @return socket id of this connection
     */
    String socketId();

    /**
     * Identification of the listener socket.
     * The listener id is not guaranteed to be unique across server instances, or between restarts.
     *
     * @return server socket id
     */
    String serverSocketId();
}
