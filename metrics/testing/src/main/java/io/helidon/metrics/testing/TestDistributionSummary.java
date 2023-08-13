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
package io.helidon.metrics.testing;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestDistributionSummary {

    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.createMeterRegistry(MetricsConfig.create());
    }

    private static DistributionSummary commonPrep(String name) {
        DistributionStatisticsConfig.Builder statsConfigBuilder = DistributionStatisticsConfig.builder()
                .percentilesHistogram(true)
                .percentiles(0.5, 0.9, 0.99, 0.999);
        DistributionSummary summary = meterRegistry.getOrCreate(DistributionSummary.builder(name, statsConfigBuilder));
        List.of(1D, 3D, 5D, 7D)
                .forEach(summary::record);
        return summary;
    }

    @Test
    void testBasicStats() {
        DistributionSummary summary = commonPrep("a");
        assertThat("Mean", summary.mean(), is(4D));
        assertThat("Min", summary.max(), is(7D));
        assertThat("Count", summary.count(), is(4L));
        assertThat("Total", summary.totalAmount(), is(16D));
    }

    @Test
    void testSnapshot() {
        DistributionSummary summary = commonPrep("c");
        HistogramSnapshot snapshot = summary.snapshot();
        assertThat("Snapshot count", snapshot.count(), is(4L));
        assertThat("Snapshot total", snapshot.total(), is(16D));
        assertThat("Snapshot total as time (microseconds)", snapshot.total(TimeUnit.MICROSECONDS), is(0.016));
    }

    @Test
    void testPercentiles() {
//        DistributionSummary summary = commonPrep("d");
//        HistogramSnapshot snapshot = summary.snapshot();

//        List<ValueAtPercentile> vaps = Util.list(snapshot.percentileValues());
//        List<CountAtBucket> cabs = Util.list(snapshot.histogramCounts());
    }
}
