/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static io.helidon.faulttolerance.CircuitBreaker.FT_CIRCUITBREAKER_CALLS_TOTAL;
import static io.helidon.faulttolerance.CircuitBreaker.FT_CIRCUITBREAKER_OPENED_TOTAL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testing.Test
class CircuitBreakerMetricsTest extends CircuitBreakerBaseTest {
    private final MetricsFactory metricsFactory;

    CircuitBreakerMetricsTest(MetricsFactory metricsFactory) {
        this.metricsFactory = metricsFactory;
    }

    @Test
    void testCircuitBreaker() {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .volume(10)
                .errorRatio(20)
                .delay(Duration.ofMillis(200))
                .successThreshold(2)
                .build();

        good(breaker);
        good(breaker);
        bad(breaker);
        good(breaker);
        good(breaker);
        good(breaker);
        good(breaker);
        good(breaker);
        bad(breaker);
        bad(breaker);       // should open - window complete

        Counter callsCounter = MetricsUtils.counter(metricsFactory,
                                                    FT_CIRCUITBREAKER_CALLS_TOTAL,
                                                    MetricsUtils.tag(metricsFactory, "name", breaker.name()));
        assertThat(callsCounter.count(), is(10L));
        Counter openedCounter = MetricsUtils.counter(metricsFactory,
                                                     FT_CIRCUITBREAKER_OPENED_TOTAL,
                                                     MetricsUtils.tag(metricsFactory, "name", breaker.name()));
        assertThat(openedCounter.count(), is(1L));
    }
}
