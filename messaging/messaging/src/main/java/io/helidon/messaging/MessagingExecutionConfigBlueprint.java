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
 * Optional messaging execution overrides. Unconfigured values inherit the messaging defaults.
 */
@Api.Preview
@Prototype.Blueprint(decorator = MessagingConfigSupport.ExecutionBuilderDecorator.class)
@Prototype.Sealed
@Prototype.Configured
interface MessagingExecutionConfigBlueprint {
    /**
     * Maximum number of admitted tasks that may wait for an execution slot; must be zero or greater.
     * <p>
     * Zero disables internal buffering; callers may still wait before admission, subject to
     * {@link #maxPendingAdmissions()}.
     *
     * @return queue capacity
     */
    @Option.Configured
    Optional<Integer> queueCapacity();

    /**
     * Positive maximum number of callers waiting for blocking admission and open connector reservations.
     * <p>
     * Waiting callers retain their own delivery until capacity becomes available. A connector reservation retains one
     * permit from reservation until it is started or closed, including while the connector acquires transport data.
     * Immediately admitted callers do not consume this budget.
     *
     * @return maximum pending admissions
     */
    @Option.Configured
    Optional<Integer> maxPendingAdmissions();

    /**
     * Positive maximum total messages retained by waiting callers and open connector reservations.
     * <p>
     * An open connector reservation retains its declared maximum while the connector may acquire transport data.
     * Immediately admissible caller deliveries do not consume this budget. A delivery that cannot fit is rejected
     * instead of parking while retaining unaccounted messages.
     *
     * @return maximum pending messages
     */
    @Option.Configured
    Optional<Integer> maxPendingMessages();

    /**
     * Positive maximum number of admitted messages.
     * <p>
     * This includes queued and executing work and completed connector deliveries whose admission lease remains held
     * until transport settlement or abandonment.
     *
     * @return maximum in-flight message count
     */
    @Option.Configured
    Optional<Integer> maxInFlightMessages();

    /**
     * Optional positive maximum time to wait for capacity, representable in nanoseconds.
     * <p>
     * For a two-phase connector delivery, capacity-wait time while reserving and starting shares this single budget;
     * transport acquisition time between those phases is excluded.
     *
     * @return admission timeout
     */
    @Option.Configured
    Optional<Duration> admissionTimeout();

}
