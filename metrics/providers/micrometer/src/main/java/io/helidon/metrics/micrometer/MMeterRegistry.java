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
package io.helidon.metrics.micrometer;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Implementation of {@link io.helidon.metrics.api.MeterRegistry} for the Micrometer adapter.
 *
 * <p>
 *     The flow of control here is interesting during new meter registration. Typically a developer uses the Helidon
 *     metrics API to create a builder for a new Helidon meter. That automatically creates that builder's delegate, an instance
 *     of the corresponding Micrometer meter builder (held as a private reference inside the Helidon builder). The developer's
 *     code then invokes this registry's getOrCreate method passing the Helidon metric builder.
 * </p>
 * <p>
 *     This code invokes the Micrometer builder's register method, passing this registry's delegate which is a Micrometer
 *     meter registry. The Micrometer registry records the meter and then invokes a callback to us, passing the new Micrometer
 *     meter. Based on the type of the new Micrometer meter we instantiate the correct type of Helidon meter as a wrapper around
 *     the new new Micrometer meter. We then provisionally update some of our internal data structures as part of the callback.
 * </p>
 * <p>
 *     After our callback returns to Micrometer and then Micrometer returns to us, we do some final touch-up to our data
 *     structures as needed and return the new Helidon meter to the developer's code which invoked getOrCreate.
 * </p>
 * <p>
 *     This is a little convoluted, but this approach allows us to automatically create Helidon meters around every Micrometer
 *     meter, <em>even those which the developer registers directly with Micrometer!</em> That way, queries of the registry
 *     using the Helidon API return the same Helidon meter wrapper instance around a given Micrometer meter, regardless of which
 *     API the developer used to register the meter: ours or Micrometer's.
 * </p>
 */
class MMeterRegistry implements io.helidon.metrics.api.MeterRegistry {

    private static final System.Logger LOGGER = System.getLogger(MMeterRegistry.class.getName());

    /**
     * Creates a new meter registry which wraps the specified Micrometer meter registry, ensuring that if
     * the meter registry is a composite registry it has a Prometheus meter registry attached (adding a new one if needed).
     * <p>
     *     The {@link io.helidon.metrics.api.MetricsConfig} does not override the settings of the pre-existing Micrometer
     *     meter registry but augments the behavior of this wrapper around it, for example specifying
     *     global tags.
     * </p>
     *
     * @param meterRegistry existing Micrometer meter registry to wrap
     * @param metricsConfig metrics config
     * @return new wrapper around the specified Micrometer meter registry
     */
    static MMeterRegistry create(MeterRegistry meterRegistry,
                                 MetricsConfig metricsConfig) {
        // The caller passed a pre-existing meter registry, with its own clock, so wrap that clock
        // with a Helidon clock adapter (MClock).
        return new MMeterRegistry(ensurePrometheusRegistryIsPresent(meterRegistry, metricsConfig),
                                  MClock.create(meterRegistry.config().clock()),
                                  metricsConfig);
    }

    /**
     * Creates a new meter registry which wraps an automatically-created new Micrometer
     * {@link io.micrometer.core.instrument.composite.CompositeMeterRegistry} with a Prometheus meter registry
     * automatically added.
     *
     * @param metricsConfig metrics config
     * @return new wrapper around a new Micrometer composite meter registry
     */
    static MMeterRegistry create(MetricsConfig metricsConfig) {
        CompositeMeterRegistry delegate = new CompositeMeterRegistry();
        return create(ensurePrometheusRegistryIsPresent(delegate, metricsConfig),
                      MClock.create(delegate.config().clock()),
                      metricsConfig);
    }

    /**
     * Creates a new meter registry which wraps an automatically-created new Micrometer
     * {@link io.micrometer.core.instrument.composite.CompositeMeterRegistry} with a Prometheus meter registry
     * automatically added, using the specified clock.
     *
     * @param clock default clock to associate with the new meter registry
     * @param metricsConfig metrics config
     * @return new wrapper around a new Micrometer composite meter registry
     */
    static MMeterRegistry create(Clock clock,
                                 MetricsConfig metricsConfig) {
        CompositeMeterRegistry delegate = new CompositeMeterRegistry(ClockWrapper.create(clock));
        // The specified clock is already a Helidon one so pass it directly; no need to wrap it.
        return create(ensurePrometheusRegistryIsPresent(delegate, metricsConfig),
                      clock,
                      metricsConfig);
    }

    private static MMeterRegistry create(MeterRegistry delegate,
                                         Clock neutralClock,
                                         MetricsConfig metricsConfig) {
        return new MMeterRegistry(delegate, neutralClock, metricsConfig);
    }

    private static MeterRegistry ensurePrometheusRegistryIsPresent(MeterRegistry meterRegistry,
                                                                   MetricsConfig metricsConfig) {
        if (meterRegistry instanceof CompositeMeterRegistry compositeMeterRegistry) {
            if (compositeMeterRegistry.getRegistries()
                    .stream()
                    .noneMatch(r -> r instanceof PrometheusMeterRegistry)) {
                compositeMeterRegistry.add(
                        new PrometheusMeterRegistry(key -> metricsConfig.lookupConfig(key).orElse(null)));
            }
        }
        return meterRegistry;
    }

    private final MeterRegistry delegate;

    /**
     * Helidon API clock to be returned by the {@link #clock} method.
     */
    private final Clock clock;

    private final MetricsConfig metricsConfig;

    /**
     * Once a Micrometer meter is registered, this map records the corresponding Helidon meter wrapper for it. This allows us,
     * for example, to return to the developer's code the proper Helidon wrapper meter during searches that return the same
     * Micrometer meters, rather than creating new Helidon wrapper meters each time.
     */
    private final ConcurrentHashMap<Meter, MMeter<?>> meters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<io.helidon.metrics.api.Meter>> scopeMembership = new ConcurrentHashMap<>();

    private final ReentrantLock lock = new ReentrantLock();

    private MMeterRegistry(MeterRegistry delegate,
                           Clock clock,
                           MetricsConfig metricsConfig) {
        this.delegate = delegate;
        this.clock = clock;
        this.metricsConfig = metricsConfig;
        delegate.config()
                .onMeterAdded(this::recordAdd)
                .onMeterRemoved(this::recordRemove);
        List<io.helidon.metrics.api.Tag> globalTags = metricsConfig.globalTags();
        if (!globalTags.isEmpty()) {
            delegate.config().meterFilter(MeterFilter.commonTags(Util.tags(globalTags)));
        }
    }

    @Override
    public List<? extends io.helidon.metrics.api.Meter> meters() {
        return meters.values().stream().toList();
    }

    @Override
    public Collection<? extends io.helidon.metrics.api.Meter> meters(Predicate<io.helidon.metrics.api.Meter> filter) {
        return meters.values()
                .stream()
                .filter(filter)
                .toList();
    }

    @Override
    public Iterable<String> scopes() {
        return scopeMembership.keySet();
    }

    // TODO enhance after adding back the filtering config
    @Override
    public boolean isMeterEnabled(String name, Iterable<Tag> tags, Optional<String> scope) {
        return metricsConfig.enabled();
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public <HB extends io.helidon.metrics.api.Meter.Builder<HB, HM>,
            HM extends io.helidon.metrics.api.Meter> HM getOrCreate(HB builder) {

        // The Micrometer builders do not have a shared inherited declaration of the register method.
        // Each type of builder declares its own so we need to decide here which specific one to invoke.
        // That's so we can invoke the Micrometer builder's register method, which acts as
        // get-or-create.
        // Micrometer's register methods will throw an IllegalArgumentException if the caller specifies a builder that finds
        // a previously-registered meter of a different type from that implied by the builder.

        lock.lock();

        try {

            // It really would be nice to use switch with instanceof pattern variables as above, but checkstyle outright
            // fails (not even issuing a false report we can make it ignore, just failing with an exception).
            io.helidon.metrics.api.Meter helidonMeter = null;
            if (!isMeterEnabled(builder.name(), builder.tags(), builder.scope())) {
                return (HM) MetricsFactory.getInstance().noOpMeter(builder);
            }
            if (builder instanceof MCounter.Builder cBuilder) {
                helidonMeter = registerAndPostProcess(cBuilder, cBuilder.delegate()::tag, cBuilder.delegate()::register);
            }
            if (builder instanceof MFunctionalCounter.Builder<?> fcBuilder) {
                helidonMeter = registerAndPostProcess(fcBuilder, fcBuilder.delegate()::tag, fcBuilder.delegate()::register);
            }
            if (builder instanceof MDistributionSummary.Builder sBuilder) {
                helidonMeter = registerAndPostProcess(sBuilder, sBuilder.delegate()::tag, sBuilder.delegate()::register);
            }
            if (builder instanceof MGauge.Builder<?> gBuilder) {
                helidonMeter = registerAndPostProcess(gBuilder, gBuilder.delegate()::tag, gBuilder.delegate()::register);
            }
            if (builder instanceof MTimer.Builder tBuilder) {
                helidonMeter = registerAndPostProcess(tBuilder, tBuilder.delegate()::tag, tBuilder.delegate()::register);
            }

            if (helidonMeter != null) {
                return (HM) helidonMeter;
            } else {
                throw new IllegalArgumentException(String.format("Unexpected builder type %s, expected one of %s",
                                                                 builder.getClass().getName(),
                                                                 List.of(MCounter.Builder.class.getName(),
                                                                         MDistributionSummary.Builder.class.getName(),
                                                                         MGauge.Builder.class.getName(),
                                                                         MTimer.Builder.class.getName())));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <M extends io.helidon.metrics.api.Meter> Optional<M> get(Class<M> mClass,
                                                                    String name,
                                                                    Iterable<io.helidon.metrics.api.Tag> tags) {

        Search search = delegate().find(name)
                .tags(Util.tags(tags));
        Meter match = search.meter();

        if (match == null) {
            return Optional.empty();
        }
        io.helidon.metrics.api.Meter neutralMeter = meters.get(match);
        if (neutralMeter == null) {
            LOGGER.log(System.Logger.Level.WARNING, String.format("Found no Helidon counterpart for Micrometer meter %s %s",
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

    }

    @Override
    public Optional<io.helidon.metrics.api.Meter> remove(io.helidon.metrics.api.Meter meter) {
        return remove(meter.id());
    }

    @Override
    public Optional<io.helidon.metrics.api.Meter> remove(io.helidon.metrics.api.Meter.Id id) {
        return internalRemove(id.name(), id.tags());
    }

    @Override
    public Optional<io.helidon.metrics.api.Meter> remove(String name, Iterable<io.helidon.metrics.api.Tag> tags) {
        return internalRemove(name, tags);
    }

    @Override
    public Optional<io.helidon.metrics.api.Meter> remove(io.helidon.metrics.api.Meter.Id id, String scope) {
        return internalRemove(id.name(), id.tags(), scope);
    }

    @Override
    public Optional<io.helidon.metrics.api.Meter> remove(String name, Iterable<io.helidon.metrics.api.Tag> tags, String scope) {
        return internalRemove(name, tags, scope);
    }

    @Override
    public boolean isDeleted(io.helidon.metrics.api.Meter meter) {
        return meter instanceof MMeter<?> helidonMeter && helidonMeter.isDeleted();
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return c.cast(delegate);
    }

    MeterRegistry delegate() {
        return delegate;
    }

    @Override
    public Iterable<? extends io.helidon.metrics.api.Meter> meters(Iterable<String> scopeSelection) {
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
    }

    private Optional<String> effectiveScope(Optional<String> explicitScope) {
        return explicitScope.isPresent()
                ? explicitScope
                : metricsConfig.scopeDefaultValue();
    }

    private <M extends Meter, HB extends MMeter.Builder<?, M, HB, HM>, HM extends MMeter<M>> HM registerAndPostProcess(
            HB mBuilder,
            BiConsumer<String, String> builderTagSetter,
            Function<MeterRegistry, M> registration) {

        if (metricsConfig.scopeTagEnabled()) {
            effectiveScope(mBuilder.scope())
                    .ifPresent(s -> builderTagSetter.accept(metricsConfig.scopeTagName(), s));
        }

        lock.lock();
        try {
            M meter = registration.apply(delegate());

            // The on-register callback from Micrometer has created an entry in the meters map.
            //
            // If scope tagging is turned on then the callback has also updated our wrapper meter's scope setting and added our
            // wrapper meter to the correct scope membership.
            //
            // If scope tagging is NOT on then the callback cannot do those updates so we need to do them here.
            HM helidonMeter = (HM) meters.get(meter);
            updateScope(meter, helidonMeter, mBuilder.scope());
            return helidonMeter;
        } finally {
            lock.unlock();
        }
    }

    private Optional<io.helidon.metrics.api.Meter> internalRemove(String name,
                                                                  Iterable<io.helidon.metrics.api.Tag> tags) {
        return internalRemove(name,
                              tags,
                              metricsConfig.scopeDefaultValue()
                                      .orElse(null));
    }

    private Optional<io.helidon.metrics.api.Meter> internalRemove(String name,
                                                                  Iterable<io.helidon.metrics.api.Tag> tags,
                                                                  String scope) {
        List<io.helidon.metrics.api.Tag> tagList = Util.list(tags);
        if (scope != null && metricsConfig.scopeTagEnabled()) {
            tagList.add(io.helidon.metrics.api.Tag.create(metricsConfig.scopeTagName(), scope));
        }
        Meter nativeMeter = delegate.find(name)
                .tags(Util.tags(tags))
                .meter();

        lock.lock();

        try {
            if (nativeMeter != null) {
                MMeter<?> result = meters.get(nativeMeter);
                delegate.remove(nativeMeter);
                return Optional.of(result);
            }
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }


    private void recordAdd(Meter addedMeter) {

        // Here (and other places) a switch with instanceof pattern variables (see above) would be nice;
        // checkstyle fails with that.
        MMeter<?> helidonMeter = null;
        if (addedMeter instanceof Counter counter) {
            helidonMeter = MCounter.create(counter);
        } else if (addedMeter instanceof DistributionSummary summary) {
            helidonMeter = MDistributionSummary.create(summary);
        } else if (addedMeter instanceof Gauge gauge) {
            helidonMeter = MGauge.create(gauge);
        } else if (addedMeter instanceof Timer timer) {
            helidonMeter = MTimer.create(timer);
        } else if (addedMeter instanceof FunctionCounter functionCounter) {
            helidonMeter = MFunctionalCounter.create(functionCounter);
        }
        if (helidonMeter == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "Attempt to record addition of unrecognized meter type " + addedMeter.getClass().getName());
            return;
        }
        lock.lock();
        try {
            meters.put(addedMeter, helidonMeter);
        } finally {
            lock.unlock();
        }
    }

    private void updateScope(Meter meter, MMeter<?> helidonMeter, Optional<String> reliableScope) {
        String scopeToSet = reliableScope.orElse(helidonMeter.scope()
                                                         .orElse(null));

        if (scopeToSet == null && metricsConfig.scopeTagEnabled()) {
            scopeToSet = meter.getId().getTag(metricsConfig.scopeTagName());
        }
        if (scopeToSet == null) {
            scopeToSet = metricsConfig.scopeDefaultValue().orElse(null);
        }
        String fixedScopeToSet = scopeToSet;
        if (scopeToSet != null) {
            if (reliableScope.isPresent()) {
                // The meter might have already been added to the wrong scope membership. This will happen if the meter is being
                // registered via the Helidon API and the meter has a scope different from the default. In such cases remove the
                // meter from the wrong membership and add it to the correct one.
                helidonMeter.scope().ifPresent(scopeInMeter -> {
                    if (!fixedScopeToSet.equals(scopeInMeter)) {
                        Set<io.helidon.metrics.api.Meter> scopeMembers = scopeMembership.get(scopeInMeter);
                        if (scopeMembers != null) {
                            scopeMembers.remove(helidonMeter);
                        }
                    }
                });
            }
            helidonMeter.scope(scopeToSet);
            scopeMembership.computeIfAbsent(scopeToSet, s -> new HashSet<>())
                    .add(helidonMeter);
        }
    }

    private void recordRemove(Meter removedMeter) {
        MMeter<?> removedHelidonMeter = meters.remove(removedMeter);
        if (removedHelidonMeter == null) {
            LOGGER.log(System.Logger.Level.WARNING, "No matching neutral meter for implementation meter " + removedMeter);
        } else {
            recordRemove(removedHelidonMeter);
        }
    }

    private MMeter<?> recordRemove(MMeter<?> removedHelidonMeter) {

        removedHelidonMeter.markAsDeleted();
        removedHelidonMeter.scope().ifPresent(scope -> {
            Set<io.helidon.metrics.api.Meter> scopeMembers = scopeMembership.get(scope);
            if (scopeMembers != null) {
                scopeMembers.remove(removedHelidonMeter);
            }
        });
        return removedHelidonMeter;
    }

    /**
     * Micrometer-friendly wrapper around a Helidon clock.
     */
    private static class ClockWrapper implements io.micrometer.core.instrument.Clock {

        static ClockWrapper create(Clock clock) {
            return new ClockWrapper(clock);
        }

        private final Clock neutralClock;

        private ClockWrapper(Clock neutralClock) {
            this.neutralClock = neutralClock;
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
