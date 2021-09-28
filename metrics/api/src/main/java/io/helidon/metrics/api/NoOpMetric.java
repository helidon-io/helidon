/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.util.concurrent.TimeUnit;
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
class NoOpMetric extends AbstractMetric {

    protected NoOpMetric(String registryType, Metadata metadata) {
        super(registryType, metadata);
    }

    static class NoOpCounter extends NoOpMetric implements Counter {

        static NoOpCounter create(String registryType, Metadata metadata) {
            return new NoOpCounter(registryType, metadata);
        }

        private NoOpCounter(String registryType, Metadata metadata) {
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

    static class NoOpConcurrentGauge extends NoOpMetric implements ConcurrentGauge {

        static NoOpConcurrentGauge create(String registryType, Metadata metadata) {
            return new NoOpConcurrentGauge(registryType, metadata);
        }

        private NoOpConcurrentGauge(String registryType, Metadata metadata) {
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

    static class NoOpGauge<T /* extends Number */> extends NoOpMetric implements Gauge<T> {
        // TODO uncomment above once MP metrics enforces the Number restriction

        private final Supplier<T> value;

        static <S /* extends Number */> NoOpGauge<S> create(String registryType, Metadata metadata, Gauge<S> metric) {
            // TODO uncomment above once MP metrics enforces the Number restriction
            return new NoOpGauge<>(registryType, metadata, metric);
        }

        private NoOpGauge(String registryType, Metadata metadata, Gauge<T> metric) {
            super(registryType, metadata);
            value = metric::getValue;
        }

        @Override
        public T getValue() {
            return value.get();
        }
    }

    static class NoOpHistogram extends NoOpMetric implements Histogram {

        static NoOpHistogram create(String registryType, Metadata metadata) {
            return new NoOpHistogram(registryType, metadata);
        }

        private NoOpHistogram(String registryType, Metadata metadata) {
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

    static class NoOpMeter extends NoOpMetric implements Meter {

        static NoOpMeter create(String registryType, Metadata metadata) {
            return new NoOpMeter(registryType, metadata);
        }

        private NoOpMeter(String registryType, Metadata metadata) {
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

    static class NoOpTimer extends NoOpMetric implements Timer {

        static class Context implements Timer.Context {
            @Override
            public long stop() {
                return 0;
            }

            @Override
            public void close() {
            }
        }

        static NoOpTimer create(String registryType, Metadata metadata) {
            return new NoOpTimer(registryType, metadata);
        }

        private NoOpTimer(String registryType, Metadata metadata) {
            super(registryType, metadata);
        }

        @Override
        public void update(long duration, TimeUnit unit) {
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
        return new NoOpTimer.Context() {
        };
    }


    static class NoOpSimpleTimer extends NoOpMetric implements SimpleTimer {

        static class Context implements SimpleTimer.Context {
            @Override
            public long stop() {
                return 0;
            }

            @Override
            public void close() {
            }
        }

        static NoOpSimpleTimer create(String registryType, Metadata metadata) {
            return new NoOpSimpleTimer(registryType, metadata);
        }

        private NoOpSimpleTimer(String registryType, Metadata metadata) {
            super(registryType, metadata);
        }

        @Override
        public void update(Duration duration) {
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
        return new NoOpSimpleTimer.Context();
    }
}
