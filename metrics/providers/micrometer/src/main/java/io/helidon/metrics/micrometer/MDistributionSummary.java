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

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

class MDistributionSummary extends MMeter<DistributionSummary> implements io.helidon.metrics.api.DistributionSummary {

    static Builder builder(String name,
                           io.helidon.metrics.api.DistributionStatisticsConfig.Builder configBuilder) {
        return new Builder(name, configBuilder);
    }

    static MDistributionSummary create(DistributionSummary summary) {
        return new MDistributionSummary(summary);
    }

    private MDistributionSummary(DistributionSummary delegate) {
        super(delegate);
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

    static class Builder extends MMeter.Builder<DistributionSummary.Builder, Builder, MDistributionSummary>
                         implements io.helidon.metrics.api.DistributionSummary.Builder {

        private Builder(String name, io.helidon.metrics.api.DistributionStatisticsConfig.Builder configBuilder) {
            super(name, DistributionSummary.builder(name));
        }

        @Override
        public Builder scale(double scale) {
            delegate().scale(scale);
            return identity();
        }

        @Override
        public Builder distributionStatisticsConfig(
                io.helidon.metrics.api.DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder) {
            io.helidon.metrics.api.DistributionStatisticsConfig config = distributionStatisticsConfigBuilder.build();
            DistributionSummary.Builder delegate = delegate();

            config.percentiles().ifPresent(p -> delegate.publishPercentiles(Util.doubleArray(p)));
            config.percentilePrecision().ifPresent(delegate::percentilePrecision);
            config.isPercentileHistogram().ifPresent(delegate::publishPercentileHistogram);
            config.serviceLevelObjectiveBoundaries().ifPresent(slos -> delegate.serviceLevelObjectives(Util.doubleArray(slos)));
            config.minimumExpectedValue().ifPresent(delegate::minimumExpectedValue);
            config.maximumExpectedValue().ifPresent(delegate::maximumExpectedValue);
            config.expiry().ifPresent(delegate::distributionStatisticExpiry);
            config.bufferLength().ifPresent(delegate::distributionStatisticBufferLength);

            return identity();
        }

        @Override
        MDistributionSummary register(MeterRegistry meterRegistry) {
            return MDistributionSummary.create(delegate().register(meterRegistry));
        }
    }
}
