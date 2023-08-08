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
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.metrics.api.HistogramSnapshot;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

class MTimer extends MMeter<Timer> implements io.helidon.metrics.api.Timer {

    static MTimer create(Timer timer) {
        return new MTimer(timer);
    }

    static io.helidon.metrics.api.Timer.Builder builder(String name) {
        return new Builder(name);
    }

    static Sample start() {
        return Sample.create(Timer.start());
    }

    static Sample start(io.helidon.metrics.api.MeterRegistry meterRegistry) {
        if (meterRegistry instanceof MeterRegistry mMeterRegistry) {
            return Sample.create(Timer.start(mMeterRegistry));
        }
        throw new IllegalArgumentException("Expected meter registry type " + MMeterRegistry.class.getName()
        + " but was " + meterRegistry.getClass().getName());
    }

    static Sample start(io.helidon.metrics.api.Clock clock) {
        if (clock instanceof MClock mClock) {
            return Sample.create(Timer.start(mClock.delegate()));
        }
        throw new IllegalArgumentException("Expected clock type " + MClock.class.getName()
        + " but was " + clock.getClass().getName());
    }

    static class Sample implements io.helidon.metrics.api.Timer.Sample {

        static Sample create(io.micrometer.core.instrument.Timer.Sample delegate) {
            return new Sample(delegate);
        }

        private final Timer.Sample delegate;

        private Sample(io.micrometer.core.instrument.Timer.Sample delegate) {
            this.delegate = delegate;
        }

        @Override
        public long stop(io.helidon.metrics.api.Timer timer) {
            if (timer instanceof MTimer mTimer) {
                return delegate.stop(mTimer.delegate());
            }
            throw new IllegalArgumentException("Expected timer type " + MTimer.class.getName()
            + " but was " + timer.getClass().getName());
        }
    }

    private MTimer(Timer delegate) {
        super(delegate);
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        return MHistogramSnapshot.create(delegate().takeSnapshot());
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
            super(Timer.builder(name));
        }

        @Override
        MTimer register(MeterRegistry meterRegistry) {
            return MTimer.create(delegate().register(meterRegistry));
        }

        @Override
        public Builder publishPercentiles(double... percentiles) {
            delegate().publishPercentiles(percentiles);
            return identity();
        }

        @Override
        public Builder percentilePrecision(Integer digitsOfPrecision) {
            delegate().percentilePrecision(digitsOfPrecision);
            return identity();
        }

        @Override
        public Builder publishPercentileHistogram() {
            delegate().publishPercentileHistogram();
            return identity();
        }

        @Override
        public Builder publishPercentileHistogram(Boolean enabled) {
            delegate().publishPercentileHistogram(enabled);
            return identity();
        }

        @Override
        public Builder serviceLevelObjectives(Duration... slos) {
            delegate().serviceLevelObjectives(slos);
            return identity();
        }

        @Override
        public Builder minimumExpectedValue(Duration min) {
            delegate().minimumExpectedValue(min);
            return identity();
        }

        @Override
        public Builder maximumExpectedValue(Duration max) {
            delegate().maximumExpectedValue(max);
            return identity();
        }

        @Override
        public Builder distributionStatisticExpiry(Duration expiry) {
            delegate().distributionStatisticExpiry(expiry);
            return identity();
        }

        @Override
        public Builder distributionStatisticBufferLength(Integer bufferLength) {
            delegate().distributionStatisticBufferLength(bufferLength);
            return identity();
        }
    }
}
