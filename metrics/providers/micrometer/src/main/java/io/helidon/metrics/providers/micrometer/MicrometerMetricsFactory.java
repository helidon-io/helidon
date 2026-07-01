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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
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
import io.helidon.metrics.api.MetricsPublisher;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.providers.micrometer.spi.SpanContextSupplierProvider;
import io.helidon.metrics.spi.MeterRegistryLifeCycleListener;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.service.registry.Services;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exemplars.DefaultExemplarSampler;

/**
 * Implementation of the neutral Helidon metrics factory based on Micrometer.
 */
class MicrometerMetricsFactory implements MetricsFactory {

    private static final System.Logger LOGGER = System.getLogger(MicrometerMetricsFactory.class.getName());
    private static final System.Logger MMETER_REGISTRY_LOGGER = System.getLogger(MMeterRegistry.class.getName());

    private final Collection<MetersProvider> metersProviders;

    private final Collection<MMeterRegistry> meterRegistries = new ConcurrentLinkedQueue<>();
    private final Collection<io.micrometer.core.instrument.MeterRegistry> publisherRegistries =
            new ConcurrentLinkedQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock globalRegistryLock = new ReentrantLock();
    private final MultipleRegistryWarnings multipleRegistryWarnings = new MultipleRegistryWarnings();
    private final ThreadLocal<Integer> globalOperationDepth = ThreadLocal.withInitial(() -> 0);
    private final Condition lifecycleChanged = lock.newCondition();

    private volatile Map<String, String> globalRegistrySystemTags = Map.of();

    private final LazyValue<Collection<MeterRegistryLifeCycleListener>> meterRegistryLifeCycleListeners =
            LazyValue.create(() -> HelidonServiceLoader.create(ServiceLoader.load(MeterRegistryLifeCycleListener.class))
                    .asList());

    private final LazyValue<SpanContextSupplierProvider> spanContextSupplierProvider =
            LazyValue.create(() -> HelidonServiceLoader.builder(ServiceLoader.load(SpanContextSupplierProvider.class))
                    .addService(new NoOpSpanContextSupplierProvider(), Double.MIN_VALUE)
                    .build()
                    .iterator()
                    .next());
    private final Consumer<MicrometerMetricsFactory> onClose;

    private volatile MMeterRegistry globalMeterRegistry;
    private MetricsConfig metricsConfig;
    private boolean closed;
    private int activeGlobalOperations;
    private boolean closeCleanupStarted;
    private boolean closeComplete;
    private Thread closeCleanupThread;

    private MicrometerMetricsFactory(MetricsConfig metricsConfig,
                                     Collection<MetersProvider> metersProviders,
                                     Consumer<MicrometerMetricsFactory> onClose) {
        this.metricsConfig = metricsConfig;
        this.metersProviders = metersProviders;
        this.onClose = onClose;
    }

    static MicrometerMetricsFactory create(MetricsConfig metricsConfig,
                                           Collection<MetersProvider> metersProviders,
                                           Consumer<MicrometerMetricsFactory> onClose) {
        return new MicrometerMetricsFactory(metricsConfig, metersProviders, onClose);
    }

    Map<String, String> globalRegistrySystemTags() {
        return globalRegistrySystemTags;
    }

    boolean hasGlobalRegistry() {
        return globalMeterRegistry != null;
    }

    // Intended for testing lifecycle cleanup.
    int meterRegistryCount() {
        return meterRegistries.size();
    }

    boolean tracks(io.micrometer.core.instrument.Meter meter) {
        return meterRegistries.stream().anyMatch(meterRegistry -> meterRegistry.tracks(meter));
    }

    void onMeterAdded(io.micrometer.core.instrument.Meter meter) {
        meterRegistries.forEach(mr -> mr.onMeterAdded(meter));
    }

    void onMeterRemoved(io.micrometer.core.instrument.Meter meter) {
        meterRegistries.forEach(mr -> mr.onMeterRemoved(meter));
    }

    @Override
    public MeterRegistry globalRegistry(Consumer<Meter> onAddListener, Consumer<Meter> onRemoveListener, boolean backfill) {
        return globalOperation(() -> {
            MMeterRegistry result = globalMeterRegistry;
            result.onMeterAdded(onAddListener);
            result.onMeterRemoved(onRemoveListener);

            if (backfill) {
                result.meters().forEach(onAddListener);
            }
            return result;
        });
    }

    @Override
    public MMeterRegistry.Builder meterRegistryBuilder() {
        ensureOpen();
        return MMeterRegistry.builder(Metrics.globalRegistry, this);
    }

    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig) {
        return MMeterRegistry.builder(Metrics.globalRegistry, this)
                .metricsConfig(metricsConfig)
                .build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig,
                                             Consumer<Meter> onAddListener,
                                             Consumer<Meter> onRemoveListener) {
        return MMeterRegistry.builder(Metrics.globalRegistry, this)
                .metricsConfig(metricsConfig)
                .onMeterAdded(onAddListener)
                .onMeterRemoved(onRemoveListener)
                .build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public MeterRegistry createMeterRegistry(Clock clock,
                                             MetricsConfig metricsConfig,
                                             Consumer<Meter> onAddListener,
                                             Consumer<Meter> onRemoveListener) {

        return MMeterRegistry.builder(Metrics.globalRegistry, this)
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
        return globalOperation(() ->
                globalMeterRegistry != null
                        ? globalMeterRegistry
                        : globalRegistry(MetricsConfig.create(Services.get(Config.class)
                                                                     .get(MetricsConfig.METRICS_CONFIG_KEY))));
    }

    @Override
    public MeterRegistry globalRegistry(MetricsConfig metricsConfig) {
        return globalOperation(() -> {
            if (globalMeterRegistry != null) {
                if (metricsConfig.equals(this.metricsConfig)) {
                    return globalMeterRegistry;
                }
                // Ideally this method will be invoked once with the proper MetricsConfig settings.
                // But it's possible for it to be invoked more than once with different
                // settings. In such a case we need to clear the old global registry and create a new one because
                // the new settings might affect its behavior.
                closeGlobalRegistry();
            }

            var registriesToPublish = prepareMeterRegistries(metricsConfig);
            registriesToPublish.forEach(Metrics.globalRegistry::add);
            publisherRegistries.addAll(registriesToPublish);
            Contexts.globalContext().register(MetricsFactory.PULL_PUBLISHERS_PRESENT,
                                              registriesToPublish.stream()
                                                      .anyMatch(r -> r instanceof PrometheusMeterRegistry));

            MMeterRegistry result = MMeterRegistry.builder(Metrics.globalRegistry, this)
                    .metricsConfig(metricsConfig)
                    .buildRegistry();

            try {
                registerMeterRegistry(metricsConfig, result, registry -> {
                    globalMeterRegistry = registry;
                    globalRegistrySystemTags = registry.displayTagPairs();
                });

                /*
                 Let listeners enroll their callbacks for meter creation and removal with the new registry if they want before
                 we apply any meters providers. This way the listeners get to learn of the meters which the registry creates from
                 the builders.
                 */
                notifyListenersOfCreate(result, metricsConfig);

                applyMetersProvidersToRegistry(this, result, metersProviders);

                return result;
            } catch (RuntimeException | Error e) {
                lock.lock();
                try {
                    if (globalMeterRegistry == result) {
                        globalMeterRegistry = null;
                        globalRegistrySystemTags = Map.of();
                    }
                } finally {
                    lock.unlock();
                }
                result.close();
                closePublisherRegistries();
                throw e;
            }
        });
    }

    @Override
    public void close() {
        List<MMeterRegistry> registriesToClose = null;
        lock.lock();
        try {
            closed = true;
            if (closeComplete) {
                return;
            }

            if (Thread.currentThread() == closeCleanupThread) {
                return;
            }

            if (globalOperationDepth.get() > 0) {
                return;
            }

            while (activeGlobalOperations > 0 && !closeCleanupStarted) {
                lifecycleChanged.awaitUninterruptibly();
            }

            if (closeCleanupStarted) {
                while (!closeComplete) {
                    lifecycleChanged.awaitUninterruptibly();
                }
                return;
            }

            registriesToClose = prepareClose();
        } finally {
            lock.unlock();
        }

        completeClose(registriesToClose);
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

    private List<io.micrometer.core.instrument.MeterRegistry> prepareMeterRegistries(MetricsConfig metricsConfig) {
        /*
        If the user specified no publishers, then use an inferred Prometheus one
        for backward compatibility. If the user specified at least one publisher, then do not
        provide a default Prometheus one and use only those the user set up that are enabled.
         */
        SpanContextSupplierProvider spanCtxSupplierProvider = spanContextSupplierProvider.get();

        var enabledMicrometerPublishers = new ArrayList<io.micrometer.core.instrument.MeterRegistry>();
        metricsConfig.publishers().stream()
                .filter(p -> p instanceof MicrometerMetricsPublisher)
                .filter(MetricsPublisher::enabled)
                .map(p -> (MicrometerMetricsPublisher) p)
                .map(p -> p instanceof PrometheusPublisher pp
                     ? pp.prometheusRegistry().apply(key -> metricsConfig.lookupConfig(key).orElse(null),
                                                     spanCtxSupplierProvider)
                        : p.registry().get())
                .forEach(enabledMicrometerPublishers::add);
        /*
        Configured provider handling omits disabled services, so if the user disabled the Prometheus publisher
        the list of publishers in the build config object is empty. To see
         */
        if (!metricsConfig.publishersConfigured()) {
            enabledMicrometerPublishers.add(spanContextSupplierProvider instanceof NoOpSpanContextSupplierProvider
                    ? new PrometheusMeterRegistry(key -> metricsConfig.lookupConfig(key).orElse(null))
                    : new PrometheusMeterRegistry(key -> metricsConfig.lookupConfig(key).orElse(null),
                                                  new CollectorRegistry(),
                                                  io.micrometer.core.instrument.Clock.SYSTEM,
                                                  new DefaultExemplarSampler(spanCtxSupplierProvider.get())));
        }

        if (enabledMicrometerPublishers.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "No active Micrometer publishers are configured");
        }
        return enabledMicrometerPublishers;

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

    MMeterRegistry registerMeterRegistry(MetricsConfig metricsConfig, MMeterRegistry meterRegistry) {
        return registerMeterRegistry(metricsConfig, meterRegistry, ignored -> { });
    }

    private MMeterRegistry registerMeterRegistry(MetricsConfig metricsConfig,
                                                 MMeterRegistry meterRegistry,
                                                 Consumer<MMeterRegistry> onRegistered) {
        lock.lock();
        try {
            checkOpen();
            this.metricsConfig = metricsConfig;
            meterRegistry.onRegistered();
            meterRegistries.add(meterRegistry);
            multipleRegistryWarnings.created(metricsConfig);
            onRegistered.accept(meterRegistry);
            return meterRegistry;
        } finally {
            lock.unlock();
        }
    }

    void onMeterRegistryClosed(MMeterRegistry meterRegistry) {
        lock.lock();
        try {
            meterRegistries.remove(meterRegistry);
            multipleRegistryWarnings.closed();
        } finally {
            lock.unlock();
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Metrics factory is closed");
        }
    }

    private void ensureOpen() {
        lock.lock();
        try {
            checkOpen();
        } finally {
            lock.unlock();
        }
    }

    private <T> T globalOperation(Supplier<T> operation) {
        globalRegistryLock.lock();
        boolean started = false;
        try {
            beginGlobalOperation();
            started = true;
            return operation.get();
        } finally {
            globalRegistryLock.unlock();
            if (started) {
                endGlobalOperation();
            }
        }
    }

    private void beginGlobalOperation() {
        lock.lock();
        try {
            checkOpen();
            activeGlobalOperations++;
            globalOperationDepth.set(globalOperationDepth.get() + 1);
        } finally {
            lock.unlock();
        }
    }

    private void endGlobalOperation() {
        int depth = globalOperationDepth.get() - 1;
        if (depth == 0) {
            globalOperationDepth.remove();
        } else {
            globalOperationDepth.set(depth);
        }

        List<MMeterRegistry> registriesToClose = null;
        lock.lock();
        try {
            activeGlobalOperations--;
            if (activeGlobalOperations == 0) {
                lifecycleChanged.signalAll();
                if (closed && !closeCleanupStarted) {
                    registriesToClose = prepareClose();
                }
            }
        } finally {
            lock.unlock();
        }

        if (registriesToClose != null) {
            completeClose(registriesToClose);
        }
    }

    private List<MMeterRegistry> prepareClose() {
        closeCleanupStarted = true;
        globalRegistrySystemTags = Map.of();
        globalMeterRegistry = null;
        return List.copyOf(meterRegistries);
    }

    private void completeClose(List<MMeterRegistry> registries) {
        lock.lock();
        try {
            closeCleanupThread = Thread.currentThread();
        } finally {
            lock.unlock();
        }

        try {
            registries.forEach(MMeterRegistry::close);
            closePublisherRegistries();
            metersProviders.forEach(provider -> {
                if (provider instanceof AutoCloseable closeable) {
                    try {
                        closeable.close();
                    } catch (Exception e) {
                        LOGGER.log(System.Logger.Level.WARNING, "Error closing metrics meter provider", e);
                    }
                }
            });
        } finally {
            try {
                onClose.accept(this);
            } finally {
                lock.lock();
                try {
                    closeCleanupThread = null;
                    closeComplete = true;
                    lifecycleChanged.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private void closeGlobalRegistry() {
        if (globalMeterRegistry != null) {
            globalMeterRegistry.close();
            meterRegistries.remove(globalMeterRegistry);
            globalMeterRegistry = null;
        }
        globalRegistrySystemTags = Map.of();
        closePublisherRegistries();
    }

    private void closePublisherRegistries() {
        List<io.micrometer.core.instrument.MeterRegistry> registries = List.copyOf(publisherRegistries);
        registries.forEach(registry -> {
            Metrics.globalRegistry.remove(registry);
            registry.close();
        });
        publisherRegistries.clear();
    }

    private static class MultipleRegistryWarnings {
        private final ReentrantLock lock = new ReentrantLock();

        private StackTraceElement[] originalCreationStackTrace;
        private boolean hasLoggedFirstMultiInstantiationWarning;
        private int activeRegistries;

        private void created(MetricsConfig metricsConfig) {
            lock.lock();
            try {
                if (activeRegistries++ == 0) {
                    originalCreationStackTrace = Thread.currentThread().getStackTrace();
                } else if (metricsConfig.warnOnMultipleRegistries()) {
                    if (!hasLoggedFirstMultiInstantiationWarning) {
                        hasLoggedFirstMultiInstantiationWarning = true;
                        MMETER_REGISTRY_LOGGER.log(System.Logger.Level.WARNING,
                                                   "Unexpected duplicate instantiation\n"
                                                           + "Original instantiation from:\n{0}\n\n"
                                                           + "Additional instantiation from:\n{1}\n",

                                                   stackTraceToString(originalCreationStackTrace),
                                                   stackTraceToString(Thread.currentThread().getStackTrace()));
                    } else {
                        MMETER_REGISTRY_LOGGER.log(System.Logger.Level.WARNING,
                                                   "Unexpected additional instantiation from:\n{0}\n",
                                                   stackTraceToString(Thread.currentThread().getStackTrace()));
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        private void closed() {
            lock.lock();
            try {
                if (activeRegistries > 0 && --activeRegistries == 0) {
                    originalCreationStackTrace = null;
                    hasLoggedFirstMultiInstantiationWarning = false;
                }
            } finally {
                lock.unlock();
            }
        }

        private static String stackTraceToString(StackTraceElement[] stackTraceElements) {
            StringJoiner joiner = new StringJoiner("\n");
            for (StackTraceElement element : stackTraceElements) {
                joiner.add(element.toString());
            }
            return joiner.toString();
        }
    }
}
