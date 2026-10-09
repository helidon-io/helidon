/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.providers.helidon;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;
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

/**
 * Helidon metrics factory, configurable independently of the service registry.
 */
@Api.Preview
public final class HelidonMetricsFactory implements MetricsFactory, RuntimeType.Api<HelidonMetricsFactoryConfig> {
    private static final System.Logger LOGGER = System.getLogger(HelidonMetricsFactory.class.getName());
    private static final System.Logger METER_REGISTRY_LOGGER = System.getLogger(HelidonMeterRegistry.class.getName());

    private final HelidonMetricsFactoryConfig config;
    private final Set<HelidonMeterRegistry> meterRegistries = new LinkedHashSet<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock globalRegistryLock = new ReentrantLock();
    private final MultipleRegistryWarnings multipleRegistryWarnings = new MultipleRegistryWarnings();
    private final ThreadLocal<Integer> registryOperationDepth = ThreadLocal.withInitial(() -> 0);
    private final Condition lifecycleChanged = lock.newCondition();

    private volatile HelidonMeterRegistry globalMeterRegistry;
    private boolean closed;
    private int activeRegistryOperations;
    private boolean closeCleanupStarted;
    private boolean closeComplete;
    private Thread closeCleanupThread;

    private HelidonMetricsFactory(HelidonMetricsFactoryConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Returns a new builder for a Helidon metrics factory.
     *
     * @return builder
     */
    public static HelidonMetricsFactoryConfig.Builder builder() {
        return HelidonMetricsFactoryConfig.builder();
    }

    /**
     * Creates a Helidon metrics factory from a generated config object.
     *
     * @param config factory config
     * @return Helidon metrics factory
     */
    public static HelidonMetricsFactory create(HelidonMetricsFactoryConfig config) {
        return new HelidonMetricsFactory(Objects.requireNonNull(config));
    }

    /**
     * Creates a Helidon metrics factory with default settings.
     *
     * @return Helidon metrics factory
     */
    public static HelidonMetricsFactory create() {
        return builder().build();
    }

    /**
     * Creates a Helidon metrics factory using builder customizations.
     *
     * @param consumer builder customizations
     * @return Helidon metrics factory
     */
    public static HelidonMetricsFactory create(Consumer<HelidonMetricsFactoryConfig.Builder> consumer) {
        return builder()
                .update(Objects.requireNonNull(consumer))
                .build();
    }

    @Override
    public HelidonMetricsFactoryConfig prototype() {
        return config;
    }

    @Override
    public MeterRegistry globalRegistry() {
        return globalOperation(() -> {
            if (globalMeterRegistry != null) {
                return globalMeterRegistry;
            }

            HelidonMeterRegistry result = HelidonMeterRegistry.builder(this)
                    .metricsConfig(config.metricsConfig())
                    .buildRegistry();

            try {
                registerMeterRegistry(config.metricsConfig(), result, registry -> globalMeterRegistry = registry);

                /*
                 Let listeners enroll their callbacks for meter creation and removal with the new registry if they want before
                 we apply any meters providers. This way the listeners get to learn of the meters which the registry creates from
                 the builders.
                 */
                config.meterRegistryLifeCycleListeners()
                        .forEach(listener -> listener.onCreate(result, config.metricsConfig()));

                result.applyMetersProviders(this, config.metersProviders());
                result.startPublishers();

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
        List<HelidonMeterRegistry> registriesToClose = null;
        lock.lock();
        try {
            closed = true;
            if (closeComplete) {
                return;
            }

            if (Thread.currentThread() == closeCleanupThread) {
                return;
            }

            if (registryOperationDepth.get() > 0) {
                return;
            }

            // Registry callbacks can hold a lock needed by another thread completing registry work or shutdown.
            if ((activeRegistryOperations > 0 || closeCleanupStarted)
                    && meterRegistries.stream().anyMatch(HelidonMeterRegistry::closeDependsOnCurrentThread)) {
                return;
            }

            while (activeRegistryOperations > 0 && !closeCleanupStarted) {
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
        return config.metricsConfig();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <B extends MeterRegistry.Builder<B, M>, M extends MeterRegistry> B meterRegistryBuilder() {
        ensureOpen();
        return (B) HelidonMeterRegistry.builder(this);
    }

    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig) {
        return newRegistryBuilder(Objects.requireNonNull(metricsConfig)).build();
    }

    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig,
                                             Consumer<Meter> onAddListener,
                                             Consumer<Meter> onRemoveListener) {
        return newRegistryBuilder(Objects.requireNonNull(metricsConfig))
                .onMeterAdded(Objects.requireNonNull(onAddListener))
                .onMeterRemoved(Objects.requireNonNull(onRemoveListener))
                .build();
    }

    @Override
    public MeterRegistry createMeterRegistry(Clock clock, MetricsConfig metricsConfig) {
        return newRegistryBuilder(Objects.requireNonNull(metricsConfig))
                .clock(Objects.requireNonNull(clock))
                .build();
    }

    @Override
    public MeterRegistry createMeterRegistry(Clock clock,
                                            MetricsConfig metricsConfig,
                                            Consumer<Meter> onAddListener,
                                            Consumer<Meter> onRemoveListener) {
        return newRegistryBuilder(Objects.requireNonNull(metricsConfig))
                .clock(Objects.requireNonNull(clock))
                .onMeterAdded(Objects.requireNonNull(onAddListener))
                .onMeterRemoved(Objects.requireNonNull(onRemoveListener))
                .build();
    }

    @Override
    public Clock clockSystem() {
        return config.clock();
    }

    @Override
    public Counter.Builder counterBuilder(String name) {
        return HelidonCounter.builder(Objects.requireNonNull(name));
    }

    @Override
    public <T> FunctionalCounter.Builder<T> functionalCounterBuilder(String name, T stateObject, Function<T, Long> fn) {
        return HelidonFunctionalCounter.builder(Objects.requireNonNull(name),
                                               Objects.requireNonNull(stateObject),
                                               Objects.requireNonNull(fn));
    }

    @Override
    public DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder() {
        return HelidonDistributionStatisticsConfig.builder();
    }

    @Override
    public DistributionSummary.Builder distributionSummaryBuilder(String name,
                                                                  DistributionStatisticsConfig.Builder configBuilder) {
        return HelidonDistributionSummary.builder(Objects.requireNonNull(name), Objects.requireNonNull(configBuilder));
    }

    @Override
    public <T> Gauge.Builder<Double> gaugeBuilder(String name, T stateObject, ToDoubleFunction<T> fn) {
        return HelidonGauge.builder(Objects.requireNonNull(name),
                                    Objects.requireNonNull(stateObject),
                                    Objects.requireNonNull(fn));
    }

    @Override
    public <N extends Number> Gauge.Builder<N> gaugeBuilder(String name, Supplier<N> supplier) {
        return HelidonGauge.builder(Objects.requireNonNull(name), Objects.requireNonNull(supplier));
    }

    @Override
    public Timer.Builder timerBuilder(String name) {
        return HelidonTimer.builder(Objects.requireNonNull(name));
    }

    @Override
    public Timer.Sample timerStart() {
        return HelidonTimer.start(clockSystem());
    }

    @Override
    public Timer.Sample timerStart(MeterRegistry registry) {
        return HelidonTimer.start(Objects.requireNonNull(registry).clock());
    }

    @Override
    public Timer.Sample timerStart(Clock clock) {
        return HelidonTimer.start(Objects.requireNonNull(clock));
    }

    @Override
    public Tag tagCreate(String key, String value) {
        return new HelidonTag(Objects.requireNonNull(key), Objects.requireNonNull(value));
    }

    @Override
    public HistogramSnapshot histogramSnapshotEmpty(long count, double total, double max) {
        return HelidonHistogramSnapshot.empty(count, total, max);
    }

    void customize(Meter.Builder<?, ?> builder) {
        config.meterBuilderCustomizers().forEach(customizer -> customizer.customize(builder));
    }

    HelidonMeterRegistry registerMeterRegistry(MetricsConfig metricsConfig, HelidonMeterRegistry meterRegistry) {
        return registerMeterRegistry(metricsConfig, meterRegistry, _ -> { });
    }

    void onMeterRegistryClosed(HelidonMeterRegistry meterRegistry) {
        lock.lock();
        try {
            meterRegistries.remove(meterRegistry);
            multipleRegistryWarnings.closed();
        } finally {
            lock.unlock();
        }
    }

    <T> T registryOperation(Supplier<T> operation) {
        beginRegistryOperation();
        try {
            return operation.get();
        } finally {
            endRegistryOperation();
        }
    }

    private static Throwable recordCloseFailure(Throwable closeFailure, Throwable newFailure) {
        if (closeFailure == null) {
            return newFailure;
        }
        if (closeFailure != newFailure) {
            try {
                closeFailure.addSuppressed(newFailure);
            } catch (Throwable _) {
                // Continue cleanup even if recording a later failure needs unavailable resources.
            }
        }
        return closeFailure;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void rethrow(Throwable failure) throws T {
        throw (T) failure;
    }

    private HelidonMeterRegistry.Builder newRegistryBuilder(MetricsConfig metricsConfig) {
        return HelidonMeterRegistry.builder(this)
                .clock(clockSystem())
                .metricsConfig(metricsConfig);
    }

    private HelidonMeterRegistry registerMeterRegistry(MetricsConfig metricsConfig,
                                                      HelidonMeterRegistry meterRegistry,
                                                      Consumer<HelidonMeterRegistry> onRegistered) {
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
            beginRegistryOperation();
            started = true;
            return operation.get();
        } finally {
            globalRegistryLock.unlock();
            if (started) {
                endRegistryOperation();
            }
        }
    }

    private void beginRegistryOperation() {
        lock.lock();
        try {
            checkOpen();
            activeRegistryOperations++;
            registryOperationDepth.set(registryOperationDepth.get() + 1);
        } finally {
            lock.unlock();
        }
    }

    private void endRegistryOperation() {
        int depth = registryOperationDepth.get() - 1;
        if (depth == 0) {
            registryOperationDepth.remove();
        } else {
            registryOperationDepth.set(depth);
        }

        List<HelidonMeterRegistry> registriesToClose = null;
        lock.lock();
        try {
            activeRegistryOperations--;
            if (activeRegistryOperations == 0) {
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

    private List<HelidonMeterRegistry> prepareClose() {
        closeCleanupStarted = true;
        globalMeterRegistry = null;
        return List.copyOf(meterRegistries);
    }

    private void completeClose(List<HelidonMeterRegistry> registries) {
        lock.lock();
        try {
            closeCleanupThread = Thread.currentThread();
        } finally {
            lock.unlock();
        }

        try {
            Throwable closeFailure = null;
            // Keep meter sources alive until publishers have performed any final export.
            for (HelidonMeterRegistry registry : registries) {
                try {
                    registry.close();
                } catch (Throwable e) {
                    closeFailure = recordCloseFailure(closeFailure, e);
                }
            }
            for (MetersProvider provider : config.metersProviders()) {
                if (provider instanceof AutoCloseable closeable) {
                    try {
                        closeable.close();
                    } catch (Throwable e) {
                        if (e instanceof Exception exception) {
                            try {
                                LOGGER.log(System.Logger.Level.WARNING, "Error closing metrics meter provider", exception);
                            } catch (Throwable loggingFailure) {
                                closeFailure = recordCloseFailure(closeFailure, loggingFailure);
                            }
                        } else {
                            closeFailure = recordCloseFailure(closeFailure, e);
                        }
                    }
                }
            }
            if (closeFailure != null) {
                HelidonMetricsFactory.<RuntimeException>rethrow(closeFailure);
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

        private static String stackTraceToString(StackTraceElement[] stackTraceElements) {
            StringJoiner joiner = new StringJoiner("\n");
            for (StackTraceElement element : stackTraceElements) {
                joiner.add(element.toString());
            }
            return joiner.toString();
        }

        private void created(MetricsConfig metricsConfig) {
            lock.lock();
            try {
                if (activeRegistries++ == 0) {
                    originalCreationStackTrace = Thread.currentThread().getStackTrace();
                } else if (metricsConfig.warnOnMultipleRegistries()) {
                    if (!hasLoggedFirstMultiInstantiationWarning) {
                        hasLoggedFirstMultiInstantiationWarning = true;
                        METER_REGISTRY_LOGGER.log(System.Logger.Level.WARNING,
                                                   "Unexpected duplicate instantiation\n"
                                                           + "Original instantiation from:\n{0}\n\n"
                                                           + "Additional instantiation from:\n{1}\n",

                                                   stackTraceToString(originalCreationStackTrace),
                                                   stackTraceToString(Thread.currentThread().getStackTrace()));
                    } else {
                        METER_REGISTRY_LOGGER.log(System.Logger.Level.WARNING,
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

    }
}
