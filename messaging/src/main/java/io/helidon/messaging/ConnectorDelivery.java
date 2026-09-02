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

import java.time.Duration;

import io.helidon.common.Api;

/**
 * Runtime-owned asynchronous task used by an incoming connector while its owner thread performs transport maintenance.
 * <p>
 * Application-facing emission remains synchronous. This handle exists so a connector such as Kafka can keep polling
 * for group heartbeats while one retained delivery executes on a messaging-runtime virtual thread.
 * <p>
 * A {@link BatchDeliveryException} propagated by either {@code await} method is aligned with this delivery's retained
 * batch. Its {@link BatchDeliveryException#batch()} is the exact same {@link MessageBatch} instance supplied to the
 * successful {@link ConnectorDeliveryReservation#start(MessageBatch) start},
 * {@link ConnectorDeliveryReservation#tryStart(MessageBatch) tryStart}, or
 * {@link ConnectorDeliveryReservation#startFailed(MessageBatch, RuntimeException) startFailed} invocation that
 * returned this delivery, and every {@link BatchItemOutcome#index()} addresses that batch.
 */
@Api.Preview
public interface ConnectorDelivery extends AutoCloseable {
    /**
     * Whether delivery processing terminated.
     *
     * @return {@code true} when processing terminated
     */
    boolean isDone();

    /**
     * Whether the calling thread is the messaging-runtime thread currently executing this delivery.
     * <p>
     * Connector shutdown code must not wait for connector completion that depends on the current delivery returning;
     * this method allows that reentrant path to skip such a self-wait without exposing the runtime thread.
     *
     * @return {@code true} when called from this delivery's execution
     */
    boolean isCurrentThread();

    /**
     * Await delivery termination and propagate its processing failure.
     * <p>
     * If the waiting connector owner is interrupted, its interrupt status is restored before the failure is reported.
     *
     * @throws BatchDeliveryException if processing reports structured per-item failure outcomes; the exception is
     *                                aligned with this delivery's retained batch as specified by this interface
     * @throws MessagingException if the waiting connector owner is interrupted or processing fails with a checked
     *                            cause
     * @throws RuntimeException if delivery processing fails with another runtime exception
     */
    void await();

    /**
     * Await delivery termination for at most the supplied duration and propagate its processing failure.
     * <p>
     * If the waiting connector owner is interrupted, its interrupt status is restored before the failure is reported.
     *
     * @param timeout maximum wait
     * @return {@code true} if processing terminated, {@code false} on timeout
     * @throws BatchDeliveryException if processing reports structured per-item failure outcomes; the exception is
     *                                aligned with this delivery's retained batch as specified by this interface
     * @throws MessagingException if the waiting connector owner is interrupted or processing fails with a checked
     *                            cause
     * @throws RuntimeException if delivery processing fails with another runtime exception
     */
    boolean await(Duration timeout);

    /**
     * Cancel delivery processing.
     * <p>
     * Cancellation interrupts active cooperative work. Completion is not reported until the task actually
     * terminates. Cancellation does not release retained admission; the connector must still call {@link #close()}
     * when the transport delivery is settled or abandoned.
     */
    void cancel();

    /**
     * Release the delivery's message-count admission after transport settlement.
     * <p>
     * Connectors must retain this lease until the source record has been acknowledged, committed, negatively
     * acknowledged, or otherwise abandoned. Calling this method before processing terminates also requests
     * cancellation; admission is not released until active processing actually stops.
     */
    @Override
    void close();
}
