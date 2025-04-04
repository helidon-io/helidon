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

/**
 * Information about one side of this communication (either local or remote).
 */
public interface PeerInfo {
    /**
     * Socket address of the peer.
     *
     * @return address
     */
    SocketAddress address();

    /**
     * Host of the peer.
     *
     * @return host
     */
    String host();

    /**
     * Port of the peer.
     *
     * @return port
     */
    int port();

    /**
     * TLS principal (from certificate) of the peer.
     *
     * @return principal of the peer, or empty if not a TLS connection, or this peer does not provide principal
     */
    Optional<Principal> tlsPrincipal();

    /**
     * TLS certificate chain of the peer.
     *
     * @return certificate chain of the peer, or empty if not a TLS connection, or this peer does not provide certificates
     */
    Optional<Certificate[]> tlsCertificates();
}
