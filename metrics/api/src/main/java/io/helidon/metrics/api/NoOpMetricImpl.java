/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * No-op implementations of each metric type with factory methods for each.
 */
class NoOpMetricImpl extends AbstractMetric implements NoOpMetric {

    protected NoOpMetricImpl(String registryType, Metadata metadata) {
        super(registryType, metadata);
    }

    static class NoOpCounterImpl extends NoOpMetricImpl implements Counter {

        static NoOpCounterImpl create(String registryType, Metadata metadata, Tag... tags) {
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

    static class NoOpGaugeImpl<N extends Number, T> extends NoOpMetricImpl implements Gauge<N> {

        private final Supplier<N> supplier;
        private final T target;
        private final Function<T, N> fn;

        static <N extends Number> NoOpGaugeImpl<N, ?> create(String registryType,
                                                             Metadata metadata,
                                                             Supplier<N> supplier,
                                                             Tag... tags) {
            return new NoOpGaugeImpl<>(registryType, metadata, supplier);
        }

        static <N extends Number, T> NoOpGaugeImpl<N, T> create(String registryType,
                                                             Metadata metadata,
                                                             T target,
                                                             Function<T, N> fn,
                                                             Tag... tags) {
            return new NoOpGaugeImpl<>(registryType, metadata, target, fn);
        }

        static <T> NoOpGaugeImplToDoubleFn<T> create(String registryType,
                                                   Metadata metadata,
                                                   T target,
                                                   ToDoubleFunction<T> fn,
                                                   Tag... tags) {
            return new NoOpGaugeImplToDoubleFn<T>(registryType, metadata, target, fn, tags);
        }

        private NoOpGaugeImpl(String registryType, Metadata metadata, Supplier<N> supplier) {
            super(registryType, metadata);
            this.supplier = supplier;
            target = null;
            this.fn = null;
        }

        private NoOpGaugeImpl(String registryType, Metadata metadata, T target, Function<T, N> fn) {
            super(registryType, metadata);
            this.target = target;
            this.fn = fn;
            supplier = null;
        }



        @Override
        public N getValue() {
            return supplier != null
                    ? supplier.get()
                    : fn.apply(target);
        }
    }

    static class NoOpGaugeImplToDoubleFn<T> extends NoOpMetricImpl implements Gauge<Double> {

        private final T target;
        private final ToDoubleFunction<T> fn;

        private NoOpGaugeImplToDoubleFn(String registryType,
                              Metadata metadata,
                              T target,
                              ToDoubleFunction<T> fn,
                              Tag... tags) {
            super(registryType, metadata);
            this.target = target;
            this.fn = fn;
        }
        @Override
        public Double getValue() {
            return fn.applyAsDouble(target);
        }
    }

    static class NoOpHistogramImpl extends NoOpMetricImpl implements Histogram {

        static NoOpHistogramImpl create(String registryType, Metadata metadata, Tag... tags) {
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
        public long size() {
            return 0;
        }

        @Override
        public double getMax() {
            return 0;
        }

        @Override
        public double getMean() {
            return 0;
        }

        @Override
        public PercentileValue[] percentileValues() {
            return new PercentileValue[0];
        }

        @Override
        public void dump(OutputStream output) {
        }
    }

    static Snapshot snapshot() {
        return new NoOpSnapshot();
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

        static NoOpTimerImpl create(String registryType, Metadata metadata, Tag... tags) {
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
        public Snapshot getSnapshot() {
            return snapshot();
        }
    }

    static class NoOpFunctionalCounterImpl<T> extends NoOpMetricImpl implements Counter {

        static <T> NoOpFunctionalCounterImpl create(String registryType,
                                                Metadata metadata,
                                                T origin,
                                                ToDoubleFunction<T> function,
                                                Tag... tags) {
            return new NoOpFunctionalCounterImpl(registryType, metadata);
        }

        private NoOpFunctionalCounterImpl(String registryType, Metadata metadata) {
            super(registryType, metadata);
        }

        @Override
        public void inc() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void inc(long n) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getCount() {
            return 0;
        }
    }

    private static Timer.Context timerContext() {
        return new NoOpTimerImpl.Context() {
        };
    }
}
