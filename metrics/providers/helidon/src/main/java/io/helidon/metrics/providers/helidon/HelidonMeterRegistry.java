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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.MetricsPublisher;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.spi.MetersProvider;

final class HelidonMeterRegistry implements MeterRegistry {
    private static final System.Logger LOGGER = System.getLogger(HelidonMeterRegistry.class.getName());

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Condition closeCompleted = lock.writeLock().newCondition();
    private final List<Consumer<Meter>> addListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Meter>> removeListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<HelidonMeterId, HelidonMeter> meters = new ConcurrentHashMap<>();
    private final ConcurrentMap<HelidonMeterId, Meter> disabledMeters = new ConcurrentHashMap<>();
    private final Set<Meter> deletedDisabledMeters = ConcurrentHashMap.newKeySet();
    private final List<HelidonMetricsPublisher.Session> publisherSessions = new ArrayList<>();
    private final Clock clock;
    private final MetricsConfig metricsConfig;
    private final HelidonMetricsFactory metricsFactory;
    private final SystemTagsManager systemTagsManager;
    private volatile boolean registeredWithFactory;
    private volatile LifecycleState lifecycleState = LifecycleState.OPEN;
    private volatile Thread closeThread;
    private Thread startupThread;

    private HelidonMeterRegistry(Builder builder) {
        this.clock = builder.clock;
        this.metricsConfig = builder.metricsConfig;
        this.metricsFactory = builder.metricsFactory;
        this.systemTagsManager = SystemTagsManager.create(metricsConfig, metricsFactory);
        this.addListeners.addAll(builder.addListeners);
        this.removeListeners.addAll(builder.removeListeners);
    }

    static Builder builder(HelidonMetricsFactory metricsFactory) {
        return new Builder(metricsFactory);
    }

    @Override
    public List<Meter> meters() {
        return new ArrayList<>(meters.values());
    }

    @Override
    public Collection<Meter> meters(Predicate<Meter> filter) {
        return meters().stream().filter(Objects.requireNonNull(filter)).toList();
    }

    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            if (lifecycleState != LifecycleState.OPEN) {
                if (lifecycleState == LifecycleState.CLOSING && closeThread != Thread.currentThread()) {
                    while (lifecycleState == LifecycleState.CLOSING) {
                        closeCompleted.awaitUninterruptibly();
                    }
                }
                return;
            }
            lifecycleState = LifecycleState.CLOSING;
            if (startupThread != null) {
                closeThread = startupThread;
                if (startupThread != Thread.currentThread()) {
                    while (lifecycleState == LifecycleState.CLOSING) {
                        closeCompleted.awaitUninterruptibly();
                    }
                }
                return;
            }
            closeThread = Thread.currentThread();
        } finally {
            lock.writeLock().unlock();
        }
        completeClose();
    }

    @Override
    public boolean isMeterEnabled(String name, Map<String, String> tags) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(tags);
        return metricsConfig.isMeterEnabled(name);
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public MetricsFactory metricsFactory() {
        return metricsFactory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <B extends Meter.Builder<B, M>, M extends Meter> M getOrCreate(B builder) {
        Objects.requireNonNull(builder);
        checkOpen();
        metricsFactory.customize(builder);
        HelidonMeter.AbstractBuilder<?, ?> helidonBuilder = toHelidonBuilder(builder);
        HelidonMeterId id = helidonBuilder.id();

        lock.readLock().lock();
        try {
            checkOpen();
            HelidonMeter existing = meters.get(id);
            if (existing != null && !existing.isDeleted()) {
                verifyType(existing, helidonBuilder);
                return (M) existing;
            }
            Meter disabledExisting = disabledMeters.get(id);
            if (disabledExisting != null) {
                verifyType(disabledExisting, helidonBuilder);
                return (M) disabledExisting;
            }
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            checkOpen();
            HelidonMeter existing = meters.get(id);
            if (existing != null && !existing.isDeleted()) {
                verifyType(existing, helidonBuilder);
                return (M) existing;
            }
            Meter disabledExisting = disabledMeters.get(id);
            if (disabledExisting != null) {
                verifyType(disabledExisting, helidonBuilder);
                return (M) disabledExisting;
            }
            if (!isMeterEnabled(builder.name(), builder.tags())) {
                Meter result = metricsFactory.noOpMeter(helidonBuilder);
                verifyType(result, helidonBuilder);
                disabledMeters.put(id, result);
                return (M) result;
            }
            HelidonMeter meter = createMeter(id, helidonBuilder);
            meters.put(id, meter);
            addListeners.forEach(listener -> listener.accept(meter));
            return (M) meter;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public <M extends Meter> Optional<M> meter(Class<M> mClass, String name, Iterable<Tag> tags) {
        Objects.requireNonNull(mClass);
        HelidonMeter meter = meters.get(HelidonTypes.meterId(name, tags));
        if (meter == null) {
            return Optional.empty();
        }
        if (mClass.isInstance(meter)) {
            return Optional.of(mClass.cast(meter));
        }
        throw new IllegalArgumentException("Matching meter is of type "
                                                   + meter.getClass().getName()
                                                   + " but "
                                                   + mClass.getName()
                                                   + " was requested");
    }

    @Override
    public Optional<Meter> remove(Meter meter) {
        return remove(Objects.requireNonNull(meter).id());
    }

    @Override
    public Optional<Meter> remove(Meter.Id id) {
        Objects.requireNonNull(id);
        lock.writeLock().lock();
        try {
            HelidonMeter removed = meters.remove(HelidonTypes.meterId(id.name(), id.tags()));
            if (removed == null) {
                return Optional.empty();
            }
            removed.markAsDeleted();
            notifyListenersOfRemove(removeListeners, removed);
            return Optional.of(removed);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<Meter> remove(String name, Iterable<Tag> tags) {
        return remove(HelidonTypes.meterId(name, tags));
    }

    @Override
    public boolean isDeleted(Meter meter) {
        Objects.requireNonNull(meter);
        if (meter instanceof HelidonMeter helidonMeter) {
            return helidonMeter.isDeleted();
        }
        return deletedDisabledMeters.contains(meter);
    }

    @Override
    public MeterRegistry onMeterAdded(Consumer<Meter> onAddListener) {
        addListeners.add(Objects.requireNonNull(onAddListener));
        return this;
    }

    @Override
    public MeterRegistry onMeterRemoved(Consumer<Meter> onRemoveListener) {
        removeListeners.add(Objects.requireNonNull(onRemoveListener));
        return this;
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return Objects.requireNonNull(c).cast(this);
    }

    HelidonMeterRegistry applyMetersProviders(MetricsFactory factory, Collection<MetersProvider> metersProviders) {
        Objects.requireNonNull(factory);
        Objects.requireNonNull(metersProviders).forEach(provider -> provider.meterBuilders(factory, this)
                .forEach(builder -> getOrCreateUntyped(builder.origin(provider.getClass().getName()))));
        return this;
    }

    SystemTagsManager systemTagsManager() {
        return systemTagsManager;
    }

    void onRegistered() {
        registeredWithFactory = true;
    }

    boolean closeDependsOnCurrentThread() {
        return closeThread == Thread.currentThread() || lock.isWriteLockedByCurrentThread();
    }

    void startPublishers() {
        lock.writeLock().lock();
        try {
            if (lifecycleState != LifecycleState.OPEN || !metricsConfig.enabled()) {
                return;
            }
            startupThread = Thread.currentThread();
        } finally {
            lock.writeLock().unlock();
        }

        Throwable startupFailure = null;
        try {
            List<HelidonMetricsPublisher> publishers = new ArrayList<>();
            for (MetricsPublisher publisher : metricsConfig.publishers()) {
                if (!publisher.enabled()) {
                    continue;
                }
                if (!(publisher instanceof HelidonMetricsPublisher helidonPublisher)) {
                    throw new IllegalArgumentException("Publisher " + publisher.getClass().getName()
                                                               + " does not support the Helidon metrics provider; implement "
                                                               + HelidonMetricsPublisher.class.getName());
                }
                publishers.add(helidonPublisher);
            }
            for (HelidonMetricsPublisher publisher : publishers) {
                if (lifecycleState != LifecycleState.OPEN) {
                    break;
                }
                publisherSessions.add(Objects.requireNonNull(publisher.start(this, metricsConfig),
                                                             "Metrics publisher returned a null session"));
            }
        } catch (Throwable e) {
            startupFailure = e;
        }

        boolean shouldClose;
        lock.writeLock().lock();
        try {
            startupThread = null;
            shouldClose = startupFailure != null || lifecycleState == LifecycleState.CLOSING;
            if (shouldClose) {
                lifecycleState = LifecycleState.CLOSING;
                closeThread = Thread.currentThread();
            }
        } finally {
            lock.writeLock().unlock();
        }
        if (shouldClose) {
            try {
                completeClose();
            } catch (Throwable e) {
                startupFailure = recordCloseFailure(startupFailure, e);
            }
        }
        if (startupFailure != null) {
            HelidonMeterRegistry.<RuntimeException>rethrow(startupFailure);
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

    private static void verifyType(Meter existing, HelidonMeter.AbstractBuilder<?, ?> builder) {
        if (!builder.meterType().isInstance(existing)) {
            throw new IllegalArgumentException("Attempt to get or create a meter of type "
                                                       + builder.meterType().getName()
                                                       + " when an existing meter "
                                                       + existing.id()
                                                       + " has an incompatible type "
                                                       + existing.getClass().getName());
        }
    }

    private static void notifyListenersOfRemove(List<Consumer<Meter>> listeners, Meter meter) {
        listeners.forEach(listener -> {
            try {
                listener.accept(meter);
            } catch (RuntimeException | Error e) {
                LOGGER.log(System.Logger.Level.WARNING, "Meter removal listener failed", e);
            }
        });
    }

    private void completeClose() {
        Throwable closeFailure = null;
        try {
            // Publishers may perform a final export while all meters are still available.
            for (HelidonMetricsPublisher.Session session : publisherSessions.reversed()) {
                try {
                    session.close();
                } catch (Throwable e) {
                    closeFailure = recordCloseFailure(closeFailure, e);
                }
            }
            publisherSessions.clear();

            List<Consumer<Meter>> listeners;
            List<HelidonMeter> removed;
            lock.writeLock().lock();
            try {
                listeners = List.copyOf(removeListeners);
                removed = new ArrayList<>(meters.values());
                meters.clear();
                deletedDisabledMeters.addAll(disabledMeters.values());
                disabledMeters.clear();
                addListeners.clear();
                removeListeners.clear();
                removed.forEach(HelidonMeter::markAsDeleted);
            } finally {
                lock.writeLock().unlock();
            }
            try {
                removed.forEach(meter -> notifyListenersOfRemove(listeners, meter));
            } catch (Throwable e) {
                closeFailure = recordCloseFailure(closeFailure, e);
            }
            try {
                if (registeredWithFactory) {
                    metricsFactory.onMeterRegistryClosed(this);
                }
            } catch (Throwable e) {
                closeFailure = recordCloseFailure(closeFailure, e);
            }
        } finally {
            lock.writeLock().lock();
            try {
                lifecycleState = LifecycleState.CLOSED;
                closeThread = null;
                closeCompleted.signalAll();
            } finally {
                lock.writeLock().unlock();
            }
        }
        if (closeFailure != null) {
            HelidonMeterRegistry.<RuntimeException>rethrow(closeFailure);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void getOrCreateUntyped(Meter.Builder<?, ?> builder) {
        getOrCreate((Meter.Builder) builder);
    }

    private void checkOpen() {
        if (lifecycleState != LifecycleState.OPEN) {
            throw new IllegalStateException("Meter registry is closed");
        }
    }

    private HelidonMeter.AbstractBuilder<?, ?> toHelidonBuilder(Meter.Builder<?, ?> builder) {
        if (builder instanceof HelidonMeter.AbstractBuilder<?, ?> helidonBuilder) {
            return helidonBuilder;
        }
        return toHelidonBuilderCopy(builder);
    }

    private HelidonMeter.AbstractBuilder<?, ?> toHelidonBuilderCopy(Meter.Builder<?, ?> builder) {
        if (builder instanceof Counter.Builder counterBuilder) {
            HelidonCounter.Builder result = HelidonCounter.builder(counterBuilder.name());
            result.from(counterBuilder);
            return result;
        }
        if (builder instanceof FunctionalCounter.Builder<?> functionalCounterBuilder) {
            return toHelidonFunctionalCounterBuilder(functionalCounterBuilder);
        }
        if (builder instanceof DistributionSummary.Builder summaryBuilder) {
            HelidonDistributionSummary.Builder result = HelidonDistributionSummary.builder(
                    summaryBuilder.name(),
                    summaryBuilder.distributionStatisticsConfig()
                            .orElseGet(HelidonDistributionStatisticsConfig::builder));
            result.from(summaryBuilder);
            summaryBuilder.scale().ifPresent(result::scale);
            summaryBuilder.publishPercentileHistogram().ifPresent(result::publishPercentileHistogram);
            return result;
        }
        if (builder instanceof Gauge.Builder<?> gaugeBuilder) {
            return toHelidonGaugeBuilder(gaugeBuilder);
        }
        if (builder instanceof Timer.Builder timerBuilder) {
            HelidonTimer.Builder result = HelidonTimer.builder(timerBuilder.name());
            result.from(timerBuilder);
            List<Double> percentiles = new ArrayList<>();
            timerBuilder.percentiles().forEach(percentiles::add);
            if (!percentiles.isEmpty()) {
                double[] percentileArray = new double[percentiles.size()];
                for (int i = 0; i < percentiles.size(); i++) {
                    percentileArray[i] = percentiles.get(i);
                }
                result.percentiles(percentileArray);
            }
            List<Duration> buckets = new ArrayList<>();
            timerBuilder.buckets().forEach(buckets::add);
            if (!buckets.isEmpty()) {
                result.buckets(buckets.toArray(Duration[]::new));
            }
            timerBuilder.minimumExpectedValue().ifPresent(result::minimumExpectedValue);
            timerBuilder.maximumExpectedValue().ifPresent(result::maximumExpectedValue);
            timerBuilder.publishPercentileHistogram().ifPresent(result::publishPercentileHistogram);
            return result;
        }
        throw new IllegalArgumentException("Unexpected builder type " + builder.getClass().getName());
    }

    private <T> HelidonFunctionalCounter.Builder<T> toHelidonFunctionalCounterBuilder(
            FunctionalCounter.Builder<T> builder) {
        HelidonFunctionalCounter.Builder<T> result = HelidonFunctionalCounter.builder(builder.name(),
                                                                                     builder.stateObject(),
                                                                                     builder.fn());
        result.from(builder);
        return result;
    }

    private <N extends Number> HelidonGauge.Builder<N> toHelidonGaugeBuilder(Gauge.Builder<N> builder) {
        HelidonGauge.Builder<N> result = HelidonGauge.builder(builder.name(), builder.supplier());
        result.from(builder);
        return result;
    }

    private HelidonMeter createMeter(Meter.Id id, HelidonMeter.AbstractBuilder<?, ?> builder) {
        if (builder instanceof HelidonCounter.Builder counterBuilder) {
            return HelidonCounter.create(id, counterBuilder);
        }
        if (builder instanceof HelidonFunctionalCounter.Builder<?> functionalCounterBuilder) {
            return HelidonFunctionalCounter.create(id, functionalCounterBuilder);
        }
        if (builder instanceof HelidonDistributionSummary.Builder summaryBuilder) {
            return HelidonDistributionSummary.create(id, summaryBuilder);
        }
        if (builder instanceof HelidonGauge.Builder<?> gaugeBuilder) {
            return HelidonGauge.create(id, gaugeBuilder);
        }
        if (builder instanceof HelidonTimer.Builder timerBuilder) {
            return HelidonTimer.create(id, timerBuilder, clock);
        }
        throw new IllegalArgumentException("Unexpected builder type " + builder.getClass().getName());
    }

    private enum LifecycleState {
        OPEN,
        CLOSING,
        CLOSED
    }

    static final class Builder implements MeterRegistry.Builder<Builder, HelidonMeterRegistry> {
        private final HelidonMetricsFactory metricsFactory;
        private final List<Consumer<Meter>> addListeners = new ArrayList<>();
        private final List<Consumer<Meter>> removeListeners = new ArrayList<>();
        private MetricsConfig metricsConfig;
        private Clock clock;

        private Builder(HelidonMetricsFactory metricsFactory) {
            this.metricsFactory = Objects.requireNonNull(metricsFactory);
            this.metricsConfig = metricsFactory.metricsConfig();
            this.clock = metricsFactory.clockSystem();
        }

        @Override
        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock);
            return this;
        }

        @Override
        public Builder metricsConfig(MetricsConfig metricsConfig) {
            this.metricsConfig = Objects.requireNonNull(metricsConfig);
            return this;
        }

        @Override
        public Builder onMeterAdded(Consumer<Meter> addListener) {
            addListeners.add(Objects.requireNonNull(addListener));
            return this;
        }

        @Override
        public Builder onMeterRemoved(Consumer<Meter> removeListener) {
            removeListeners.add(Objects.requireNonNull(removeListener));
            return this;
        }

        @Override
        public HelidonMeterRegistry build() {
            return metricsFactory.registryOperation(() -> {
                HelidonMeterRegistry registry = buildRegistry();
                try {
                    metricsFactory.registerMeterRegistry(metricsConfig, registry);
                    registry.startPublishers();
                    return registry;
                } catch (RuntimeException | Error e) {
                    try {
                        registry.close();
                    } catch (RuntimeException | Error closeFailure) {
                        recordCloseFailure(e, closeFailure);
                    }
                    throw e;
                }
            });
        }

        HelidonMeterRegistry buildRegistry() {
            return new HelidonMeterRegistry(this);
        }
    }
}
