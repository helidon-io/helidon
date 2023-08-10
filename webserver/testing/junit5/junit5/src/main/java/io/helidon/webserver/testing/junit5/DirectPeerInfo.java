/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5;

import java.net.SocketAddress;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Optional;

import io.helidon.common.socket.PeerInfo;

/**
 * Peer information that can be used with {@link DirectSocket}.
 *
 * @param address socket address - socket address of the peer
 * @param host host - for network, this is the used host of the peer
 * @param port port - for network, this is the used port of the peer
 * @param tlsPrincipal principal - for network obtained from SSL handshake or configuration (if used)
 * @param tlsCertificates  certificates - for network obtained from SSL handshake or configuration
 */
public record DirectPeerInfo(SocketAddress address, String host, int port, Optional<Principal> tlsPrincipal,
                      Optional<Certificate[]> tlsCertificates) implements PeerInfo {
}
