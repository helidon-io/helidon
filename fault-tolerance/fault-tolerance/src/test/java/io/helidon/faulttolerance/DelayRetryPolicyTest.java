/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DelayRetryPolicyTest {
    @Test
    void testDelay() {
        Retry.DelayingRetryPolicy policy = Retry.DelayingRetryPolicy.builder()
                .delay(Duration.ofMillis(100))
                .calls(3)
                .delayFactor(3)
                .build();

        long firstCall = System.nanoTime();

        Optional<Long> aLong = policy.nextDelayMillis(firstCall, 0, 1);
        assertThat(aLong, optionalValue(is(100L)));
        aLong = policy.nextDelayMillis(firstCall, 100, 2);
        assertThat(aLong, optionalValue(is(300L)));
        aLong = policy.nextDelayMillis(firstCall, 100, 3); // limit of calls
        assertThat(aLong, is(optionalEmpty()));
    }

    @Test
    void testNoDelay() {
        Retry.DelayingRetryPolicy policy = Retry.DelayingRetryPolicy.builder()
                .delay(Duration.ZERO)
                .calls(3)
                .delayFactor(3)
                .build();

        long firstCall = System.nanoTime();

        Optional<Long> aLong = policy.nextDelayMillis(firstCall, 0, 1);
        assertThat(aLong, optionalValue(is(0L)));
        aLong = policy.nextDelayMillis(firstCall, 0, 2);
        assertThat(aLong, optionalValue(is(0L)));
        aLong = policy.nextDelayMillis(firstCall, 100, 3); // limit of calls
        assertThat(aLong, is(optionalEmpty()));
    }

    @Test
    void testMaximumDelay() {
        Retry.DelayingRetryPolicy policy = Retry.DelayingRetryPolicy.builder()
                .delay(Duration.ofMillis(100))
                .calls(4)
                .delayFactor(3)
                .maxDelay(Duration.ofMillis(250))
                .build();

        long firstCall = System.currentTimeMillis();

        assertThat(policy.nextDelayMillis(firstCall, 0, 1), optionalValue(is(100L)));
        assertThat(policy.nextDelayMillis(firstCall, 100, 2), optionalValue(is(250L)));
        assertThat(policy.nextDelayMillis(firstCall, 250, 3), optionalValue(is(250L)));
    }

    @Test
    void testDelayFactorAndJitterAreCombined() {
        Retry.DelayingRetryPolicy policy = Retry.DelayingRetryPolicy.builder()
                .delay(Duration.ofMillis(100))
                .calls(3)
                .delayFactor(2)
                .jitter(Duration.ofMillis(10))
                .build();
        boolean observedJitter = false;

        for (int i = 0; i < 1_000; i++) {
            Optional<Long> delay = policy.nextDelayMillis(System.currentTimeMillis(), 100, 2);
            assertThat(delay, optionalValue(both(greaterThanOrEqualTo(190L))
                                                     .and(lessThanOrEqualTo(210L))));
            if (delay.orElseThrow() != 200) {
                observedJitter = true;
            }
        }

        assertThat("Configured jitter must be applied after the delay factor", observedJitter, is(true));
    }

    @Test
    void testDelayFactorAndRelativeJitterAreCombined() {
        Retry.DelayingRetryPolicy policy = Retry.DelayingRetryPolicy.builder()
                .delay(Duration.ofMillis(100))
                .calls(3)
                .delayFactor(2)
                .jitterFactor(0.25)
                .build();
        boolean observedJitter = false;

        for (int i = 0; i < 1_000; i++) {
            Optional<Long> delay = policy.nextDelayMillis(System.currentTimeMillis(), 100, 2);
            assertThat(delay, optionalValue(both(greaterThanOrEqualTo(150L))
                                                     .and(lessThanOrEqualTo(250L))));
            if (delay.orElseThrow() != 200) {
                observedJitter = true;
            }
        }

        assertThat("Configured relative jitter must be applied after the delay factor", observedJitter, is(true));
    }

    @Test
    void testHugeDelayDoesNotOverflow() {
        Retry.DelayingRetryPolicy policy = Retry.DelayingRetryPolicy.builder()
                .delay(Duration.ofMillis(Long.MAX_VALUE))
                .calls(3)
                .delayFactor(Double.MAX_VALUE)
                .build();

        assertThat(policy.nextDelayMillis(System.currentTimeMillis(), 0, 1),
                   optionalValue(is(Long.MAX_VALUE)));
        assertThat(policy.nextDelayMillis(System.currentTimeMillis(), Long.MAX_VALUE, 2),
                   optionalValue(is(Long.MAX_VALUE)));
    }

    @Test
    void testConfigurationCombinesDelayFactorJitterAndMaximumDelay() {
        RetryConfig config = RetryConfig.builder()
                .calls(3)
                .delay(Duration.ofMillis(100))
                .delayFactor(2)
                .jitter(Duration.ofMillis(10))
                .maxDelay(Duration.ofMillis(205))
                .buildPrototype();

        assertThat(config.maxDelay(), optionalValue(is(Duration.ofMillis(205))));
        Retry.RetryPolicy policy = config.retryPolicy().orElseThrow();
        assertThat(policy, instanceOf(Retry.DelayingRetryPolicy.class));

        Optional<Long> delay = policy.nextDelayMillis(System.currentTimeMillis(), 100, 2);
        assertThat(delay, optionalValue(both(greaterThanOrEqualTo(190L))
                                                 .and(lessThanOrEqualTo(205L))));
    }

    @Test
    void testEquivalentPoliciesAndConfigurationsAreEqual() {
        Retry.DelayingRetryPolicy firstPolicy = Retry.DelayingRetryPolicy.builder()
                .calls(5)
                .delay(Duration.ofMillis(100))
                .delayFactor(2)
                .jitterFactor(0.25)
                .maxDelay(Duration.ofSeconds(2))
                .build();
        Retry.DelayingRetryPolicy secondPolicy = Retry.DelayingRetryPolicy.builder()
                .calls(5)
                .delay(Duration.ofMillis(100))
                .delayFactor(2)
                .jitterFactor(0.25)
                .maxDelay(Duration.ofSeconds(2))
                .build();
        RetryConfig firstConfig = RetryConfig.builder()
                .calls(5)
                .delay(Duration.ofMillis(100))
                .delayFactor(2)
                .jitterFactor(0.25)
                .maxDelay(Duration.ofSeconds(2))
                .buildPrototype();
        RetryConfig secondConfig = RetryConfig.builder()
                .calls(5)
                .delay(Duration.ofMillis(100))
                .delayFactor(2)
                .jitterFactor(0.25)
                .maxDelay(Duration.ofSeconds(2))
                .buildPrototype();

        assertThat(firstPolicy, is(secondPolicy));
        assertThat(firstPolicy.hashCode(), is(secondPolicy.hashCode()));
        assertThat(firstConfig, is(secondConfig));
        assertThat(firstConfig.hashCode(), is(secondConfig.hashCode()));
    }

    @Test
    void testInvalidConfigurationIsRejected() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> RetryConfig.builder().calls(0).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> RetryConfig.builder().delay(Duration.ofMillis(-1)).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> RetryConfig.builder().overallTimeout(Duration.ZERO).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> RetryConfig.builder().delayFactor(Double.POSITIVE_INFINITY).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> RetryConfig.builder().jitter(Duration.ofMillis(-1)).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> RetryConfig.builder().jitterFactor(-0.5).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> RetryConfig.builder().jitterFactor(1.5).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> RetryConfig.builder().jitterFactor(1).buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> RetryConfig.builder()
                                           .jitter(Duration.ofMillis(10))
                                           .jitterFactor(0.25)
                                           .buildPrototype()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> RetryConfig.builder().maxDelay(Duration.ofMillis(-1)).buildPrototype()));
    }

    @Test
    void testInvalidPolicyIsRejected() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> Retry.DelayingRetryPolicy.builder().calls(0).build()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> Retry.DelayingRetryPolicy.builder()
                                           .delay(Duration.ofMillis(-1))
                                           .build()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> Retry.DelayingRetryPolicy.builder()
                                           .delayFactor(Double.POSITIVE_INFINITY)
                                           .build()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> Retry.DelayingRetryPolicy.builder().jitterFactor(1).build()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> Retry.DelayingRetryPolicy.builder()
                                           .jitter(Duration.ofMillis(10))
                                           .jitterFactor(0.25)
                                           .build()),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> Retry.DelayingRetryPolicy.builder()
                                           .maxDelay(Duration.ofMillis(-1))
                                           .build()));
    }
}
