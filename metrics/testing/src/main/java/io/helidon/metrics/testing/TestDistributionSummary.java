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

import io.helidon.metrics.api.CountAtBucket;
import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.ValueAtPercentile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class TestDistributionSummary {

    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.createMeterRegistry(MetricsConfig.create());
    }

    private static DistributionSummary commonPrep(String name, DistributionStatisticsConfig.Builder statsConfigBuilder) {
        DistributionSummary summary = meterRegistry.getOrCreate(DistributionSummary.builder(name, statsConfigBuilder));
        List.of(1D, 3D, 5D, 7D)
                .forEach(summary::record);
        return summary;
    }


    @Test
    void testBasicStats() {
        DistributionSummary summary = commonPrep("a",
                                                 DistributionStatisticsConfig.builder());
        assertThat("Mean", summary.mean(), is(4D));
        assertThat("Min", summary.max(), is(7D));
        assertThat("Count", summary.count(), is(4L));
        assertThat("Total", summary.totalAmount(), is(16D));
    }

    @Test
    void testBasicSnapshot() {
        DistributionSummary summary = commonPrep("c",
                                                 DistributionStatisticsConfig.builder());
        HistogramSnapshot snapshot = summary.snapshot();
        assertThat("Snapshot count", snapshot.count(), is(4L));
        assertThat("Snapshot total", snapshot.total(), is(16D));
        assertThat("Snapshot total as time (microseconds)", snapshot.total(TimeUnit.MICROSECONDS), is(0.016));
    }

    @Test
    void testPercentiles() {
        DistributionSummary summary = commonPrep("d",
                                                 DistributionStatisticsConfig.builder()
                                                         .percentiles(0.5, 0.9, 0.99, 0.999));
        HistogramSnapshot snapshot = summary.snapshot();

        List<ValueAtPercentile> vaps = Util.list(snapshot.percentileValues());

        assertThat("Values at percentile",
                   vaps,
                   contains(
                           equalTo(Vap.create(0.50D, 3.0625D)),
                           equalTo(Vap.create(0.90D, 7.1875D)),
                           equalTo(Vap.create(0.99D, 7.1875D)),
                           equalTo(Vap.create(0.999D, 7.1875D))));
    }

    @Test
    void testBuckets() {
        DistributionSummary summary = commonPrep("e",
                DistributionStatisticsConfig.builder()
                        .buckets(5.0D, 10.0D, 15.0D));

        HistogramSnapshot snapshot = summary.snapshot();

        List<CountAtBucket> cabs = Util.list(snapshot.histogramCounts());

        assertThat("Counts at buckets",
                   cabs,
                   contains(
                           equalTo(Cab.create(5.0D, 3.0D)),
                           equalTo(Cab.create(10.0D, 4.0D)),
                           equalTo(Cab.create(15.0D, 4.0D))));

    }

    private record Vap(double percentile, double value) implements ValueAtPercentile {

        private static ValueAtPercentile create(double percentile, double value) {
            return new Vap(percentile, value);
        }

        @Override
        public double value(TimeUnit unit) {
            return unit.convert((long) value, TimeUnit.NANOSECONDS);
        }

        @Override
        public <R> R unwrap(Class<? extends R> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return String.format("Vap[percentile=%f,value=%f]", percentile, value);
        }
    }

    private record Cab(double bucket, double count) implements CountAtBucket {

        private static Cab create(double bucket, double count) {
            return new Cab(bucket, count);
        }

        @Override
        public double bucket(TimeUnit unit) {
            return unit.convert((long) bucket, TimeUnit.NANOSECONDS);
        }

        @Override
        public <R> R unwrap(Class<? extends R> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return String.format("Vap[percentile=%f,value=%f]", bucket, count);
        }
    }
}
