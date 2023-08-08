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
package io.helidon.metrics.api;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * No-op implementation of the Helidon {@link io.helidon.metrics.api.Meter} interface.
 */
class NoOpMeter implements Meter {

    private final Id id;
    private final String unit;
    private final String description;
    private final Type type;

    static class Id implements Meter.Id {
        static Id create(String name, Iterable<Tag> tags) {
            return new Id(name, tags);
        }

        static Id create(String name, Tag... tags) {
            return new Id(name, Arrays.asList(tags));
        }

        private final String name;
        private final List<Tag> tags = new ArrayList<>(); // must be ordered by tag name for consistency

        private Id(String name, Iterable<Tag> tags) {
            this.name = name;
            tags.forEach(this.tags::add);
            this.tags.sort(Comparator.comparing(Tag::key));
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public  List<Tag> tags() {
            return tags.stream().toList();
        }

        Iterable<Tag> tagsAsIterable() {
            return tags;
        }

        String tag(String key) {
            return tags.stream()
                    .filter(t -> t.key().equals(key))
                    .map(Tag::value)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Id id = (Id) o;
            return name.equals(id.name) && tags.equals(id.tags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, tags);
        }
    }

    private NoOpMeter(NoOpMeter.Builder<?, ?> builder) {
        this(new NoOpMeter.Id(builder.name, builder.tags.values()),
             builder.unit,
             builder.description,
             builder.type);
    }

    private NoOpMeter(Id id, String baseUnit, String description, Type type) {
        this.id = id;
        this.unit = baseUnit;
        this.description = description;
        this.type = type;
    }

    @Override
    public Id id() {
        return id;
    }

    @Override
    public String baseUnit() {
        return unit;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Type type() {
        return type;
    }

    abstract static class Builder<B extends Builder<B, M>, M extends Meter> {

        private final String name;
        private final Map<String, Tag> tags = new TreeMap<>(); // tree map for ordering by tag name
        private String description;
        private String unit;
        private final Type type;

        private Builder(String name, Type type) {
            this.name = name;
            this.type = type;
        }

        abstract M build();

        public B tags(Iterable<Tag> tags) {
            tags.forEach(tag -> this.tags.put(tag.key(), tag));
            return identity();
        }

        public B tag(String key, String value) {
            tags.put(key, Tag.of(key, value));
            return identity();
        }

        public B description(String description) {
            this.description = description;
            return identity();
        }

        public B baseUnit(String unit) {
            this.unit = unit;
            return identity();
        }

        public B identity() {
            return (B) this;
        }

        public String name() {
            return name;
        }

        public Iterable<Tag> tags() {
            return tags.values();
        }

        public String baseUnit() {
            return unit;
        }

        public String description() {
            return description;
        }
    }

    static class Counter extends NoOpMeter implements io.helidon.metrics.api.Counter {

        static Counter create(String name, Iterable<Tag> tags) {
            return builder(name)
                    .tags(tags)
                    .build();
        }

        static class Builder extends NoOpMeter.Builder<Builder, Counter> implements io.helidon.metrics.api.Counter.Builder {

            protected Builder(String name) {
                super(name, Type.COUNTER);
            }


            @Override
            public Counter build() {
                return new NoOpMeter.Counter(this);
            }

        }

        static Counter.Builder builder(String name) {
            return new Builder(name);
        }

        static <T> FunctionalCounter.Builder<T> builder(String name, T target, ToDoubleFunction<T> fn) {
            return new FunctionalCounter.Builder<>(name, target, fn);
        }

        protected Counter(Builder builder) {
            super(builder);
        }

        @Override
        public void increment() {
        }

        @Override
        public void increment(double amount) {
        }

        @Override
        public double count() {
            return 0;
        }
    }

    static class FunctionalCounter<T> extends Counter {

        static class Builder<T> extends Counter.Builder implements io.helidon.metrics.api.Counter.Builder {

            private Builder(String name, T target, ToDoubleFunction<T> fn) {
                super(name);
            }

            @Override
            public FunctionalCounter<T> build() {
                return new FunctionalCounter<>(this);
            }
        }

        private FunctionalCounter(Builder<T> builder) {
            super(builder);
        }

        @Override
        public void increment() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void increment(double amount) {
            throw new UnsupportedOperationException();
        }
    }

    static class DistributionSummary extends NoOpMeter implements io.helidon.metrics.api.DistributionSummary {

        static class Builder extends NoOpMeter.Builder<Builder, DistributionSummary>
                implements io.helidon.metrics.api.DistributionSummary.Builder {

            private Builder(String name) {
                super(name, Type.DISTRIBUTION_SUMMARY);
            }

            @Override
            public DistributionSummary build() {
                return new DistributionSummary(this);
            }

            @Override
            public Builder scale(double scale) {
                return identity();
            }

            @Override
            public Builder distributionStatisticsConfig(
                    io.helidon.metrics.api.DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder) {
                return identity();
            }
        }

        static DistributionSummary.Builder builder(String name) {
            return new DistributionSummary.Builder(name);
        }

        private DistributionSummary(Builder builder) {
            super(builder);
        }

        @Override
        public void record(double amount) {
        }

        @Override
        public long count() {
            return 0;
        }

        @Override
        public double totalAmount() {
            return 0;
        }

        @Override
        public double mean() {
            return 0;
        }

        @Override
        public double max() {
            return 0;
        }
    }

    static class HistogramSnapshot implements io.helidon.metrics.api.HistogramSnapshot {

        private final long count;
        private final double total;
        private final double max;

        static HistogramSnapshot empty(long count, double total, double max) {
            return new HistogramSnapshot(count, total, max);
        }

        private HistogramSnapshot(long count, double total, double max) {
            this.count = count;
            this.total = total;
            this.max = max;
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
            return timeUnit.convert((long) total, TimeUnit.NANOSECONDS);
        }

        @Override
        public double max() {
            return max;
        }

        @Override
        public double mean() {
            return total / count;
        }

        @Override
        public double mean(TimeUnit timeUnit) {
            return timeUnit.convert((long) mean(), TimeUnit.NANOSECONDS);
        }

        @Override
        public Iterable<ValueAtPercentile> percentileValues() {
            return Set.of();
        }

        @Override
        public Iterable<CountAtBucket> histogramCounts() {
            return Set.of();
        }

        @Override
        public void outputSummary(PrintStream out, double scale) {
        }
    }

    static class Gauge extends NoOpMeter implements io.helidon.metrics.api.Gauge {

        static <T> Builder<T> builder(String name, T stateObject, ToDoubleFunction<T> fn) {
            return new Builder<>(name, stateObject, fn);
        }

        static class Builder<T> extends NoOpMeter.Builder<Builder<T>, Gauge> implements io.helidon.metrics.api.Gauge.Builder<T> {

            private final T stateObject;
            private final ToDoubleFunction<T> fn;

            private Builder(String name, T stateObject, ToDoubleFunction<T> fn) {
                super(name, Type.GAUGE);
                this.stateObject = stateObject;
                this.fn = fn;
            }

            @Override
            public Gauge build() {
                return new Gauge(this);
            }
        }

        private <T> Gauge(Builder<T> builder) {
            super(builder);
        }

        @Override
        public double value() {
            return 0;
        }
    }

    static class Timer extends NoOpMeter implements io.helidon.metrics.api.Timer {

        static class Sample implements io.helidon.metrics.api.Timer.Sample {

            private Sample() {
            }

            @Override
            public long stop(io.helidon.metrics.api.Timer timer) {
                return 0;
            }
        }

        static Sample start() {
            return new Sample();
        }

        static Sample start(MeterRegistry meterRegistry) {
            return new Sample();
        }

        static Sample start(Clock clock) {
            return new Sample();
        }

        static class Builder extends NoOpMeter.Builder<Builder, Timer> implements io.helidon.metrics.api.Timer.Builder {

            private Builder(String name) {
                super(name, Type.TIMER);
            }

            @Override
            public Timer build() {
                return new Timer(this);
            }

            @Override
            public Builder publishPercentiles(double... percentiles) {
                return identity();
            }

            @Override
            public Builder percentilePrecision(Integer digitsOfPrecision) {
                return identity();
            }

            @Override
            public Builder publishPercentileHistogram() {
                return identity();
            }

            @Override
            public Builder publishPercentileHistogram(Boolean enabled) {
                return identity();
            }

            @Override
            public Builder serviceLevelObjectives(Duration... slos) {
                return identity();
            }

            @Override
            public Builder minimumExpectedValue(Duration min) {
                return identity();
            }

            @Override
            public Builder maximumExpectedValue(Duration max) {
                return identity();
            }

            @Override
            public Builder distributionStatisticExpiry(Duration expiry) {
                return identity();
            }

            @Override
            public Builder distributionStatisticBufferLength(Integer bufferLength) {
                return identity();
            }
        }

        static Builder builder(String name) {
            return new Builder(name);
        }

        private Timer(Builder builder) {
            super(builder);
        }

        @Override
        public io.helidon.metrics.api.HistogramSnapshot takeSnapshot() {
            return null;
        }

        @Override
        public void record(long amount, TimeUnit unit) {

        }

        @Override
        public void record(Duration duration) {

        }

        @Override
        public <T> T record(Supplier<T> f) {
            return null;
        }

        @Override
        public <T> T record(Callable<T> f) throws Exception {
            return null;
        }

        @Override
        public void record(Runnable f) {

        }

        @Override
        public Runnable wrap(Runnable f) {
            return null;
        }

        @Override
        public <T> Callable<T> wrap(Callable<T> f) {
            return null;
        }

        @Override
        public <T> Supplier<T> wrap(Supplier<T> f) {
            return null;
        }

        @Override
        public long count() {
            return 0;
        }

        @Override
        public double totalTime(TimeUnit unit) {
            return 0;
        }

        @Override
        public double mean(TimeUnit unit) {
            return 0;
        }

        @Override
        public double max(TimeUnit unit) {
            return 0;
        }
    }

    static class DistributionStatisticsConfig implements io.helidon.metrics.api.DistributionStatisticsConfig {

        static Builder builder() {
            return new Builder();
        }

        static class Builder implements io.helidon.metrics.api.DistributionStatisticsConfig.Builder {

            @Override
            public io.helidon.metrics.api.DistributionStatisticsConfig build() {
                return new NoOpMeter.DistributionStatisticsConfig(this);
            }

            @Override
            public io.helidon.metrics.api.DistributionStatisticsConfig.Builder expiry(Duration expiry) {
                return identity();
            }

            @Override
            public io.helidon.metrics.api.DistributionStatisticsConfig.Builder bufferLength(Integer bufferLength) {
                return identity();
            }

            @Override
            public io.helidon.metrics.api.DistributionStatisticsConfig.Builder percentilesHistogram(Boolean enabled) {
                return identity();
            }

            @Override
            public io.helidon.metrics.api.DistributionStatisticsConfig.Builder minimumExpectedValue(Double min) {
                return identity();
            }

            @Override
            public io.helidon.metrics.api.DistributionStatisticsConfig.Builder maximumExpectedValue(Double max) {
                return identity();
            }

            @Override
            public io.helidon.metrics.api.DistributionStatisticsConfig.Builder percentiles(double... percentiles) {
                return identity();
            }

            @Override
            public io.helidon.metrics.api.DistributionStatisticsConfig.Builder percentiles(Iterable<Double> percentiles) {
                return identity();
            }

            @Override
            public io.helidon.metrics.api.DistributionStatisticsConfig.Builder percentilePrecision(Integer digitsOfPrecision) {
                return identity();
            }

            @Override
            public io.helidon.metrics.api.DistributionStatisticsConfig.Builder serviceLevelObjectives(double... slos) {
                return identity();
            }

            @Override
            public io.helidon.metrics.api.DistributionStatisticsConfig.Builder serviceLevelObjectives(Iterable<Double> slos) {
                return identity();
            }
        }

        private DistributionStatisticsConfig(DistributionStatisticsConfig.Builder builder) {
        }

        @Override
        public io.helidon.metrics.api.DistributionStatisticsConfig merge(
                io.helidon.metrics.api.DistributionStatisticsConfig parent) {
            return builder().build();
        }

        @Override
        public Optional<Boolean> isPercentileHistogram() {
            return Optional.empty();
        }

        @Override
        public Optional<Boolean> isPublishingPercentiles() {
            return Optional.empty();
        }

        @Override
        public Optional<Boolean> isPublishingHistogram() {
            return Optional.empty();
        }

        @Override
        public Optional<Iterable<Double>> percentiles() {
            return Optional.empty();
        }

        @Override
        public Optional<Integer> percentilePrecision() {
            return Optional.empty();
        }

        @Override
        public Optional<Double> minimumExpectedValue() {
            return Optional.empty();
        }

        @Override
        public Optional<Double> maximumExpectedValue() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> expiry() {
            return Optional.empty();
        }

        @Override
        public Optional<Integer> bufferLength() {
            return Optional.empty();
        }

        @Override
        public Optional<Iterable<Double>> serviceLevelObjectiveBoundaries() {
            return Optional.empty();
        }
    }
}
