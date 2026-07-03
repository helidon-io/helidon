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
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

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
    private final Collection<MeterRegistryLifeCycleListener> meterRegistryLifeCycleListeners;
    private final SpanContextSupplierProvider spanContextSupplierProvider;

    private final Collection<MMeterRegistry> meterRegistries = new ConcurrentLinkedQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock globalRegistryLock = new ReentrantLock();
    private final MultipleRegistryWarnings multipleRegistryWarnings = new MultipleRegistryWarnings();
    private final ThreadLocal<Integer> globalOperationDepth = ThreadLocal.withInitial(() -> 0);
    private final Condition lifecycleChanged = lock.newCondition();

    private final MetricsConfig metricsConfig;

    private volatile MMeterRegistry globalMeterRegistry;
    private boolean closed;
    private int activeGlobalOperations;
    private boolean closeCleanupStarted;
    private boolean closeComplete;
    private Thread closeCleanupThread;

    private MicrometerMetricsFactory(MetricsConfig metricsConfig,
                                     Collection<MetersProvider> metersProviders,
                                     Collection<MeterRegistryLifeCycleListener> meterRegistryLifeCycleListeners,
                                     SpanContextSupplierProvider spanContextSupplierProvider) {
        this.metricsConfig = metricsConfig;
        this.metersProviders = metersProviders;
        this.meterRegistryLifeCycleListeners = meterRegistryLifeCycleListeners;
        this.spanContextSupplierProvider = spanContextSupplierProvider;
    }

    static MicrometerMetricsFactory create(MetricsConfig metricsConfig,
                                           Collection<MetersProvider> metersProviders) {
        return create(metricsConfig,
                      metersProviders,
                      List.of(),
                      new NoOpSpanContextSupplierProvider());
    }

    static MicrometerMetricsFactory create(MetricsConfig metricsConfig,
                                           Collection<MetersProvider> metersProviders,
                                           Collection<MeterRegistryLifeCycleListener> meterRegistryLifeCycleListeners,
                                           SpanContextSupplierProvider spanContextSupplierProvider) {
        return new MicrometerMetricsFactory(metricsConfig,
                                            metersProviders,
                                            meterRegistryLifeCycleListeners,
                                            spanContextSupplierProvider);
    }

    // Intended for testing lifecycle cleanup.
    int meterRegistryCount() {
        return meterRegistries.size();
    }

    @Override
    public MMeterRegistry.Builder meterRegistryBuilder() {
        ensureOpen();
        return MMeterRegistry.builder(this);
    }

    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig) {
        return MMeterRegistry.builder(this)
                .metricsConfig(metricsConfig)
                .build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig,
                                             Consumer<Meter> onAddListener,
                                             Consumer<Meter> onRemoveListener) {
        return MMeterRegistry.builder(this)
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

        return MMeterRegistry.builder(this)
                .metricsConfig(metricsConfig)
                .clock(clock)
                .onMeterAdded(onAddListener)
                .onMeterRemoved(onRemoveListener)
                .build();
    }

    @Override
    public MeterRegistry createMeterRegistry(Clock clock, MetricsConfig metricsConfig) {
        return MMeterRegistry.builder(this)
                .clock(clock)
                .metricsConfig(metricsConfig)
                .build();
    }

    @Override
    public MeterRegistry globalRegistry() {
        return globalOperation(() -> {
            if (globalMeterRegistry != null) {
                return globalMeterRegistry;
            }

            MMeterRegistry result = MMeterRegistry.builder(this)
                    .metricsConfig(metricsConfig)
                    .buildRegistry();

            try {
                registerMeterRegistry(metricsConfig, result, registry -> globalMeterRegistry = registry);

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
                    }
                } finally {
                    lock.unlock();
                }
                result.close();
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

    List<io.micrometer.core.instrument.MeterRegistry> prepareMeterRegistries(MetricsConfig metricsConfig) {
        /*
        If the user specified no publishers, then use an inferred Prometheus one
        for backward compatibility. If the user specified at least one publisher, then do not
        provide a default Prometheus one and use only those the user set up that are enabled.
        */
        var enabledMicrometerPublishers = new ArrayList<io.micrometer.core.instrument.MeterRegistry>();
        try {
            metricsConfig.publishers().stream()
                    .filter(p -> p instanceof MicrometerMetricsPublisher)
                    .filter(MetricsPublisher::enabled)
                    .map(p -> (MicrometerMetricsPublisher) p)
                    .map(p -> p instanceof PrometheusPublisher pp
                         ? pp.prometheusRegistry().apply(key -> metricsConfig.lookupConfig(key).orElse(null),
                                                         spanContextSupplierProvider)
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
                                                      new DefaultExemplarSampler(spanContextSupplierProvider.get())));
            }
        } catch (RuntimeException | Error e) {
            enabledMicrometerPublishers.forEach(registry -> {
                try {
                    registry.close();
                } catch (RuntimeException | Error cleanupError) {
                    e.addSuppressed(cleanupError);
                }
            });
            throw e;
        }

        if (enabledMicrometerPublishers.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "No active Micrometer publishers are configured");
        }
        return enabledMicrometerPublishers;

    }

    private void notifyListenersOfCreate(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        meterRegistryLifeCycleListeners.forEach(listener -> listener.onCreate(meterRegistry, metricsConfig));
    }

    @SuppressWarnings("unchecked")
    private <B extends Meter.Builder<B, M>, M extends Meter> MeterRegistry applyMetersProvidersToRegistry(
            MetricsFactory factory,
            MeterRegistry registry,
            Collection<MetersProvider> metersProviders) {
        metersProviders.stream()
                .flatMap(mp -> mp.meterBuilders(factory, registry).stream())
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
            metersProviders.forEach(provider -> {
                if (provider instanceof AutoCloseable closeable) {
                    try {
                        closeable.close();
                    } catch (Exception e) {
                        LOGGER.log(System.Logger.Level.WARNING, "Error closing metrics meter provider", e);
                    }
                }
            });
            Throwable closeFailure = null;
            for (MMeterRegistry registry : registries) {
                try {
                    registry.close();
                } catch (RuntimeException | Error e) {
                    if (closeFailure == null) {
                        closeFailure = e;
                    } else if (closeFailure != e) {
                        closeFailure.addSuppressed(e);
                    }
                }
            }
            if (closeFailure instanceof RuntimeException e) {
                throw e;
            }
            if (closeFailure instanceof Error e) {
                throw e;
            }
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
