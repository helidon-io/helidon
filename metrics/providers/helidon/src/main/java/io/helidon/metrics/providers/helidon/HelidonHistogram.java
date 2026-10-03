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

import java.io.Serial;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

import io.helidon.metrics.api.ValueAtPercentile;

abstract sealed class HelidonHistogram {
    private static final double[] EMPTY_SAMPLES = new double[0];

    private final double[] percentiles;
    private final double[] buckets;
    private final LongAdder[] bucketCounts;
    private final Reservoir reservoir;
    private final LongAdder count;
    private final DoubleAdder total;
    private final DoubleAccumulator max;

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
        if (this.percentiles.length == 0) {
            reservoir = null;
            count = new LongAdder();
            total = new DoubleAdder();
            max = new DoubleAccumulator(Double::max, 0D);
        } else {
            reservoir = new Reservoir(this.percentiles);
            count = null;
            total = null;
            max = null;
        }
    }

    static HelidonHistogram create(double[] percentiles, double[] buckets) {
        double[] normalizedPercentiles = uniquePercentiles(percentiles);
        return normalizedPercentiles.length == 0
                ? new PlainHistogram(normalizedPercentiles, buckets)
                : new PercentileHistogram(normalizedPercentiles, buckets);
    }

    abstract void record(double amount);

    boolean hasPercentiles() {
        return reservoir != null;
    }

    long count() {
        return reservoir == null ? count.sum() : reservoir.count();
    }

    double total() {
        return reservoir == null ? total.sum() : reservoir.total();
    }

    double max() {
        return reservoir == null ? max.get() : reservoir.max();
    }

    double mean() {
        long count = count();
        return count == 0 ? 0D : total() / count;
    }

    HelidonHistogramSnapshot snapshot() {
        long[] snapshotBucketCounts = new long[bucketCounts.length];
        long cumulative = 0;
        for (int i = 0; i < bucketCounts.length; i++) {
            cumulative += bucketCounts[i].sum();
            snapshotBucketCounts[i] = cumulative;
        }
        long snapshotCount;
        double snapshotTotal;
        double snapshotMax;
        List<ValueAtPercentile> snapshotPercentiles;
        if (reservoir == null) {
            snapshotCount = count.sum();
            snapshotTotal = total.sum();
            snapshotMax = max.get();
            snapshotPercentiles = List.of();
        } else {
            SampleSnapshot snapshot = reservoir.snapshot();
            snapshotCount = snapshot.count();
            snapshotTotal = snapshot.total();
            snapshotMax = snapshot.max();
            snapshotPercentiles = snapshot.percentileValues();
        }
        // Independently sampled buckets must still be bounded by the observation count.
        snapshotCount = Math.max(snapshotCount, cumulative);
        return HelidonHistogramSnapshot.create(snapshotCount,
                                               snapshotTotal,
                                               snapshotMax,
                                               snapshotPercentiles,
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

    private int firstBucket(double amount) {
        int index = Arrays.binarySearch(buckets, amount);
        if (index < 0) {
            index = -index - 1;
        }
        return index;
    }

    private void recordBucket(double amount) {
        int bucketIndex = firstBucket(amount);
        if (bucketIndex < bucketCounts.length) {
            bucketCounts[bucketIndex].increment();
        }
    }

    private static final class PlainHistogram extends HelidonHistogram {
        private PlainHistogram(double[] percentiles, double[] buckets) {
            super(percentiles, buckets);
        }

        @Override
        void record(double amount) {
            if (amount < 0 || !Double.isFinite(amount)) {
                return;
            }
            super.count.increment();
            super.total.add(amount);
            super.max.accumulate(amount);
            super.recordBucket(amount);
        }
    }

    private static final class PercentileHistogram extends HelidonHistogram {
        private PercentileHistogram(double[] percentiles, double[] buckets) {
            super(percentiles, buckets);
        }

        @Override
        void record(double amount) {
            if (amount < 0 || !Double.isFinite(amount)) {
                return;
            }
            super.reservoir.record(amount);
            super.recordBucket(amount);
        }
    }

    private static final class Reservoir {
        private static final int LANES = 16;
        private static final int STATISTICS_STRIDE = 16;

        private final Lane[] lanes = new Lane[LANES];
        // Separate writable lanes from one another and from the shared array header.
        private final long[] statistics = new long[(LANES + 1) * STATISTICS_STRIDE];
        private final ReentrantLock snapshotLock = new ReentrantLock();
        private final long[] snapshotCounts = new long[LANES];
        private final double[] percentiles;
        // Detached batches remain recoverable until their merged sample and percentile values are committed.
        private final BatchSnapshot[] pending = new BatchSnapshot[LANES];

        private double[] samples = EMPTY_SAMPLES;
        private long processedCount;
        private int nextLane;
        private SampleSnapshot cachedSnapshot;

        private Reservoir(double[] percentiles) {
            this.percentiles = percentiles;
            for (int index = 0; index < lanes.length; index++) {
                lanes[index] = new Lane(statistics, (index + 1) * STATISTICS_STRIDE);
            }
        }

        private static long addCounts(long first, long second) {
            // Beyond the representable population, keep sampling bounds nonnegative.
            return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
        }

        private static long nextLong(long bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("Bound must be positive: " + bound);
            }
            return nextLongPositive(bound);
        }

        // The caller must provide a positive bound.
        private static long nextLongPositive(long bound) {
            var random = ThreadLocalRandom.current();
            long value = random.nextLong();
            long low = value * bound;
            if (Long.compareUnsigned(low, bound) < 0) {
                return nextLongWithRejection(random, bound, value, low);
            }
            return Math.unsignedMultiplyHigh(value, bound);
        }

        private static long nextLongWithRejection(ThreadLocalRandom random, long bound, long value, long low) {
            // Reject the excess mappings, reusing the first draw if it is already acceptable.
            long threshold = Long.remainderUnsigned(-bound, bound);
            while (Long.compareUnsigned(low, threshold) < 0) {
                value = random.nextLong();
                low = value * bound;
            }
            return Math.unsignedMultiplyHigh(value, bound);
        }

        private static void copySamples(double[] source,
                                        int size,
                                        int needed,
                                        double[] target,
                                        int offset) {
            if (needed == size) {
                System.arraycopy(source, 0, target, offset, size);
                return;
            }
            boolean excluded = needed > size / 2;
            int selected = excluded ? size - needed : needed;
            // Readers own these arrays. Permuting the initialized prefix preserves every sample if a snapshot fails.
            for (int index = size - 1; index >= size - selected; index--) {
                int draw = (int) nextLong(index + 1);
                double value = source[draw];
                source[draw] = source[index];
                source[index] = value;
            }
            System.arraycopy(source, excluded ? 0 : size - needed, target, offset, needed);
        }

        private long count() {
            long result = 0;
            for (Lane lane : lanes) {
                result = addCounts(result, lane.count());
            }
            return result;
        }

        private double total() {
            double result = 0;
            for (Lane lane : lanes) {
                result += lane.total();
            }
            return result;
        }

        private double max() {
            double result = 0;
            for (Lane lane : lanes) {
                result = Math.max(result, lane.max());
            }
            return result;
        }

        private void record(double amount) {
            int lane = (int) Thread.currentThread().threadId() & (LANES - 1);
            lanes[lane].record(amount);
        }

        private SampleSnapshot snapshot() {
            snapshotLock.lock();
            try {
                if (nextLane != 0) {
                    // Recover detached observations, then include records completed since the failed snapshot.
                    completeSnapshot();
                }
                return snapshotUnchanged() ? cachedSnapshot : completeSnapshot();
            } finally {
                snapshotLock.unlock();
            }
        }

        private SampleSnapshot completeSnapshot() {
            while (nextLane < lanes.length) {
                pending[nextLane] = lanes[nextLane].drain();
                nextLane++;
            }

            long count = 0;
            long batchCount = 0;
            double total = 0;
            double max = 0;
            // Each lane's cumulative count equals its previous captured count plus this batch until saturation.
            for (BatchSnapshot snapshot : pending) {
                count = addCounts(count, snapshot.count());
                batchCount = addCounts(batchCount, snapshot.batchCount());
                total += snapshot.total();
                max = Math.max(max, snapshot.max());
            }

            double[] updatedSamples = samples;
            if (processedCount < Long.MAX_VALUE && batchCount != 0) {
                updatedSamples = batchCount <= HelidonTypes.DEFAULT_RESERVOIR_SIZE ? addSamples(count) : merge(count, batchCount);
            }
            List<ValueAtPercentile> percentileValues;
            if (cachedSnapshot != null && updatedSamples == samples) {
                percentileValues = cachedSnapshot.percentileValues();
            } else {
                if (updatedSamples != samples) {
                    // The changed sample is reader-owned and remains uncommitted if percentile creation fails.
                    Arrays.sort(updatedSamples);
                }
                percentileValues = HelidonHistogramSnapshot.percentileValues(updatedSamples, percentiles);
            }
            var snapshot = new SampleSnapshot(count, total, max, percentileValues);

            // No allocation or sampling occurs after this point, so failure cannot lose a detached population.
            samples = updatedSamples;
            processedCount = count;
            for (int index = 0; index < pending.length; index++) {
                snapshotCounts[index] = pending[index].count();
                if (pending[index].batchCount() != 0) {
                    lanes[index].spare = pending[index].samples();
                }
            }
            cachedSnapshot = snapshot;
            Arrays.fill(pending, null);
            nextLane = 0;
            return snapshot;
        }

        private boolean snapshotUnchanged() {
            // Saturated counts cannot reveal subsequent changes to the other aggregates.
            if (cachedSnapshot == null || cachedSnapshot.count() == Long.MAX_VALUE) {
                return false;
            }
            for (int index = 0; index < lanes.length; index++) {
                if (lanes[index].count() != snapshotCounts[index]) {
                    return false;
                }
            }
            return true;
        }

        private double[] addSamples(long count) {
            double[] updatedSamples = samples;
            long population = processedCount;
            var random = ThreadLocalRandom.current();
            for (BatchSnapshot snapshot : pending) {
                // A small batch retains every observation, so it can extend the lifetime Algorithm R directly.
                for (int index = 0; index < snapshot.batchCount(); index++) {
                    long selected;
                    if (population < HelidonTypes.DEFAULT_RESERVOIR_SIZE) {
                        selected = population;
                    } else if (population < Long.MAX_VALUE) {
                        selected = random.nextLong(population + 1);
                    } else {
                        break;
                    }
                    if (selected < HelidonTypes.DEFAULT_RESERVOIR_SIZE) {
                        if (updatedSamples == samples) {
                            updatedSamples = Arrays.copyOf(samples,
                                                           (int) Math.min(count, HelidonTypes.DEFAULT_RESERVOIR_SIZE));
                        }
                        updatedSamples[(int) selected] = snapshot.samples()[index];
                    }
                    population++;
                }
            }
            return updatedSamples;
        }

        private double[] merge(long count, long batchCount) {
            var random = ThreadLocalRandom.current();
            boolean rare = count < Long.MAX_VALUE
                    && samples.length == HelidonTypes.DEFAULT_RESERVOIR_SIZE
                    && batchCount <= count / HelidonTypes.DEFAULT_RESERVOIR_SIZE;
            if (rare && nextLong(count) >= batchCount * HelidonTypes.DEFAULT_RESERVOIR_SIZE) {
                return samples;
            }
            long[] remaining = new long[pending.length + 1];
            int[] selected = new int[remaining.length];
            remaining[0] = processedCount;
            for (int index = 0; index < pending.length; index++) {
                remaining[index + 1] = pending[index].batchCount();
            }
            long remainingTotal = count;
            int samplesToSelect = HelidonTypes.DEFAULT_RESERVOIR_SIZE;
            if (rare) {
                // Propose one incoming population, then ordinary quotas; no retained identity is forced.
                long draw = nextLong(batchCount);
                int population = 1;
                while (draw >= remaining[population]) {
                    draw -= remaining[population++];
                }
                selected[population]++;
                remaining[population]--;
                remainingTotal--;
                samplesToSelect--;
            }
            // Weight the committed lifetime population and every new batch before selecting retained observations.
            boolean exactPopulation = count < Long.MAX_VALUE;
            PopulationSelector selector = !rare && exactPopulation && count >= 4L * HelidonTypes.DEFAULT_RESERVOIR_SIZE
                    ? new PopulationSelector(remaining, count)
                    : null;
            for (int index = 0; index < samplesToSelect; index++) {
                int population = 0;
                if (selector != null) {
                    population = selector.select(remaining, remainingTotal);
                } else {
                    long draw = nextLong(remainingTotal);
                    if (exactPopulation) {
                        long cumulative = remaining[0];
                        for (int group = 1; group < remaining.length; group++) {
                            // Unsaturated prefix sums and the draw are nonnegative, so subtraction cannot overflow.
                            population += 1 - (int) ((draw - cumulative) >>> 63);
                            cumulative += remaining[group];
                        }
                    } else {
                        // Keep this fallback for the whole merge even after the saturated draw bound decreases.
                        while (draw >= remaining[population]) {
                            draw -= remaining[population++];
                        }
                    }
                }
                selected[population]++;
                remaining[population]--;
                remainingTotal--;
            }

            // A proposal with J incoming observations has J times the ordinary quota probability.
            if (rare && random.nextInt(HelidonTypes.DEFAULT_RESERVOIR_SIZE - selected[0]) != 0) {
                return samples;
            }
            if (selected[0] == HelidonTypes.DEFAULT_RESERVOIR_SIZE) {
                return samples;
            }
            double[] merged = new double[HelidonTypes.DEFAULT_RESERVOIR_SIZE];
            copySamples(samples, samples.length, selected[0], merged, 0);
            int offset = selected[0];
            for (int index = 0; index < pending.length; index++) {
                BatchSnapshot snapshot = pending[index];
                int size = (int) Math.min(snapshot.batchCount(), HelidonTypes.DEFAULT_RESERVOIR_SIZE);
                copySamples(snapshot.samples(), size, selected[index + 1], merged, offset);
                offset += selected[index + 1];
            }
            return merged;
        }

        private static final class PopulationSelector {
            private static final int INDEX_BITS = 10;

            private final long total;
            private final long[] ends;
            private final int[] bins;
            private final int shift;

            private PopulationSelector(long[] counts, long total) {
                this.total = total;
                ends = new long[counts.length];
                long cumulative = 0;
                for (int group = 0; group < counts.length; group++) {
                    cumulative += counts[group];
                    ends[group] = cumulative;
                }
                shift = Math.max(0, Long.SIZE - Long.numberOfLeadingZeros(total - 1) - INDEX_BITS);
                bins = new int[(int) (((total - 1) >>> shift) + 1)];
                int group = 0;
                for (int bin = 0; bin < bins.length; bin++) {
                    long lower = (long) bin << shift;
                    while (lower >= ends[group]) {
                        group++;
                    }
                    bins[bin] = group;
                }
            }

            private int population(long draw) {
                int group = bins[(int) (draw >>> shift)];
                while (draw >= ends[group]) {
                    group++;
                }
                return group;
            }

            private int select(long[] remaining, long remainingTotal) {
                for (int attempt = 0; attempt < 2; attempt++) {
                    long draw = nextLong(total);
                    int group = population(draw);
                    long lower = group == 0 ? 0 : ends[group - 1];
                    // Keep exactly the remaining group's share of its original population range.
                    if (draw - lower < remaining[group]) {
                        return group;
                    }
                }
                // A fresh draw after rejection preserves the remaining populations' exact relative weights.
                long draw = nextLong(remainingTotal);
                int population = 0;
                long cumulative = remaining[0];
                for (int group = 1; group < remaining.length; group++) {
                    population += 1 - (int) ((draw - cumulative) >>> 63);
                    cumulative += remaining[group];
                }
                return population;
            }
        }
    }

    private static final class Lane {
        private static final VarHandle STATISTIC = MethodHandles.arrayElementVarHandle(long[].class);

        private final StampedLock lock = new PaddedLock();
        private final long[] statistics;
        private final int offset;

        // Every lane retains a uniform sample only of observations not yet drained into the lifetime reservoir.
        private double[] values = new double[16];
        // Only serialized snapshot readers access this buffer; writers use values under the lane lock.
        private double[] spare;

        private Lane(long[] statistics, int offset) {
            this.statistics = statistics;
            this.offset = offset;
        }

        private long count() {
            return (long) STATISTIC.getAcquire(statistics, offset);
        }

        private double total() {
            return Double.longBitsToDouble((long) STATISTIC.getAcquire(statistics, offset + 1));
        }

        private double max() {
            return Double.longBitsToDouble((long) STATISTIC.getAcquire(statistics, offset + 2));
        }

        private void record(double amount) {
            long stamp = lock.writeLock();
            try {
                long batchCount = statistics[offset + 3];
                if (batchCount < Long.MAX_VALUE) {
                    // Keep sampling on the regular recording path, including while the sample fills.
                    long selected = Reservoir.nextLongPositive(batchCount + 1);
                    if (batchCount < HelidonTypes.DEFAULT_RESERVOIR_SIZE) {
                        int index = (int) batchCount;
                        if (index == values.length) {
                            values = Arrays.copyOf(values, Math.min(values.length * 2, HelidonTypes.DEFAULT_RESERVOIR_SIZE));
                        }
                        values[index] = amount;
                    } else if (selected < values.length) {
                        values[(int) selected] = amount;
                    }
                }
                statistics[offset + 3] = batchCount == Long.MAX_VALUE ? batchCount : batchCount + 1;

                long count = statistics[offset];
                double total = Double.longBitsToDouble(statistics[offset + 1]) + amount;
                double max = Math.max(Double.longBitsToDouble(statistics[offset + 2]), amount);
                STATISTIC.setRelease(statistics, offset + 1, Double.doubleToRawLongBits(total));
                STATISTIC.setRelease(statistics, offset + 2, Double.doubleToRawLongBits(max));
                // Publish the count after the sample and aggregates. Saturate instead of wrapping a sampling bound.
                STATISTIC.setRelease(statistics, offset, count == Long.MAX_VALUE ? count : count + 1);
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        private BatchSnapshot drain() {
            long stamp = lock.writeLock();
            try {
                long count = count();
                long batchCount = statistics[offset + 3];
                if (batchCount == 0) {
                    return new BatchSnapshot(count, total(), max(), 0, EMPTY_SAMPLES);
                }
                // Allocate before detachment; an allocation failure leaves both buffers intact.
                double[] replacement = spare == null ? new double[16] : spare;
                var snapshot = new BatchSnapshot(count, total(), max(), batchCount, values);
                values = replacement;
                spare = null;
                statistics[offset + 3] = 0;
                return snapshot;
            } finally {
                lock.unlockWrite(stamp);
            }
        }
    }

    @SuppressWarnings("unused")
    private static final class PaddedLock extends StampedLock {
        @Serial
        private static final long serialVersionUID = 1L;

        // Reserve inline space for independently mutated lane-lock state, including after heap compaction.
        private long padding0;
        private long padding1;
        private long padding2;
        private long padding3;
        private long padding4;
        private long padding5;
        private long padding6;
        private long padding7;
        private long padding8;
        private long padding9;
        private long padding10;
        private long padding11;
        private long padding12;
        private long padding13;
        private long padding14;
        private long padding15;
    }

    private record SampleSnapshot(long count, double total, double max, List<ValueAtPercentile> percentileValues) {
    }

    private record BatchSnapshot(long count, double total, double max, long batchCount, double[] samples) {
    }
}
