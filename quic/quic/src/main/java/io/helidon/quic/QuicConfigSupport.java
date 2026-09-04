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

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;

import io.helidon.builder.api.Prototype;

import static io.helidon.quic.frame.QuicFrame.MAX_VL_INTEGER;

final class QuicConfigSupport {
    static final int MINIMUM_DATAGRAM_SIZE = 1200;
    static final int MAXIMUM_DATAGRAM_SIZE = 65527;
    // Every additional ACK range needs at least a one-byte gap and a one-byte range length.
    static final int MAX_ACK_RANGES_PER_FRAME = MAXIMUM_DATAGRAM_SIZE / 2;
    static final int DEFAULT_MAX_ACK_RANGES_PER_FRAME = 1024;
    static final int DEFAULT_MAX_HANDSHAKE_MESSAGE_SIZE = 32 << 10;
    static final long DEFAULT_MAX_BYTES_IN_FLIGHT = 1L << 24;
    static final long MIN_MAX_BYTES_IN_FLIGHT = MINIMUM_DATAGRAM_SIZE;
    static final long DEFAULT_MAX_UNI_STREAMS = 100;
    static final long DEFAULT_MAX_BIDI_STREAMS = 100;
    static final long DEFAULT_INITIAL_MAX_STREAM_DATA = 6L << 20;
    static final long DEFAULT_INITIAL_MAX_DATA = 15L << 20;
    static final long MAX_STREAM_COUNT = 1L << 60;
    static final int DEFAULT_MAX_UDP_PAYLOAD_SIZE = MAXIMUM_DATAGRAM_SIZE;
    static final Duration MAX_IDLE_TIMEOUT = Duration.ofMillis(MAX_VL_INTEGER);

    private QuicConfigSupport() {
    }

    static final class Decorator implements Prototype.BuilderDecorator<QuicConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(QuicConfig.BuilderBase<?, ?> target) {
            if (target.availableVersions().isEmpty()) {
                throw new IllegalArgumentException("Need at least one available QUIC version");
            }
            HashSet<QuicVersion> availableVersions = new HashSet<>();
            for (QuicVersion version : target.availableVersions()) {
                Objects.requireNonNull(version, "availableVersions contains null");
                if (!availableVersions.add(version)) {
                    throw new IllegalArgumentException("Available QUIC versions must be distinct: "
                                                               + target.availableVersions());
                }
            }
            target.socketReceiveBufferSize().ifPresent(value -> {
                if (value < 1) {
                    throw new IllegalArgumentException("socketReceiveBufferSize must be greater than 0: " + value);
                }
            });
            target.socketSendBufferSize().ifPresent(value -> {
                if (value < 1) {
                    throw new IllegalArgumentException("socketSendBufferSize must be greater than 0: " + value);
                }
            });
            if (target.maxUdpPayloadSize() < MINIMUM_DATAGRAM_SIZE
                    || target.maxUdpPayloadSize() > MAXIMUM_DATAGRAM_SIZE) {
                throw new IllegalArgumentException("maxUdpPayloadSize must be between 1200 and 65527: "
                                                           + target.maxUdpPayloadSize());
            }
            if (target.maxAckRangesPerFrame() < 1
                    || target.maxAckRangesPerFrame() > MAX_ACK_RANGES_PER_FRAME) {
                throw new IllegalArgumentException("maxAckRangesPerFrame must be between 1 and "
                                                           + MAX_ACK_RANGES_PER_FRAME + ": "
                                                           + target.maxAckRangesPerFrame());
            }
            if (target.maxHandshakeMessageSize() <= 0) {
                throw new IllegalArgumentException("maxHandshakeMessageSize must be greater than 0: "
                                                           + target.maxHandshakeMessageSize());
            }
            if (target.initialMaxData() < 0 || target.initialMaxData() > MAX_VL_INTEGER) {
                throw new IllegalArgumentException("initialMaxData must be between 0 and 4611686018427387903: "
                                                           + target.initialMaxData());
            }
            if (target.initialMaxStreamData() < 0 || target.initialMaxStreamData() > MAX_VL_INTEGER) {
                throw new IllegalArgumentException("initialMaxStreamData must be between 0 and 4611686018427387903: "
                                                           + target.initialMaxStreamData());
            }
            if (target.maxBidiStreams() < 0 || target.maxBidiStreams() > MAX_STREAM_COUNT) {
                throw new IllegalArgumentException("maxBidiStreams must be between 0 and 1152921504606846976: "
                                                           + target.maxBidiStreams());
            }
            if (target.maxUniStreams() < 0 || target.maxUniStreams() > MAX_STREAM_COUNT) {
                throw new IllegalArgumentException("maxUniStreams must be between 0 and 1152921504606846976: "
                                                           + target.maxUniStreams());
            }
            if (target.idleTimeout().isNegative() || target.idleTimeout().compareTo(MAX_IDLE_TIMEOUT) > 0) {
                throw new IllegalArgumentException(
                        "idleTimeout must be between 0 and 4611686018427387903 milliseconds: " + target.idleTimeout());
            }
            if (target.maxBytesInFlight() < MIN_MAX_BYTES_IN_FLIGHT || target.maxBytesInFlight() > MAX_VL_INTEGER) {
                throw new IllegalArgumentException("maxBytesInFlight must be between 1200 and 4611686018427387903: "
                                                           + target.maxBytesInFlight());
            }
        }
    }
}
