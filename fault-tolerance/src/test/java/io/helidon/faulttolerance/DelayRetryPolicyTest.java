/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import static io.helidon.config.testing.OptionalMatcher.empty;
import static io.helidon.config.testing.OptionalMatcher.value;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class DelayRetryPolicyTest {
    @Test
    void testDelay() {
        Retry.DelayingRetryPolicy policy = Retry.DelayingRetryPolicy.builder()
                .delay(Duration.ofMillis(100))
                .calls(3)
                .delayFactor(3)
                .build();

        long firstCall = System.nanoTime();

        Optional<Long> aLong = policy.nextDelayMillis(firstCall, 0, 0);
        assertThat(aLong, value(is(100L)));
        aLong = policy.nextDelayMillis(firstCall, 100, 1);
        assertThat(aLong, value(is(300L)));
        aLong = policy.nextDelayMillis(firstCall, 100, 2); // should just apply factor on last delay
        assertThat(aLong, value(is(300L)));
        aLong = policy.nextDelayMillis(firstCall, 100, 3); // limit of calls
        assertThat(aLong, is(empty()));
    }

    @Test
    void testNoDelay() {
        Retry.DelayingRetryPolicy policy = Retry.DelayingRetryPolicy.builder()
                .delay(Duration.ZERO)
                .calls(3)
                .delayFactor(3)
                .build();

        long firstCall = System.nanoTime();

        Optional<Long> aLong = policy.nextDelayMillis(firstCall, 0, 0);
        assertThat(aLong, value(is(0L)));
        aLong = policy.nextDelayMillis(firstCall, 0, 1);
        assertThat(aLong, value(is(0L)));
        aLong = policy.nextDelayMillis(firstCall, 0, 2); // should just apply factor on last delay
        assertThat(aLong, value(is(0L)));
        aLong = policy.nextDelayMillis(firstCall, 100, 3); // limit of calls
        assertThat(aLong, is(empty()));
    }
}
