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

package io.helidon.metrics.providers.helidon;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.Bucket;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.ValueAtPercentile;

final class HelidonHistogramSnapshot implements HistogramSnapshot {
    private final long count;
    private final double total;
    private final double max;
    private final List<ValueAtPercentile> percentileValues;
    private final List<Bucket> histogramCounts;

    private HelidonHistogramSnapshot(long count,
                                     double total,
                                     double max,
                                     Collection<ValueAtPercentile> percentileValues,
                                     Collection<Bucket> histogramCounts) {
        this.count = count;
        this.total = total;
        this.max = max;
        this.percentileValues = List.copyOf(Objects.requireNonNull(percentileValues));
        this.histogramCounts = List.copyOf(Objects.requireNonNull(histogramCounts));
    }

    static HelidonHistogramSnapshot create(long count,
                                           double total,
                                           double max,
                                           double[] samples,
                                           double[] percentiles,
                                           double[] buckets,
                                           long[] bucketCounts) {
        double[] sorted = HelidonTypes.sorted(samples);
        List<ValueAtPercentile> percentileValues = new ArrayList<>(percentiles.length);
        for (double percentile : percentiles) {
            percentileValues.add(new HelidonValueAtPercentile(percentile, valueAt(sorted, percentile)));
        }

        List<Bucket> histogramCounts = new ArrayList<>(buckets.length);
        for (int i = 0; i < buckets.length; i++) {
            histogramCounts.add(new HelidonBucket(buckets[i], bucketCounts[i]));
        }

        return new HelidonHistogramSnapshot(count, total, max, percentileValues, histogramCounts);
    }

    static HelidonHistogramSnapshot empty(long count, double total, double max) {
        return new HelidonHistogramSnapshot(count, total, max, List.of(), List.of());
    }

    @Override
    public long count() {
        return count;
    }

    @Override
    public double total() {
        return total;
    }

    @Override
    public double total(TimeUnit timeUnit) {
        return HelidonTypes.toTimeUnit(total, Objects.requireNonNull(timeUnit));
    }

    @Override
    public double max() {
        return max;
    }

    @Override
    public double mean() {
        return count == 0 ? 0D : total / count;
    }

    @Override
    public double mean(TimeUnit timeUnit) {
        return HelidonTypes.toTimeUnit(mean(), Objects.requireNonNull(timeUnit));
    }

    @Override
    public Iterable<? extends ValueAtPercentile> percentileValues() {
        return percentileValues;
    }

    @Override
    public Iterable<Bucket> histogramCounts() {
        return histogramCounts;
    }

    @Override
    public void outputSummary(PrintStream out, double scale) {
        Objects.requireNonNull(out)
                .printf(Locale.ROOT, "count = %d, total = %f, max = %f, mean = %f%n",
                        count,
                        total * scale,
                        max * scale,
                        mean() * scale);
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return Objects.requireNonNull(c).cast(this);
    }

    private static double valueAt(double[] sorted, double percentile) {
        if (sorted.length == 0) {
            return Double.NaN;
        }
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        index = Math.max(0, Math.min(index, sorted.length - 1));
        return sorted[index];
    }
}
