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

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Timer;

import io.micrometer.core.instrument.Clock;

class MTimer extends MMeter<io.micrometer.core.instrument.Timer> implements io.helidon.metrics.api.Timer {

    private MTimer(Meter.Id id, io.micrometer.core.instrument.Timer delegate, Builder builder) {
        super(id, delegate, builder);
    }

    private MTimer(Meter.Id id, io.micrometer.core.instrument.Timer delegate) {
        super(id, delegate);
    }

    private MTimer(Meter.Id id, io.micrometer.core.instrument.Timer delegate, Optional<String> scope) {
        super(id, delegate, scope);
    }

    static MTimer create(Meter.Id id, io.micrometer.core.instrument.Timer timer) {
        return new MTimer(id, timer);
    }

    static Builder builder(String name) {
        return new Builder(name);
    }

    static Builder builderFrom(Timer.Builder tBuilder) {
        Builder builder = builder(tBuilder.name());

        return builder.from(tBuilder);
    }

    static MTimer create(Meter.Id id, io.micrometer.core.instrument.Timer delegate, Optional<String> scope) {
        return new MTimer(id, delegate, scope);
    }

    static Sample start() {
        return Sample.create(io.micrometer.core.instrument.Timer.start());
    }

    static Sample start(io.helidon.metrics.api.MeterRegistry meterRegistry) {
        if (meterRegistry instanceof MMeterRegistry mMeterRegistry) {
            return Sample.create(io.micrometer.core.instrument.Timer.start(mMeterRegistry.delegate()));
        }
        throw new IllegalArgumentException("Expected meter registry type " + MMeterRegistry.class.getName()
                                                   + " but was " + meterRegistry.getClass().getName());
    }

    static Sample start(io.helidon.metrics.api.Clock clock) {
        // This is a relatively infrequently-used method, so it is not overly costly
        // to create a new instance of Micrometer's Clock each invocation.
        return Sample.create(io.micrometer.core.instrument.Timer.start(new Clock() {
            @Override
            public long wallTime() {
                return clock.wallTime();
            }

            @Override
            public long monotonicTime() {
                return clock.monotonicTime();
            }
        }));
    }

    @Override
    public HistogramSnapshot snapshot() {
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

    @Override
    public String toString() {
        TimeUnit baseTimeUnit = delegate().baseTimeUnit();
        return stringJoiner()
                .add("count=" + delegate().count())
                .add("totalTime=" + Duration.of((long) delegate().totalTime(baseTimeUnit),
                                                baseTimeUnit.toChronoUnit()))
                .toString();
    }

    static class Sample implements io.helidon.metrics.api.Timer.Sample {

        private final io.micrometer.core.instrument.Timer.Sample delegate;

        private Sample(io.micrometer.core.instrument.Timer.Sample delegate) {
            this.delegate = delegate;
        }

        static Sample create(io.micrometer.core.instrument.Timer.Sample delegate) {
            return new Sample(delegate);
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

    static class Builder extends
                         MMeter.Builder<io.micrometer.core.instrument.Timer.Builder, io.micrometer.core.instrument.Timer,
                                 MTimer.Builder, MTimer>
            implements io.helidon.metrics.api.Timer.Builder {

        private double[] percentiles;
        private Duration[] buckets;
        private Duration min;
        private Duration max;

        private Builder(String name) {
            super(name, io.micrometer.core.instrument.Timer.builder(name));
            percentiles(MDistributionStatisticsConfig.Builder.DEFAULT_PERCENTILES);
        }

        @Override
        public Builder percentiles(double... percentiles) {
            this.percentiles = percentiles;
            delegate().publishPercentiles(percentiles);
            return identity();
        }

        @Override
        public Builder buckets(Duration... buckets) {
            this.buckets = buckets;
            delegate().serviceLevelObjectives(buckets);
            return identity();
        }

        @Override
        public Builder minimumExpectedValue(Duration min) {
            this.min = min;
            delegate().minimumExpectedValue(min);
            return identity();
        }

        @Override
        public Builder maximumExpectedValue(Duration max) {
            this.max = max;
            delegate().maximumExpectedValue(max);
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
            // The Micrometer Timer does not have baseUnit (it's fixed at ns) but for uniformity we implement this anyway.
            return identity();
        }

        @Override
        public Timer.Builder publishPercentileHistogram(boolean value) {
            delegate().publishPercentileHistogram(value);
            return identity();
        }

        @Override
        public Iterable<Double> percentiles() {
            return Util.iterable(percentiles);
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

        @Override
        protected MTimer build(Meter.Id id, io.micrometer.core.instrument.Timer meter) {
            return new MTimer(id, meter, this);
        }

        @Override
        protected Class<? extends Meter> meterType() {
            return Timer.class;
        }

        Builder from(Timer.Builder other) {
            percentiles = iterToArray(other.percentiles());
            buckets = StreamSupport.stream(other.buckets().spliterator(), false).toList().toArray(new Duration[0]);
            other.maximumExpectedValue().ifPresent(this::maximumExpectedValue);
            other.minimumExpectedValue().ifPresent(this::minimumExpectedValue);
            return super.from(other);
        }

        private static double[] iterToArray(Iterable<Double> iter) {
            List<Double> doubles = StreamSupport.stream(iter.spliterator(), false).toList();
            double[] d = new double[doubles.size()];
            for (int i = 0; i < doubles.size(); i++) {
                d[i] = doubles.get(i);
            }
            return d;
        }


    }
}
