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

package io.helidon.messaging;

import java.util.Optional;

import io.helidon.common.Api;

/**
 * Runtime lifecycle and retained-delivery admission exposed to an incoming connector.
 */
@Api.Preview
public interface IncomingConnectorContext {
    /**
     * Report that transport resources are ready and wait until the owning graph is running.
     * <p>
     * An incoming connector must call this exactly once before acquiring its first delivery. Runtime-managed contexts
     * block until every incoming connector is ready. They return {@code false} when startup is cancelled. The default
     * permits independently run connectors to proceed immediately.
     *
     * @return {@code true} when delivery acquisition may start, {@code false} when startup was cancelled
     */
    default boolean awaitRunning() {
        return true;
    }

    /**
     * Channel name.
     *
     * @return channel name
     */
    String channel();

    /**
     * Maximum messages the runtime can admit in one retained connector delivery.
     * <p>
     * Incoming connectors should use this limit to bound polling or reading before submitting a delivery.
     *
     * @return maximum messages per delivery
     */
    default int maxDeliveryMessages() {
        return Integer.MAX_VALUE;
    }

    /**
     * Reserve pending capacity for the largest delivery this channel accepts before acquiring one connector delivery.
     * <p>
     * Runtime-provided contexts block with bounded pending accounting.
     *
     * @return pending delivery reservation
     * @throws MessagingRejectedException if capacity cannot be reserved
     */
    ConnectorDeliveryReservation reserveDelivery();

    /**
     * Attempt to reserve pending capacity before acquiring one connector delivery without blocking.
     * <p>
     * A durable connector can pause new acquisition and continue transport maintenance while this method returns
     * empty. Repeated attempts share the channel's admission-timeout budget until a reservation succeeds.
     *
     * @return reservation, or empty when pending capacity is currently unavailable
     * @throws MessagingRejectedException if the request can never fit, its admission timeout expires, or the runtime
     *                                    is shutting down
     */
    Optional<ConnectorDeliveryReservation> tryReserveDelivery();
}
