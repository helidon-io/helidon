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

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.spi.MetersProvider;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Implementation of the neutral Helidon metrics factory based on Micrometer.
 */
class MicrometerMetricsFactory implements MetricsFactory {

    private final LazyValue<MeterRegistry> globalMeterRegistry;
    private final MetricsConfig metricsConfig;
    private final Collection<MetersProvider> metersProviders;

    private MicrometerMetricsFactory(MetricsConfig metricsConfig,
                                     Collection<MetersProvider> metersProviders) {
        this.metricsConfig = metricsConfig;
        this.metersProviders = metersProviders;

        globalMeterRegistry = LazyValue.create(() -> {
            ensurePrometheusRegistry(Metrics.globalRegistry, metricsConfig);
            MMeterRegistry reg = MMeterRegistry.builder(Metrics.globalRegistry, this).build();
            return MMeterRegistry.applyMetersProvidersToRegistry(this, reg, metersProviders);
        });
    }

    static MicrometerMetricsFactory create(Config rootConfig,
                                           MetricsConfig metricsConfig,
                                           Collection<MetersProvider> metersProviders) {

        return new MicrometerMetricsFactory(metricsConfig, metersProviders);
    }

    @Override
    public MeterRegistry globalRegistry(Consumer<Meter> onAddListener, Consumer<Meter> onRemoveListener, boolean backfill) {
        MeterRegistry result = globalMeterRegistry.get();
        result.onMeterAdded(onAddListener);
        result.onMeterRemoved(onRemoveListener);

        if (backfill) {
            result.meters().forEach(onAddListener);
        }
        return result;
    }

    @Override
    public MMeterRegistry.Builder meterRegistryBuilder() {
        return MMeterRegistry.builder(Metrics.globalRegistry, this);
    }

    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig) {
        return MMeterRegistry.builder(Metrics.globalRegistry, this)
                .metricsConfig(metricsConfig)
                .build();
    }

    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig,
                                             Consumer<Meter> onAddListener,
                                             Consumer<Meter> onRemoveListener) {
        return MMeterRegistry.builder(Metrics.globalRegistry,
                                      this)
                .metricsConfig(metricsConfig)
                .onMeterAdded(onAddListener)
                .onMeterRemoved(onRemoveListener)
                .build();
    }

    @Override
    public MeterRegistry createMeterRegistry(Clock clock,
                                             MetricsConfig metricsConfig,
                                             Consumer<Meter> onAddListener,
                                             Consumer<Meter> onRemoveListener) {

        return MMeterRegistry.builder(Metrics.globalRegistry,
                                     this)
                .metricsConfig(metricsConfig)
                .clock(clock)
                .onMeterAdded(onAddListener)
                .onMeterRemoved(onRemoveListener)
                .build();
    }

    @Override
    public MeterRegistry createMeterRegistry(Clock clock, MetricsConfig metricsConfig) {
        return MMeterRegistry.builder(Metrics.globalRegistry, this)
                .clock(clock)
                .metricsConfig(metricsConfig)
                .build();
    }

    @Override
    public MeterRegistry globalRegistry() {
        return globalMeterRegistry.get();
    }

    @Override
    public MetricsConfig metricsConfig() {
        return metricsConfig;
    }

    @Override
    public Clock clockSystem() {
        return new Clock() {

            private final io.micrometer.core.instrument.Clock delegate = io.micrometer.core.instrument.Clock.SYSTEM;

            @Override
            public long wallTime() {
                return delegate.wallTime();
            }

            @Override
            public long monotonicTime() {
                return delegate.monotonicTime();
            }

            @Override
            public <R> R unwrap(Class<? extends R> c) {
                return c.cast(delegate);
            }
        };
    }

    @Override
    public Counter.Builder counterBuilder(String name) {
        return MCounter.builder(name);
    }

    @Override
    public <T> FunctionalCounter.Builder<T> functionalCounterBuilder(String name,
                                                                     T stateObject,
                                                                     Function<T, Long> fn) {
        return MFunctionalCounter.builder(name, stateObject, fn);
    }

    @Override
    public DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder() {
        return MDistributionStatisticsConfig.builder();
    }

    @Override
    public DistributionSummary.Builder distributionSummaryBuilder(String name,
                                                                  DistributionStatisticsConfig.Builder configBuilder) {
        return MDistributionSummary.builder(name, configBuilder);
    }

    @Override
    public <N extends Number> Gauge.Builder<N> gaugeBuilder(String name, Supplier<N> supplier) {
        return MGauge.builder(name, supplier);
    }

    @Override
    public <T> Gauge.Builder<Double> gaugeBuilder(String name, T stateObject, ToDoubleFunction<T> fn) {
        return MGauge.builder(name, stateObject, fn);
    }

    @Override
    public Timer.Builder timerBuilder(String name) {
        return MTimer.builder(name);
    }

    @Override
    public Timer.Sample timerStart() {
        return MTimer.start();
    }

    @Override
    public Timer.Sample timerStart(MeterRegistry registry) {
        return MTimer.start(registry);
    }

    @Override
    public Timer.Sample timerStart(Clock clock) {
        return MTimer.start(clock);
    }

    @Override
    public Tag tagCreate(String key, String value) {
        return MTag.of(key, value);
    }

    @Override
    public HistogramSnapshot histogramSnapshotEmpty(long count, double total, double max) {
        return MHistogramSnapshot.create(io.micrometer.core.instrument.distribution.HistogramSnapshot.empty(count, total, max));
    }

    private static void ensurePrometheusRegistry(CompositeMeterRegistry compositeMeterRegistry,
                                                 MetricsConfig metricsConfig) {
        if (compositeMeterRegistry
                .getRegistries()
                .stream()
                .noneMatch(mr -> mr instanceof PrometheusMeterRegistry)) {
            compositeMeterRegistry.add(new PrometheusMeterRegistry(key -> metricsConfig.lookupConfig(key).orElse(null)));
        }
    }
}
