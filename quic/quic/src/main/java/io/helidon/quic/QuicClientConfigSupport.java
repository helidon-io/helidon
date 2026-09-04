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

final class QuicClientConfigSupport {
    private QuicClientConfigSupport() {
    }

    static final class Decorator implements Prototype.BuilderDecorator<QuicClientConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(QuicClientConfig.BuilderBase<?, ?> target) {
            QuicPublicApiSupport.resolvedBindAddress(target.bindAddress(), "client bind address");
            QuicPublicApiSupport.positiveNanosDuration(target.initialResponseTimeout(), "initialResponseTimeout");
            QuicPublicApiSupport.positiveNanosDuration(target.handshakeTimeout(), "handshakeTimeout");
            QuicPublicApiSupport.positiveNanosDuration(target.streamOpenTimeout(), "streamOpenTimeout");
            if (target.initialResponseTimeout().compareTo(target.handshakeTimeout()) > 0) {
                throw new IllegalArgumentException("initialResponseTimeout must not exceed handshakeTimeout");
            }
            target.clientId().ifPresent(clientId -> {
                if (clientId.isBlank()) {
                    throw new IllegalArgumentException("clientId must not be blank");
                }
            });
        }
    }
}
