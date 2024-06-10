/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.LazyValue;

class PeerInfoImpl implements PeerInfo {
    private final LazyValue<SocketAddress> socketAddress;
    private final LazyValue<String> host;
    private final LazyValue<Integer> port;
    private final LazyValue<Optional<Principal>> principalSupplier;
    private final LazyValue<Optional<Certificate[]>> certificateSupplier;

    private PeerInfoImpl(LazyValue<SocketAddress> socketAddress,
                         LazyValue<String> host,
                         LazyValue<Integer> port,
                         LazyValue<Optional<Principal>> principalSupplier,
                         LazyValue<Optional<Certificate[]>> certificateSupplier) {
        this.socketAddress = socketAddress;
        this.host = host;
        this.port = port;
        this.principalSupplier = principalSupplier;
        this.certificateSupplier = certificateSupplier;
    }

    static PeerInfo createLocal(PlainSocket socket) {
        return new PeerInfoImpl(LazyValue.create(socket::localSocketAddress),
                                LazyValue.create(socket::localHost),
                                LazyValue.create(socket::localPort),
                                LazyValue.create(Optional.empty()),
                                LazyValue.create(Optional.empty()));
    }

    static PeerInfoImpl createLocal(TlsSocket socket) {
        // remote socket - all lazy values, as they cannot change (and they require creating another object)
        return new PeerInfoImpl(LazyValue.create(socket::localSocketAddress),
                                LazyValue.create(socket::localHost),
                                LazyValue.create(socket::localPort),
                                LazyValue.create(socket::tlsPrincipal),
                                LazyValue.create(socket::tlsCertificates));
    }

    static PeerInfoImpl createRemote(TlsSocket socket) {
        // remote socket - all lazy values, as they cannot change (and they require creating another object)
        return new PeerInfoImpl(LazyValue.create(socket::remoteSocketAddress),
                                LazyValue.create(socket::remoteHost),
                                LazyValue.create(socket::remotePort),
                                LazyValue.create(socket::tlsPeerPrincipal),
                                LazyValue.create(socket::tlsPeerCertificates));
    }

    static PeerInfoImpl createRemote(PlainSocket socket) {
        return new PeerInfoImpl(LazyValue.create(socket::remoteSocketAddress),
                                LazyValue.create(socket::remoteHost),
                                LazyValue.create(socket::remotePort),
                                LazyValue.create(Optional.empty()),
                                LazyValue.create(Optional.empty()));
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
