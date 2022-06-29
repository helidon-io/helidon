/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;

/**
 * No-op implementations of each metric type with factory methods for each.
 */
class NoOpMetricImpl extends AbstractMetric implements NoOpMetric {

    protected NoOpMetricImpl(String registryType, Metadata metadata) {
        super(registryType, metadata);
    }

    static class NoOpCounterImpl extends NoOpMetricImpl implements Counter {

        static NoOpCounterImpl create(String registryType, Metadata metadata) {
            return new NoOpCounterImpl(registryType, metadata);
        }

        static NoOpCounterImpl create(String registryType, Metadata metadata, Counter counter) {
            return new NoOpCounterImpl(registryType, metadata);
        }

        private NoOpCounterImpl(String registryType, Metadata metadata) {
            super(registryType, metadata);
        }

        @Override
        public void inc() {
        }

        @Override
        public void inc(long n) {
        }

        @Override
        public long getCount() {
            return 0;
        }
    }

    static class NoOpConcurrentGaugeImpl extends NoOpMetricImpl implements ConcurrentGauge {

        static NoOpConcurrentGaugeImpl create(String registryType, Metadata metadata) {
            return new NoOpConcurrentGaugeImpl(registryType, metadata);
        }

        private NoOpConcurrentGaugeImpl(String registryType, Metadata metadata) {
            super(registryType, metadata);
        }

        @Override
        public long getCount() {
            return 0;
        }

        @Override
        public long getMax() {
            return 0;
        }

        @Override
        public long getMin() {
            return 0;
        }

        @Override
        public void inc() {
        }

        @Override
        public void dec() {
        }
    }

    static class NoOpGaugeImpl<T /* extends Number */> extends NoOpMetricImpl implements Gauge<T> {
        // TODO uncomment above once MP metrics enforces the Number restriction

        private final Supplier<T> value;

        static <S /* extends Number */> NoOpGaugeImpl<S> create(String registryType, Metadata metadata, Gauge<S> metric) {
            // TODO uncomment above once MP metrics enforces the Number restriction
            return new NoOpGaugeImpl<>(registryType, metadata, metric);
        }

        static <S /* extends Number */> NoOpGaugeImpl<S> create(String registryType, Metadata metadata) {
            // TODO uncomment above once MP metrics enforces the Number restriction
            return new NoOpGaugeImpl<>(registryType, metadata, () -> null);
        }

        private NoOpGaugeImpl(String registryType, Metadata metadata, Gauge<T> metric) {
            super(registryType, metadata);
            value = metric::getValue;
        }

        @Override
        public T getValue() {
            return value.get();
        }
    }

    static class NoOpHistogramImpl extends NoOpMetricImpl implements Histogram {

        static NoOpHistogramImpl create(String registryType, Metadata metadata) {
            return new NoOpHistogramImpl(registryType, metadata);
        }

        private NoOpHistogramImpl(String registryType, Metadata metadata) {
            super(registryType, metadata);
        }

        @Override
        public void update(int value) {
        }

        @Override
        public void update(long value) {
        }

        @Override
        public long getCount() {
            return 0;
        }

        @Override
        public Snapshot getSnapshot() {
            return snapshot();
        }

        @Override
        public long getSum() {
            return 0;
        }
    }

    static class NoOpSnapshot extends Snapshot {
        @Override
        public double getValue(double quantile) {
            return 0;
        }

        @Override
        public long[] getValues() {
            return new long[0];
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public long getMax() {
            return 0;
        }

        @Override
        public double getMean() {
            return 0;
        }

        @Override
        public long getMin() {
            return 0;
        }

        @Override
        public double getStdDev() {
            return 0;
        }

        @Override
        public void dump(OutputStream output) {
        }
    }

    static Snapshot snapshot() {
        return new NoOpSnapshot();
    }

    static class NoOpMeterImpl extends NoOpMetricImpl implements Meter {

        static NoOpMeterImpl create(String registryType, Metadata metadata) {
            return new NoOpMeterImpl(registryType, metadata);
        }

        private NoOpMeterImpl(String registryType, Metadata metadata) {
            super(registryType, metadata);
        }

        @Override
        public void mark() {
        }

        @Override
        public void mark(long n) {
        }

        @Override
        public long getCount() {
            return 0;
        }

        @Override
        public double getFifteenMinuteRate() {
            return 0;
        }

        @Override
        public double getFiveMinuteRate() {
            return 0;
        }

        @Override
        public double getMeanRate() {
            return 0;
        }

        @Override
        public double getOneMinuteRate() {
            return 0;
        }
    }

    static class NoOpTimerImpl extends NoOpMetricImpl implements Timer {

        static class Context implements Timer.Context {
            @Override
            public long stop() {
                return 0;
            }

            @Override
            public void close() {
            }
        }

        static NoOpTimerImpl create(String registryType, Metadata metadata) {
            return new NoOpTimerImpl(registryType, metadata);
        }

        private NoOpTimerImpl(String registryType, Metadata metadata) {
            super(registryType, metadata);
        }

        @Override
        public void update(Duration duration) {
        }

        @Override
        public Duration getElapsedTime() {
            return Duration.ZERO;
        }

        @Override
        public <T> T time(Callable<T> event) throws Exception {
            return event.call();
        }

        @Override
        public void time(Runnable event) {
            event.run();
        }

        @Override
        public Timer.Context time() {
            return timerContext();
        }

        @Override
        public long getCount() {
            return 0;
        }

        @Override
        public double getFifteenMinuteRate() {
            return 0;
        }

        @Override
        public double getFiveMinuteRate() {
            return 0;
        }

        @Override
        public double getMeanRate() {
            return 0;
        }

        @Override
        public double getOneMinuteRate() {
            return 0;
        }

        @Override
        public Snapshot getSnapshot() {
            return snapshot();
        }
    }

    private static Timer.Context timerContext() {
        return new NoOpTimerImpl.Context() {
        };
    }

    static class NoOpSimpleTimerImpl extends NoOpMetricImpl implements SimpleTimer {

        static class Context implements SimpleTimer.Context {
            @Override
            public long stop() {
                return 0;
            }

            @Override
            public void close() {
            }
        }

        static NoOpSimpleTimerImpl create(String registryType, Metadata metadata) {
            return new NoOpSimpleTimerImpl(registryType, metadata);
        }

        private NoOpSimpleTimerImpl(String registryType, Metadata metadata) {
            super(registryType, metadata);
        }

        @Override
        public void update(Duration duration) {
        }

        @Override
        public Duration getMaxTimeDuration() {
            return Duration.ZERO;
        }

        @Override
        public Duration getMinTimeDuration() {
            return Duration.ZERO;
        }

        @Override
        public <T> T time(Callable<T> event) throws Exception {
            return event.call();
        }

        @Override
        public void time(Runnable event) {
            event.run();
        }

        @Override
        public SimpleTimer.Context time() {
            return simpleTimerContext();
        }

        @Override
        public Duration getElapsedTime() {
            return null;
        }

        @Override
        public long getCount() {
            return 0;
        }
    }

    private static SimpleTimer.Context simpleTimerContext() {
        return new NoOpSimpleTimerImpl.Context();
    }
}
