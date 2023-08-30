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
package io.helidon.metrics.providers.micrometer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import io.helidon.metrics.api.DistributionStatisticsConfig;

import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

class MDistributionStatisticsConfig implements io.helidon.metrics.api.DistributionStatisticsConfig {

    private final DistributionStatisticConfig delegate;

    /**
     * Creates a new config instance from a builder (which wraps a Micrometer config builder).
     *
     * @param builder builder which wraps the Micrometer config builder
     */
    private MDistributionStatisticsConfig(Builder builder) {
        delegate = builder.delegate.build();
    }

    /**
     * Creates a new config instance, primarily from a merge operation.
     *
     * @param delegate pre-existing delegate
     */
    private MDistributionStatisticsConfig(DistributionStatisticConfig delegate) {
        this.delegate = delegate;
    }

    static Builder builder() {
        return new Builder();
    }

    static <T> T chooseOpt(T fromChild, Supplier<Optional<T>> fromParent) {
        return Objects.requireNonNullElseGet(fromChild,
                                             () -> fromParent.get().orElse(null));
    }

    static MDistributionStatisticsConfig.Builder builderFrom(DistributionStatisticsConfig.Builder other) {
        MDistributionStatisticsConfig.Builder configBuilder = MDistributionStatisticsConfig.builder();
        configBuilder.from(other);
        return configBuilder;
    }


    @Override
    public Optional<Iterable<Double>> percentiles() {
        return Optional.ofNullable(Util.iterable(delegate.getPercentiles()));
    }

    @Override
    public Optional<Double> minimumExpectedValue() {
        return Optional.ofNullable(delegate.getMinimumExpectedValueAsDouble());
    }

    @Override
    public Optional<Double> maximumExpectedValue() {
        return Optional.ofNullable(delegate.getMaximumExpectedValueAsDouble());
    }

    @Override
    public Optional<Iterable<Double>> buckets() {
        return Optional.ofNullable(Util.iterable(delegate.getServiceLevelObjectiveBoundaries()));
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return c.cast(delegate);
    }

    io.micrometer.core.instrument.distribution.DistributionStatisticConfig delegate() {
        return delegate;
    }

    static class Builder implements io.helidon.metrics.api.DistributionStatisticsConfig.Builder {

        private final DistributionStatisticConfig.Builder delegate;
        private Optional<Double> min = Optional.empty();
        private Optional<Double> max = Optional.empty();
        private double[] percentiles;
        private double[] buckets;


        private Builder() {
            delegate = DistributionStatisticConfig.builder();
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
            delegate.percentiles(percentiles);
            return this;
        }

        @Override
        public Builder percentiles(Iterable<Double> percentiles) {
            this.percentiles = Util.doubleArray(percentiles);
            delegate.percentiles(this.percentiles);
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
            return c.cast(delegate);
        }

        DistributionStatisticConfig.Builder delegate() {
            return delegate;
        }

        public Builder from(DistributionStatisticsConfig.Builder other) {
            other.minimumExpectedValue().ifPresent(this::minimumExpectedValue);
            other.maximumExpectedValue().ifPresent(this::maximumExpectedValue);
            buckets = Util.doubleArray(other.buckets());
            percentiles = Util.doubleArray(other.percentiles());
            return this;
        }
    }
}
