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

import java.time.Duration;
import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.quic.QuicConfig;
import io.helidon.webserver.quic.spi.QuicSubProtocolProvider;
import io.helidon.webserver.spi.TransportBindingFactoryProvider;

/**
 * QUIC transport binding configuration.
 */
@Api.Incubating
@Prototype.Blueprint(decorator = QuicTransportConfigSupport.Decorator.class)
@Prototype.Configured(root = false, value = QuicTransportBindingTypes.QUIC)
@Prototype.Provides(TransportBindingFactoryProvider.class)
@Prototype.CustomMethods(QuicTransportConfigSupport.CustomMethods.class)
interface QuicTransportConfigBlueprint {
    /**
     * Whether this binding is enabled.
     *
     * @return whether this binding is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Whether this binding is required to become active.
     *
     * @return whether this binding is required
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean required();

    /**
     * Whether clients without a valid address token receive a QUIC Retry packet before connection allocation.
     * Enabling Retry adds one round trip to a client's first connection. Successful connections receive a NEW_TOKEN for a
     * later connection attempt.
     *
     * @return whether QUIC Retry is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean retryEnabled();

    /**
     * Maximum elapsed time allowed for a stateful server QUIC handshake; the value must be positive and fit in signed
     * 64-bit nanoseconds.
     * The deadline starts when an Initial packet is admitted and is not restarted by packet receipt, retransmission,
     * address validation, or partial TLS progress. Stateless Version Negotiation and Retry processing occur before the
     * deadline starts. This deadline is independent of the negotiated QUIC connection idle timeout.
     *
     * @return maximum server handshake duration
     */
    @Option.Configured
    @Option.Default(QuicTransportConfigSupport.DEFAULT_HANDSHAKE_TIMEOUT)
    Duration handshakeTimeout();

    /**
     * Maximum number of admitted QUIC connections which may concurrently have an incomplete server handshake.
     * Additional Initial packets are silently discarded before listener admission, TLS, or connection allocation while
     * this limit is reached. A connection stops consuming this limit when its handshake completes or its state is cleaned
     * up. This limit is independent of the listener-wide connection limit.
     * The value must be greater than zero.
     *
     * @return maximum concurrent pending server handshakes
     */
    @Option.Configured
    @Option.DefaultInt(QuicTransportConfigSupport.DEFAULT_MAX_PENDING_HANDSHAKES)
    int maxPendingHandshakes();

    /**
     * Server preference order for registered QUIC application-layer protocol negotiation (ALPN) identifiers; when
     * configured, the list must contain every ALPN identifier exposed by the listener's enabled QUIC protocols exactly
     * once, with the most preferred identifier first, while an empty list uses a deterministic transport-derived
     * fallback order.
     * Unknown, duplicate, and missing identifiers are rejected during configuration or listener startup. Applications
     * which require a specific preference must configure this option.
     *
     * @return explicit ALPN preference, or an empty list to use the transport-derived fallback
     */
    @Option.Configured("alpn-preference")
    @Option.Singular("preferredAlpn")
    List<String> alpnPreference();

    /**
     * QUIC sub-protocol providers available to this binding.
     *
     * @return discovered and explicitly configured QUIC sub-protocol providers
     */
    @Api.Internal
    @Option.Provider(QuicSubProtocolProvider.class)
    @Option.Singular
    @SuppressWarnings("rawtypes")
    List<QuicSubProtocolProvider> subProtocolProviders();

    /**
     * QUIC protocol and transport configuration.
     *
     * @return QUIC configuration
     */
    @Option.Configured(merge = true)
    @Option.DefaultMethod("create")
    QuicConfig quic();
}
