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
package io.helidon.metrics.api;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * No-op implementation of the {@link io.helidon.metrics.api.MetricsFactory} interface.
 */
class NoOpMetricsFactory implements MetricsFactory {

    private static final Clock SYSTEM_CLOCK = new Clock() {
        @Override
        public <R> R unwrap(Class<? extends R> c) {
            return c.cast(this);
        }

        @Override
        public long wallTime() {
            return System.currentTimeMillis();
        }

        @Override
        public long monotonicTime() {
            return System.nanoTime();
        }
    };
    private final MeterRegistry meterRegistry = new NoOpMeterRegistry();
    private final MetricsConfig metricsConfig = MetricsConfig.create();

    static NoOpMetricsFactory create() {
        return new NoOpMetricsFactory();
    }

    @Override
    public void close() {
    }

    @Override
    public MeterRegistry globalRegistry() {
        return meterRegistry;
    }

    @Override
    public MeterRegistry globalRegistry(MetricsConfig metricsConfig) {
        return meterRegistry;
    }

    @Override
    public MeterRegistry globalRegistry(Consumer<Meter> onAddListener, Consumer<Meter> onRemoveListener, boolean backfill) {
        return meterRegistry;
    }

    @Override
    public MetricsConfig metricsConfig() {
        return metricsConfig;
    }

    @Override
    public NoOpMeterRegistry.Builder meterRegistryBuilder() {
        return NoOpMeterRegistry.builder();
    }

    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig) {
        return new NoOpMeterRegistry();
    }

    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig,
                                             Consumer<Meter> onAddListener,
                                             Consumer<Meter> onRemoveListener) {
        return createMeterRegistry(metricsConfig)
                .onMeterAdded(onAddListener)
                .onMeterRemoved(onRemoveListener);
    }

    @Override
    public MeterRegistry createMeterRegistry(Clock clock, MetricsConfig metricsConfig) {
        return createMeterRegistry(metricsConfig);
    }

    @Override
    public MeterRegistry createMeterRegistry(Clock clock,
                                             MetricsConfig metricsConfig,
                                             Consumer<Meter> onAddListener,
                                             Consumer<Meter> onRemoveListener) {
        return createMeterRegistry(clock, metricsConfig)
                .onMeterAdded(onAddListener)
                .onMeterRemoved(onRemoveListener);
    }

    @Override
    public Clock clockSystem() {
        return SYSTEM_CLOCK;
    }

    @Override
    public Tag tagCreate(String key, String value) {
        return new NoOpTag(key, value);
    }

    @Override
    public Counter.Builder counterBuilder(String name) {
        return NoOpMeter.Counter.builder(name);
    }

    @Override
    public <T> FunctionalCounter.Builder<T> functionalCounterBuilder(String name,
                                                                     T stateObject,
                                                                     Function<T, Long> fn) {
        return NoOpMeter.FunctionalCounter.builder(name, stateObject, fn);
    }

    @Override
    public DistributionSummary.Builder distributionSummaryBuilder(String name,
                                                                  DistributionStatisticsConfig.Builder configBuilder) {
        return NoOpMeter.DistributionSummary.builder(name)
                .distributionStatisticsConfig(configBuilder);
    }

    @Override
    public <T> Gauge.Builder<Double> gaugeBuilder(String name, T stateObject, ToDoubleFunction<T> fn) {
        return NoOpMeter.Gauge.builder(name, stateObject, fn);
    }

    @Override
    public <N extends Number> Gauge.Builder<N> gaugeBuilder(String name, Supplier<N> supplier) {
        return NoOpMeter.Gauge.builder(name, supplier);
    }

    @Override
    public Timer.Builder timerBuilder(String name) {
        return NoOpMeter.Timer.builder(name);
    }

    @Override
    public DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder() {
        return NoOpMeter.DistributionStatisticsConfig.builder();
    }

    @Override
    public HistogramSnapshot histogramSnapshotEmpty(long count, double total, double max) {
        return NoOpMeter.HistogramSnapshot.empty(count, total, max);
    }

    @Override
    public Timer.Sample timerStart() {
        return NoOpMeter.Timer.start();
    }

    @Override
    public Timer.Sample timerStart(MeterRegistry registry) {
        return NoOpMeter.Timer.start(registry);
    }

    @Override
    public Timer.Sample timerStart(Clock clock) {
        return NoOpMeter.Timer.start(clock);
    }
}
