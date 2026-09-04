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
import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * QUIC protocol configuration and durable transport policy.
 */
@Prototype.Blueprint(decorator = QuicConfigSupport.Decorator.class)
@Prototype.Configured
@Api.Incubating
interface QuicConfigBlueprint {

    /**
     * QUIC versions this endpoint may advertise and negotiate. The list must be non-empty and contain no duplicates.
     * For clients, the first list element is used for the first flight.
     *
     * @return available QUIC versions
     */
    @Option.Singular
    @Option.Configured
    @Option.Default({"QUIC_V2", "QUIC_V1"})
    List<QuicVersion> availableVersions();

    /**
     * Socket receive buffer size applied to a newly opened QUIC endpoint channel.
     * When empty, the platform default is used. A configured value must be greater than {@code 0}.
     *
     * @return requested socket receive buffer size in bytes
     */
    @Option.Configured
    Optional<Integer> socketReceiveBufferSize();

    /**
     * Socket send buffer size applied to a newly opened QUIC endpoint channel.
     * When empty, the platform default is used. A configured value must be greater than {@code 0}.
     *
     * @return requested socket send buffer size in bytes
     */
    @Option.Configured
    Optional<Integer> socketSendBufferSize();

    /**
     * Maximum UDP payload size the transport should attempt to send or accept.
     * The value must be between {@code 1200} and {@code 65527}, inclusive.
     *
     * @return maximum UDP payload size in bytes
     */
    @Option.Configured
    @Option.DefaultInt(QuicConfigSupport.DEFAULT_MAX_UDP_PAYLOAD_SIZE)
    int maxUdpPayloadSize();

    /**
     * Maximum number of packet-number ranges accepted in one peer ACK frame, including the first ACK range; after structural
     * and packet-type validation, a packet containing a structurally valid ACK frame that exceeds this limit is silently
     * discarded without closing the connection, and connection-state validation of the rejected ACK, such as checking for
     * acknowledgments of unsent or skipped packet numbers, is not performed. This local resource limit is not advertised or
     * negotiated with the peer. The value must be between {@code 1} and {@code 32763}, inclusive.
     *
     * @return maximum number of accepted ACK ranges per frame
     */
    @Option.Configured
    @Option.DefaultInt(QuicConfigSupport.DEFAULT_MAX_ACK_RANGES_PER_FRAME)
    int maxAckRangesPerFrame();

    /**
     * Maximum TLS handshake-message size accepted while reassembling QUIC CRYPTO data.
     * The value must be greater than {@code 0}.
     *
     * @return maximum TLS handshake-message size in bytes
     */
    @Option.Configured
    @Option.DefaultInt(QuicConfigSupport.DEFAULT_MAX_HANDSHAKE_MESSAGE_SIZE)
    int maxHandshakeMessageSize();

    /**
     * Connection-level flow-control limit advertised to the peer in QUIC transport
     * parameters. The value must be between {@code 0} and {@code 2^62 - 1}, inclusive.
     *
     * @return initial connection-level flow-control limit
     */
    @Option.Configured
    @Option.DefaultLong(QuicConfigSupport.DEFAULT_INITIAL_MAX_DATA)
    long initialMaxData();

    /**
     * Per-stream flow-control limit advertised to the peer in QUIC transport
     * parameters. The value must be between {@code 0} and {@code 2^62 - 1}, inclusive.
     *
     * @return initial per-stream flow-control limit
     */
    @Option.Configured
    @Option.DefaultLong(QuicConfigSupport.DEFAULT_INITIAL_MAX_STREAM_DATA)
    long initialMaxStreamData();

    /**
     * Initial bidirectional stream creation limit advertised to the peer.
     * The value must be between {@code 0} and {@code 2^60}, inclusive.
     *
     * @return initial bidirectional stream limit
     */
    @Option.Configured
    @Option.DefaultLong(QuicConfigSupport.DEFAULT_MAX_BIDI_STREAMS)
    long maxBidiStreams();

    /**
     * Initial unidirectional stream creation limit advertised to the peer.
     * The value must be between {@code 0} and {@code 2^60}, inclusive.
     *
     * @return initial unidirectional stream limit
     */
    @Option.Configured
    @Option.DefaultLong(QuicConfigSupport.DEFAULT_MAX_UNI_STREAMS)
    long maxUniStreams();

    /**
     * Additional QUIC transport parameters not already modeled by dedicated top-level
     * QUIC configuration options.
     *
     * @return additional transport-parameter configuration
     */
    @Option.Configured(merge = true)
    @Option.DefaultMethod("create")
    QuicTransportParametersConfig transportParameters();

    /**
     * Idle timeout advertised for new QUIC connections. The duration must be between {@code 0} and
     * {@code 2^62 - 1} milliseconds, inclusive; zero disables the timeout.
     *
     * @return idle timeout
     */
    @Option.Configured
    @Option.Default("PT30S")
    Duration idleTimeout();

    /**
     * Congestion-control algorithm used for newly created connections.
     *
     * @return congestion-control algorithm
     */
    @Option.Configured
    @Option.Default("CUBIC")
    QuicCongestionAlgorithm congestionAlgorithm();

    /**
     * Maximum number of bytes that congestion control may treat as in flight.
     * The value must be between {@code 1200} and {@code 2^62 - 1}, inclusive.
     *
     * @return bytes-in-flight limit
     */
    @Option.Configured
    @Option.DefaultLong(QuicConfigSupport.DEFAULT_MAX_BYTES_IN_FLIGHT)
    long maxBytesInFlight();

    /**
     * Whether TRACE diagnostics can include raw QUIC protocol data.
     * <p>
     * This is an unsafe diagnostic option. When enabled together with TRACE logging,
     * logs can include packet bytes, connection IDs, tokens, transport-parameter values,
     * and other secret material. This option can only be enabled programmatically through
     * the builder and must not be used in production.
     * </p>
     *
     * @return whether raw QUIC protocol data can be logged at TRACE level
     */
    @Option.DefaultBoolean(false)
    boolean unsafeRawData();
}
