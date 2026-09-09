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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.faulttolerance.RetryConfig;

/**
 * Portable incoming delivery failure policy.
 */
@Api.Preview
@Prototype.Blueprint(decorator = FailurePolicyBuilderDecorator.class)
@Prototype.Configured
@Prototype.CustomMethods(FailurePolicyBuilderDecorator.class)
interface FailurePolicyBlueprint {
    /**
     * Fault tolerance retry configuration under {@code failure.retry}, with {@code calls}, {@code delay},
     * {@code delay-factor}, {@code jitter}, {@code jitter-factor}, {@code max-delay}, {@code overall-timeout}, and
     * {@code enable-metrics}; omitted keys use messaging defaults of {@code Integer.MAX_VALUE} calls, a one-second
     * initial delay, factor-two exponential backoff, no jitter, a one-minute maximum delay, a practically unbounded
     * timeout, and disabled metrics.
     * <p>
     * A programmatically supplied {@link RetryConfig} retains its FT values. To customize the messaging defaults, use
     * {@code RetryConfig.builder(FailurePolicy.create().retry())}.
     *
     * @return retry configuration
     */
    @Option.Configured
    @Option.DefaultMethod(type = FailurePolicyBuilderDecorator.class, value = "defaultRetry")
    RetryConfig retry();

    /**
     * Terminal disposition after delivery attempts are exhausted; dead letter requires a target channel. A
     * pre-dispatch mapping failure reported through
     * {@link ConnectorDeliveryReservation#startFailed(MessageBatch, RuntimeException)} or
     * {@link ConnectorDeliveryReservation#tryStartFailed(MessageBatch, RuntimeException)} is
     * always treated as exhausted after its initial attempt because the runtime cannot repeat transport mapping.
     *
     * @return terminal disposition
     */
    @Option.Configured
    @Option.Default("FAIL")
    FailureDisposition onExhausted();

    /**
     * Dead-letter delivery configuration; required for {@link FailureDisposition#DEAD_LETTER} and invalid for other
     * dispositions.
     * <p>
     * Runtime validation covers the logical channel graph. It cannot detect when distinct connector bindings resolve
     * to the same transport destination, such as two Kafka channels configured with the same topic. A dead-letter
     * target must not resolve back to the source connector.
     *
     * @return dead-letter configuration
     */
    @Option.Configured
    Optional<DeadLetterConfig> deadLetter();

}
