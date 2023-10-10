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
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.helidon.common.HelidonServiceLoader;
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
import io.helidon.metrics.providers.micrometer.spi.SpanContextSupplierProvider;
import io.helidon.metrics.spi.MeterRegistryLifeCycleListener;
import io.helidon.metrics.spi.MetersProvider;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exemplars.DefaultExemplarSampler;

/**
 * Implementation of the neutral Helidon metrics factory based on Micrometer.
 */
class MicrometerMetricsFactory implements MetricsFactory {

    private final MetricsConfig metricsConfig;
    private final Collection<MetersProvider> metersProviders;

    private final Collection<MMeterRegistry> meterRegistries = new ConcurrentLinkedQueue<>();
    private final ReentrantLock lock = new ReentrantLock();

    private final LazyValue<Collection<MeterRegistryLifeCycleListener>> meterRegistryLifeCycleListeners =
            LazyValue.create(() -> HelidonServiceLoader.create(ServiceLoader.load(MeterRegistryLifeCycleListener.class))
                    .asList());

    private final LazyValue<SpanContextSupplierProvider> spanContextSupplierProvider =
            LazyValue.create(() -> HelidonServiceLoader.builder(ServiceLoader.load(SpanContextSupplierProvider.class))
                    .addService(new NoOpSpanContextSupplierProvider(), Double.MIN_VALUE)
                    .build()
                    .iterator()
                    .next());

    private MMeterRegistry globalMeterRegistry;

    private MicrometerMetricsFactory(MetricsConfig metricsConfig,
                                     Collection<MetersProvider> metersProviders) {
        this.metricsConfig = metricsConfig;
        this.metersProviders = metersProviders;
    }

    static MicrometerMetricsFactory create(Config rootConfig,
                                           MetricsConfig metricsConfig,
                                           Collection<MetersProvider> metersProviders) {

        return new MicrometerMetricsFactory(metricsConfig, metersProviders);
    }

    void onMeterAdded(io.micrometer.core.instrument.Meter meter) {
        meterRegistries.forEach(mr -> mr.onMeterAdded(meter));
    }

    void onMeterRemoved(io.micrometer.core.instrument.Meter meter) {
        meterRegistries.forEach(mr -> mr.onMeterRemoved(meter));
    }

    @Override
    public MeterRegistry globalRegistry(Consumer<Meter> onAddListener, Consumer<Meter> onRemoveListener, boolean backfill) {
        globalMeterRegistry.onMeterAdded(onAddListener);
        globalMeterRegistry.onMeterRemoved(onRemoveListener);

        if (backfill) {
            globalMeterRegistry.meters().forEach(onAddListener);
        }
        return globalMeterRegistry;
    }

    @Override
    public MMeterRegistry.Builder meterRegistryBuilder() {
        return MMeterRegistry.builder(Metrics.globalRegistry, this);
    }

    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig) {
        return save(MMeterRegistry.builder(Metrics.globalRegistry, this)
                            .metricsConfig(metricsConfig)
                            .build());
    }

    @SuppressWarnings("unchecked")
    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig,
                                             Consumer<Meter> onAddListener,
                                             Consumer<Meter> onRemoveListener) {
        return save(MMeterRegistry.builder(Metrics.globalRegistry,
                                           this)
                            .metricsConfig(metricsConfig)
                            .onMeterAdded(onAddListener)
                            .onMeterRemoved(onRemoveListener)
                            .build());
    }

    @SuppressWarnings("unchecked")
    @Override
    public MeterRegistry createMeterRegistry(Clock clock,
                                             MetricsConfig metricsConfig,
                                             Consumer<Meter> onAddListener,
                                             Consumer<Meter> onRemoveListener) {

        return save(MMeterRegistry.builder(Metrics.globalRegistry,
                                           this)
                            .metricsConfig(metricsConfig)
                            .clock(clock)
                            .onMeterAdded(onAddListener)
                            .onMeterRemoved(onRemoveListener)
                            .build());
    }

    @Override
    public MeterRegistry createMeterRegistry(Clock clock, MetricsConfig metricsConfig) {
        return save(MMeterRegistry.builder(Metrics.globalRegistry, this)
                            .clock(clock)
                            .metricsConfig(metricsConfig)
                            .build());
    }

    @Override
    public MeterRegistry globalRegistry() {
        return globalMeterRegistry != null
                ? globalMeterRegistry
                : globalRegistry(MetricsConfig.create());
    }

    @Override
    public MeterRegistry globalRegistry(MetricsConfig metricsConfig) {
        lock.lock();
        try {
            if (globalMeterRegistry != null) {
                globalMeterRegistry.close();
                meterRegistries.remove(globalMeterRegistry);
            }
            ensurePrometheusRegistry(Metrics.globalRegistry, metricsConfig);
            globalMeterRegistry = save(MMeterRegistry.builder(Metrics.globalRegistry, this)
                                               .metricsConfig(metricsConfig)
                                               .build());

            /*
             Let listeners enroll their callbacks for meter creation and removal with the new registry if they want before
             we apply any meters providers. This way the listeners get to learn of the meters which the registry creates from
             the builders.
             */
            notifyListenersOfCreate(globalMeterRegistry, metricsConfig);

            applyMetersProvidersToRegistry(this, globalMeterRegistry, metersProviders);

            return globalMeterRegistry;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        List<MMeterRegistry> registries = List.copyOf(meterRegistries);
        registries.forEach(MMeterRegistry::close);
        meterRegistries.clear();
        globalMeterRegistry = null;
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

    private void ensurePrometheusRegistry(CompositeMeterRegistry compositeMeterRegistry,
                                          MetricsConfig metricsConfig) {
        if (compositeMeterRegistry
                .getRegistries()
                .stream()
                .noneMatch(mr -> mr instanceof PrometheusMeterRegistry)) {
            // If we have a non-no-op span context supplier provider we have to create the prometheus meter registry with
            // some extra constructor arguments so that we can also pass the exemplar sampler with the span context supplier.
            SpanContextSupplierProvider provider = spanContextSupplierProvider.get();
            PrometheusMeterRegistry prometheusMeterRegistry = provider instanceof NoOpSpanContextSupplierProvider
                    ? new PrometheusMeterRegistry(key -> metricsConfig.lookupConfig(key).orElse(null))
                    : new PrometheusMeterRegistry(key -> metricsConfig.lookupConfig(key).orElse(null),
                                                  new CollectorRegistry(),
                                                  io.micrometer.core.instrument.Clock.SYSTEM,
                                                  new DefaultExemplarSampler(provider.get()));
            compositeMeterRegistry.add(prometheusMeterRegistry);
        }
    }

    private void notifyListenersOfCreate(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        meterRegistryLifeCycleListeners.get().forEach(listener -> listener.onCreate(meterRegistry, metricsConfig));
    }

    @SuppressWarnings("unchecked")
    private <B extends Meter.Builder<B, M>, M extends Meter> MeterRegistry applyMetersProvidersToRegistry(
            MetricsFactory factory,
            MeterRegistry registry,
            Collection<MetersProvider> metersProviders) {
        metersProviders.stream()
                .flatMap(mp -> mp.meterBuilders(factory).stream())
                .forEach(b -> registry.getOrCreate((B) b));

        return registry;
    }

    private MMeterRegistry save(MMeterRegistry meterRegistry) {
        meterRegistries.add(meterRegistry);
        return meterRegistry;
    }
}
