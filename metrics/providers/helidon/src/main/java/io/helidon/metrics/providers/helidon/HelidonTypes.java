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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.Tag;

final class HelidonTypes {
    static final double[] DEFAULT_PERCENTILES = {0.5, 0.75, 0.95, 0.98, 0.99, 0.999};
    static final int DEFAULT_RESERVOIR_SIZE = 4096;
    static final long DEFAULT_TIMER_HISTOGRAM_MIN_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
    static final long DEFAULT_TIMER_HISTOGRAM_MAX_NANOS = TimeUnit.SECONDS.toNanos(30);
    static final double DEFAULT_SUMMARY_HISTOGRAM_MIN = 1D;
    static final double DEFAULT_SUMMARY_HISTOGRAM_MAX = Double.POSITIVE_INFINITY;

    private static final double[] PERCENTILE_HISTOGRAM_BUCKETS = percentileHistogramBucketCandidates();

    private HelidonTypes() {
    }

    static double toTimeUnit(double nanos, TimeUnit unit) {
        Objects.requireNonNull(unit);
        return nanos / TimeUnit.NANOSECONDS.convert(1, unit);
    }

    static long toNanos(long amount, TimeUnit unit) {
        Objects.requireNonNull(unit);
        return TimeUnit.NANOSECONDS.convert(amount, unit);
    }

    static double[] doubleArray(Iterable<Double> values) {
        List<Double> list = new ArrayList<>();
        Objects.requireNonNull(values).forEach(value -> list.add(Objects.requireNonNull(value)));
        double[] result = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    static double[] sorted(double[] values) {
        double[] result = Arrays.copyOf(values, values.length);
        Arrays.sort(result);
        return result;
    }

    static double[] percentileHistogramBuckets(double min, double max, double[] explicitBuckets) {
        double checkedMin = checkedHistogramBoundary("minimum expected value", min);
        double checkedMax = max == Double.POSITIVE_INFINITY ? max : checkedHistogramBoundary("maximum expected value", max);
        if (checkedMin > checkedMax) {
            throw new IllegalArgumentException(
                    "Maximum expected value must be greater than or equal to minimum expected value");
        }

        TreeSet<Double> buckets = new TreeSet<>();
        buckets.add(checkedMin);
        buckets.add(checkedMax);
        for (double bucket : PERCENTILE_HISTOGRAM_BUCKETS) {
            if (bucket >= checkedMin && bucket <= checkedMax) {
                buckets.add(bucket);
            }
        }
        for (double bucket : explicitBuckets) {
            buckets.add(bucket);
        }
        return buckets.stream().mapToDouble(Double::doubleValue).toArray();
    }

    static List<Tag> tagsFrom(Map<String, String> tags) {
        return Objects.requireNonNull(tags).entrySet()
                .stream()
                .map(entry -> new HelidonTag(entry.getKey(), entry.getValue()))
                .map(Tag.class::cast)
                .toList();
    }

    static SortedMap<String, String> tagMap(Iterable<Tag> tags) {
        SortedMap<String, String> result = new TreeMap<>();
        Objects.requireNonNull(tags).forEach(tag -> {
            Objects.requireNonNull(tag);
            result.put(tag.key(), tag.value());
        });
        return result;
    }

    static HelidonMeterId meterId(String name, Iterable<Tag> tags) {
        return new HelidonMeterId(name, tags);
    }

    static String durationString(double nanos) {
        return Duration.ofNanos((long) nanos).toString();
    }

    private static double[] percentileHistogramBucketCandidates() {
        List<Double> result = new ArrayList<>();
        result.add(1D);
        result.add(2D);
        result.add(3D);

        // The established bucket sequence ends before the next power of four would overflow a long.
        for (long base = 4L; base <= Long.MAX_VALUE / 4L; base *= 4L) {
            long step = base / 3L;
            long upper = base * 4L - step;
            for (long bucket = base; bucket < upper; bucket += step) {
                result.add((double) bucket);
            }
        }
        return result.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static double checkedHistogramBoundary(String description, double value) {
        if (!Double.isFinite(value) || value <= 0D) {
            throw new IllegalArgumentException(description + " must be a finite value greater than 0");
        }
        return value;
    }

}
