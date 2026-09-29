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
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

final class HelidonHistogram {
    private final double[] percentiles;
    private final double[] buckets;
    private final LongAdder[] bucketCounts;
    private final Reservoir reservoir;
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
        this.reservoir = this.percentiles.length == 0 ? null : new Reservoir();
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
            reservoir.record(amount);
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
        long[] snapshotBucketCounts = new long[bucketCounts.length];
        long cumulative = 0;
        for (int i = 0; i < bucketCounts.length; i++) {
            cumulative += bucketCounts[i].sum();
            snapshotBucketCounts[i] = cumulative;
        }
        // Independently sampled adders must still describe buckets bounded by the observation count.
        long snapshotCount = Math.max(count(), cumulative);
        double[] samples = reservoir == null ? new double[0] : reservoir.samples(snapshotCount);
        return HelidonHistogramSnapshot.create(snapshotCount,
                                               total(),
                                               max(),
                                               samples,
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

    private int firstBucket(double amount) {
        int index = Arrays.binarySearch(buckets, amount);
        if (index < 0) {
            index = -index - 1;
        }
        return index;
    }

    private static final class Reservoir {
        // The lowest independent random priorities form a uniform lifetime sample; ties are sampled uniformly too.
        private final double[] values = new double[HelidonTypes.DEFAULT_RESERVOIR_SIZE];
        private final int[] priorities = new int[values.length];
        private final ReentrantLock lock = new ReentrantLock();
        private volatile int threshold = Integer.MAX_VALUE;
        private int size;
        private int thresholdCount;
        private long thresholdObservations;

        private void record(double amount) {
            var random = ThreadLocalRandom.current();
            int priority = random.nextInt() >>> 1;
            if (priority > threshold) {
                return;
            }

            lock.lock();
            try {
                if (size < values.length) {
                    values[size] = amount;
                    priorities[size++] = priority;
                    if (size == values.length) {
                        // Keep every initial observation, then build the max-heap in linear time.
                        for (int index = size / 2 - 1; index >= 0; index--) {
                            replace(index, priorities[index], values[index]);
                        }
                        updateThreshold();
                    }
                    return;
                }

                int currentThreshold = priorities[0];
                if (priority > currentThreshold) {
                    return;
                }
                if (priority == currentThreshold) {
                    // Equal priorities form their own uniform reservoir at the sampling boundary.
                    if (thresholdObservations == Long.MAX_VALUE) {
                        // The metric's long observation count can no longer represent this population.
                        return;
                    }
                    long selected = random.nextLong(++thresholdObservations);
                    if (selected < thresholdCount) {
                        values[thresholdIndex((int) selected)] = amount;
                    }
                    return;
                }

                int index = thresholdCount == 1 ? 0 : thresholdIndex(random.nextInt(thresholdCount));
                replace(index, priority, amount);
                if (priorities[0] == currentThreshold) {
                    thresholdCount--;
                } else {
                    updateThreshold();
                }
            } finally {
                lock.unlock();
            }
        }

        private double[] samples(long snapshotCount) {
            lock.lock();
            try {
                return Arrays.copyOf(values, (int) Math.min(snapshotCount, size));
            } finally {
                lock.unlock();
            }
        }

        private void replace(int index, int priority, double amount) {
            int child;
            while ((child = index * 2 + 1) < size) {
                if (child + 1 < size && priorities[child + 1] > priorities[child]) {
                    child++;
                }
                if (priority >= priorities[child]) {
                    break;
                }
                priorities[index] = priorities[child];
                values[index] = values[child];
                index = child;
            }
            priorities[index] = priority;
            values[index] = amount;
        }

        private void updateThreshold() {
            int currentThreshold = priorities[0];
            thresholdCount = countAtThreshold(0, currentThreshold);
            // Every observation at a newly lower boundary was retained while the boundary was higher.
            thresholdObservations = thresholdCount;
            // Publish only after updating the heap. A stale, higher threshold merely takes the lock unnecessarily.
            threshold = currentThreshold;
        }

        private int countAtThreshold(int index, int priority) {
            if (index >= size || priorities[index] != priority) {
                return 0;
            }
            return 1 + countAtThreshold(index * 2 + 1, priority) + countAtThreshold(index * 2 + 2, priority);
        }

        private int thresholdIndex(int selected) {
            int priority = priorities[0];
            for (int index = 0; index < size; index++) {
                if (priorities[index] == priority && selected-- == 0) {
                    return index;
                }
            }
            throw new IllegalStateException("Reservoir boundary observation is missing");
        }
    }
}
