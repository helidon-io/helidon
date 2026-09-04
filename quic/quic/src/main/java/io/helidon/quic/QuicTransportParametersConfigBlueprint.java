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
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * Additional QUIC transport parameters for client and server endpoints; active connection migration is not supported,
 * so endpoints always advertise the {@code disable_active_migration} transport parameter.
 * Common parameters such as idle timeout, stream limits, flow-control windows, and maximum UDP payload
 * size remain modeled directly on {@link QuicConfig}.
 */
@Prototype.Blueprint(decorator = QuicTransportParametersConfigSupport.Decorator.class)
@Prototype.Configured
@Api.Incubating
interface QuicTransportParametersConfigBlueprint {
    /**
     * Exponent this endpoint uses to encode the ACK delay it reports to its peer.
     * The value must be between {@code 0} and {@code 20}, inclusive. When not configured, the RFC default of {@code 3}
     * is used.
     *
     * @return optional ACK-delay exponent override
     */
    @Option.Configured
    Optional<Integer> ackDelayExponent();

    /**
     * Maximum ACK delay this endpoint advertises to its peer. The value must be between {@code 0} and {@code 16383}
     * milliseconds, inclusive. When not configured, the RFC default of {@code 25} milliseconds is used.
     *
     * @return optional maximum ACK delay
     */
    @Option.Configured
    Optional<Duration> maxAckDelay();

    /**
     * Maximum number of active connection IDs this endpoint is willing to retain from its peer.
     * The value must be between {@code 2} and {@code 1024}, inclusive. The implementation limit bounds connection-ID
     * tracking and retirement work. When not configured, the RFC default of {@code 2} is used.
     *
     * @return optional active-connection-ID limit
     */
    @Option.Configured
    Optional<Long> activeConnectionIdLimit();
}
