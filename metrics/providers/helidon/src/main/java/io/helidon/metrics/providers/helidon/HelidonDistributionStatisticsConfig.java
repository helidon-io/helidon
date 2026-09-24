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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.metrics.api.DistributionStatisticsConfig;

final class HelidonDistributionStatisticsConfig implements DistributionStatisticsConfig {
    private final Optional<Double> min;
    private final Optional<Double> max;
    private final List<Double> percentiles;
    private final List<Double> buckets;

    private HelidonDistributionStatisticsConfig(Builder builder) {
        this.min = builder.minimumExpectedValue();
        this.max = builder.maximumExpectedValue();
        this.percentiles = List.copyOf(builder.percentiles);
        this.buckets = List.copyOf(builder.buckets);
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<Iterable<Double>> percentiles() {
        return Optional.of(percentiles);
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
        return Optional.of(buckets);
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return Objects.requireNonNull(c).cast(this);
    }

    static final class Builder implements DistributionStatisticsConfig.Builder {
        private Optional<Double> min = Optional.empty();
        private Optional<Double> max = Optional.empty();
        private List<Double> percentiles = HelidonTypes.DEFAULT_PERCENTILES.length == 0
                ? List.of()
                : Arrays.stream(HelidonTypes.DEFAULT_PERCENTILES).boxed().toList();
        private List<Double> buckets = List.of();

        @Override
        public DistributionStatisticsConfig build() {
            return new HelidonDistributionStatisticsConfig(this);
        }

        @Override
        public Builder minimumExpectedValue(Double min) {
            this.min = Optional.of(Objects.requireNonNull(min));
            return this;
        }

        @Override
        public Builder maximumExpectedValue(Double max) {
            this.max = Optional.of(Objects.requireNonNull(max));
            return this;
        }

        @Override
        public Builder percentiles(double... percentiles) {
            this.percentiles = Arrays.stream(Objects.requireNonNull(percentiles)).boxed().toList();
            return this;
        }

        @Override
        public Builder percentiles(Iterable<Double> percentiles) {
            this.percentiles = toList(percentiles);
            return this;
        }

        @Override
        public Builder buckets(double... buckets) {
            this.buckets = Arrays.stream(Objects.requireNonNull(buckets)).boxed().toList();
            return this;
        }

        @Override
        public Builder buckets(Iterable<Double> buckets) {
            this.buckets = toList(buckets);
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
            return percentiles;
        }

        @Override
        public Iterable<Double> buckets() {
            return buckets;
        }

        @Override
        public <R> R unwrap(Class<? extends R> c) {
            return Objects.requireNonNull(c).cast(this);
        }

        private static List<Double> toList(Iterable<Double> values) {
            List<Double> result = new ArrayList<>();
            Objects.requireNonNull(values).forEach(value -> result.add(Objects.requireNonNull(value)));
            return List.copyOf(result);
        }
    }
}
