/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
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
import io.helidon.metrics.api.ScopingConfig;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.providers.micrometer.ConfiguredPrometheusMeterRegistryProvider.ConfiguredPrometheusMeterRegistry;
import io.helidon.metrics.providers.micrometer.spi.SpanContextSupplierProvider;
import io.helidon.metrics.spi.MeterRegistryLifeCycleListener;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.metrics.spi.MetricsProgrammaticConfig;
import io.helidon.service.registry.Services;

import io.micrometer.core.instrument.Metrics;

/**
 * Implementation of the neutral Helidon metrics factory based on Micrometer.
 */
class MicrometerMetricsFactory implements MetricsFactory {

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
    private MetricsConfig metricsConfig;

    private MicrometerMetricsFactory(MetricsConfig metricsConfig,
                                     Collection<MetersProvider> metersProviders) {
        this.metricsConfig = metricsConfig;
        this.metersProviders = metersProviders;
    }

    private MicrometerMetricsFactory(Config metricsNode, Collection<MetersProvider> metersProviders) {
        this(prepareMetricsConfig(metricsNode), metersProviders);
    }

    @Deprecated(since = "4.4.0", forRemoval = true)
    static MicrometerMetricsFactory create(io.helidon.common.config.Config rootConfig,
                                           MetricsConfig metricsConfig,
                                           Collection<MetersProvider> metersProviders) {

        return new MicrometerMetricsFactory(metricsConfig, metersProviders);
    }

    static MicrometerMetricsFactory create(io.helidon.common.config.Config metricsNode,
                                           Collection<MetersProvider> metersProviders) {

        return new MicrometerMetricsFactory(metricsNode, metersProviders);
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
        return save(metricsConfig, MMeterRegistry.builder(Metrics.globalRegistry, this)
                .metricsConfig(metricsConfig)
                .build());
    }

    @SuppressWarnings("unchecked")
    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig,
                                             Consumer<Meter> onAddListener,
                                             Consumer<Meter> onRemoveListener) {
        return save(metricsConfig, MMeterRegistry.builder(Metrics.globalRegistry,
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

        return save(metricsConfig, MMeterRegistry.builder(Metrics.globalRegistry,
                                                          this)
                .metricsConfig(metricsConfig)
                .clock(clock)
                .onMeterAdded(onAddListener)
                .onMeterRemoved(onRemoveListener)
                .build());
    }

    @Override
    public MeterRegistry createMeterRegistry(Clock clock, MetricsConfig metricsConfig) {
        return save(metricsConfig, MMeterRegistry.builder(Metrics.globalRegistry, this)
                .clock(clock)
                .metricsConfig(metricsConfig)
                .build());
    }

    @Override
    public MeterRegistry globalRegistry() {
        return globalMeterRegistry != null
                ? globalMeterRegistry
                : globalRegistry(MicrometerMetricsConfig.create(Services.get(Config.class)
                                                                        .get(MetricsConfig.METRICS_CONFIG_KEY)));
    }

    @Override
    public MeterRegistry globalRegistry(MetricsConfig metricsConfig) {
        lock.lock();
        try {
            List<Meter> previouslyRegisteredMeters = null;
            List<Consumer<Meter>> previousOnAddListeners = null;
            List<Consumer<Meter>> previousOnRemoveListeners = null;

            if (globalMeterRegistry != null) {
                if (metricsConfig.equals(this.metricsConfig)) {
                    return globalMeterRegistry;
                }
                // Ideally this method will be invoked once with the proper MetricsConfig settings.
                // But it's possible for it to be invoked more than once with different
                // settings. In such a case we need to clear the old global registry and create a new one because
                // the new settings might affect its behavior.
                previouslyRegisteredMeters = globalMeterRegistry.meters();
                previousOnAddListeners = globalMeterRegistry.onMeterAddedListeners();
                previousOnRemoveListeners = globalMeterRegistry.onMeterRemovedListeners();

                globalMeterRegistry.close();
                meterRegistries.remove(globalMeterRegistry);

            }
            if (metricsConfig instanceof MicrometerMetricsConfig micrometerMetricsConfig) {

                var existingRegistries = Set.copyOf(Metrics.globalRegistry.getRegistries());
                existingRegistries.forEach(Metrics.globalRegistry::remove);

                micrometerMetricsConfig.registries().stream()
                        .map(reg -> reg instanceof ConfiguredPrometheusMeterRegistry prometheusReg
                                ? prometheusRegistryForExemplarAsNeeded(prometheusReg)
                                : reg.meterRegistrySupplier().get())
                        .forEach(Metrics.globalRegistry::add);
            }

            globalMeterRegistry = save(metricsConfig, MMeterRegistry.builder(Metrics.globalRegistry, this)
                    .metricsConfig(metricsConfig)
                    .build());

            /*
             Let listeners enroll their callbacks for meter creation and removal with the new registry if they want before
             we apply any meters providers. This way the listeners get to learn of the meters which the registry creates from
             the builders.
             */
            notifyListenersOfCreate(globalMeterRegistry, metricsConfig);

            if (previouslyRegisteredMeters != null) {
                globalMeterRegistry.merge(previouslyRegisteredMeters, previousOnAddListeners, previousOnRemoveListeners);
            } else {
                applyMetersProvidersToRegistry(this, globalMeterRegistry, metersProviders);
            }

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
    public MetricsConfig defaultMetricsConfig() {
        return MicrometerMetricsConfig.create();
    }

    @Override
    public MetricsConfig metricsConfig(Config config) {
        return MicrometerMetricsConfig.create(config);
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
        return MDistributionStatisticsConfig.Unconnected.builder();
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

    private static MicrometerMetricsConfig prepareMetricsConfig(Config metricsNode) {
        var builder = MicrometerMetricsConfig.builder()
                .config(metricsNode);
        MetricsProgrammaticConfig.apply(target(builder));

        var result = builder.build();
        SystemTagsManager.instance(result);
        return result;
    }

    private static MetricsProgrammaticConfig.Target target(MicrometerMetricsConfig.Builder builder) {
        return new MetricsProgrammaticConfig.Target() {
            @Override
            public Optional<ScopingConfig> scoping() {
                return builder.scoping();
            }

            @Override
            public void appTagName(String appTagName) {
                builder.appTagName(appTagName);
            }

            @Override
            public void scoping(ScopingConfig.Builder scopingBuilder) {
                builder.scoping(scopingBuilder);
            }
        };
    }

    private io.micrometer.core.instrument.MeterRegistry prometheusRegistryForExemplarAsNeeded(
            ConfiguredPrometheusMeterRegistry configuredPrometheusRegistry) {
        SpanContextSupplierProvider provider = spanContextSupplierProvider.get();
        return provider instanceof NoOpSpanContextSupplierProvider
                ? configuredPrometheusRegistry.meterRegistrySupplier().get()
                : configuredPrometheusRegistry.meterRegistryFunction().apply(provider.get());
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

    private MMeterRegistry save(MetricsConfig metricsConfig, MMeterRegistry meterRegistry) {
        this.metricsConfig = metricsConfig;
        meterRegistries.add(meterRegistry);
        return meterRegistry;
    }
}
