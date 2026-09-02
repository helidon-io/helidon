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

package io.helidon.messaging.spi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.messaging.BatchDeliveryException;
import io.helidon.messaging.BatchItemStatus;
import io.helidon.messaging.MessageBatch;
import io.helidon.messaging.MessagingRejectedException;

/**
 * Pending message-count capacity reserved by an incoming connector before it acquires transport data.
 * <p>
 * A connector must reserve the maximum delivery it may retain before polling, reading, or otherwise accepting that
 * delivery from its transport. The actual delivery supplied to {@link #start(MessageBatch)} or
 * {@link #startFailed(MessageBatch, RuntimeException)} must not exceed the reserved message count. Starting
 * atomically transfers the actual count from pending capacity to in-flight capacity and releases unused reservation
 * capacity.
 * <p>
 * A reservation has one owner and one terminal transition: it is either started once, started with a pre-dispatch
 * failure once, or closed. Closing is idempotent and releases capacity exactly once. After a successful start,
 * ownership transfers to the returned {@link ConnectorDelivery}; closing this reservation no longer releases the
 * delivery lease.
 * <p>
 * Each started connector delivery runs in a fresh Helidon context. The connector source thread's context is not
 * inherited by application handlers, processors, interceptors, routes, or outgoing connectors.
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
     * @return admitted delivery task and settlement lease bound to {@code batch} by the
     *         {@link ConnectorDelivery structured-failure alignment contract}
     * @throws MessagingRejectedException if the delivery cannot be admitted
     * @throws IllegalStateException if this reservation was already started or another start is in progress
     */
    ConnectorDelivery start(MessageBatch<?> batch);

    /**
     * Start a retained connector delivery whose transport-to-message mapping failed before dispatch.
     * <p>
     * Runtime implementations can apply the channel failure policy to failed items without invoking application
     * handlers for those items. The runtime cannot repeat transport mapping because it does not own the native
     * transport record or mapper. Bounded policies retain their configured failure-attempt accounting; an unlimited
     * policy treats the mapping failure as exhausted after its initial attempt so that the returned delivery always
     * terminates.
     * <p>
     * A structured {@link BatchDeliveryException} aligned to {@code batch} can identify unmappable items as
     * {@link BatchItemStatus#FAILED} or {@link BatchItemStatus#INDETERMINATE} and mapped but undispatched siblings as
     * {@link BatchItemStatus#NOT_ATTEMPTED}. The runtime settles the failed subset first, then dispatches the deferred
     * subset only after successful drop or dead-letter settlement. An undispatched item must not be marked
     * {@link BatchItemStatus#SUCCEEDED}.
     * <p>
     * The default implementation closes this reservation and rethrows the supplied failure so implementations
     * compiled against an earlier version fail safely instead of dispatching an invalid batch.
     *
     * @param batch retained delivery metadata used for failure accounting, drop, or dead-letter handling
     * @param failure pre-dispatch mapping failure
     * @return admitted delivery task and settlement lease bound to {@code batch} by the
     *         {@link ConnectorDelivery structured-failure alignment contract}
     * @throws RuntimeException the supplied failure when this operation is not implemented by the runtime
     */
    default ConnectorDelivery startFailed(MessageBatch<?> batch, RuntimeException failure) {
        RuntimeException primaryFailure;
        try {
            Objects.requireNonNull(batch);
            primaryFailure = Objects.requireNonNull(failure);
        } catch (RuntimeException validationFailure) {
            primaryFailure = validationFailure;
        }
        try {
            close();
        } catch (RuntimeException | Error closeFailure) {
            if (closeFailure != primaryFailure) {
                primaryFailure.addSuppressed(closeFailure);
            }
        }
        throw primaryFailure;
    }

    /**
     * Attempt to start a retained connector delivery without waiting.
     * <p>
     * The retained lease covers the supplied batch and subsets created through {@link MessageBatch#subset(List)}.
     * Rebuilt batches and replacement envelopes require separate admission.
     *
     * @param batch complete retained delivery
     * @return admitted delivery task bound to {@code batch} by the
     *         {@link ConnectorDelivery structured-failure alignment contract}, or empty when in-flight capacity is
     *         currently unavailable
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
