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

package io.helidon.webclient.http3;

import java.time.Duration;
import java.util.Objects;

import io.helidon.builder.api.Prototype;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3Settings;
import io.helidon.quic.QuicConfig;

class Http3ClientConfigSupport {
    private Http3ClientConfigSupport() {
    }

    static void validateTimeouts(Duration initialResponseTimeout,
                                 Duration handshakeTimeout,
                                 Duration streamOpenTimeout) {
        requirePositiveNanos("initialResponseTimeout", initialResponseTimeout);
        requirePositiveNanos("handshakeTimeout", handshakeTimeout);
        requirePositiveNanos("streamOpenTimeout", streamOpenTimeout);
        if (initialResponseTimeout.compareTo(handshakeTimeout) > 0) {
            throw new IllegalArgumentException("initialResponseTimeout must not exceed handshakeTimeout: "
                                                       + initialResponseTimeout + " > " + handshakeTimeout);
        }
    }

    static void validateQuic(QuicConfig quicConfig) {
        Objects.requireNonNull(quicConfig, "quicConfig");
        long maxUniStreams = quicConfig.maxUniStreams();
        if (maxUniStreams < Http3Protocol.MINIMUM_PEER_UNI_STREAMS) {
            throw new IllegalArgumentException("HTTP/3 requires quic.maxUniStreams to be at least "
                                                       + Http3Protocol.MINIMUM_PEER_UNI_STREAMS + ": " + maxUniStreams);
        }
    }

    private static void requirePositiveNanos(String name, Duration duration) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive: " + duration);
        }
        try {
            duration.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(name + " must fit in nanoseconds: " + duration, e);
        }
    }

    static class ProtocolConfigDecorator
            implements Prototype.BuilderDecorator<Http3ClientProtocolConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(Http3ClientProtocolConfig.BuilderBase<?, ?> target) {
            Http3Settings.createConfigured(target.maxFieldSectionSize(),
                                           target.qpackMaxTableCapacity(),
                                           target.qpackBlockedStreams());
            target.quic().ifPresent(Http3ClientConfigSupport::validateQuic);
            validateTimeouts(target.initialResponseTimeout(),
                             target.handshakeTimeout(),
                             target.streamOpenTimeout());
        }
    }
}
