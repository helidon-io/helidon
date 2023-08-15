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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.MetricsConfig;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.prometheus.PrometheusMeterRegistry;

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

    private final ConcurrentHashMap<Meter, io.helidon.metrics.api.Meter> meters = new ConcurrentHashMap<>();

    private MMeterRegistry(MeterRegistry delegate,
                           Clock clock,
                           MetricsConfig metricsConfig) {
        this.delegate = delegate;
        this.clock = clock;
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
    public Clock clock() {
        return clock;
    }

    @Override
    public <HM extends io.helidon.metrics.api.Meter,
            HB extends io.helidon.metrics.api.Meter.Builder<HB, HM>> HM getOrCreate(HB builder) {


        // The Micrometer builders do not have a shared inherited declaration of the register method.
        // Each type of builder declares its own so we need to decide here which specific one to invoke.
        // That's so we can invoke the Micrometer builder's register method, which acts as
        // get-or-create.
        // Micrometer's register methods will throw an IllegalArgumentException if the caller specifies a builder that finds
        // a previously-registered meter of a different type from that implied by the builder.

        Meter meter;
        // TODO Convert to switch instanceof expressions once checkstyle understand the syntax.
        if (builder instanceof MCounter.Builder cBuilder) {
            Counter counter = cBuilder.delegate().register(delegate);
            meter = counter;
        } else if (builder instanceof MDistributionSummary.Builder sBuilder) {
            DistributionSummary summary = sBuilder.delegate().register(delegate);
            meter = summary;
        } else if (builder instanceof MGauge.Builder<?> gBuilder) {
            Gauge gauge = gBuilder.delegate().register(delegate);
            meter = gauge;
        } else if (builder instanceof MTimer.Builder tBuilder) {
            Timer timer = tBuilder.delegate().register(delegate);
            meter = timer;
        } else {
            throw new IllegalArgumentException(String.format("Unexpected builder type %s, expected one of %s",
                                                             builder.getClass().getName(),
                                                             List.of(MCounter.Builder.class.getName(),
                                                                     MDistributionSummary.Builder.class.getName(),
                                                                     MGauge.Builder.class.getName(),
                                                                     MTimer.Builder.class.getName())));
        }
        return (HM) meters.get(meter);
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
        return internalRemove(id.name(), Util.tags(id.tags()));
    }

    @Override
    public Optional<io.helidon.metrics.api.Meter> remove(String name,
                                               Iterable<io.helidon.metrics.api.Tag> tags) {
        return internalRemove(name, Util.tags(tags));
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return c.cast(delegate);
    }

    MeterRegistry delegate() {
        return delegate;
    }

    private Optional<io.helidon.metrics.api.Meter> internalRemove(String name,
                                                                  Iterable<Tag> tags) {
        Meter nativeMeter = delegate.find(name)
                .tags(tags)
                .meter();
        if (nativeMeter != null) {
            io.helidon.metrics.api.Meter result = meters.get(nativeMeter);
            delegate.remove(nativeMeter);
            return Optional.of(result);
        }
        return Optional.empty();
    }

    private void recordAdd(Meter addedMeter) {
        if (addedMeter instanceof Counter counter) {
            meters.put(addedMeter, MCounter.create(counter));
        } else if (addedMeter instanceof DistributionSummary summary) {
            meters.put(addedMeter, MDistributionSummary.create(summary));
        } else if (addedMeter instanceof Gauge gauge) {
            meters.put(addedMeter, MGauge.create(gauge));
        } else if (addedMeter instanceof Timer timer) {
            meters.put(addedMeter, MTimer.create(timer));
        } else {
            LOGGER.log(System.Logger.Level.WARNING,
                       "Attempt to record addition of unrecognized meter type " + addedMeter.getClass().getName());
        }
    }

    private void recordRemove(Meter removedMeter) {
        io.helidon.metrics.api.Meter removedNeutralMeter = meters.remove(removedMeter);
        if (removedNeutralMeter == null) {
            LOGGER.log(System.Logger.Level.WARNING, "No matching neutral meter for implementation meter " + removedMeter);
        }
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
