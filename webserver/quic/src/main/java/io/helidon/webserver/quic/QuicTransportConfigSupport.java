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

package io.helidon.webserver.quic;

import java.util.HashSet;
import java.util.Set;

import io.helidon.builder.api.Prototype;

final class QuicTransportConfigSupport {
    static final String DEFAULT_HANDSHAKE_TIMEOUT = "PT10S";
    static final int DEFAULT_MAX_PENDING_HANDSHAKES = 256;

    private QuicTransportConfigSupport() {
    }

    static final class Decorator implements Prototype.BuilderDecorator<QuicTransportConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(QuicTransportConfig.BuilderBase<?, ?> target) {
            if (target.handshakeTimeout().isNegative() || target.handshakeTimeout().isZero()) {
                throw new IllegalArgumentException("handshakeTimeout must be positive: " + target.handshakeTimeout());
            }
            try {
                target.handshakeTimeout().toNanos();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("handshakeTimeout must fit in nanoseconds: "
                                                           + target.handshakeTimeout(), e);
            }
            if (target.maxPendingHandshakes() < 1) {
                throw new IllegalArgumentException("maxPendingHandshakes must be greater than 0: "
                                                           + target.maxPendingHandshakes());
            }
            Set<String> configuredAlpns = new HashSet<>();
            for (String alpn : target.alpnPreference()) {
                if (!configuredAlpns.add(alpn)) {
                    throw new IllegalArgumentException("alpnPreference must not contain duplicate ALPN identifier: \""
                                                               + alpn + "\"");
                }
            }
        }
    }
}
