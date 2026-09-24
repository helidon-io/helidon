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
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Timer;

final class HelidonTimer extends HelidonMeter implements Timer {
    private final HelidonHistogram histogram;
    private final Clock clock;
    private final Optional<TimeUnit> baseTimeUnit;

    private HelidonTimer(Meter.Id id, Builder builder, Clock clock) {
        super(id, Type.TIMER, builder);
        this.histogram = HelidonHistogram.create(HelidonTypes.doubleArray(builder.percentiles()),
                                                 builder.histogramBucketsAsNanos());
        this.clock = Objects.requireNonNull(clock);
        this.baseTimeUnit = Optional.ofNullable(builder.baseTimeUnit);
    }

    static Builder builder(String name) {
        return new Builder(name);
    }

    static HelidonTimer create(Meter.Id id, Builder builder, Clock clock) {
        return new HelidonTimer(id, builder, clock);
    }

    static Timer.Sample start(Clock clock) {
        return new Sample(Objects.requireNonNull(clock));
    }

    @Override
    public Optional<String> baseUnit() {
        return baseTimeUnit.map(TimeUnit::name);
    }

    @Override
    public HistogramSnapshot snapshot() {
        return histogram.snapshot();
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        Objects.requireNonNull(unit);
        if (amount >= 0) {
            histogram.record(HelidonTypes.toNanos(amount, unit));
        }
    }

    @Override
    public void record(Duration duration) {
        Objects.requireNonNull(duration);
        if (!duration.isNegative()) {
            record(duration.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public <T> T record(Supplier<T> f) {
        Objects.requireNonNull(f);
        long start = clock.monotonicTime();
        try {
            return f.get();
        } finally {
            record(clock.monotonicTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public <T> T record(Callable<T> f) throws Exception {
        Objects.requireNonNull(f);
        long start = clock.monotonicTime();
        try {
            return f.call();
        } finally {
            record(clock.monotonicTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public void record(Runnable f) {
        Objects.requireNonNull(f);
        long start = clock.monotonicTime();
        try {
            f.run();
        } finally {
            record(clock.monotonicTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public Runnable wrap(Runnable f) {
        Objects.requireNonNull(f);
        return () -> record(f);
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> f) {
        Objects.requireNonNull(f);
        return () -> record(f);
    }

    @Override
    public <T> Supplier<T> wrap(Supplier<T> f) {
        Objects.requireNonNull(f);
        return () -> record(f);
    }

    @Override
    public long count() {
        return histogram.count();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        Objects.requireNonNull(unit);
        return HelidonTypes.toTimeUnit(histogram.total(), unit);
    }

    @Override
    public double mean(TimeUnit unit) {
        Objects.requireNonNull(unit);
        return HelidonTypes.toTimeUnit(histogram.mean(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
        Objects.requireNonNull(unit);
        return HelidonTypes.toTimeUnit(histogram.max(), unit);
    }

    @Override
    public String toString() {
        return stringPrefix()
                + "count=" + count()
                + ", totalTime=" + HelidonTypes.durationString(histogram.total())
                + ", max=" + HelidonTypes.durationString(histogram.max())
                + "]";
    }

    static final class Sample implements Timer.Sample {
        private final Clock clock;
        private final long start;

        private Sample(Clock clock) {
            this.clock = Objects.requireNonNull(clock);
            this.start = clock.monotonicTime();
        }

        @Override
        public long stop(Timer timer) {
            long elapsed = clock.monotonicTime() - start;
            Objects.requireNonNull(timer).record(elapsed, TimeUnit.NANOSECONDS);
            return elapsed;
        }
    }

    static final class Builder extends HelidonMeter.AbstractBuilder<Timer.Builder, Timer> implements Timer.Builder {
        private double[] percentiles = HelidonTypes.DEFAULT_PERCENTILES;
        private Duration[] buckets = new Duration[0];
        private Duration min;
        private Duration max;
        private Boolean publishPercentileHistogram;
        private TimeUnit baseTimeUnit;

        private Builder(String name) {
            super(name);
        }

        @Override
        public Builder baseUnit(String baseUnit) {
            Objects.requireNonNull(baseUnit);
            if (!baseUnit.isBlank()) {
                this.baseTimeUnit = TimeUnit.valueOf(baseUnit.toUpperCase(Locale.ROOT));
            }
            return this;
        }

        @Override
        public Builder baseUnit(TimeUnit baseUnit) {
            this.baseTimeUnit = Objects.requireNonNull(baseUnit);
            return this;
        }

        @Override
        public Optional<String> baseUnit() {
            return Optional.ofNullable(baseTimeUnit).map(TimeUnit::name);
        }

        @Override
        public Builder percentiles(double... percentiles) {
            this.percentiles = Objects.requireNonNull(percentiles).clone();
            return this;
        }

        @Override
        public Builder buckets(Duration... buckets) {
            this.buckets = Objects.requireNonNull(buckets).clone();
            for (Duration bucket : this.buckets) {
                Objects.requireNonNull(bucket);
            }
            return this;
        }

        @Override
        public Builder minimumExpectedValue(Duration min) {
            this.min = Objects.requireNonNull(min);
            return this;
        }

        @Override
        public Builder maximumExpectedValue(Duration max) {
            this.max = Objects.requireNonNull(max);
            return this;
        }

        @Override
        public Builder publishPercentileHistogram(boolean value) {
            this.publishPercentileHistogram = value;
            return this;
        }

        @Override
        public Iterable<Double> percentiles() {
            return Arrays.stream(percentiles).boxed().toList();
        }

        @Override
        public Iterable<Duration> buckets() {
            return Arrays.asList(buckets);
        }

        double[] histogramBucketsAsNanos() {
            double[] explicitBuckets = Arrays.stream(buckets).mapToDouble(Duration::toNanos).toArray();
            if (!Boolean.TRUE.equals(publishPercentileHistogram)) {
                return explicitBuckets;
            }
            double minimum = minimumExpectedValue()
                    .map(Duration::toNanos)
                    .map(Long::doubleValue)
                    .orElse((double) HelidonTypes.DEFAULT_TIMER_HISTOGRAM_MIN_NANOS);
            double maximum = maximumExpectedValue()
                    .map(Duration::toNanos)
                    .map(Long::doubleValue)
                    .orElse((double) HelidonTypes.DEFAULT_TIMER_HISTOGRAM_MAX_NANOS);
            return HelidonTypes.percentileHistogramBuckets(minimum, maximum, explicitBuckets);
        }

        @Override
        public Optional<Duration> minimumExpectedValue() {
            return Optional.ofNullable(min);
        }

        @Override
        public Optional<Duration> maximumExpectedValue() {
            return Optional.ofNullable(max);
        }

        @Override
        public Optional<Boolean> publishPercentileHistogram() {
            return Optional.ofNullable(publishPercentileHistogram);
        }

        @Override
        Class<? extends Meter> meterType() {
            return Timer.class;
        }
    }
}
