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

import io.helidon.builder.api.Prototype;

final class FailurePolicyBuilderDecorator
        implements Prototype.BuilderDecorator<FailurePolicy.BuilderBase<?, ?>> {
    @Override
    public void decorate(FailurePolicy.BuilderBase<?, ?> target) {
        RetryConfig retry = target.retry();
        Duration retryDelay = retry.delay();
        int maxAttempts = retry.maxAttempts();
        RetryConfigBuilderDecorator.validate(retryDelay, maxAttempts);
        FailureDisposition onExhausted = target.onExhausted();
        Optional<DeadLetterConfig> deadLetter = target.deadLetter();
        String deadLetterChannel = null;
        if (onExhausted == FailureDisposition.DROP && maxAttempts == 0) {
            throw new IllegalArgumentException(
                    "failure.retry.max-attempts must be greater than zero for DROP");
        }
        if (onExhausted == FailureDisposition.DEAD_LETTER) {
            if (maxAttempts == 0) {
                throw new IllegalArgumentException(
                        "failure.retry.max-attempts must be greater than zero for DEAD_LETTER");
            }
            if (deadLetter.isEmpty()) {
                throw new IllegalArgumentException(
                        "failure.dead-letter.channel must be configured for DEAD_LETTER");
            }
            deadLetterChannel = deadLetter.orElseThrow().channel();
            DeadLetterConfigBuilderDecorator.validate(deadLetterChannel);
        } else if (deadLetter.isPresent()) {
            throw new IllegalArgumentException(
                    "failure.dead-letter.channel is only valid for DEAD_LETTER");
        }

        RetryConfig canonicalRetry = RetryConfig.builder()
                .delay(retryDelay)
                .maxAttempts(maxAttempts)
                .build();
        DeadLetterConfig canonicalDeadLetter = deadLetterChannel == null
                ? null
                : DeadLetterConfig.builder().channel(deadLetterChannel).build();
        target.retry(canonicalRetry);
        if (canonicalDeadLetter != null) {
            target.deadLetter(canonicalDeadLetter);
        }
    }
}
