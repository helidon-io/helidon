/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.providers.micrometer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Timer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

class TestTimer {

    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.createMeterRegistry(MetricsConfig.create());
    }

    @Test
    void testUnwrap() {
        long initialValue = 0L;
        long incrA = 2L;
        long incrB = 7L;
        Timer t = meterRegistry.getOrCreate(Timer.builder("a"));

        io.micrometer.core.instrument.Timer mTimer = t.unwrap(io.micrometer.core.instrument.Timer.class);
        assertThat("Initial value", mTimer.count(), is(initialValue));

        t.record(incrA, TimeUnit.MILLISECONDS);
        mTimer.record(incrB, TimeUnit.MILLISECONDS);

        assertThat("Neutral count after updates", t.count(), is(2L));
        assertThat("Micrometer count after updates", mTimer.count(), is(2L));

        double fromMicrometer = mTimer.totalTime(TimeUnit.MILLISECONDS);
        assertThat("Updated Micrometer value",
                   fromMicrometer,
                   greaterThanOrEqualTo((double) incrA + incrB));
        assertThat("Updated Micrometer value",
                   t.totalTime(TimeUnit.MILLISECONDS),
                   is(fromMicrometer));
    }
}
