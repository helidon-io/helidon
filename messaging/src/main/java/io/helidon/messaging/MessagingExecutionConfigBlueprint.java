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
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * Messaging admission and shutdown configuration.
 */
@Api.Preview
@Prototype.Blueprint(decorator = MessagingExecutionConfigBlueprint.BuilderDecorator.class)
@Prototype.Configured
interface MessagingExecutionConfigBlueprint {
    /**
     * Maximum number of admitted tasks that may wait for an execution slot.
     * <p>
     * Zero disables internal buffering; callers may still wait before admission, subject to
     * {@link #maxPendingAdmissions()}.
     *
     * @return queue capacity
     */
    @Option.Configured("queue-capacity")
    @Option.Default("0")
    int queueCapacity();

    /**
     * Maximum number of callers waiting for blocking admission and open connector reservations.
     * <p>
     * Waiting callers retain their own delivery until capacity becomes available. A connector reservation retains one
     * permit from reservation until it is started or closed, including while the connector acquires transport data.
     * Immediately admitted callers do not consume this budget.
     *
     * @return maximum pending admissions
     */
    @Option.Configured("max-pending-admissions")
    @Option.Default("64")
    int maxPendingAdmissions();

    /**
     * Maximum total messages retained by waiting callers and open connector reservations.
     * <p>
     * An open connector reservation retains its declared maximum while the connector may acquire transport data.
     * Immediately admissible caller deliveries do not consume this budget. A delivery that cannot fit is rejected
     * instead of parking while retaining unaccounted messages.
     *
     * @return maximum pending messages
     */
    @Option.Configured("max-pending-messages")
    @Option.Default("1024")
    int maxPendingMessages();

    /**
     * Maximum number of admitted messages.
     * <p>
     * This includes queued and executing work and completed connector deliveries whose admission lease remains held
     * until transport settlement or abandonment.
     *
     * @return maximum in-flight message count
     */
    @Option.Configured("max-in-flight-messages")
    @Option.Default("1024")
    int maxInFlightMessages();

    /**
     * Optional maximum time to wait for capacity.
     * <p>
     * For a two-phase connector delivery, capacity-wait time while reserving and starting shares this single budget;
     * transport acquisition time between those phases is excluded.
     *
     * @return admission timeout
     */
    @Option.Configured("admission-timeout")
    Optional<Duration> admissionTimeout();

    /**
     * Maximum time to wait for admitted messaging work to finish and graph-owned resources to close during shutdown
     * or failed-startup rollback.
     * <p>
     * This does not bound connector startup or readiness. It is a runtime-wide setting and cannot be overridden per
     * channel.
     *
     * @return shutdown timeout
     */
    @Option.Configured("shutdown-timeout")
    @Option.Default("PT10S")
    Duration shutdownTimeout();

    /**
     * Validates messaging execution limits.
     */
    class BuilderDecorator implements Prototype.BuilderDecorator<MessagingExecutionConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(MessagingExecutionConfig.BuilderBase<?, ?> target) {
            if (target.queueCapacity() < 0) {
                throw new IllegalArgumentException("messaging.execution.queue-capacity must be zero or greater");
            }
            if (target.maxPendingAdmissions() <= 0) {
                throw new IllegalArgumentException(
                        "messaging.execution.max-pending-admissions must be greater than zero");
            }
            if (target.maxPendingMessages() <= 0) {
                throw new IllegalArgumentException(
                        "messaging.execution.max-pending-messages must be greater than zero");
            }
            if (target.maxInFlightMessages() <= 0) {
                throw new IllegalArgumentException(
                        "messaging.execution.max-in-flight-messages must be greater than zero");
            }
            target.admissionTimeout().ifPresent(timeout -> requirePositive(timeout,
                                                                           "messaging.execution.admission-timeout"));
            requirePositive(target.shutdownTimeout(), "messaging.execution.shutdown-timeout");
        }

        private static void requirePositive(Duration duration, String name) {
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(name + " must be greater than zero");
            }
            try {
                duration.toNanos();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(name + " must be representable in nanoseconds", e);
            }
        }
    }
}
