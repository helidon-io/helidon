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

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.MeterConfig;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.api.Tag;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.search.Search;

/**
 * Implementation of {@link io.helidon.metrics.api.MeterRegistry} for the Micrometer adapter.
 *
 * <p>
 * The flow of control here is interesting during new meter registration. Typically a developer uses the Helidon
 * metrics API to create a builder for a new Helidon meter. That automatically creates that builder's delegate, an instance
 * of the corresponding Micrometer meter builder (held as a private reference inside the Helidon builder). The developer's
 * code then invokes this registry's getOrCreate method passing the Helidon metric builder.
 * </p>
 * <p>
 * This code invokes the Micrometer builder's register method, passing this registry's delegate which is a Micrometer
 * meter registry. The Micrometer registry invokes a callback to us, passing the new Micrometer meter, before recording it.
 * During a Helidon registration we collect these callbacks until the native register method returns, so we can match the
 * returned native meter to its builder even when native filters change its ID or register other meters.
 * </p>
 * <p>
 * After Micrometer returns to us, we wrap the native meters, update our internal data structures, notify our listeners,
 * and return the Helidon meter to the developer's code which invoked getOrCreate. For registrations originating directly
 * from Micrometer, we create the wrapper and notify listeners during the native callback.
 * </p>
 * <p>
 * This is a little convoluted, but this approach allows us to automatically create Helidon meters around every Micrometer
 * meter, <em>even those which the developer registers directly with Micrometer.</em> That way, queries of the registry
 * using the Helidon API return the same Helidon meter wrapper instance around a given Micrometer meter, regardless of which
 * API the developer used to register the meter: ours or Micrometer's.
 * </p>
 */
class MMeterRegistry implements io.helidon.metrics.api.MeterRegistry {

    private static final System.Logger LOGGER = System.getLogger(MMeterRegistry.class.getName());

    private final io.micrometer.core.instrument.MeterRegistry delegate;

    /*
    Note that onMeterAdded and onMeterRemoved which manage these two lists do not lock; they rely on
    the thread safety provided by the copy-on-write behavior. If you change the implementations here you might need to add
    locking to those two methods.
     */
    private final List<Consumer<io.helidon.metrics.api.Meter>> onAddListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<io.helidon.metrics.api.Meter>> onRemoveListeners = new CopyOnWriteArrayList<>();

    /**
     * Helidon API clock to be returned by the {@link #clock()} method.
     */
    private final Clock clock;
    private final MicrometerMetricsFactory metricsFactory;
    private final MetricsConfig metricsConfig;
    private final SystemTagsManager systemTagsManager;
    private volatile boolean registeredWithFactory;

    /**
     * Once a Micrometer meter is registered, this map records the corresponding Helidon meter wrapper for it. This allows us,
     * for example, to return to the developer's code the proper Helidon wrapper meter during searches that return the same
     * Micrometer meters, rather than creating new Helidon wrapper meters each time.
     */
    private final Map<Meter, MMeter> meters = new HashMap<>();

    // Protected by the write lock. Registrations can nest when native filters or listeners register another meter.
    private List<Meter> pendingMeterAdds;
    private Map<Gauge, Object> pendingGaugeSources;

    private final Map<io.helidon.metrics.api.Meter.Id, MMeter<?>> metersById = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Condition closeCompleted = lock.writeLock().newCondition();
    private LifecycleState lifecycleState = LifecycleState.OPEN;
    private volatile Thread closeThread;

    private MMeterRegistry(io.micrometer.core.instrument.MeterRegistry delegate,
                           MicrometerMetricsFactory metricsFactory,
                           MetricsConfig metricsConfig,
                           Clock clock) {
        this.delegate = delegate;
        this.clock = clock;
        this.metricsFactory = metricsFactory;
        this.metricsConfig = metricsConfig;
        this.systemTagsManager = SystemTagsManager.create(metricsConfig, metricsFactory);
        delegate.config()
                .onMeterAdded(this::onMeterAdded)
                .onMeterRemoved(this::onMeterRemoved)
                .meterFilter(new MeterFilter() {
                    @Override
                    public Meter.Id map(Meter.Id id) {
                        Map<String, String> systemTags = systemTagsManager.displayTagPairs();
                        if (systemTags.isEmpty()) {
                            return id;
                        }
                        List<io.micrometer.core.instrument.Tag> tags = new ArrayList<>();
                        systemTags.forEach((name, value) ->
                                                   tags.add(io.micrometer.core.instrument.Tag.of(name, value)));
                        return id.replaceTags(Tags.concat(tags, id.getTagsAsIterable()));
                    }
                });
    }

    static Builder builder(MicrometerMetricsFactory metricsFactory) {
        return new Builder(metricsFactory);
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
            closeThread = Thread.currentThread();
            onAddListeners.clear();
            onRemoveListeners.clear();
            meters.values().forEach(MMeter::markAsDeleted);
            meters.clear();
            metersById.clear();
        } finally {
            lock.writeLock().unlock();
        }

        Throwable closeFailure = null;
        CompositeMeterRegistry compositeMeterRegistry = (CompositeMeterRegistry) delegate;
        List<io.micrometer.core.instrument.MeterRegistry> publishers = List.of();
        try {
            publishers = List.copyOf(compositeMeterRegistry.getRegistries());
        } catch (Throwable e) {
            closeFailure = recordCloseFailure(closeFailure, e);
        }
        for (io.micrometer.core.instrument.MeterRegistry publisher : publishers) {
            try {
                publisher.close();
            } catch (Throwable e) {
                closeFailure = recordCloseFailure(closeFailure, e);
            }
            try {
                compositeMeterRegistry.remove(publisher);
            } catch (Throwable e) {
                closeFailure = recordCloseFailure(closeFailure, e);
            }
        }
        try {
            compositeMeterRegistry.close();
        } catch (Throwable e) {
            closeFailure = recordCloseFailure(closeFailure, e);
        }
        try {
            if (registeredWithFactory) {
                metricsFactory.onMeterRegistryClosed(this);
            }
        } catch (Throwable e) {
            closeFailure = recordCloseFailure(closeFailure, e);
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
            MMeterRegistry.<RuntimeException>rethrow(closeFailure);
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

    private void checkOpen() {
        if (lifecycleState != LifecycleState.OPEN) {
            throw new IllegalStateException("Meter registry is closed");
        }
    }

    void onRegistered() {
        registeredWithFactory = true;
    }

    boolean isClosingOnCurrentThread() {
        return closeThread == Thread.currentThread();
    }

    @Override
    public List<io.helidon.metrics.api.Meter> meters() {
        lock.readLock().lock();
        List<io.helidon.metrics.api.Meter> temp;
        try {
            temp = new ArrayList<>(meters.values());
        } finally {
            lock.readLock().unlock();
        }
        return temp.stream()
                .map(io.helidon.metrics.api.Meter.class::cast)
                .toList();
    }

    @Override
    public Collection<io.helidon.metrics.api.Meter> meters(Predicate<io.helidon.metrics.api.Meter> filter) {
        lock.readLock().lock();
        List<io.helidon.metrics.api.Meter> temp;
        try {
            temp = new ArrayList<>(meters.values());
        } finally {
            lock.readLock().unlock();
        }
        return temp.stream()
                .map(io.helidon.metrics.api.Meter.class::cast)
                .filter(filter)
                .toList();

    }

    @Override
    public boolean isMeterEnabled(String name) {
        return metricsConfig.isMeterEnabled(name);
    }

    @Override
    public boolean isMeterEnabled(String name, Map<String, String> tags) {
        /*
        This method uses only config, not any mutable data structures, so no need to lock.
         */
        Objects.requireNonNull(name);
        Objects.requireNonNull(tags);
        return isMeterEnabled(name);
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public <HB extends io.helidon.metrics.api.Meter.Builder<HB, HM>,
            HM extends io.helidon.metrics.api.Meter> HM getOrCreate(HB builder) {
        Objects.requireNonNull(builder);
        lock.readLock().lock();
        try {
            checkOpen();
        } finally {
            lock.readLock().unlock();
        }
        metricsFactory.customize(builder);
        return getOrCreateCustomized(builder);
    }

    @Override
    public <M extends io.helidon.metrics.api.Meter> Optional<M> meter(Class<M> mClass,
                                                                      String name,
                                                                      Iterable<Tag> tags) {

        lock.readLock().lock();
        try {
            Search search = delegate().find(name)
                    .tags(MTag.tags(tags));
            Meter match = search.meter();

            if (match == null) {
                return Optional.empty();
            }
            io.helidon.metrics.api.Meter neutralMeter = meters.get(match);
            if (neutralMeter == null) {
                LOGGER.log(Level.WARNING, String.format("Found no Helidon counterpart for Micrometer meter %s %s",
                                                        name,
                                                        Util.list(tags)));
                return Optional.empty();
            }
            if (mClass.isInstance(neutralMeter)) {
                return Optional.of(mClass.cast(neutralMeter));
            }
            throw new IllegalArgumentException(
                    String.format("Matching meter is of type %s but %s was requested",
                                  match.getClass().getName(),
                                  mClass.getName()));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<io.helidon.metrics.api.Meter> remove(io.helidon.metrics.api.Meter meter) {
        return internalRemove(meter.id());
    }

    @Override
    public Optional<io.helidon.metrics.api.Meter> remove(io.helidon.metrics.api.Meter.Id id) {
        return internalRemove(id);
    }

    @Override
    public Optional<io.helidon.metrics.api.Meter> remove(String name, Iterable<Tag> tags) {
        return internalRemove(MMeter.PlainId.create(name, tags));
    }

    @Override
    public boolean isDeleted(io.helidon.metrics.api.Meter meter) {
        return meter instanceof MMeter<?> helidonMeter && helidonMeter.isDeleted();
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return c.cast(delegate);
    }

    io.micrometer.core.instrument.MeterRegistry delegate() {
        return delegate;
    }

    @Override
    public io.helidon.metrics.api.MeterRegistry onMeterAdded(Consumer<io.helidon.metrics.api.Meter> listener) {
        onAddListeners.add(listener);
        return this;
    }

    @Override
    public io.helidon.metrics.api.MeterRegistry onMeterRemoved(Consumer<io.helidon.metrics.api.Meter> listener) {
        onRemoveListeners.add(listener);
        return this;
    }

    @Override
    public MetricsFactory metricsFactory() {
        return metricsFactory;
    }

    void erase() {
        lock.writeLock().lock();

        try {
            meters.clear();
            onAddListeners.clear();
            onRemoveListeners.clear();
            metersById.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    io.helidon.metrics.api.Meter getOrCreateUntyped(io.helidon.metrics.api.Meter.Builder<?, ?> builder) {
        // The Micrometer builders do not have a shared inherited declaration of the register method.
        // Each type of builder declares its own so we need to decide here which specific one to invoke.
        // That's so we can invoke the Micrometer builder's register method, which acts as
        // get-or-create.
        // Micrometer's register methods will throw an IllegalArgumentException if the caller specifies a builder that finds
        // a previously-registered meter of a different type from that implied by the builder.

        // No locking needed yet. If configuration (which is immutable) says the meter is disabled, we just return a no-op meter;
        // we do not update any data structures.
        io.helidon.metrics.api.Meter disabledMeter = noopMeterIfDisabled(builder);
        if (disabledMeter != null) {
            return disabledMeter;
        }
        configureMeter(builder);

        io.helidon.metrics.api.Meter helidonMeter;

        if (builder instanceof MCounter.Builder cBuilder) {
            helidonMeter = getOrCreate(cBuilder, cBuilder.delegate()::register);
        } else if (builder instanceof MFunctionalCounter.Builder<?> fcBuilder) {
            helidonMeter = getOrCreate(fcBuilder, fcBuilder.delegate()::register);
        } else if (builder instanceof MDistributionSummary.Builder sBuilder) {
            helidonMeter = getOrCreate(sBuilder, sBuilder.delegate()::register);
        } else if (builder instanceof MGauge.Builder gBuilder) {
            helidonMeter = getOrCreate(gBuilder, ((MGauge.Builder<?, ?>) gBuilder).delegate()::register);
        } else if (builder instanceof MTimer.Builder tBuilder) {
            helidonMeter = getOrCreate(tBuilder, tBuilder.delegate()::register);
        } else {
            throw new IllegalArgumentException(String.format("Unexpected builder type %s, expected one of %s",
                                                             builder.getClass().getName(),
                                                             List.of(MCounter.Builder.class.getName(),
                                                                     MFunctionalCounter.Builder.class.getName(),
                                                                     MDistributionSummary.Builder.class.getName(),
                                                                     MGauge.Builder.class.getName(),
                                                                     MTimer.Builder.class.getName())));
        }
        return helidonMeter;
    }

    void onMeterAdded(Meter addedMeter) {

        /*
        We are not guaranteed that one of our own update operations--which would already hold the write lock--is triggering
        this callback from Micrometer. A developer might be using the Micrometer API directly, for example. So acquire the lock
        in any case.
         */

        lock.writeLock().lock();
        try {
            if (lifecycleState != LifecycleState.OPEN) {
                return;
            }
            if (pendingMeterAdds != null) {
                pendingMeterAdds.add(addedMeter);
                return;
            }
            recordMeterAdded(addedMeter, null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    void onMeterRemoved(Meter removedMeter) {
        /*
         See locking comment with onMeterAdded.
         */
        lock.writeLock().lock();

        try {
            if (lifecycleState != LifecycleState.OPEN) {
                return;
            }
            MMeter<?> removedHelidonMeter = meters.remove(removedMeter);
            if (removedHelidonMeter == null) {
                LOGGER.log(Level.WARNING, "No matching neutral meter for implementation meter " + removedMeter.getId());
            } else {
                recordRemove(removedHelidonMeter);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void onGaugeCreated(Gauge gauge, Object source) {
        // Foreign native registrations must not acquire our lock while holding the native registry's lock.
        if (lock.isWriteLockedByCurrentThread() && pendingGaugeSources != null) {
            pendingGaugeSources.put(gauge, source);
        }
    }

    private <M extends Meter> void recordMeterAdded(M addedMeter, MMeter.Builder<?, M, ?, ?> builder) {
        /*
        Use a builder only after native registration has returned this exact meter. Gauge builders additionally require
        their private creation source to match: a filter can insert a different gauge with the same eventual native ID.
         */
        io.helidon.metrics.api.Meter.Id neutralIdForAddedMeter = neutralIdWithoutSystemTags(addedMeter.getId());
        MMeter<M> mMeter;
        if (builder == null) {
            mMeter = MMeter.create(neutralIdForAddedMeter, addedMeter);
        } else {
            mMeter = builder.build(neutralIdForAddedMeter, addedMeter);
        }
        recordNewMeter(neutralIdForAddedMeter, mMeter, addedMeter);
        onAddListeners.forEach(listener -> listener.accept(mMeter));
    }

    private <HB extends io.helidon.metrics.api.Meter.Builder<HB, HM>,
            HM extends io.helidon.metrics.api.Meter> HM getOrCreateCustomized(HB builder) {
        // Just cast the builder if it "one of ours" because that means it was prepared using our MetricsFactory, and that
        // would already have set the builder up with the correct Micrometer builder delegate.
        if (builder instanceof MMeter.Builder<?, ?, ?, ?> mBuilder) {
            return (HM) getOrCreateUntyped((HB) mBuilder);
        }

        // If this is not "one of ours" then we need to create a new builder, based on the one passed in but with the correct
        // Micrometer delegate builder assigned.
        return (HM) getOrCreateUntyped(convertNeutralBuilder(builder));
    }

    private void configureMeter(io.helidon.metrics.api.Meter.Builder<?, ?> builder) {
        MeterConfig meterConfig = metricsConfig.meterConfig(builder.name()).orElse(null);
        if (meterConfig == null || (meterConfig.percentiles().isEmpty()
                && meterConfig.buckets().isEmpty()
                && meterConfig.minimumExpectedValue().isEmpty()
                && meterConfig.maximumExpectedValue().isEmpty())) {
            return;
        }
        if (!(builder instanceof io.helidon.metrics.api.Timer.Builder timerBuilder)) {
            throw new IllegalArgumentException("Timer statistics are configured for a meter which is not a timer: "
                                                       + builder.name());
        }
        meterConfig.percentiles().ifPresent(percentiles ->
                timerBuilder.percentiles(percentiles.stream().mapToDouble(Double::doubleValue).toArray()));
        meterConfig.buckets().ifPresent(buckets -> timerBuilder.buckets(buckets.toArray(Duration[]::new)));
        meterConfig.minimumExpectedValue().ifPresent(timerBuilder::minimumExpectedValue);
        meterConfig.maximumExpectedValue().ifPresent(timerBuilder::maximumExpectedValue);
        if ((meterConfig.minimumExpectedValue().isPresent() || meterConfig.maximumExpectedValue().isPresent())
                && timerBuilder.minimumExpectedValue().isPresent() && timerBuilder.maximumExpectedValue().isPresent()
                && timerBuilder.minimumExpectedValue().get().compareTo(timerBuilder.maximumExpectedValue().get()) > 0) {
            throw new IllegalArgumentException("Timer minimum-expected-value must not exceed maximum-expected-value: "
                                                       + builder.name());
        }
    }

    private io.helidon.metrics.api.Meter noopMeterIfDisabled(io.helidon.metrics.api.Meter.Builder<?, ?> builder) {
        if (!isMeterEnabled(builder.name(), builder.tags())) {
            lock.readLock().lock();
            try {
                checkOpen();
            } finally {
                lock.readLock().unlock();
            }
            io.helidon.metrics.api.Meter result = metricsFactory.noOpMeter(builder);
            onAddListeners.forEach(listener -> listener.accept(result));
            return result;
        }
        return null;
    }

    /*
     * Returns an existing meter matching the specified builder metadata and ID, or null if none.
     *
     * The caller must have acquired either the read or write lock.
     */
    private <M extends Meter,
            HB extends MMeter.Builder<?, M, HB, HM>,
            HM extends MMeter<M>> MMeter<M> meterIfRegistered(MMeter.Builder<?, M, HB, HM> mBuilder,
                                                              io.helidon.metrics.api.Meter.Id id) {
        MMeter<?> foundMeter = metersById.get(id);
        if (foundMeter != null) {
            if (!mBuilder.meterType().isInstance(foundMeter)) {
                throw new IllegalArgumentException("Attempt to get or create a meter of type "
                                                           + mBuilder.meterType().getName()
                                                           + " when an existing meter " + id
                                                           + " has an incompatible type " + foundMeter.getClass()
                        .getName());
            }
            return (HM) foundMeter;
        }
        return null;
    }

    private <M extends Meter,
            HB extends MMeter.Builder<?, M, HB, HM>,
            HM extends MMeter<M>> io.helidon.metrics.api.Meter getOrCreate(HB mBuilder,
                                                                           Function<io.micrometer.core.instrument.MeterRegistry,
                                                                                   M> registration) {

        io.helidon.metrics.api.Meter.Id id = mBuilder.id();

        lock.readLock().lock();

        try {
            checkOpen();
            MMeter<?> foundMeter = meterIfRegistered(mBuilder, id);
            if (foundMeter != null) {
                return (HM) foundMeter;
            }
        } finally {
            lock.readLock().unlock();
        }

        /*
         Effectively, "promote" our lock from read (which we acquired a few lines above) to write. Because ReentrantReadWriteLock
         does not actually support promoting, we have to release the read lock (which we did just above), acquire the write
         lock, and recheck what we checked earlier while we had the read lock.
         */

        lock.writeLock().lock();

        try {
            checkOpen();
            io.helidon.metrics.api.Meter previouslyRegisteredMeter = meterIfRegistered(mBuilder, id);
            if (previouslyRegisteredMeter != null) {
                return previouslyRegisteredMeter;
            }

            displayTagPairs().forEach(mBuilder::delegateTag);
            List<Meter> previousPendingAdds = pendingMeterAdds;
            Map<Gauge, Object> previousGaugeSources = pendingGaugeSources;
            List<Meter> addedMeters = new ArrayList<>();
            Map<Gauge, Object> gaugeSources = mBuilder instanceof MGauge.Builder<?, ?> ? new IdentityHashMap<>() : null;
            M meter;
            pendingMeterAdds = addedMeters;
            pendingGaugeSources = gaugeSources;
            try {
                meter = registration.apply(delegate());
            } catch (RuntimeException | Error failure) {
                pendingMeterAdds = previousPendingAdds;
                pendingGaugeSources = previousGaugeSources;
                // A filter or native listener might have registered another meter before this registration failed.
                // Retain only callbacks whose exact native meters actually reached the native registry.
                for (Meter added : addedMeters) {
                    if (delegate.getMeters().stream().anyMatch(registered -> registered == added)) {
                        try {
                            recordMeterAdded(added, null);
                        } catch (RuntimeException | Error listenerFailure) {
                            if (failure != listenerFailure) {
                                failure.addSuppressed(listenerFailure);
                            }
                        }
                    }
                }
                throw failure;
            } finally {
                pendingMeterAdds = previousPendingAdds;
                pendingGaugeSources = previousGaugeSources;
            }
            checkOpen();
            // Correlate the returned meter before an unrelated meter's listener can fail.
            for (int i = 0; i < addedMeters.size(); i++) {
                if (addedMeters.get(i) == meter) {
                    if (i > 0) {
                        addedMeters.remove(i);
                        addedMeters.addFirst(meter);
                    }
                    break;
                }
            }
            Throwable listenerFailure = null;
            for (Meter added : addedMeters) {
                if (lifecycleState != LifecycleState.OPEN) {
                    break;
                }
                try {
                    if (added == meter
                            && (!(mBuilder instanceof MGauge.Builder<?, ?> gaugeBuilder)
                            || gaugeBuilder.ownsRegistrationSource(gaugeSources.get(meter)))) {
                        recordMeterAdded(meter, mBuilder);
                    } else {
                        recordMeterAdded(added, null);
                    }
                } catch (RuntimeException | Error failure) {
                    if (listenerFailure == null) {
                        listenerFailure = failure;
                    } else if (listenerFailure != failure) {
                        listenerFailure.addSuppressed(failure);
                    }
                }
            }
            if (listenerFailure != null) {
                MMeterRegistry.<RuntimeException>rethrow(listenerFailure);
            }
            checkOpen();

            HM result = (HM) meters.get(meter);
            if (result == null) {
                /*
                Our on-add listener never ran, so the delegate registry must have found a pre-existing meter when we asked it
                but we have no record of that meter being linked to one of ours. This is surprising, but go ahead and wrap
                the pre-existing meter with a neutral Helidon meter and go on.
                */

                LOGGER.log(Level.WARNING,
                           "Unexpected discovery of unknown previously-created meter; creating wrapper for " + meter.getId());
                io.helidon.metrics.api.Meter.Id nativeId = neutralIdWithoutSystemTags(meter.getId());
                result = wrapMeter(nativeId, meter);
                recordNewMeter(nativeId, result, meter);
            }

            return result;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private <M extends Meter,
            HB extends MMeter.Builder<?, M, HB, HM>,
            HM extends MMeter<M>> HM wrapMeter(io.helidon.metrics.api.Meter.Id id,
                                               M addedMeter) {

        MMeter<?> helidonMeter = null;
        if (addedMeter instanceof Counter counter) {
            helidonMeter = MCounter.create(id, counter);
        } else if (addedMeter instanceof DistributionSummary summary) {
            helidonMeter = MDistributionSummary.create(id, summary);
        } else if (addedMeter instanceof Gauge gauge) {
            helidonMeter = MGauge.create(id, gauge);
        } else if (addedMeter instanceof Timer timer) {
            helidonMeter = MTimer.create(id, timer);
        } else if (addedMeter instanceof FunctionCounter functionCounter) {
            helidonMeter = MFunctionalCounter.create(id, functionCounter);
        }
        if (helidonMeter == null) {
            LOGGER.log(Level.DEBUG,
                       String.format("Addition of meter %s which is of an unsupported type; ignored", addedMeter));
        }
        return (HM) helidonMeter;
    }

    private <M extends Meter,
            HB extends MMeter.Builder<?, M, HB, HM>,
            HM extends MMeter<M>> HB convertNeutralBuilder(io.helidon.metrics.api.Meter.Builder<?, ?> builder) {
        if (builder instanceof io.helidon.metrics.api.Counter.Builder cBuilder) {
            return (HB) MCounter.builder(cBuilder.name()).from(cBuilder);
        }
        if (builder instanceof FunctionalCounter.Builder fcBuilder) {
            return (HB) MFunctionalCounter.builderFrom(fcBuilder);
        }
        if (builder instanceof io.helidon.metrics.api.Gauge.Builder<?> gBuilder) {
            return (HB) MGauge.builderFrom(gBuilder);
        }
        if (builder instanceof io.helidon.metrics.api.DistributionSummary.Builder sBuilder) {
            return (HB) MDistributionSummary.builderFrom(sBuilder);
        }
        if (builder instanceof io.helidon.metrics.api.Timer.Builder tBuilder) {
            return (HB) MTimer.builderFrom(tBuilder);
        }
        throw new IllegalArgumentException("Unexpected builder type: " + builder.getClass().getName());
    }

    private Optional<io.helidon.metrics.api.Meter> internalRemove(io.helidon.metrics.api.Meter.Id id) {

        lock.writeLock().lock();

        try {
            if (lifecycleState != LifecycleState.OPEN) {
                return Optional.empty();
            }
            Meter nativeMeter = delegate.find(id.name())
                    .tags(MTag.tags(id.tags()))
                    .meter();

            if (nativeMeter != null) {
                MMeter<?> result = meters.get(nativeMeter);
                delegate.remove(nativeMeter);
                onRemoveListeners.forEach(listener -> {
                    try {
                        listener.accept(result);
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING,
                                   "Error invoking onRemoveListener " + listener.getClass().getName() + "; continuing",
                                   ex);
                    }
                });
                return Optional.of(result);
            }
            return Optional.empty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private io.helidon.metrics.api.Meter.Id neutralIdWithoutSystemTags(Meter.Id micrometerId) {
        Map<String, String> systemTags = displayTagPairs();
        List<Tag> tags = new ArrayList<>();
        MTag.neutralTags(micrometerId.getTags()).forEach(tag -> {
            String systemTagValue = systemTags.get(tag.key());
            if (!tag.value().equals(systemTagValue)) {
                tags.add(tag);
            }
        });

        return MMeter.PlainId.create(micrometerId.getName(),
                                     tags);
    }

    Map<String, String> displayTagPairs() {
        return systemTagsManager.displayTagPairs();
    }

    private void recordNewMeter(io.helidon.metrics.api.Meter.Id id,
                                MMeter<?> newNeutralMeter,
                                Meter delegate) {
        meters.put(delegate, newNeutralMeter);
        metersById.put(id, newNeutralMeter);
    }

    private MMeter<?> recordRemove(MMeter<?> removedHelidonMeter) {

        metersById.remove(removedHelidonMeter.id());
        removedHelidonMeter.markAsDeleted();
        onRemoveListeners.forEach(listener -> {
            try {
                listener.accept(removedHelidonMeter);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING,
                           "Error invoking onRemoveListener " + listener.getClass().getName() + "; continuing",
                           ex);
            }
        });
        return removedHelidonMeter;
    }

    static class Builder<B extends Builder<B, R>, R extends MMeterRegistry>
            implements io.helidon.metrics.api.MeterRegistry.Builder<B, R> {

        private final MicrometerMetricsFactory metricsFactory;
        private MetricsConfig metricsConfig;
        private Optional<Clock> clock = Optional.empty();
        private Optional<Consumer<io.helidon.metrics.api.Meter>> onAddListener = Optional.empty();
        private Optional<Consumer<io.helidon.metrics.api.Meter>> onRemoveListener = Optional.empty();

        private Builder(MicrometerMetricsFactory metricsFactory) {
            this.metricsFactory = metricsFactory;
            this.metricsConfig = metricsFactory.metricsConfig();
        }

        @Override
        public B metricsConfig(MetricsConfig metricsConfig) {
            this.metricsConfig = metricsConfig;
            return identity();
        }

        @Override
        public B clock(Clock clock) {
            this.clock = Optional.of(clock);
            return identity();
        }

        @Override
        public B onMeterAdded(Consumer<io.helidon.metrics.api.Meter> listener) {
            onAddListener = Optional.of(listener);
            return identity();
        }

        @Override
        public B onMeterRemoved(Consumer<io.helidon.metrics.api.Meter> listener) {
            onRemoveListener = Optional.of(listener);
            return identity();
        }

        @Override
        public R build() {
            MMeterRegistry result = buildRegistry();
            try {
                return (R) metricsFactory.registerMeterRegistry(metricsConfig, result);
            } catch (RuntimeException | Error e) {
                result.close();
                throw e;
            }
        }

        MMeterRegistry buildRegistry() {
            GaugeTrackingRegistry delegate = new GaugeTrackingRegistry(clock
                    .<io.micrometer.core.instrument.Clock>map(ClockWrapper::create)
                    .orElse(io.micrometer.core.instrument.Clock.SYSTEM));
            MMeterRegistry result = null;
            try {
                metricsFactory.prepareMeterRegistries(metricsConfig).forEach(delegate::add);
                MMeterRegistry newRegistry = new MMeterRegistry(delegate,
                                                                metricsFactory,
                                                                metricsConfig,
                                                                clock.orElse(MClock.create(delegate.config().clock())));
                result = newRegistry;
                delegate.owner = newRegistry;

                onAddListener.ifPresent(newRegistry::onMeterAdded);
                onRemoveListener.ifPresent(newRegistry::onMeterRemoved);

                return newRegistry;
            } catch (RuntimeException | Error e) {
                if (result == null) {
                    delegate.close();
                } else {
                    result.close();
                }
                throw e;
            }
        }
    }

    private static class GaugeTrackingRegistry extends CompositeMeterRegistry {

        private volatile MMeterRegistry owner;

        private GaugeTrackingRegistry(io.micrometer.core.instrument.Clock clock) {
            super(clock);
        }

        @Override
        protected <T> Gauge newGauge(Meter.Id id, T stateObject, ToDoubleFunction<T> fn) {
            // Keep Micrometer's null-state behavior while correlating creation using the original private source.
            Gauge gauge = super.newGauge(id, MGauge.hasNullRegistrationState(stateObject) ? null : stateObject, fn);
            MMeterRegistry registry = owner;
            if (registry != null) {
                registry.onGaugeCreated(gauge, stateObject);
            }
            return gauge;
        }
    }

    private enum LifecycleState {
        OPEN,
        CLOSING,
        CLOSED
    }

    /**
     * Micrometer-friendly wrapper around a Helidon clock.
     */
    private static class ClockWrapper implements io.micrometer.core.instrument.Clock {

        private final Clock neutralClock;

        private ClockWrapper(Clock neutralClock) {
            this.neutralClock = neutralClock;
        }

        static ClockWrapper create(Clock clock) {
            return new ClockWrapper(clock);
        }

        @Override
        public long wallTime() {
            return neutralClock.wallTime();
        }

        @Override
        public long monotonicTime() {
            return neutralClock.monotonicTime();
        }
    }
}
