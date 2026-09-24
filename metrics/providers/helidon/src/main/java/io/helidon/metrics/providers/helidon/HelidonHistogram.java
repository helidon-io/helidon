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

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

final class HelidonHistogram {
    private final double[] percentiles;
    private final double[] buckets;
    private final LongAdder[] bucketCounts;
    private final AtomicLongArray reservoir;
    private final LongAdder count = new LongAdder();
    private final DoubleAdder total = new DoubleAdder();
    private final DoubleAccumulator max = new DoubleAccumulator(Double::max, 0D);

    private HelidonHistogram(double[] percentiles, double[] buckets) {
        this.percentiles = percentiles;
        double[] sortedBuckets = HelidonTypes.sorted(buckets);
        int uniqueCount = 0;
        for (double bucket : sortedBuckets) {
            if (bucket == 0D) {
                bucket = 0D;
            }
            if (uniqueCount == 0 || Double.compare(bucket, sortedBuckets[uniqueCount - 1]) != 0) {
                sortedBuckets[uniqueCount++] = bucket;
            }
        }
        this.buckets = uniqueCount == sortedBuckets.length ? sortedBuckets : Arrays.copyOf(sortedBuckets, uniqueCount);
        this.bucketCounts = new LongAdder[this.buckets.length];
        for (int i = 0; i < bucketCounts.length; i++) {
            bucketCounts[i] = new LongAdder();
        }
        this.reservoir = this.percentiles.length == 0 ? null : new AtomicLongArray(HelidonTypes.DEFAULT_RESERVOIR_SIZE);
        if (reservoir != null) {
            for (int i = 0; i < reservoir.length(); i++) {
                reservoir.set(i, Double.doubleToRawLongBits(Double.NaN));
            }
        }
    }

    static HelidonHistogram create(double[] percentiles, double[] buckets) {
        double[] normalizedPercentiles = uniquePercentiles(percentiles);
        return new HelidonHistogram(normalizedPercentiles, buckets);
    }

    void record(double amount) {
        if (amount < 0 || !Double.isFinite(amount)) {
            return;
        }

        if (reservoir != null) {
            reservoir.set(ThreadLocalRandom.current().nextInt(reservoir.length()), Double.doubleToRawLongBits(amount));
        }
        count.increment();
        total.add(amount);
        max.accumulate(amount);
        int bucketIndex = firstBucket(amount);
        if (bucketIndex < bucketCounts.length) {
            bucketCounts[bucketIndex].increment();
        }
    }

    long count() {
        return count.sum();
    }

    double total() {
        return total.sum();
    }

    double max() {
        return max.get();
    }

    double mean() {
        long count = count();
        return count == 0 ? 0D : total() / count;
    }

    HelidonHistogramSnapshot snapshot() {
        long snapshotCount = count();
        long[] snapshotBucketCounts = new long[bucketCounts.length];
        long cumulative = 0;
        for (int i = 0; i < bucketCounts.length; i++) {
            cumulative += bucketCounts[i].sum();
            snapshotBucketCounts[i] = cumulative;
        }
        return HelidonHistogramSnapshot.create(snapshotCount,
                                               total(),
                                               max(),
                                               samples(snapshotCount),
                                               percentiles,
                                               buckets,
                                               snapshotBucketCounts);
    }

    private static double[] uniquePercentiles(double[] percentiles) {
        boolean ordered = true;
        for (int i = 0; i < percentiles.length; i++) {
            double percentile = percentiles[i];
            if (percentile < 0 || percentile > 1) {
                throw new IllegalArgumentException("Percentile must be between 0 and 1: " + percentile);
            }
            if (i > 0 && Double.compare(percentiles[i - 1], percentile) >= 0) {
                ordered = false;
            }
        }
        double[] sorted = Arrays.copyOf(percentiles, percentiles.length);
        if (ordered) {
            return sorted;
        }

        Arrays.sort(sorted);
        int uniqueCount = 0;
        for (double percentile : sorted) {
            if (uniqueCount == 0 || Double.compare(percentile, sorted[uniqueCount - 1]) != 0) {
                sorted[uniqueCount++] = percentile;
            }
        }
        if (uniqueCount == sorted.length) {
            System.arraycopy(percentiles, 0, sorted, 0, percentiles.length);
            return sorted;
        }

        // Preserve encounter order, including the first NaN representation and distinct signed zeros.
        double[] result = new double[uniqueCount];
        boolean[] seen = new boolean[uniqueCount];
        int next = 0;
        for (double percentile : percentiles) {
            int index = Arrays.binarySearch(sorted, 0, uniqueCount, percentile);
            if (!seen[index]) {
                seen[index] = true;
                result[next++] = percentile;
            }
        }
        return result;
    }

    private double[] samples(long snapshotCount) {
        if (reservoir == null) {
            return new double[0];
        }

        int targetSampleCount = (int) Math.min(snapshotCount, reservoir.length());
        double[] samples = new double[targetSampleCount];
        int sampleCount = 0;
        for (int i = 0; i < reservoir.length() && sampleCount < targetSampleCount; i++) {
            double sample = Double.longBitsToDouble(reservoir.get(i));
            if (!Double.isNaN(sample)) {
                samples[sampleCount++] = sample;
            }
        }
        return sampleCount < samples.length ? Arrays.copyOf(samples, sampleCount) : samples;
    }

    private int firstBucket(double amount) {
        int index = Arrays.binarySearch(buckets, amount);
        if (index < 0) {
            index = -index - 1;
        }
        return index;
    }
}
