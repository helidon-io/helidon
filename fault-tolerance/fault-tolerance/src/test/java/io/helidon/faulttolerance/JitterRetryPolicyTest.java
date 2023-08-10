/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

class JitterRetryPolicyTest {
    @Test
    void testFixedDelay() {
        Retry.JitterRetryPolicy policy = Retry.JitterRetryPolicy.builder()
                .jitter(Duration.ZERO)
                .delay(Duration.ofMillis(100))
                .calls(3)
                .build();

        long firstCall = System.nanoTime();
        Optional<Long> aLong = policy.nextDelayMillis(firstCall, 0, 0);
        assertThat(aLong, optionalValue(is(100L)));
        aLong = policy.nextDelayMillis(firstCall, aLong.get(), 1);
        assertThat(aLong, optionalValue(is(100L)));
    }

    @Test
    void testRepeats() {
        Retry.JitterRetryPolicy policy = Retry.JitterRetryPolicy.builder()
                .jitter(Duration.ZERO)
                .delay(Duration.ofMillis(100))
                .calls(3)
                .build();

        for (int i = 0; i < 100; i++) {
            long firstCall = System.nanoTime();
            // running in cycle to ensure we do not store state
            assertThat(policy.nextDelayMillis(firstCall, 0, 1), optionalValue(is(100L)));
            assertThat(policy.nextDelayMillis(firstCall, 0, 2), optionalValue(is(100L)));
            assertThat(policy.nextDelayMillis(firstCall, 0, 3), is(optionalEmpty()));
            assertThat(policy.nextDelayMillis(firstCall, 0, 1), optionalValue(is(100L)));
            assertThat(policy.nextDelayMillis(firstCall, 0, 3), is(optionalEmpty()));
        }
    }

    @Test
    void testRandomDelay() {
        Retry.JitterRetryPolicy policy = Retry.JitterRetryPolicy.builder()
                .jitter(Duration.ofMillis(50))
                .delay(Duration.ofMillis(100))
                .calls(3)
                .build();

        long firstCall = System.nanoTime();

        boolean hadNegative = false;
        boolean hadPositive = false;
        for (int i = 0; i < 10000; i++) {
            Optional<Long> aLong = policy.nextDelayMillis(firstCall, 0, 0);
            assertThat(aLong, optionalPresent());
            long value = aLong.get();
            assertThat(value, is(both(greaterThan(49L)).and(lessThan(151L))));
            if (value < 100) {
                hadNegative = true;
            } else if (value > 100) {
                hadPositive = true;
            }
        }

        assertThat("In 10000 tries we should get at least one negative jitter", hadNegative, is(true));
        assertThat("In 10000 tries we should get at least one positive jitter", hadPositive, is(true));
    }

    @Test
    void testNoDelayJitter() {
        Retry.JitterRetryPolicy policy = Retry.JitterRetryPolicy.builder()
                .jitter(Duration.ofMillis(50))
                .delay(Duration.ZERO)
                .calls(3)
                .build();

        long firstCall = System.nanoTime();

        boolean hadPositive = false;
        boolean hadZero = false;
        for (int i = 0; i < 10000; i++) {
            Optional<Long> aLong = policy.nextDelayMillis(firstCall, 0, 0);
            assertThat(aLong, optionalPresent());
            long value = aLong.get();
            assertThat(value, is(both(greaterThan(-1L)).and(lessThan(50L))));
            if (value == 0) {
                hadZero = true;
            } else if (value > 0) {
                hadPositive = true;
            }
        }

        assertThat("In 10000 tries we should get at least one negative jitter", hadZero, is(true));
        assertThat("In 10000 tries we should get at least one positive jitter", hadPositive, is(true));
    }

    @Test
    void testNoDelay() {
        Retry.JitterRetryPolicy policy = Retry.JitterRetryPolicy.builder()
                .jitter(Duration.ZERO)
                .delay(Duration.ZERO)
                .calls(3)
                .build();

        long firstCall = System.nanoTime();

        Optional<Long> aLong = policy.nextDelayMillis(firstCall, 0, 0);
        assertThat(aLong, optionalValue(is(0L)));
        aLong = policy.nextDelayMillis(firstCall, 0, 1);
        assertThat(aLong, optionalValue(is(0L)));
        aLong = policy.nextDelayMillis(firstCall, 0, 2); // should just apply factor on last delay
        assertThat(aLong, optionalValue(is(0L)));
        aLong = policy.nextDelayMillis(firstCall, 100, 3); // limit of calls
        assertThat(aLong, is(optionalEmpty()));
    }
}
