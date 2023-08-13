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
package io.helidon.metrics.micrometer;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

class MDistributionStatisticsConfig implements io.helidon.metrics.api.DistributionStatisticsConfig {

    static Builder builder() {
        return new Builder();
    }

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

    @Override
    public MDistributionStatisticsConfig merge(io.helidon.metrics.api.DistributionStatisticsConfig parent) {
        DistributionStatisticConfig newDelegate = DistributionStatisticConfig.builder()
                .percentilesHistogram(
                        chooseOpt(delegate.isPercentileHistogram(),
                                  parent::isPercentileHistogram))
                .percentiles(
                        choose(delegate.getPercentiles(),
                               () -> Util.doubleArray(parent.percentiles())))
                .serviceLevelObjectives(
                        choose(delegate.getServiceLevelObjectiveBoundaries(),
                               () -> Util.doubleArray(parent.serviceLevelObjectiveBoundaries())))
                .percentilePrecision(
                        chooseOpt(delegate.getPercentilePrecision(),
                                  parent::percentilePrecision))
                .minimumExpectedValue(
                        chooseOpt(delegate.getMinimumExpectedValueAsDouble(),
                                  parent::minimumExpectedValue))
                .maximumExpectedValue(
                        chooseOpt(delegate.getMaximumExpectedValueAsDouble(),
                                  parent::maximumExpectedValue))
                .expiry(
                        chooseOpt(delegate.getExpiry(),
                                  parent::expiry))
                .bufferLength(
                        chooseOpt(delegate.getBufferLength(),
                                  parent::bufferLength))
                .build();
        return new MDistributionStatisticsConfig(newDelegate);
    }



        @Override
    public Optional<Boolean> isPercentileHistogram() {
        return Optional.ofNullable(delegate.isPercentileHistogram());
    }

    @Override
    public Optional<Boolean> isPublishingPercentiles() {
        return Optional.of(delegate.isPublishingPercentiles());
    }

    @Override
    public Optional<Boolean>  isPublishingHistogram() {
        return Optional.of(delegate.isPublishingHistogram());
    }

    @Override
    public Optional<Iterable<Double>> percentiles() {
        return Optional.ofNullable(Util.iterable(delegate.getPercentiles()));
    }

    @Override
    public Optional<Integer> percentilePrecision() {
        return Optional.ofNullable(delegate.getPercentilePrecision());
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
    public Optional<Duration> expiry() {
        return Optional.ofNullable(delegate.getExpiry());
    }

    @Override
    public Optional<Integer> bufferLength() {
        return Optional.ofNullable(delegate.getBufferLength());
    }

    @Override
    public Optional<Iterable<Double>> serviceLevelObjectiveBoundaries() {
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

        private Builder() {
            delegate = DistributionStatisticConfig.builder();
        }

        @Override
        public MDistributionStatisticsConfig build() {
            return new MDistributionStatisticsConfig(this);
        }

        @Override
        public Builder expiry(Duration expiry) {
            delegate.expiry(expiry);
            return this;
        }

        @Override
        public Builder bufferLength(Integer bufferLength) {
            delegate.bufferLength(bufferLength);
            return this;
        }

        @Override
        public Builder percentilesHistogram(Boolean enabled) {
            delegate.percentilesHistogram(enabled);
            return this;
        }

        @Override
        public Builder minimumExpectedValue(Double min) {
            delegate.minimumExpectedValue(min);
            return this;
        }

        @Override
        public Builder maximumExpectedValue(Double max) {
            delegate.maximumExpectedValue(max);
            return this;
        }

        @Override
        public Builder percentiles(double... percentiles) {
            delegate.percentiles(percentiles);
            return this;
        }

        @Override
        public Builder percentiles(Iterable<Double> percentiles) {
            delegate.percentiles(Util.doubleArray(percentiles));
            return this;
        }

        @Override
        public Builder percentilePrecision(Integer digitsOfPrecision) {
            delegate.percentilePrecision(digitsOfPrecision);
            return this;
        }

        @Override
        public Builder serviceLevelObjectives(double... slos) {
            delegate.serviceLevelObjectives(slos);
            return this;
        }

        @Override
        public Builder serviceLevelObjectives(Iterable<Double> slos) {
            delegate.serviceLevelObjectives(Util.doubleArray(slos));
            return this;
        }

        @Override
        public <R> R unwrap(Class<? extends R> c) {
            return c.cast(delegate);
        }

        DistributionStatisticConfig.Builder delegate() {
            return delegate;
        }
    }

    static <T> T chooseOpt(T fromChild, Supplier<Optional<T>> fromParent) {
        return Objects.requireNonNullElseGet(fromChild,
                                             () -> fromParent.get().orElse(null));
    }

    static <T> T choose(T fromChild, Supplier<T> fromParent) {
        return Objects.requireNonNullElseGet(fromChild, fromParent);
    }


}
