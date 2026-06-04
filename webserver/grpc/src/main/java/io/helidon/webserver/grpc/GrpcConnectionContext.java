/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc;

import java.util.Optional;

import io.helidon.common.socket.PeerInfo;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ProxyProtocolData;


/**
 * Exposes connection-level information to gRPC request handlers. An instance of this interface is
 * available from {@link ServerContextKeys#CONNECTION_CONTEXT} in the current gRPC context.
 */
public interface GrpcConnectionContext {
    /**
     * Server socket id.
     * @return socket id
     */
    String serverSocketId();

    /**
     * Connection socket id.
     * @return child socket id, never null
     */
    String connectionSocketId();

    /**
     * Remote peer information.
     *
     * @return peer info
     */
    PeerInfo remotePeer();

    /**
     * Local peer information.
     *
     * @return peer info
     */
    PeerInfo localPeer();

    /**
     * Whether the request is secure.
     *
     * @return whether secure
     */
    boolean isSecure();

    /**
     * Proxy protocol header data.
     *
     * @return protocol header data if proxy protocol is enabled on socket
     * @see ListenerConfig#enableProxyProtocol()
     */
    Optional<ProxyProtocolData> proxyProtocolData();

    /**
     * Normalized DNS host name requested by the client using TLS Server Name Indication (SNI).
     * <p>
     * This value comes from the client TLS handshake, so it is suitable for diagnostics and application choices that
     * are expected to use client-requested host names, but it should not be treated as a trusted identity by itself.
     *
     * @return normalized SNI requested host, if available
     */
    default Optional<String> sniRequestedHost() {
        return Optional.empty();
    }

    /**
     * Configured virtual-host host that matched the TLS Server Name Indication (SNI) requested host.
     * <p>
     * For wildcard virtual hosts, this method returns the configured wildcard host such as {@code *.example.com}.
     *
     * @return configured SNI matched host, if a virtual host matched
     */
    default Optional<String> sniMatchedHost() {
        return Optional.empty();
    }
}
