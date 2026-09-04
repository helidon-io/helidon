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

package io.helidon.webserver.http3;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.tls.TlsUtils;
import io.helidon.quic.QuicConnection;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.TransportBindingContext;

final class Http3ConnectionContext implements ConnectionContext {
    private static final String STREAM_SCOPED_IO = "HTTP/3 byte I/O is stream-scoped";

    private final TransportBindingContext listenerContext;
    private final QuicConnection connection;
    private final Router router;
    private final Optional<SniContext> sniContext;
    private final Optional<String> tlsCommonName;

    Http3ConnectionContext(TransportBindingContext listenerContext, QuicConnection connection) {
        this(listenerContext, connection, Optional.empty());
    }

    Http3ConnectionContext(TransportBindingContext listenerContext,
                           QuicConnection connection,
                           Optional<SniContext> sniContext) {
        this.listenerContext = Objects.requireNonNull(listenerContext, "listenerContext");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.router = listenerContext.router();
        this.sniContext = Objects.requireNonNull(sniContext, "sniContext");
        this.tlsCommonName = connection.remotePeer().tlsCertificates().flatMap(TlsUtils::parseCn);
    }

    @Override
    public ListenerContext listenerContext() {
        return listenerContext.listenerContext();
    }

    @Override
    public ExecutorService executor() {
        return listenerContext.listenerContext().executor();
    }

    @Override
    public DataWriter dataWriter() {
        throw new UnsupportedOperationException(STREAM_SCOPED_IO);
    }

    @Override
    public DataReader dataReader() {
        throw new UnsupportedOperationException(STREAM_SCOPED_IO);
    }

    @Override
    public Router router() {
        return router;
    }

    @Override
    public PeerInfo remotePeer() {
        return connection.remotePeer();
    }

    @Override
    public PeerInfo localPeer() {
        return connection.localPeer();
    }

    @Override
    public boolean isSecure() {
        return connection.isSecure();
    }

    @Override
    public String socketId() {
        return connection.socketId();
    }

    @Override
    public String childSocketId() {
        return connection.childSocketId();
    }

    @Override
    public Optional<SniContext> sniContext() {
        return sniContext;
    }

    Optional<String> tlsCommonName() {
        return tlsCommonName;
    }

}
