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
     * Whether the calling thread is the runtime thread executing this delivery.
     *
     * @return {@code true} when called by this delivery task
     */
    boolean isCurrentThread();

    /**
     * Await delivery termination and propagate its processing failure.
     *
     * @throws InterruptedException if the waiting connector owner is interrupted
     * @throws RuntimeException if delivery processing fails
     */
    void await() throws InterruptedException;

    /**
     * Await delivery termination for at most the supplied duration and propagate its processing failure.
     *
     * @param timeout maximum wait
     * @return {@code true} if processing terminated, {@code false} on timeout
     * @throws InterruptedException if the waiting connector owner is interrupted
     * @throws RuntimeException if delivery processing fails
     */
    boolean await(Duration timeout) throws InterruptedException;

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
