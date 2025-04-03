/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.io.InputStream;
import java.util.Optional;
import java.util.function.UnaryOperator;

import io.helidon.common.context.Context;
import io.helidon.http.RoutedPath;
import io.helidon.http.media.ReadableEntity;
import io.helidon.service.registry.Service;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ProxyProtocolData;

/**
 * HTTP server request.
 */
@Service.Describe(Service.PerRequest.class)
public interface ServerRequest extends HttpRequest {

    /**
     * Reset request related state on the connection if any.
     */
    void reset();

    /**
     * Whether this request was over a secure protocol (TLS).
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

    /**
     * Context of this web server request, to set and get information.
     * The context is not registered as the current context! You can use a dedicated module ({@code helidon-webserver-context})
     * to add a filter that would execute all requests within a context.
     *
     * @return request context
     */
    Context context();

    /**
     * Listener context. This gives access to low level tools of Helidon WebServer, please use with care.
     *
     * @return listener context
     */
    ListenerContext listenerContext();

    /**
     * HTTP security associated with this listener, configured on routing.
     *
     * @return security
     * @see io.helidon.webserver.http.HttpRouting.Builder#security(HttpSecurity)
     */
    HttpSecurity security();

    /**
     * Whether we have already sent the {@link io.helidon.http.Status#CONTINUE_100} when expect continue is
     * present. This method returns {@code true} for cases where expect continue is not set.
     * This method returns {@code false} for requests without entity.
     *
     * @return whether 100-Continue was sent back to client
     */
    boolean continueSent();

    /**
     * Configure a custom input stream to wrap the input stream of the request.
     *
     * @param filterFunction the function to replace input stream of this request with a user provided one
     */
    void streamFilter(UnaryOperator<InputStream> filterFunction);

    /**
     * Access proxy protocol data for the connection on which this request was sent.
     *
     * @return proxy protocol data, if available
     * @see io.helidon.webserver.ListenerConfig#enableProxyProtocol()
     */
    Optional<ProxyProtocolData> proxyProtocolData();
}
