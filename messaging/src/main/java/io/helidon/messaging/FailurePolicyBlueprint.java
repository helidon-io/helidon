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
 * Portable incoming delivery failure policy.
 */
@Api.Preview
@Prototype.Blueprint(decorator = FailurePolicyBuilderDecorator.class)
@Prototype.Configured
interface FailurePolicyBlueprint {
    /**
     * Positive delay before retrying a failed delivery.
     *
     * @return retry delay
     */
    @Option.Configured("retry.delay")
    @Option.Default("PT1S")
    Duration retryDelay();

    /**
     * Maximum total delivery attempts, including the initial attempt; the value must be zero or greater, zero means
     * unlimited and is only valid with {@link FailureDisposition#FAIL}, and {@link FailureDisposition#DROP} and
     * {@link FailureDisposition#DEAD_LETTER} require a positive limit. An
     * unlimited pre-dispatch mapping failure reported through
     * {@link ConnectorDeliveryReservation#startFailed(MessageBatch, RuntimeException)} is treated as exhausted after
     * its initial attempt because the runtime cannot repeat transport mapping.
     *
     * @return maximum delivery attempts, or zero for unlimited attempts
     */
    @Option.Configured("retry.max-attempts")
    @Option.Default("0")
    int maxAttempts();

    /**
     * Terminal disposition after delivery attempts are exhausted; {@link FailureDisposition#DROP} and
     * {@link FailureDisposition#DEAD_LETTER} require positive maximum attempts, and dead letter also requires a target
     * channel.
     * <p>
     * {@link FailureDisposition#DROP} and {@link FailureDisposition#DEAD_LETTER} require a positive
     * {@link #maxAttempts()} so that exhaustion can be reached.
     *
     * @return terminal disposition
     */
    @Option.Configured
    @Option.Default("FAIL")
    FailureDisposition onExhausted();

    /**
     * Logical channel used for dead-letter delivery; required for {@link FailureDisposition#DEAD_LETTER} and invalid
     * for other dispositions.
     * <p>
     * Runtime validation covers the logical channel graph. It cannot detect when distinct connector bindings resolve
     * to the same transport destination, such as two Kafka channels configured with the same topic. A dead-letter
     * target must not resolve back to the source connector.
     *
     * @return dead-letter channel
     */
    @Option.Configured("dead-letter.channel")
    Optional<String> deadLetterChannel();

}
