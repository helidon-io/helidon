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
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ProxyProtocolData;


/**
 * {@link GrpcConnectionContext} which exposes specific information from a {@link ConnectionContext}.
 */
final class GrpcConnectionContextImpl implements GrpcConnectionContext {
    private final ConnectionContext connectionContext;

    GrpcConnectionContextImpl(ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String serverSocketId() {
        return connectionContext.socketId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String connectionSocketId() {
        return connectionContext.childSocketId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PeerInfo remotePeer() {
        return connectionContext.remotePeer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PeerInfo localPeer() {
        return connectionContext.localPeer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSecure() {
        return connectionContext.isSecure();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ProxyProtocolData> proxyProtocolData() {
        return connectionContext.proxyProtocolData();
    }
}
