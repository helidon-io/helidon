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

import java.util.List;
import java.util.Optional;

import io.helidon.common.Api;

/**
 * Pending message-count capacity reserved by an incoming connector before it acquires transport data.
 * <p>
 * A connector must reserve the maximum delivery it may retain before polling, reading, or otherwise accepting that
 * delivery from its transport. The actual delivery supplied to {@link #start(MessageBatch)} must not exceed
 * the reserved message count. Starting atomically transfers the actual count from pending capacity to in-flight
 * capacity and releases unused reservation capacity.
 * <p>
 * A reservation has one owner and one terminal transition: it is either started once or closed. Closing is
 * idempotent and releases capacity exactly once. After a successful start, ownership transfers to the returned
 * {@link ConnectorDelivery}; closing this reservation no longer releases the delivery lease.
 */
@Api.Preview
public interface ConnectorDeliveryReservation extends AutoCloseable {
    /**
     * Start a retained connector delivery, waiting for execution admission when necessary.
     * <p>
     * Capacity-wait time spent acquiring this reservation and starting it shares the channel's single admission
     * timeout budget. Time spent by the connector acquiring transport data between those phases is excluded.
     * The retained lease covers the supplied batch and subsets created through {@link MessageBatch#subset(List)}.
     * Rebuilt batches and replacement envelopes require separate admission.
     *
     * @param batch complete retained delivery
     * @return admitted delivery task and settlement lease
     * @throws MessagingRejectedException if the delivery cannot be admitted
     * @throws IllegalStateException if this reservation was already started or another start is in progress
     */
    ConnectorDelivery start(MessageBatch<?> batch);

    /**
     * Attempt to start a retained connector delivery without waiting.
     * <p>
     * The retained lease covers the supplied batch and subsets created through {@link MessageBatch#subset(List)}.
     * Rebuilt batches and replacement envelopes require separate admission.
     *
     * @param batch complete retained delivery
     * @return admitted delivery task, or empty when in-flight capacity is currently unavailable
     * @throws MessagingRejectedException if the delivery exceeds this reservation or the reservation is unavailable
     * @throws IllegalStateException if this reservation was already started or another start is in progress
     */
    Optional<ConnectorDelivery> tryStart(MessageBatch<?> batch);

    /**
     * Close an unstarted reservation and release its pending capacity.
     * <p>
     * This method is idempotent. After a successful start the returned {@link ConnectorDelivery} owns the capacity.
     */
    @Override
    void close();
}
