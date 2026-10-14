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
package io.helidon.metrics.api;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.is;

class NoOpTimerTest {

    @Test
    void disabledTimerExecutesSuppliersWithoutRecording() {
        var factory = NoOpMetricsFactory.create(MetricsConfig.create());
        Timer timer = factory.createMeterRegistry(MetricsConfig.create()).getOrCreate(factory.timerBuilder("disabled"));
        var calls = new AtomicInteger();
        Supplier<Integer> supplier = calls::incrementAndGet;

        assertThat(timer.record(supplier), is(1));
        assertThat(timer.wrap(supplier).get(), is(2));
        assertThat(calls.get(), is(2));
        assertThat(timer.count(), is(0L));
        assertThat(timer.snapshot().count(), is(0L));
        assertThat(timer.snapshot().percentileValues(), emptyIterable());
        assertThat(timer.snapshot().histogramCounts(), emptyIterable());
    }

    @Test
    void disabledTimerBuilderRetainsIndependentHistogramConfiguration() {
        var factory = NoOpMetricsFactory.create(MetricsConfig.create());
        Timer.Builder builder = factory.timerBuilder("disabled");
        assertThat(builder.percentiles(), emptyIterable());
        assertThat(builder.buckets(), emptyIterable());

        double[] percentiles = {0.5, 0.95};
        Duration[] buckets = {Duration.ofMillis(10), Duration.ofMillis(100)};
        builder.percentiles(percentiles).buckets(buckets);
        percentiles[0] = 0.1;
        buckets[0] = Duration.ofSeconds(1);

        assertThat(builder.percentiles(), contains(0.5, 0.95));
        assertThat(builder.buckets(), contains(Duration.ofMillis(10), Duration.ofMillis(100)));
    }
}
