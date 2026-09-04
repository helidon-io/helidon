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
import java.util.Objects;
import java.util.Set;

import io.helidon.builder.api.Prototype;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.WebServerConfig;

final class QuicTransportConfigSupport {
    static final String DEFAULT_HANDSHAKE_TIMEOUT = "PT10S";
    static final int DEFAULT_MAX_PENDING_HANDSHAKES = 256;

    private QuicTransportConfigSupport() {
    }

    static final class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Add this QUIC transport configuration to a listener.
         * <p>
         * The target listener supplies the address, port, TLS, routing, request limit, and connection limit.
         *
         * @param config QUIC transport configuration
         * @param listenerBuilder listener builder to update
         * @return the supplied listener builder
         */
        @Prototype.PrototypeMethod
        @Prototype.Annotated("io.helidon.common.Api.Incubating")
        static ListenerConfig.Builder addTo(QuicTransportConfig config, ListenerConfig.Builder listenerBuilder) {
            Objects.requireNonNull(listenerBuilder, "listenerBuilder");
            listenerBuilder.addBinding(QuicTransportBindingFactory.create(config));
            return listenerBuilder;
        }

        /**
         * Add this QUIC transport configuration to the default listener of a web server.
         * <p>
         * The target listener supplies the address, port, TLS, routing, request limit, and connection limit.
         *
         * @param config QUIC transport configuration
         * @param serverBuilder web server builder to update
         * @return the supplied web server builder
         */
        @Prototype.PrototypeMethod
        @Prototype.Annotated("io.helidon.common.Api.Incubating")
        static WebServerConfig.Builder addTo(QuicTransportConfig config, WebServerConfig.Builder serverBuilder) {
            Objects.requireNonNull(serverBuilder, "serverBuilder");
            serverBuilder.addBinding(QuicTransportBindingFactory.create(config));
            return serverBuilder;
        }
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
