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
import io.helidon.metrics.api.Timer;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
class TimeoutMetricsTest {
    private final MetricsFactory metricsFactory;

    TimeoutMetricsTest(MetricsFactory metricsFactory) {
        this.metricsFactory = metricsFactory;
    }

    @Test
    void testTimeout() {
        Timeout timeout;
        Counter callsCounter;

        timeout = Timeout.builder()
                .timeout(Duration.ofSeconds(1))
                .name("quick")      // same name
                .build();
        timeout.invoke(() -> null);
        callsCounter = MetricsUtils.counter(metricsFactory,
                                            Timeout.FT_TIMEOUT_CALLS_TOTAL,
                                            MetricsUtils.tag(metricsFactory, "name", "quick"));
        assertThat(callsCounter.count(), is(1L));

        timeout = Timeout.builder()
                .timeout(Duration.ofSeconds(1))
                .name("quick")      // same name
                .build();
        timeout.invoke(() -> null);
        assertThat(callsCounter.count(), is(2L));
    }

    @Test
    void testTimeoutTimer() {
        Timer timer;
        Timeout timeout;

        timeout = Timeout.builder()
                .timeout(Duration.ofSeconds(1))
                .name("very_quick")      // same name
                .build();
        timeout.invoke(() -> null);
        timer = MetricsUtils.timer(metricsFactory,
                                   Timeout.FT_TIMEOUT_EXECUTIONDURATION,
                                   MetricsUtils.tag(metricsFactory, "name", "very_quick"));
        assertThat(timer.count(), is(1L));

        timeout = Timeout.builder()
                .timeout(Duration.ofSeconds(1))
                .name("very_quick")      // same name
                .build();
        timeout.invoke(() -> null);
        assertThat(timer.count(), is(2L));
    }
}
