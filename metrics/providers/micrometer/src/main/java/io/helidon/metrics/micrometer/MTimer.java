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
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.HistogramSnapshot;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

class MTimer extends MMeter<Timer> implements io.helidon.metrics.api.Timer {

    static MTimer of(Timer timer) {
        return new MTimer(timer);
    }

    private MTimer(Timer delegate) {
        super(delegate);
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        return MHistogramSnapshot.of(delegate().takeSnapshot());
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        delegate().record(amount, unit);
    }

    @Override
    public void record(Duration duration) {
        delegate().record(duration);
    }

    @Override
    public <T> T record(Supplier<T> f) {
        return delegate().record(f);
    }

    @Override
    public <T> T record(Callable<T> f) throws Exception {
        return delegate().recordCallable(f);
    }

    @Override
    public void record(Runnable f) {
        delegate().record(f);
    }

    @Override
    public Runnable wrap(Runnable f) {
        return delegate().wrap(f);
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> f) {
        return delegate().wrap(f);
    }

    @Override
    public <T> Supplier<T> wrap(Supplier<T> f) {
        return delegate().wrap(f);
    }

    @Override
    public long count() {
        return delegate().count();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return delegate().totalTime(unit);
    }

    @Override
    public double mean(TimeUnit unit) {
        return delegate().mean(unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return delegate().max(unit);
    }

    static class Builder extends MMeter.Builder<Timer.Builder, MTimer.Builder, MTimer>
    implements io.helidon.metrics.api.Timer.Builder {

        private Builder(String name) {
            super(name, Timer.builder(name));
        }

        @Override
        public io.helidon.metrics.api.Timer.Builder distributionStatisticsConfig(
                DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder) {
            io.helidon.metrics.api.DistributionStatisticsConfig config = distributionStatisticsConfigBuilder.build();
            Timer.Builder delegate = delegate();

            config.percentiles().ifPresent(p -> delegate.publishPercentiles(Util.doubleArray(p)));
            config.percentilePrecision().ifPresent(delegate::percentilePrecision);
            config.isPercentileHistogram().ifPresent(delegate::publishPercentileHistogram);
            config.serviceLevelObjectiveBoundaries().ifPresent(slos -> delegate.serviceLevelObjectives(Util.doubleArray(slos)));
            config.minimumExpectedValue().ifPresent(delegate::minimumExpectedValue);
            config.maximumExpectedValue().ifPresent(delegate::maximumExpectedValue);
            config.expiry().ifPresent(delegate::distributionStatisticExpiry);
            config.bufferLength().ifPresent(delegate::distributionStatisticBufferLength);

            return identity();
            return null;
        }

        @Override
        MTimer register(MeterRegistry meterRegistry) {
            return MTimer.of(delegate().register(meterRegistry));
        }
    }
}
