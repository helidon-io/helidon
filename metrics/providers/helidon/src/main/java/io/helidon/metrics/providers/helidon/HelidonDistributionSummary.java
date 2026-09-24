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

import java.util.Objects;
import java.util.Optional;

import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.Meter;

final class HelidonDistributionSummary extends HelidonMeter implements DistributionSummary {
    private final HelidonHistogram histogram;
    private final double scale;

    private HelidonDistributionSummary(Meter.Id id, Builder builder) {
        super(id, Type.DISTRIBUTION_SUMMARY, builder);
        this.histogram = HelidonHistogram.create(HelidonTypes.doubleArray(builder.percentiles()),
                                                 builder.histogramBuckets());
        this.scale = builder.scale().orElse(1D);
    }

    static Builder builder(String name, DistributionStatisticsConfig.Builder configBuilder) {
        return new Builder(name, configBuilder);
    }

    static HelidonDistributionSummary create(Meter.Id id, Builder builder) {
        return new HelidonDistributionSummary(id, builder);
    }

    @Override
    public void record(double amount) {
        histogram.record(amount * scale);
    }

    @Override
    public long count() {
        return histogram.count();
    }

    @Override
    public double totalAmount() {
        return histogram.total();
    }

    @Override
    public double mean() {
        return histogram.mean();
    }

    @Override
    public double max() {
        return histogram.max();
    }

    @Override
    public HistogramSnapshot snapshot() {
        return histogram.snapshot();
    }

    @Override
    public String toString() {
        return stringPrefix()
                + "count=" + count()
                + ", total=" + totalAmount()
                + ", max=" + max()
                + "]";
    }

    static final class Builder extends HelidonMeter.AbstractBuilder<DistributionSummary.Builder, DistributionSummary>
            implements DistributionSummary.Builder {
        private DistributionStatisticsConfig.Builder configBuilder;
        private Optional<Double> scale = Optional.empty();
        private Optional<Boolean> publishPercentileHistogram = Optional.empty();

        private Builder(String name, DistributionStatisticsConfig.Builder configBuilder) {
            super(name);
            this.configBuilder = Objects.requireNonNull(configBuilder);
        }

        @Override
        public Builder scale(double scale) {
            this.scale = Optional.of(scale);
            return this;
        }

        @Override
        public Builder distributionStatisticsConfig(DistributionStatisticsConfig.Builder configBuilder) {
            this.configBuilder = Objects.requireNonNull(configBuilder);
            return this;
        }

        @Override
        public Builder publishPercentileHistogram(boolean value) {
            this.publishPercentileHistogram = Optional.of(value);
            return this;
        }

        @Override
        public Optional<Double> scale() {
            return scale;
        }

        @Override
        public Optional<DistributionStatisticsConfig.Builder> distributionStatisticsConfig() {
            return Optional.of(configBuilder);
        }

        @Override
        public Optional<Boolean> publishPercentileHistogram() {
            return publishPercentileHistogram;
        }

        Iterable<Double> percentiles() {
            return configBuilder.percentiles();
        }

        double[] histogramBuckets() {
            double[] explicitBuckets = HelidonTypes.doubleArray(configBuilder.buckets());
            if (!publishPercentileHistogram.orElse(false)) {
                return explicitBuckets;
            }
            return HelidonTypes.percentileHistogramBuckets(configBuilder.minimumExpectedValue()
                                                                   .orElse(HelidonTypes.DEFAULT_SUMMARY_HISTOGRAM_MIN),
                                                           configBuilder.maximumExpectedValue()
                                                                   .orElse(HelidonTypes.DEFAULT_SUMMARY_HISTOGRAM_MAX),
                                                           explicitBuckets);
        }

        @Override
        Class<? extends Meter> meterType() {
            return DistributionSummary.class;
        }
    }
}
