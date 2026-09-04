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

import io.helidon.builder.api.Prototype;

final class QuicServerConfigSupport {
    private QuicServerConfigSupport() {
    }

    static final class Decorator implements Prototype.BuilderDecorator<QuicServerConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(QuicServerConfig.BuilderBase<?, ?> target) {
            target.applicationProtocols(
                    QuicPublicApiSupport.applicationProtocols(target.applicationProtocols()));
            QuicPublicApiSupport.resolvedBindAddress(target.bindAddress(), "server bind address");
            QuicPublicApiSupport.positiveNanosDuration(target.handshakeTimeout(), "handshakeTimeout");
            QuicPublicApiSupport.positiveNanosDuration(target.streamOpenTimeout(), "streamOpenTimeout");
            QuicPublicApiSupport.positiveNanosDuration(target.shutdownTimeout(), "shutdownTimeout");
            if (target.maxPendingHandshakes() < 1) {
                throw new IllegalArgumentException("maxPendingHandshakes must be greater than 0: "
                                                           + target.maxPendingHandshakes());
            }
            if (target.maxConnections() < 1) {
                throw new IllegalArgumentException("maxConnections must be greater than 0: "
                                                           + target.maxConnections());
            }
            target.serverId().ifPresent(serverId -> {
                if (serverId.isBlank()) {
                    throw new IllegalArgumentException("serverId must not be blank");
                }
            });
        }
    }
}
