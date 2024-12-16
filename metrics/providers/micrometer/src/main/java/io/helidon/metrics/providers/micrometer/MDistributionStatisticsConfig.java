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
package io.helidon.metrics.providers.micrometer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.metrics.api.DistributionStatisticsConfig;

import io.micrometer.core.instrument.DistributionSummary;

/**
 * Statistics config implementation for the Micrometer metrics provider.
 * <p>
 *     Most of our implementation classes have a delegate which points to the corresponding Micrometer instance. Although
 *     Micrometer does have a {@link io.micrometer.core.instrument.distribution.DistributionStatisticConfig} type it is
 *     hidden inside the {@link io.micrometer.core.instrument.DistributionSummary} and we cannot access it.
 * <p>
 *     As a result, this class does not have a delegate. To satisfy the Helidon metrics API this type keeps a copy of the
 *     relevant information that is also stored in its Micrometer counterpart.
 * </p>
 */
class MDistributionStatisticsConfig implements io.helidon.metrics.api.DistributionStatisticsConfig {

    private final double[] percentiles;
    private final double[] buckets;
    private final Optional<Double> minimumExpectedValue;
    private final Optional<Double> maximumExpectedValue;

    /**
     * Creates a new config instance from a builder (which wraps a Micrometer config builder).
     *
     * @param builder builder which wraps the Micrometer config builder
     */
    private MDistributionStatisticsConfig(Builder builder) {
        percentiles = builder.percentiles;
        buckets = builder.buckets;
        minimumExpectedValue = builder.minimumExpectedValue();
        maximumExpectedValue = builder.maximumExpectedValue();
    }

    static Builder builder(DistributionSummary.Builder micrometerSummaryBuilder) {
        return new Builder(micrometerSummaryBuilder);
    }

    static <T> T chooseOpt(T fromChild, Supplier<Optional<T>> fromParent) {
        return Objects.requireNonNullElseGet(fromChild,
                                             () -> fromParent.get().orElse(null));
    }

    static MDistributionStatisticsConfig.Builder builderFrom(MDistributionSummary.Builder sBuilder,
                                                             DistributionStatisticsConfig.Builder other) {
        MDistributionStatisticsConfig.Builder configBuilder = MDistributionStatisticsConfig.builder(sBuilder.delegate());
        configBuilder.from(other);
        return configBuilder;
    }


    @Override
    public Optional<Iterable<Double>> percentiles() {
        return Optional.ofNullable(Util.iterable(percentiles));
    }

    @Override
    public Optional<Double> minimumExpectedValue() {
        return minimumExpectedValue;
    }

    @Override
    public Optional<Double> maximumExpectedValue() {
        return maximumExpectedValue;
    }

    @Override
    public Optional<Iterable<Double>> buckets() {
        return Optional.ofNullable(Util.iterable(buckets));
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        throw new IllegalArgumentException(getClass().getSimpleName() + " does not have a delegate to unwrap");
    }

    /**
     * Builder for the {@link }{@link io.helidon.metrics.api.DistributionStatisticsConfig}} in the Micrometer provider.
     * <p>
     *     Although Micrometer uses its own stats config builder internally it is not accessible to us. Instead, for example to
     *     set the percentiles in the Micrometer stats config builder, we invoke a method on the Micrometer
     *     {}@link {@link io.micrometer.core.instrument.DistributionSummary.Builder}, and it delegates to its internal stats
     *     builder.
     * <p>
     *     Therefore, the "delegate" for this builder is the builder for the Micrometer DistributionSummary. Further, because
     *     the Micrometer DistributionSummary.Builder does not expose the stats config values that have been set in its internal
     *     stats config builder, our implementation has to record those values itself.
     * </p>
     */
    static class Builder implements io.helidon.metrics.api.DistributionStatisticsConfig.Builder {

        static final double[] DEFAULT_PERCENTILES = {0.5, 0.75, 0.95, 0.98, 0.99, 0.999};
        static final int DEFAULT_PRECISION = 3;
        private final DistributionSummary.Builder delegate;
        private Optional<Double> min = Optional.empty();
        private Optional<Double> max = Optional.empty();
        private double[] percentiles = DEFAULT_PERCENTILES;
        private double[] buckets;


        private Builder(DistributionSummary.Builder delegate) {
            this.delegate = delegate;
            delegate.percentilePrecision(DEFAULT_PRECISION);
            delegate.publishPercentiles(DEFAULT_PERCENTILES);
        }

        @Override
        public MDistributionStatisticsConfig build() {
            return new MDistributionStatisticsConfig(this);
        }

        @Override
        public Builder minimumExpectedValue(Double min) {
            this.min = Optional.of(min);
            delegate.minimumExpectedValue(min);
            return this;
        }

        @Override
        public Builder maximumExpectedValue(Double max) {
            this.max = Optional.of(max);
            delegate.maximumExpectedValue(max);
            return this;
        }

        @Override
        public Builder percentiles(double... percentiles) {
            this.percentiles = percentiles;
            delegate.publishPercentiles(percentiles);
            return this;
        }

        @Override
        public Builder percentiles(Iterable<Double> percentiles) {
            this.percentiles = Util.doubleArray(percentiles);
            delegate.publishPercentiles(this.percentiles);
            return this;
        }

        @Override
        public Builder buckets(double... buckets) {
            this.buckets = buckets;
            delegate.serviceLevelObjectives(buckets);
            return this;
        }

        @Override
        public Builder buckets(Iterable<Double> buckets) {
            this.buckets = Util.doubleArray(buckets);
            delegate.serviceLevelObjectives(this.buckets);
            return this;
        }

        @Override
        public Optional<Double> minimumExpectedValue() {
            return min;
        }

        @Override
        public Optional<Double> maximumExpectedValue() {
            return max;
        }

        @Override
        public Iterable<Double> percentiles() {
            return Util.iterable(percentiles);
        }

        @Override
        public Iterable<Double> buckets() {
            return Util.iterable(buckets);
        }

        @Override
        public <R> R unwrap(Class<? extends R> c) {
            if (c.isInstance(delegate)) {
                return c.cast(delegate);
            }
            if (c.isInstance(this)) {
                return c.cast(this);
            }
            throw new IllegalArgumentException("Cannot unwrap to " + c.getName());
        }

        public Builder from(DistributionStatisticsConfig.Builder other) {
            other.minimumExpectedValue().ifPresent(this::minimumExpectedValue);
            other.maximumExpectedValue().ifPresent(this::maximumExpectedValue);
            buckets = Util.doubleArray(other.buckets());
            percentiles = Util.doubleArray(other.percentiles());
            return this;
        }
    }

    /**
     * Implementation of {@link io.helidon.metrics.api.DistributionStatisticsConfig} for Micrometer with no related
     * {@link io.micrometer.core.instrument.DistributionSummary}.
     * <p>
     *     In some cases, our wrapper around the Micrometer
     *     {@link io.micrometer.core.instrument.distribution.DistributionStatisticConfig} will not have a delegate which points
     *     to a related {@link io.micrometer.core.instrument.DistributionSummary}. This class handles that case.
     * </p>
     */
    static class Unconnected implements DistributionStatisticsConfig {
        private Optional<Double> min = Optional.empty();
        private Optional<Double> max = Optional.empty();
        private double[] percentiles;
        private double[] buckets;


        static Builder builder() {
            return new Builder();
        }

        private Unconnected(DistributionStatisticsConfig.Builder builder) {
            min = builder.minimumExpectedValue();
            max = builder.maximumExpectedValue();
            percentiles = Util.doubleArray(builder.percentiles());
            buckets = Util.doubleArray(builder.buckets());
        }

        @Override
        public Optional<Iterable<Double>> percentiles() {
            return Optional.ofNullable(Util.iterable(percentiles));
        }

        @Override
        public Optional<Double> minimumExpectedValue() {
            return min;
        }

        @Override
        public Optional<Double> maximumExpectedValue() {
            return max;
        }

        @Override
        public Optional<Iterable<Double>> buckets() {
            return Optional.ofNullable(Util.iterable(buckets));
        }

        @Override
        public <R> R unwrap(Class<? extends R> c) {
            return c.cast(this);
        }

        static class Builder implements DistributionStatisticsConfig.Builder {

            private Optional<Double> min = Optional.empty();
            private Optional<Double> max = Optional.empty();
            private double[] percentiles = MDistributionStatisticsConfig.Builder.DEFAULT_PERCENTILES;
            private double[] buckets;

            @Override
            public DistributionStatisticsConfig.Builder minimumExpectedValue(Double min) {
                this.min = Optional.of(min);
                return identity();
            }

            @Override
            public DistributionStatisticsConfig.Builder maximumExpectedValue(Double max) {
                this.max = Optional.of(max);
                return identity();
            }

            @Override
            public DistributionStatisticsConfig.Builder percentiles(double... percentiles) {
                this.percentiles = percentiles;
                return identity();
            }

            @Override
            public DistributionStatisticsConfig.Builder percentiles(Iterable<Double> percentiles) {
                this.percentiles = Util.doubleArray(percentiles);
                return identity();
            }

            @Override
            public DistributionStatisticsConfig.Builder buckets(double... buckets) {
                this.buckets = buckets;
                return identity();
            }

            @Override
            public DistributionStatisticsConfig.Builder buckets(Iterable<Double> buckets) {
                this.buckets = Util.doubleArray(buckets);
                return identity();
            }

            @Override
            public Optional<Double> minimumExpectedValue() {
                return min;
            }

            @Override
            public Optional<Double> maximumExpectedValue() {
                return max;
            }

            @Override
            public Iterable<Double> percentiles() {
                return Util.iterable(percentiles);
            }

            @Override
            public Iterable<Double> buckets() {
                return Util.iterable(buckets);
            }

            @Override
            public DistributionStatisticsConfig build() {
                return new Unconnected(this);
            }

            @Override
            public <R> R unwrap(Class<? extends R> c) {
                return c.cast(this);
            }
        }
    }
}
