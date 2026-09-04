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

package io.helidon.quic;

import java.net.InetSocketAddress;

import io.helidon.builder.api.Prototype;

final class QuicClientTargetSupport {
    private QuicClientTargetSupport() {
    }

    static final class Decorator implements Prototype.BuilderDecorator<QuicClientTarget.BuilderBase<?, ?>> {
        @Override
        public void decorate(QuicClientTarget.BuilderBase<?, ?> target) {
            InetSocketAddress peerAddress = target.peerAddress()
                    .orElseThrow(() -> new IllegalArgumentException("QUIC peer address is required"));
            if (peerAddress.isUnresolved()) {
                throw new IllegalArgumentException("QUIC peer address must be resolved: " + peerAddress);
            }
            if (peerAddress.getPort() < 1) {
                throw new IllegalArgumentException("QUIC peer address must have a positive port: " + peerAddress);
            }
            String tlsPeerName = target.tlsPeerName()
                    .orElseThrow(() -> new IllegalArgumentException("TLS peer name is required"));
            if (tlsPeerName.isBlank()) {
                throw new IllegalArgumentException("TLS peer name must not be blank");
            }
            int tlsPeerPort = target.tlsPeerPort();
            if (tlsPeerPort == 0) {
                target.tlsPeerPort(peerAddress.getPort());
            } else if (tlsPeerPort < 1 || tlsPeerPort > 65_535) {
                throw new IllegalArgumentException("TLS peer port must be between 1 and 65535: " + tlsPeerPort);
            }
            target.applicationProtocols(
                    QuicPublicApiSupport.applicationProtocols(target.applicationProtocols()));
        }
    }
}
