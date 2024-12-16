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

import java.util.Optional;

import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.Meter;

import static io.micrometer.core.instrument.distribution.DistributionStatisticConfig.DEFAULT;

class MDistributionSummary extends MMeter<io.micrometer.core.instrument.DistributionSummary>
        implements io.helidon.metrics.api.DistributionSummary {

    private MDistributionSummary(Meter.Id id,
                                 io.micrometer.core.instrument.DistributionSummary delegate,
                                 Optional<String> scope) {
        super(id, delegate, scope);
    }

    private MDistributionSummary(Meter.Id id, io.micrometer.core.instrument.DistributionSummary delegate, Builder builder) {
        super(id, delegate, builder);
    }

    /**
     * Creates a new builder for a wrapper around a Micrometer summary that will be registered later, typically if the
     * developer is creating a summary using the Helidon API.
     *
     * @param name          name of the new summary
     * @param configBuilder builder for the distribution statistics config the summary should use
     * @return new builder for a wrapper summary
     */
    static Builder builder(String name,
                           DistributionStatisticsConfig.Builder configBuilder) {
        return new Builder(name, configBuilder);
    }

    static Builder builder(String name) {
        return new Builder(name);
    }

    static Builder builderFrom(DistributionSummary.Builder sBuilder) {


        MDistributionSummary.Builder b = MDistributionSummary.builder(sBuilder.name());
        MDistributionStatisticsConfig.Builder configBuilder = sBuilder.distributionStatisticsConfig().isPresent()
                ? MDistributionStatisticsConfig.builder(b.delegate())
                : MDistributionStatisticsConfig.builderFrom(b, sBuilder.distributionStatisticsConfig().get());

        b.distributionStatisticsConfig(configBuilder)
                .from(sBuilder);
        return b;
    }

    /**
     * Creates a new wrapper summary around an existing Micrometer summary, typically if the developer has registered a
     * summary directly using the Micrometer API rather than through the Helidon adapter but we need to expose the summary
     * via a wrapper.
     *
     * @param summary the Micrometer summary
     * @param scope   scope to apply
     * @return new wrapper around the summary
     */
    static MDistributionSummary create(Meter.Id id,
                                       io.micrometer.core.instrument.DistributionSummary summary,
                                       Optional<String> scope) {
        return new MDistributionSummary(id, summary, scope);
    }

    @Override
    public void record(double amount) {
        delegate().record(amount);
    }

    @Override
    public long count() {
        return delegate().count();
    }

    @Override
    public double totalAmount() {
        return delegate().totalAmount();
    }

    @Override
    public double mean() {
        return delegate().mean();
    }

    @Override
    public double max() {
        return delegate().max();
    }

    @Override
    public String toString() {
        return stringJoiner()
                .add("count=" + delegate().count())
                .add("totalAmount=" + delegate().totalAmount())
                .toString();
    }

    @Override
    public HistogramSnapshot snapshot() {
        return MHistogramSnapshot.create(delegate().takeSnapshot());
    }

    static class Builder extends
                         MMeter.Builder<io.micrometer.core.instrument.DistributionSummary.Builder,
                                 io.micrometer.core.instrument.DistributionSummary, Builder, MDistributionSummary>
            implements io.helidon.metrics.api.DistributionSummary.Builder {

        private Double scale;
        private DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder;
        private Boolean publishPercentileHistogram;

        private Builder(String name, DistributionStatisticsConfig.Builder configBuilder) {
            this(name, configBuilder.build());
        }

        private Builder(String name, DistributionStatisticsConfig config) {
            this(name);
            distributionStatisticsConfigBuilder
                    .percentiles(config.percentiles()
                                                .map(Util::doubleArray)
                                                .orElse(DEFAULT.getPercentiles()))
                    .buckets(config.buckets()
                                                    .map(Util::doubleArray)
                                                    .orElse(DEFAULT.getServiceLevelObjectiveBoundaries()))
                    .minimumExpectedValue(config.minimumExpectedValue()
                                                  .orElse(DEFAULT.getMinimumExpectedValueAsDouble()))
                    .maximumExpectedValue(config.maximumExpectedValue()
                                                  .orElse(DEFAULT.getMaximumExpectedValueAsDouble()));
        }

        private Builder(String name) {
            super(name, io.micrometer.core.instrument.DistributionSummary.builder(name));
            distributionStatisticsConfigBuilder = MDistributionStatisticsConfig.builder(delegate());
        }

        @Override
        protected MDistributionSummary build(Meter.Id id, io.micrometer.core.instrument.DistributionSummary summary) {
            return new MDistributionSummary(id, summary, this);
        }

        @Override
        public Builder scale(double scale) {
            this.scale = scale;
            delegate().scale(scale);
            return identity();
        }

        @Override
        public Builder distributionStatisticsConfig(
                io.helidon.metrics.api.DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder) {
            this.distributionStatisticsConfigBuilder = distributionStatisticsConfigBuilder;
            io.helidon.metrics.api.DistributionStatisticsConfig config = distributionStatisticsConfigBuilder.build();
            io.micrometer.core.instrument.DistributionSummary.Builder delegate = delegate();

            config.percentiles().ifPresent(p -> delegate.publishPercentiles(Util.doubleArray(p)));
            config.buckets().ifPresent(slos -> delegate.serviceLevelObjectives(Util.doubleArray(slos)));
            config.minimumExpectedValue().ifPresent(delegate::minimumExpectedValue);
            config.maximumExpectedValue().ifPresent(delegate::maximumExpectedValue);

            return identity();
        }

        @Override
        protected Builder delegateTags(Iterable<io.micrometer.core.instrument.Tag> tags) {
            delegate().tags(tags);
            return identity();
        }

        @Override
        protected Builder delegateTag(String key, String value) {
            delegate().tag(key, value);
            return identity();
        }

        @Override
        protected Builder delegateDescription(String description) {
            delegate().description(description);
            return identity();
        }

        @Override
        protected Builder delegateBaseUnit(String baseUnit) {
            delegate().baseUnit(baseUnit);
            return identity();
        }

        @Override
        public DistributionSummary.Builder publishPercentileHistogram(boolean value) {
            delegate().publishPercentileHistogram(value);
            publishPercentileHistogram = value;
            return identity();
        }

        @Override
        public Optional<Double> scale() {
            return Optional.ofNullable(scale);
        }

        @Override
        public Optional<DistributionStatisticsConfig.Builder> distributionStatisticsConfig() {
            return Optional.ofNullable(distributionStatisticsConfigBuilder);
        }

        @Override
        public Optional<Boolean> publishPercentileHistogram() {
            return Optional.ofNullable(publishPercentileHistogram);
        }

        protected Builder from(DistributionSummary.Builder other) {
            other.scale().ifPresent(this::scale);
            return this;
        }

        @Override
        protected Class<? extends Meter> meterType() {
            return DistributionSummary.class;
        }
    }
}
