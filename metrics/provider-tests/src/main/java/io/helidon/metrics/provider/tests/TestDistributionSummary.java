/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
package io.helidon.metrics.provider.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.Bucket;
import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.ValueAtPercentile;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

class TestDistributionSummary {

    private static MetricsFactory metricsFactory;
    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        metricsFactory = Services.get(MetricsFactory.class);
        meterRegistry = metricsFactory.createMeterRegistry(MetricsConfig.create());
    }

    @AfterAll
    static void closeRegistry() {
        meterRegistry.close();
    }

    @Test
    void testBasicStats() {
        DistributionSummary summary = commonPrep("a",
                                                 metricsFactory.distributionStatisticsConfigBuilder());
        assertThat("Mean", summary.mean(), is(4D));
        assertThat("Min", summary.max(), is(7D));
        assertThat("Count", summary.count(), is(4L));
        assertThat("Total", summary.totalAmount(), is(16D));
    }

    @Test
    void testBasicSnapshot() {
        DistributionSummary summary = commonPrep("c",
                                                 metricsFactory.distributionStatisticsConfigBuilder());
        HistogramSnapshot snapshot = summary.snapshot();
        assertThat("Snapshot count", snapshot.count(), is(4L));
        assertThat("Snapshot total", snapshot.total(), is(16D));
        assertThat("Snapshot total as time (microseconds)", snapshot.total(TimeUnit.MICROSECONDS), is(0.016));
    }

    @Test
    void testPercentiles() {
        DistributionSummary summary = meterRegistry.getOrCreate(
                metricsFactory.distributionSummaryBuilder("d",
                                                          metricsFactory.distributionStatisticsConfigBuilder()
                                                                  .percentiles(0.5, 0.9, 0.99, 0.999)));
        for (int i = 0; i < 100; i++) {
            for (int value = 1; value <= 100; value++) {
                summary.record(value);
            }
        }
        HistogramSnapshot snapshot = summary.snapshot();

        List<ValueAtPercentile> vaps = list(snapshot.percentileValues());

        assertThat("Percentile settings",
                   vaps.stream().map(ValueAtPercentile::percentile).toList(),
                   contains(0.5D, 0.9D, 0.99D, 0.999D));
        double previous = Double.NEGATIVE_INFINITY;
        for (ValueAtPercentile vap : vaps) {
            assertThat("Value at percentile " + vap.percentile(),
                       vap.value(),
                       allOf(greaterThanOrEqualTo(0.8D), lessThanOrEqualTo(100.2D)));
            assertThat("Percentile values are monotonic", vap.value(), greaterThanOrEqualTo(previous));
            previous = vap.value();
        }
        assertThat("Median percentile value",
                   vaps.get(0).value(),
                   allOf(greaterThanOrEqualTo(30D), lessThanOrEqualTo(70D)));
        assertThat("P90 percentile value", vaps.get(1).value(), greaterThanOrEqualTo(80D));
        assertThat("P99 percentile value", vaps.get(2).value(), greaterThanOrEqualTo(90D));
        assertThat("P999 percentile value", vaps.get(3).value(), greaterThanOrEqualTo(90D));
    }

    @Test
    void testBuckets() {
        DistributionSummary summary = commonPrep("e",
                                                 metricsFactory.distributionStatisticsConfigBuilder()
                                                         .buckets(5.0D, 10.0D, 15.0D));

        HistogramSnapshot snapshot = summary.snapshot();

        List<Bucket> cabs = list(snapshot.histogramCounts());

        assertThat("Bucket boundaries", cabs.stream().map(Bucket::boundary).toList(), contains(5D, 10D, 15D));
        assertThat("Counts at buckets", cabs.stream().map(Bucket::count).toList(), contains(3L, 4L, 4L));
    }

    @Test
    void testPublishPercentileHistogramUsesExpectedValueBounds() {
        DistributionSummary summary = meterRegistry.getOrCreate(
                metricsFactory.distributionSummaryBuilder("histogram.flag.summary",
                                                          metricsFactory.distributionStatisticsConfigBuilder()
                                                                  .minimumExpectedValue(1D)
                                                                  .maximumExpectedValue(10D))
                        .publishPercentileHistogram(true));
        summary.record(2D);
        summary.record(11D);

        List<Bucket> buckets = list(summary.snapshot().histogramCounts());

        assertThat("Published histogram buckets", buckets.size(), greaterThanOrEqualTo(3));
        assertThat("Published histogram bucket boundaries",
                   buckets.stream().map(Bucket::boundary).toList(),
                   hasItems(1D, 10D));
    }

    private static DistributionSummary commonPrep(String name, DistributionStatisticsConfig.Builder statsConfigBuilder) {
        DistributionSummary summary = meterRegistry.getOrCreate(metricsFactory.distributionSummaryBuilder(name,
                                                                                                          statsConfigBuilder));
        List.of(1D, 3D, 5D, 7D)
                .forEach(summary::record);
        return summary;
    }

    /**
     * Creates a new {@link java.util.List} from an {@link java.lang.Iterable}.
     *
     * @param iterable iterable to convert
     * @param <T>      type of the items
     * @return new list containing the elements reported by the iterable
     */
    private static <T> List<T> list(Iterable<? extends T> iterable) {
        List<T> result = new ArrayList<>();
        iterable.forEach(result::add);
        return result;
    }
}
