/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.ValueAtPercentile;
import io.helidon.testing.junit5.Testing;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@Testing.Test
class TestDistributionSummary {

    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.globalRegistry();
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

        List<ValueAtPercentile> vaps = list(snapshot.percentileValues());

        // Micrometer allows developers to set the precision with which percentiles are maintained which can give rise to
        // some variance in the values reported for the percentiles.
        assertThat("Values at percentile",
                   vaps,
                   contains(
                           ValueAtPercentileMatcher.matchesWithinTolerance(Vap.create(0.50D, 3.0D), 0.2d),
                           ValueAtPercentileMatcher.matchesWithinTolerance(Vap.create(0.90D, 7.0D), 0.2d),
                           ValueAtPercentileMatcher.matchesWithinTolerance(Vap.create(0.99D, 7.0), 0.2d),
                           ValueAtPercentileMatcher.matchesWithinTolerance(Vap.create(0.999D, 7.0), 0.2d)));
    }

    @Test
    void testBuckets() {
        DistributionSummary summary = commonPrep("e",
                                                 DistributionStatisticsConfig.builder()
                                                         .buckets(5.0D, 10.0D, 15.0D));

        HistogramSnapshot snapshot = summary.snapshot();

        List<Bucket> cabs = list(snapshot.histogramCounts());

        assertThat("Counts at buckets",
                   cabs,
                   contains(
                           equalTo(Cab.create(5.0D, 3)),
                           equalTo(Cab.create(10.0D, 4)),
                           equalTo(Cab.create(15.0D, 4))));

    }

    private static DistributionSummary commonPrep(String name, DistributionStatisticsConfig.Builder statsConfigBuilder) {
        DistributionSummary summary = meterRegistry.getOrCreate(DistributionSummary.builder(name, statsConfigBuilder));
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

    private record Vap(double percentile, double value) implements ValueAtPercentile {

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

        private static ValueAtPercentile create(double percentile, double value) {
            return new Vap(percentile, value);
        }
    }

    /**
     * Hamcrest matcher for a ValueAtPercentile that checks the percentile setting and the value recorded for that percentile.
     */
    private static class ValueAtPercentileMatcher extends TypeSafeMatcher<ValueAtPercentile> {

        static ValueAtPercentileMatcher matchesWithinTolerance(ValueAtPercentile expected, double variance) {
            return new ValueAtPercentileMatcher(expected, variance);
        }

        private final ValueAtPercentile expected;
        private final Matcher<Double> valueWithinToleranceMatcher;

        private ValueAtPercentileMatcher(ValueAtPercentile expected, double variance) {
            valueWithinToleranceMatcher = Matchers.closeTo(expected.value(), variance);
            this.expected = expected;
        }

        @Override
        protected boolean matchesSafely(ValueAtPercentile item) {
            return item.percentile() == expected.percentile()
                    && valueWithinToleranceMatcher.matches(item.value());
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("percentile expected to be " + expected.percentile()
                                           + " and ");
            valueWithinToleranceMatcher.describeTo(description);
        }
    }

    private record Cab(double boundary, long count) implements Bucket {

        @Override
        public double boundary(TimeUnit unit) {
            return unit.convert((long) boundary, TimeUnit.NANOSECONDS);
        }

        @Override
        public <R> R unwrap(Class<? extends R> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return String.format("Vap[boundary=%f,count=%d]", boundary, count);
        }

        private static Cab create(double bucket, long count) {
            return new Cab(bucket, count);
        }
    }
}
