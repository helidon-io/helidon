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
 * available from the {@link io.helidon.common.context.Context#get(Class)} by passing {@code GrpcConnectionContext.class}.
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
}
