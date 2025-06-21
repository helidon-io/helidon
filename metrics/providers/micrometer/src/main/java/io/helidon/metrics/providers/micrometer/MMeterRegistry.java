/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.spi.MetersProvider;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
 * meter registry. The Micrometer registry records the meter and then invokes a callback to us, passing the new Micrometer
 * meter. Based on the type of the new Micrometer meter we instantiate the correct type of Helidon meter as a wrapper around
 * the new new Micrometer meter. We then provisionally update some of our internal data structures as part of the callback.
 * </p>
 * <p>
 * After our callback returns to Micrometer and then Micrometer returns to us, we do some final touch-up to our data
 * structures as needed and return the new Helidon meter to the developer's code which invoked getOrCreate.
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

    /**
     * Once a Micrometer meter is registered, this map records the corresponding Helidon meter wrapper for it. This allows us,
     * for example, to return to the developer's code the proper Helidon wrapper meter during searches that return the same
     * Micrometer meters, rather than creating new Helidon wrapper meters each time.
     */
    private final Map<Meter, MMeter> meters = new HashMap<>();

    private final Map<String, Map<io.helidon.metrics.api.Meter.Id, MMeter.Builder<?, ?, ?, ?>>> buildersByPromMeterId =
            new HashMap<>();
    private final Map<String, Set<io.helidon.metrics.api.Meter>> scopeMembership = new HashMap<>();

    private final Map<io.helidon.metrics.api.Meter.Id, MMeter<?>> metersById = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private MMeterRegistry(io.micrometer.core.instrument.MeterRegistry delegate,
                           MicrometerMetricsFactory metricsFactory,
                           MetricsConfig metricsConfig,
                           Clock clock) {
        this.delegate = delegate;
        this.clock = clock;
        this.metricsFactory = metricsFactory;
        this.metricsConfig = metricsConfig;
    }

    static Builder builder(
            MeterRegistry delegate,
            MicrometerMetricsFactory metricsFactory) {

        return new Builder(delegate, metricsFactory);
    }

    static MMeterRegistry applyMetersProvidersToRegistry(MetricsFactory factory,
                                                         MMeterRegistry registry,
                                                         Collection<MetersProvider> metersProviders) {
        metersProviders.stream()
                .flatMap(mp -> mp.meterBuilders(factory).stream())
                .forEach(registry::getOrCreateUntyped);

        return registry;
    }

    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            onAddListeners.clear();
            onRemoveListeners.clear();
            List.copyOf(meters.values()).forEach(this::remove);
            meters.clear();
            buildersByPromMeterId.clear();
            scopeMembership.clear();
            metersById.clear();
        } finally {
            lock.writeLock().unlock();
        }
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
    public Iterable<String> scopes() {
        lock.readLock().lock();
        try {
            return new HashSet<>(scopeMembership.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isMeterEnabled(String name, Map<String, String> tags, Optional<String> scope) {
        /*
        This method uses only config, not any mutable data structures, so no need to lock.
         */
        String effectiveScope = scope.orElse(SystemTagsManager.instance().effectiveScope(scope)
                                                     .orElse(io.helidon.metrics.api.Meter.Scope.DEFAULT));
        return metricsConfig.enabled()
                && metricsConfig.isMeterEnabled(name, effectiveScope);
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public <HB extends io.helidon.metrics.api.Meter.Builder<HB, HM>,
            HM extends io.helidon.metrics.api.Meter> HM getOrCreate(HB builder) {
        // Just cast the builder if it "one of ours" because that means it was prepared using our MetricsFactory, and that
        // would already have set the builder up with the correct Micrometer builder delegate.
        if (builder instanceof MMeter.Builder<?, ?, ?, ?> mBuilder) {
            return (HM) getOrCreateUntyped((HB) mBuilder);
        }

        // If this is not "one of ours" then we need to create a new builder, based on the one passed in but with the correct
        // Micrometer delegate builder assigned.
        return (HM) getOrCreateUntyped(convertNeutralBuilder(builder));
    }

    @Override
    public <M extends io.helidon.metrics.api.Meter> Optional<M> meter(Class<M> mClass,
                                                                      String name,
                                                                      Iterable<io.helidon.metrics.api.Tag> tags) {

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
        return internalRemove(meter.id(), meter.scope());
    }

    @Override
    public Optional<io.helidon.metrics.api.Meter> remove(io.helidon.metrics.api.Meter.Id id) {
        return internalRemove(id, Optional.empty());
    }

    @Override
    public Optional<io.helidon.metrics.api.Meter> remove(String name, Iterable<io.helidon.metrics.api.Tag> tags) {
        return internalRemove(MMeter.PlainId.create(name, tags), Optional.empty());
    }

    @Override
    public Optional<io.helidon.metrics.api.Meter> remove(io.helidon.metrics.api.Meter.Id id, String scope) {
        return internalRemove(id, Optional.ofNullable(scope));
    }

    @Override
    public Optional<io.helidon.metrics.api.Meter> remove(String name, Iterable<io.helidon.metrics.api.Tag> tags, String scope) {
        return internalRemove(MMeter.PlainId.create(name, tags), Optional.ofNullable(scope));
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
    public Iterable<io.helidon.metrics.api.Meter> meters(Iterable<String> scopeSelection) {
        lock.readLock().lock();
        try {
            if (scopeSelection.iterator().hasNext()) {
                Set<io.helidon.metrics.api.Meter> result = new HashSet<>();
                for (String scope : scopeSelection) {
                    if (scopeMembership.containsKey(scope)) {
                        result.addAll(scopeMembership.get(scope));
                    }
                }
                return result;
            }
            return meters();
        } finally {
            lock.readLock().unlock();
        }
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

    void erase() {
        lock.writeLock().lock();

        try {
            buildersByPromMeterId.clear();
            meters.clear();
            onAddListeners.clear();
            onRemoveListeners.clear();
            scopeMembership.clear();
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

        io.helidon.metrics.api.Meter helidonMeter;

        if (builder instanceof MCounter.Builder cBuilder) {
            helidonMeter = getOrCreate(cBuilder, cBuilder::addTag, cBuilder.delegate()::register);
        } else if (builder instanceof MFunctionalCounter.Builder<?> fcBuilder) {
            helidonMeter = getOrCreate(fcBuilder, fcBuilder::addTag, fcBuilder.delegate()::register);
        } else if (builder instanceof MDistributionSummary.Builder sBuilder) {
            helidonMeter = getOrCreate(sBuilder, sBuilder::addTag, sBuilder.delegate()::register);
        } else if (builder instanceof MGauge.Builder gBuilder) {
            helidonMeter = getOrCreate(gBuilder, gBuilder::addTag, ((MGauge.Builder<?, ?>) gBuilder).delegate()::register);
        } else if (builder instanceof MTimer.Builder tBuilder) {
            helidonMeter = getOrCreate(tBuilder, tBuilder::addTag, tBuilder.delegate()::register);
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

    <HM extends MMeter<M>, M extends Meter, B, HB extends MMeter.Builder<B, M, HB, HM>> void onMeterAdded(M addedMeter) {

        /*
        We are not guaranteed that one of our own update operations--which would already hold the write lock--is triggering
        this callback from Micrometer. A developer might be using the Micrometer API directly, for example. So acquire the lock
        in any case.
         */

        lock.writeLock().lock();
        try {
            /*
            If we originated this callback by invoking the delegate registry, then there should be a builder
            waiting for us to use. If the meter was created in some other way, then there will be no builder and
            we will create the MMeter from the meter passed to us by Micrometer..
             */

            io.helidon.metrics.api.Meter.Id neutralIdForAddedMeter = neutralIdWithoutSystemTags(addedMeter.getId());

            /*
             We do not have an explicit scope so get the scope from the tags of the new meter if we can.
             */
            Optional<String> scope = SystemTagsManager.instance()
                    .effectiveScope(Optional.empty(), neutralIdForAddedMeter.tags());

            /*
             See if there is any "pending builder" that we already created in getOrCreate and use it to create this new meter.
             */
            Map<io.helidon.metrics.api.Meter.Id, MMeter.Builder<?, ?, ?, ?>> buildersInScope =
                    buildersByPromMeterId.get(scope.orElse(""));
            MMeter<M> mMeter;

            MMeter.Builder<B, M, HB, HM> builder = null;
            if (buildersInScope != null) {
                builder = (MMeter.Builder<B, M, HB, HM>) buildersInScope.get(neutralIdForAddedMeter);
            }

            /*
             Figure out the real scope: prefer what is in the builder (if we even have a builder), if none then use a scope from
             the tags in the added meter, and lastly use the default from configuration.
             */
            scope = chooseScope(addedMeter, builder != null ? builder.scope() : Optional.empty());

            io.helidon.metrics.api.Meter.Id id;

            if (builder == null) {
                if (scope.isEmpty()) {
                    LOGGER.log(Level.DEBUG, "Processing meter creation with no scope from the meter or configuration: "
                            + addedMeter.getId());
                }
                id = neutralIdForAddedMeter;
                mMeter = MMeter.create(id, addedMeter, scope);
            } else {
                id = builder.id();
                scope.ifPresent(builder::scope);
                mMeter = builder.build(id, addedMeter);
            }

            recordNewMeter(id, mMeter, addedMeter, scope);

            /*
             Signal to getOrCreate that in fact a new delegate meter was created (because we are in this method at all).
             */
            if (buildersInScope != null) {
                buildersInScope.remove(id);
            }

            onAddListeners.forEach(listener -> {
                listener.accept(mMeter);
            });

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

    private io.helidon.metrics.api.Meter noopMeterIfDisabled(io.helidon.metrics.api.Meter.Builder<?, ?> builder) {
        if (!isMeterEnabled(builder.name(), builder.tags(), builder.scope())) {

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
                                                                           Function<Tag, ?> builderTagSetter,
                                                                           Function<io.micrometer.core.instrument.MeterRegistry,
                                                                                   M> registration) {

        // Select the actual scope value from the builder (if any) or a default scope value known to the system tags manager.
        Optional<String> effectiveScope = SystemTagsManager.instance()
                .effectiveScope(mBuilder.scope());

        // If there is a usable scope value, add a tag to the builder if configuration has a scope tag name.
        effectiveScope.ifPresent(realScope -> SystemTagsManager.instance()
                .assignScope(realScope, builderTagSetter));

        io.helidon.metrics.api.Meter.Id id = mBuilder.id();

        lock.readLock().lock();

        try {
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
            io.helidon.metrics.api.Meter previouslyRegisteredMeter = meterIfRegistered(mBuilder, id);
            if (previouslyRegisteredMeter != null) {
                return previouslyRegisteredMeter;
            }

            Map<io.helidon.metrics.api.Meter.Id, MMeter.Builder<?, ?, ?, ?>> pendingBuildersInScope =
                    buildersByPromMeterId.computeIfAbsent(effectiveScope.orElse(""),
                                                          k -> new HashMap<>());
            MMeter.Builder<?, ?, ?, ?> previousBuilderWithId = pendingBuildersInScope.put(id, mBuilder);
            if (previousBuilderWithId != null) {
                LOGGER.log(Level.WARNING,
                           "Unexpected overwrite of existing pending builder " + previousBuilderWithId
                           + " during creation of new meter " + mBuilder);
            }

            M meter = registration.apply(delegate());

            /*
             Normally, the on-add listener will have removed the pending builder in scope, but do so here again if the listener
             did not run--if the meter already exists in the Micrometer meter registry, for example.
             */
            pendingBuildersInScope.remove(id);

            HM result = (HM) meters.get(meter);
            if (result == null) {
                /*
                Our on-add listener never ran, so the delegate registry must have found a pre-existing meter when we asked it
                but we have no record of that meter being linked to one of ours. This is surprising, but go ahead and wrap
                the pre-existing meter with a neutral Helidon meter and go on.
                */

                LOGGER.log(Level.WARNING,
                           "Unexpected discovery of unknown previously-created meter; creating wrapper for " + meter.getId());
                result = wrapMeter(id, meter, mBuilder.scope());
                recordNewMeter(id, result, meter, effectiveScope);
            }

            return result;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private <M extends Meter,
            HB extends MMeter.Builder<?, M, HB, HM>,
            HM extends MMeter<M>> HM wrapMeter(io.helidon.metrics.api.Meter.Id id,
                                               M addedMeter,
                                               Optional<String> scope) {

        MMeter<?> helidonMeter = null;
        Optional<String> scopeToUse = chooseScope(addedMeter, scope);
        if (addedMeter instanceof Counter counter) {
            helidonMeter = MCounter.create(id, counter, scopeToUse);
        } else if (addedMeter instanceof DistributionSummary summary) {
            helidonMeter = MDistributionSummary.create(id, summary, scopeToUse);
        } else if (addedMeter instanceof Gauge gauge) {
            helidonMeter = MGauge.create(id, gauge, scopeToUse);
        } else if (addedMeter instanceof Timer timer) {
            helidonMeter = MTimer.create(id, timer, scopeToUse);
        } else if (addedMeter instanceof FunctionCounter functionCounter) {
            helidonMeter = MFunctionalCounter.create(id, functionCounter, scopeToUse);
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

    private Optional<String> chooseScope(Meter meter, Optional<String> reliableScope) {
        // The so-called "reliable" scope typically comes from a Helidon meter builder if a builder is available to the caller.
        // IF the reliable scope is present then it is reliable because the developer set it.
        //
        // The scope from the meter itself might not be reliable. For example, a Micrometer meter registered directly with the
        // Micrometer registry by the developer is not guaranteed to follow the scope tagging conventions Helidon expects.
        // When Micrometer invokes our "on-add" callback, the callback creates a Helidon meter to wrap the Micrometer one, in
        // the processing looking for the scope tag according to our convention in the Micrometer meter's tags.
        // The callback uses that tag value--if present--to set the scope in the Helidon meter it creates as a wrapper. If the
        // developer went to the trouble to create a Micrometer meter with tags following our scope tagging convention, then the
        // callback respects that and use the value from the tag to set the Helidon meter's scope.
        //
        // This method figures out and what scope truly applies to this meter, as best it can.

        return reliableScope
                .or(() ->
                            metricsConfig.scoping()
                                    .tagName()
                                    .map(scopeTagName -> meter.getId().getTag(scopeTagName))
                                    .filter(scopeTagValue -> !scopeTagValue.isBlank()))
                .or(metricsConfig.scoping()::defaultValue);
    }

    /**
     * Remove the meter specified by the ID, using the provided scope (if present) as the scope of the meter.
     *
     * @param id    {@link io.helidon.metrics.api.Meter.Id} for the meter to remove
     * @param scope definitive scope (if present)
     * @return the removed meter if it was found to be removed; empty otherwise
     */
    private Optional<io.helidon.metrics.api.Meter> internalRemove(io.helidon.metrics.api.Meter.Id id,
                                                                  Optional<String> scope) {

        // Create an ID to use for searching. It will need to have the scope tag if one was specified in the original ID's tags
        // or if the system tags manager says that a scope tag is enabled.

        Iterable<io.helidon.metrics.api.Tag> tags = SystemTagsManager.instance().withScopeTag(id.tags(), scope);

        lock.writeLock().lock();

        try {
            Meter nativeMeter = delegate.find(id.name())
                    .tags(MTag.tags(tags))
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
        return MMeter.PlainId.create(micrometerId.getName(),
                                     SystemTagsManager.instance().withoutSystemTags(MTag.neutralTags(micrometerId.getTags())));
    }

    private void recordNewMeter(io.helidon.metrics.api.Meter.Id id,
                                MMeter<?> newNeutralMeter,
                                Meter delegate,
                                Optional<String> scope) {
        meters.put(delegate, newNeutralMeter);
        metersById.put(id, newNeutralMeter);
        scope.ifPresent(s -> scopeMembership.computeIfAbsent(s, key -> new HashSet<>())
                .add(newNeutralMeter));
    }

    private MMeter<?> recordRemove(MMeter<?> removedHelidonMeter) {

        metersById.remove(removedHelidonMeter.id());
        removedHelidonMeter.markAsDeleted();
        removedHelidonMeter.scope().ifPresent(scope -> {
            Set<io.helidon.metrics.api.Meter> scopeMembers = scopeMembership.get(scope);
            if (scopeMembers != null) {
                scopeMembers.remove(removedHelidonMeter);
            }
        });
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

        private final MeterRegistry delegate;
        private final MicrometerMetricsFactory metricsFactory;
        private final Collection<MetersProvider> metersProviders = new ArrayList<>();
        private MetricsConfig metricsConfig;
        private Optional<Clock> clock = Optional.empty();
        private Optional<Consumer<io.helidon.metrics.api.Meter>> onAddListener = Optional.empty();
        private Optional<Consumer<io.helidon.metrics.api.Meter>> onRemoveListener = Optional.empty();

        private Builder(MeterRegistry delegate, MicrometerMetricsFactory metricsFactory) {
            this.delegate = delegate;
            this.metricsFactory = metricsFactory;
            this.metricsConfig = metricsFactory.metricsConfig();
        }

        B metersProviders(Collection<MetersProvider> metersProviders) {
            this.metersProviders.clear();
            this.metersProviders.addAll(metersProviders);
            return identity();
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

            MMeterRegistry result = new MMeterRegistry(delegate,
                                                       metricsFactory,
                                                       metricsConfig,
                                                       clock.orElse(MClock.create(delegate.config().clock())));

            onAddListener.ifPresent(result::onMeterAdded);
            onRemoveListener.ifPresent(result::onMeterRemoved);

            return (R) applyMetersProvidersToRegistry(metricsFactory, result, metersProviders);
        }
    }

}
