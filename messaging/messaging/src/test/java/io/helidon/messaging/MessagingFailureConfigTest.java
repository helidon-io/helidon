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

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.faulttolerance.Retry;
import io.helidon.faulttolerance.RetryConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingFailureConfigTest {
    @Test
    void emptyOverridesPreserveDeclaredPolicy() {
        FailurePolicy declared = declaredPolicy();
        MessagingFailureConfig overrides = MessagingFailureConfig.create();

        assertThat(overrides.retry().isEmpty(), is(true));
        assertThat(MessagingConfigSupport.failurePolicy(declared, overrides), is(declared));
        assertThat(MessagingConfigSupport.failurePolicy(declared, overrides).retry(), sameInstance(declared.retry()));
        assertThat(MessagingConfigSupport.failurePolicy(FailurePolicy.create(), overrides).retry(),
                   sameInstance(FailurePolicy.create().retry()));
    }

    @Test
    void explicitRetryReplacesDeclaredRetryAsAWhole() {
        FailurePolicy declared = declaredPolicy();
        MessagingFailureConfig overrides = MessagingFailureConfig.builder()
                .retry(retry -> retry.calls(2).enableMetrics(false))
                .build();

        FailurePolicy effective = MessagingConfigSupport.failurePolicy(declared, overrides);

        assertThat(effective.retry(), sameInstance(overrides.retry().orElseThrow()));
        RetryConfig retry = effective.retry().prototype();
        assertThat(retry.calls(), is(2));
        assertThat(retry.delay(), is(RetryConfig.DEFAULT_DELAY));
        assertThat(retry.overallTimeout(), is(RetryConfig.DEFAULT_OVERALL_TIMEOUT));
        assertThat(retry.enableMetrics(), is(false));
        assertThat(retry.name().isEmpty(), is(true));
        assertThat(retry.applyOn().isEmpty(), is(true));
        assertThat(retry.skipOn().isEmpty(), is(true));
        assertThat(retry.maxDelay().isEmpty(), is(true));
        assertThat(effective.onExhausted(), is(declared.onExhausted()));
        assertThat(effective.deadLetter(), is(declared.deadLetter()));
    }

    @Test
    void yamlAndBuildersProduceEquivalentCompleteRetries() {
        MessagingFailureConfig configured = MessagingFailureConfig.create(Config.just("""
                retry:
                  calls: 2
                  delay: PT0.002S
                  delay-factor: 1.5
                  jitter: PT0.001S
                  jitter-factor: -1
                  max-delay: PT1S
                  overall-timeout: PT5S
                  enable-metrics: false
                on-exhausted: DEAD_LETTER
                dead-letter:
                  channel: failed-orders
                """, MediaTypes.APPLICATION_YAML));
        MessagingFailureConfig programmatic = MessagingFailureConfig.builder()
                .retry(retry -> retry.name("retry")
                        .calls(2)
                        .delay(Duration.ofMillis(2))
                        .delayFactor(1.5)
                        .jitter(Duration.ofMillis(1))
                        .jitterFactor(-1)
                        .maxDelay(Duration.ofSeconds(1))
                        .overallTimeout(Duration.ofSeconds(5))
                        .enableMetrics(false))
                .onExhausted(FailureDisposition.DEAD_LETTER)
                .deadLetter(deadLetter -> deadLetter.channel("failed-orders"))
                .build();

        assertThat(configured.retry().orElseThrow().prototype(), is(programmatic.retry().orElseThrow().prototype()));
        assertThat(configured.onExhausted(), is(programmatic.onExhausted()));
        assertThat(configured.deadLetter(), is(programmatic.deadLetter()));
        assertThat(MessagingConfigSupport.failurePolicy(FailurePolicy.create(), configured).retry(),
                   sameInstance(configured.retry().orElseThrow()));
        assertThat(MessagingConfigSupport.failurePolicy(FailurePolicy.create(), programmatic).retry(),
                   sameInstance(programmatic.retry().orElseThrow()));
    }

    @Test
    void emptyExplicitRetryUsesFaultToleranceDefaults() {
        MessagingFailureConfig config = MessagingFailureConfig.create(Config.just("retry: {}", MediaTypes.APPLICATION_YAML));
        Retry retry = config.retry().orElseThrow();

        assertThat(retry.prototype().calls(), is(RetryConfig.DEFAULT_CALLS));
        assertThat(retry.prototype().delay(), is(RetryConfig.DEFAULT_DELAY));
        assertThat(retry.prototype().overallTimeout(), is(RetryConfig.DEFAULT_OVERALL_TIMEOUT));
        assertThat(MessagingConfigSupport.failurePolicy(declaredPolicy(), config).retry(), sameInstance(retry));
    }

    @Test
    void changingDispositionClearsAnInheritedDeadLetterTarget() {
        FailurePolicy effective = MessagingConfigSupport.failurePolicy(declaredPolicy(),
                MessagingFailureConfig.builder().onExhausted(FailureDisposition.DROP).build());

        assertThat(effective.onExhausted(), is(FailureDisposition.DROP));
        assertThat(effective.deadLetter().isEmpty(), is(true));
    }

    @Test
    void explicitDeadLetterTargetRemainsSubjectToPolicyValidation() {
        MessagingFailureConfig overrides = MessagingFailureConfig.builder()
                .onExhausted(FailureDisposition.DROP)
                .deadLetter(deadLetter -> deadLetter.channel("invalid-target"))
                .build();

        assertThrows(IllegalArgumentException.class,
                     () -> MessagingConfigSupport.failurePolicy(declaredPolicy(), overrides));
    }

    @Test
    void effectiveRetrySettingsAreValidatedDuringSetup() {
        assertThrows(RuntimeException.class,
                     () -> MessagingFailureConfig.builder().retry(retry -> retry.calls(0)).build());
        assertThrows(RuntimeException.class,
                     () -> MessagingFailureConfig.create(Config.just("retry.calls: 0", MediaTypes.APPLICATION_YAML)));
    }

    private static FailurePolicy declaredPolicy() {
        return FailurePolicy.builder()
                .retry(Retry.builder()
                               .name("declared")
                               .calls(7)
                               .delay(Duration.ofMillis(25))
                               .delayFactor(1.5)
                               .jitterFactor(0.2)
                               .maxDelay(Duration.ofSeconds(4))
                               .overallTimeout(Duration.ofSeconds(7))
                               .enableMetrics(true)
                               .addApplyOn(IllegalStateException.class)
                               .addSkipOn(UnsupportedOperationException.class)
                               .build())
                .onExhausted(FailureDisposition.DEAD_LETTER)
                .deadLetter(deadLetter -> deadLetter.channel("declared-dlq"))
                .build();
    }
}
