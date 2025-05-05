/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Tag;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
class RetryMetricsTest {

    @BeforeAll
    static void setupTest() {
        FaultTolerance.config(Config.create());
    }

    @Test
    void testRetry() {
        Retry retry;
        Counter retryCounter;
        Counter callsCounter;

        retry = Retry.builder()
                .calls(3)
                .overallTimeout(Duration.ofSeconds(5))
                .delay(Duration.ofMillis(0))
                .name("flaky")     // same name
                .build();
        retry.invoke(new FlakySupplier(2));
        callsCounter = MetricsUtils.counter(Retry.FT_RETRY_CALLS_TOTAL, Tag.create("name", "flaky"));
        assertThat(callsCounter.count(), is(3L));
        retryCounter = MetricsUtils.counter(Retry.FT_RETRY_RETRIES_TOTAL, Tag.create("name", "flaky"));
        assertThat(retryCounter.count(), is(2L));

        retry = Retry.builder()
                .calls(4)
                .overallTimeout(Duration.ofSeconds(5))
                .delay(Duration.ofMillis(0))
                .name("flaky")     // same name
                .build();
        retry.invoke(new FlakySupplier(3));
        assertThat(callsCounter.count(), is(7L));
        assertThat(retryCounter.count(), is(5L));
    }

    @Test
    void testRetryGeneratedName() {
        Retry retry;
        Counter retryCounter;
        Counter callsCounter;

        retry = Retry.builder()
                .calls(2)
                .overallTimeout(Duration.ofSeconds(5))
                .delay(Duration.ofMillis(0))
                .build();
        retry.invoke(new FlakySupplier(1));
        callsCounter = MetricsUtils.counter(Retry.FT_RETRY_CALLS_TOTAL, Tag.create("name", retry.name()));
        assertThat(callsCounter.count(), is(2L));
        retryCounter = MetricsUtils.counter(Retry.FT_RETRY_RETRIES_TOTAL, Tag.create("name", retry.name()));
        assertThat(retryCounter.count(), is(1L));
    }

    private static class FlakySupplier implements Supplier<Long> {

        private final AtomicInteger failures;

        FlakySupplier(int failures) {
            this.failures = new AtomicInteger(Integer.max(0, failures));
        }

        @Override
        public Long get() {
            if (failures.getAndDecrement() == 0) {
                return 0L;      // success
            }
            throw new RuntimeException("failed");
        }
    }
}
