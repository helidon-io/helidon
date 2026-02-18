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

package io.helidon.common.socket;

import java.net.SocketAddress;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Optional;
import java.util.function.Supplier;

class PeerInfoImpl implements PeerInfo {
    private final Supplier<SocketAddress> socketAddress;
    private final Supplier<String> host;
    private final Supplier<Integer> port;
    private final Supplier<Optional<Principal>> principalSupplier;
    private final Supplier<Optional<Certificate[]>> certificateSupplier;

    private PeerInfoImpl(Supplier<SocketAddress> socketAddress,
                         Supplier<String> host,
                         Supplier<Integer> port,
                         Supplier<Optional<Principal>> principalSupplier,
                         Supplier<Optional<Certificate[]>> certificateSupplier) {
        this.socketAddress = socketAddress;
        this.host = host;
        this.port = port;
        this.principalSupplier = principalSupplier;
        this.certificateSupplier = certificateSupplier;
    }

    static PeerInfo createLocal(PlainSocket socket) {
        return new PeerInfoImpl(socket::localSocketAddress,
                                socket::localHost,
                                socket::localPort,
                                Optional::empty,
                                Optional::empty);
    }

    static PeerInfoImpl createLocal(TlsSocket socket) {
        return new PeerInfoImpl(socket::localSocketAddress,
                                socket::localHost,
                                socket::localPort,
                                socket::tlsPrincipal,
                                socket::tlsCertificates);
    }

    static PeerInfoImpl createRemote(TlsSocket socket) {
        return new PeerInfoImpl(socket::remoteSocketAddress,
                                socket::remoteHost,
                                socket::remotePort,
                                socket::tlsPeerPrincipal,
                                socket::tlsPeerCertificates);
    }

    static PeerInfoImpl createRemote(PlainSocket socket) {
        return new PeerInfoImpl(socket::remoteSocketAddress,
                                socket::remoteHost,
                                socket::remotePort,
                                Optional::empty,
                                Optional::empty);
    }

    static PeerInfo createLocal(NioSocket socket) {
        return new PeerInfoImpl(socket::localSocketAddress,
                                socket::localHost,
                                socket::localPort,
                                Optional::empty,
                                Optional::empty);
    }

    static PeerInfo createRemote(NioSocket socket) {
        return new PeerInfoImpl(socket::remoteSocketAddress,
                                socket::remoteHost,
                                socket::remotePort,
                                Optional::empty,
                                Optional::empty);
    }

    static PeerInfoImpl createLocal(TlsNioSocket socket) {
        return new PeerInfoImpl(socket::localSocketAddress,
                                socket::localHost,
                                socket::localPort,
                                socket::tlsPrincipal,
                                socket::tlsCertificates);
    }

    static PeerInfoImpl createRemote(TlsNioSocket socket) {
        return new PeerInfoImpl(socket::remoteSocketAddress,
                                socket::remoteHost,
                                socket::remotePort,
                                socket::tlsPeerPrincipal,
                                socket::tlsPeerCertificates);
    }

    @Override
    public SocketAddress address() {
        return socketAddress.get();
    }

    @Override
    public String host() {
        return host.get();
    }

    @Override
    public int port() {
        return port.get();
    }

    @Override
    public Optional<Principal> tlsPrincipal() {
        return principalSupplier.get();
    }

    @Override
    public Optional<Certificate[]> tlsCertificates() {
        return certificateSupplier.get();
    }
}
