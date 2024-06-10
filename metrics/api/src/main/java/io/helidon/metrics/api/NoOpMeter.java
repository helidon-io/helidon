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
package io.helidon.metrics.api;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * No-op implementation of the Helidon {@link io.helidon.metrics.api.Meter} interface.
 *
 * <p>
 * It's a big class, but it's fairly nicely organized and it keeps the no-op meter implementations
 * from cluttering up the containing directory as separate classes.
 * </p>
 */
class NoOpMeter implements Meter, NoOpWrapper {

    private final Id id;
    private final String unit;
    private final String description;
    private final Type type;
    private final String scope;

    private NoOpMeter(NoOpMeter.Builder<?, ?> builder) {
        this(new NoOpMeter.Id(builder.name, builder.tags),
             builder.unit,
             builder.description,
             builder.type,
             builder.scope);
    }

    private NoOpMeter(Id id, String baseUnit, String description, Type type, String scope) {
        this.id = id;
        this.unit = Objects.requireNonNullElse(baseUnit, "");
        this.description = Objects.requireNonNullElse(description, "");
        this.type = type;
        this.scope = scope;
    }

    static Map<String, String> tagsMap(Iterable<Tag> tags) {
        var result = new TreeMap<String, String>();
        tags.forEach(tag -> result.put(tag.key(), tag.value()));
        return result;
    }

    @Override
    public Id id() {
        return id;
    }

    @Override
    public Optional<String> baseUnit() {
        return Optional.ofNullable(unit);
    }

    @Override
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public Optional<String> scope() {
        return Optional.ofNullable(scope);
    }

    private static NoOpMeter.Id create(NoOpMeter.Builder<?, ?> builder) {
        return new NoOpMeter.Id(builder.name(), builder.tags());

    }

    static class Id implements Meter.Id {
        private final String name;
        private final List<Tag> tags = new ArrayList<>(); // must be ordered by tag name for consistency

        private Id(String name, Map<String, String> tags) {
            this.name = name;
            tags.forEach((k, v) -> this.tags.add(Tag.create(k, v)));
            this.tags.sort(Comparator.comparing(Tag::key));
        }

        static Id create(String name, Map<String, String> tags) {
            return new Id(name, tags);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<Tag> tags() {
            return Collections.unmodifiableList(tags);
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

    abstract static class Builder<B extends Builder<B, M>, M extends Meter> implements Wrapper {

        private final String name;
        private final Map<String, String> tags = new TreeMap<>(); // tree map for ordering by tag name
        private final Type type;
        private String description;
        private String unit;
        private String scope;

        private Builder(String name, Type type) {
            this.name = name;
            this.type = type;
        }

        abstract M build();

        public B tags(Iterable<Tag> tags) {
            tags.forEach(tag -> this.tags.put(tag.key(), tag.value()));
            return identity();
        }

        public B addTag(Tag tag) {
            tags.put(tag.key(), tag.value());
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

        public B scope(String scope) {
            this.scope = scope;
            return identity();
        }

        public B identity() {
            return (B) this;
        }

        public String name() {
            return name;
        }

        public Map<String, String> tags() {
            return new TreeMap<>(tags);
        }

        public Optional<String> baseUnit() {
            return Optional.ofNullable(unit);
        }

        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        public Optional<String> scope() {
            return Optional.ofNullable(scope);
        }

        @Override
        public <R> R unwrap(Class<? extends R> c) {
            return c.cast(this);
        }
    }

    static class Counter extends NoOpMeter implements io.helidon.metrics.api.Counter {

        protected Counter(Builder builder) {
            super(builder);
        }

        static Counter create(String name, Iterable<Tag> tags) {
            return builder(name)
                    .tags(tags)
                    .build();
        }

        static Counter.Builder builder(String name) {
            return new Builder(name);
        }

        @Override
        public void increment() {
        }

        @Override
        public void increment(long amount) {
        }

        @Override
        public long count() {
            return 0L;
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
    }

    static class FunctionalCounter extends NoOpMeter implements io.helidon.metrics.api.FunctionalCounter {

        private FunctionalCounter(Builder<?> builder) {
            super(builder);
        }

        static <T> FunctionalCounter.Builder<T> builder(String name, T target, Function<T, Long> fn) {
            return new FunctionalCounter.Builder<>(name, target, fn);
        }

        @Override
        public long count() {
            return 0;
        }

        static class Builder<T> extends NoOpMeter.Builder<Builder<T>, FunctionalCounter>
                implements io.helidon.metrics.api.FunctionalCounter.Builder<T> {

            private final T stateObject;
            private final Function<T, Long> fn;

            private Builder(String name, T stateObject, Function<T, Long> fn) {
                super(name, Type.COUNTER);
                this.stateObject = stateObject;
                this.fn = fn;
            }

            @Override
            public T stateObject() {
                return stateObject;
            }

            @Override
            public Function<T, Long> fn() {
                return fn;
            }

            @Override
            public FunctionalCounter build() {
                return new FunctionalCounter(this);
            }
        }
    }

    static class DistributionSummary extends NoOpMeter implements io.helidon.metrics.api.DistributionSummary {

        private DistributionSummary(Builder builder) {
            super(builder);
        }

        static DistributionSummary.Builder builder(String name) {
            return new DistributionSummary.Builder(name);
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

        @Override
        public io.helidon.metrics.api.HistogramSnapshot snapshot() {
            return new NoOpMeter.HistogramSnapshot(0L, 0D, 0D);
        }

        static class Builder extends NoOpMeter.Builder<Builder, DistributionSummary>
                implements io.helidon.metrics.api.DistributionSummary.Builder {

            private static final double DEFAULT_SCALE = 1.0D;

            private io.helidon.metrics.api.DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder;
            private Double scale;

            private Builder(String name) {
                super(name, Type.DISTRIBUTION_SUMMARY);
            }

            @Override
            public DistributionSummary build() {
                return new DistributionSummary(this);
            }

            @Override
            public Builder scale(double scale) {
                this.scale = scale;
                return identity();
            }

            @Override
            public Builder distributionStatisticsConfig(
                    io.helidon.metrics.api.DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder) {
                this.distributionStatisticsConfigBuilder = distributionStatisticsConfigBuilder;
                return identity();
            }

            @Override
            public Optional<Double> scale() {
                return Optional.ofNullable(scale);
            }

            @Override
            public Optional<io.helidon.metrics.api.DistributionStatisticsConfig.Builder> distributionStatisticsConfig() {
                return Optional.ofNullable(distributionStatisticsConfigBuilder);
            }
        }
    }

    static class HistogramSnapshot implements io.helidon.metrics.api.HistogramSnapshot, NoOpWrapper {

        private final long count;
        private final double total;
        private final double max;

        private HistogramSnapshot(long count, double total, double max) {
            this.count = count;
            this.total = total;
            this.max = max;
        }

        static HistogramSnapshot empty(long count, double total, double max) {
            return new HistogramSnapshot(count, total, max);
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
        public Iterable<Bucket> histogramCounts() {
            return Set.of();
        }

        @Override
        public void outputSummary(PrintStream out, double scale) {
        }
    }

    abstract static class Gauge<N extends Number> extends NoOpMeter implements io.helidon.metrics.api.Gauge<N> {

        protected Gauge(NoOpMeter.Gauge.Builder<?, N> builder) {
            super(builder);
        }

        static <T> Builder.DoubleFunctionBased<T> builder(String name, T stateObject, ToDoubleFunction<T> fn) {
            return new Builder.DoubleFunctionBased<>(name, stateObject, fn);
        }

        static <N extends Number> Builder.SupplierBased<N> builder(String name, Supplier<N> supplier) {
            return new Builder.SupplierBased<>(name, supplier);
        }

        abstract static class Builder<B extends Builder<B, N>, N extends Number>
                extends NoOpMeter.Builder<B, Gauge<N>> implements io.helidon.metrics.api.Gauge.Builder<N> {

            protected Builder(String name) {
                super(name, Type.GAUGE);
            }

            static class DoubleFunctionBased<T> extends Builder<DoubleFunctionBased<T>, Double> {

                private final T stateObject;
                private final ToDoubleFunction<T> fn;

                private DoubleFunctionBased(String name, T stateObject, ToDoubleFunction<T> fn) {
                    super(name);
                    this.stateObject = stateObject;
                    this.fn = fn;
                }

                @Override
                Gauge<Double> build() {
                    return new Gauge<>(this) {
                        @Override
                        public Double value() {
                            return fn.applyAsDouble(stateObject);
                        }
                    };
                }

                @Override
                public Supplier<Double> supplier() {
                    return () -> fn.applyAsDouble(stateObject);
                }
            }

            static class SupplierBased<N extends Number> extends Builder<SupplierBased<N>, N> {
                private final Supplier<N> supplier;

                private SupplierBased(String name, Supplier<N> supplier) {
                    super(name);
                    this.supplier = supplier;
                }

                @Override
                Gauge<N> build() {
                    return new Gauge<>(this) {
                        @Override
                        public N value() {
                            return supplier.get();
                        }
                    };
                }

                @Override
                public Supplier<N> supplier() {
                    return supplier;
                }
            }
        }
    }

    static class Timer extends NoOpMeter implements io.helidon.metrics.api.Timer {

        private Timer(Builder builder) {
            super(builder);
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

        static Builder builder(String name) {
            return new Builder(name);
        }

        @Override
        public io.helidon.metrics.api.HistogramSnapshot snapshot() {
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
            return f.call();
        }

        @Override
        public void record(Runnable f) {
            f.run();
        }

        @Override
        public Runnable wrap(Runnable f) {
            return f;
        }

        @Override
        public <T> Callable<T> wrap(Callable<T> f) {
            return f;
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

        static class Sample implements io.helidon.metrics.api.Timer.Sample {

            private Sample() {
            }

            @Override
            public long stop(io.helidon.metrics.api.Timer timer) {
                return 0;
            }
        }

        static class Builder extends NoOpMeter.Builder<Builder, Timer> implements io.helidon.metrics.api.Timer.Builder {

            private double[] percentiles;
            private Duration[] buckets;
            private Duration min;
            private Duration max;

            private Builder(String name) {
                super(name, Type.TIMER);
            }

            @Override
            public Timer build() {
                return new Timer(this);
            }

            @Override
            public Builder percentiles(double... percentiles) {
                this.percentiles = percentiles;
                return identity();
            }

            @Override
            public Builder buckets(Duration... buckets) {
                this.buckets = buckets;
                return identity();
            }

            @Override
            public Builder minimumExpectedValue(Duration min) {
                this.min = min;
                return identity();
            }

            @Override
            public Builder maximumExpectedValue(Duration max) {
                this.max = max;
                return identity();
            }

            @Override
            public Iterable<Double> percentiles() {
                return Arrays.stream(percentiles)
                        .boxed().toList();
            }

            @Override
            public Iterable<Duration> buckets() {
                return List.of(buckets);
            }

            @Override
            public Optional<Duration> minimumExpectedValue() {
                return Optional.ofNullable(min);
            }

            @Override
            public Optional<Duration> maximumExpectedValue() {
                return Optional.ofNullable(max);
            }

        }
    }

    static class DistributionStatisticsConfig
            implements io.helidon.metrics.api.DistributionStatisticsConfig, NoOpWrapper {

        private DistributionStatisticsConfig(DistributionStatisticsConfig.Builder builder) {
        }

        static Builder builder() {
            return new Builder();
        }

        @Override
        public Optional<Iterable<Double>> percentiles() {
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
        public Optional<Iterable<Double>> buckets() {
            return Optional.empty();
        }

        static class Builder implements io.helidon.metrics.api.DistributionStatisticsConfig.Builder, NoOpWrapper {

            @Override
            public io.helidon.metrics.api.DistributionStatisticsConfig build() {
                return new DistributionStatisticsConfig(this);
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
            public io.helidon.metrics.api.DistributionStatisticsConfig.Builder buckets(double... buckets) {
                return identity();
            }

            @Override
            public io.helidon.metrics.api.DistributionStatisticsConfig.Builder buckets(Iterable<Double> buckets) {
                return identity();
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
            public Iterable<Double> percentiles() {
                return Set.of();
            }

            @Override
            public Iterable<Double> buckets() {
                return Set.of();
            }
        }
    }
}

