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

import io.helidon.metrics.api.MetricsConfig;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

class MMeterRegistry implements io.helidon.metrics.api.MeterRegistry {

    private static final System.Logger LOGGER = System.getLogger(MMeterRegistry.class.getName());

    static MMeterRegistry create(MeterRegistry meterRegistry) {
        return new MMeterRegistry(meterRegistry);
    }

    static MMeterRegistry create(MetricsConfig metricsConfig) {
        return new MMeterRegistry(new PrometheusMeterRegistry(new PrometheusConfig() {
            @Override
            public String get(String key) {
                return metricsConfig.lookupConfig(key).orElse(null);
            }
        }));
    }

    private final MeterRegistry delegate;

    private final ConcurrentHashMap<Meter, io.helidon.metrics.api.Meter> meters = new ConcurrentHashMap<>();

    private MMeterRegistry(MeterRegistry delegate) {
        this.delegate = delegate;
        delegate.config()
                .onMeterAdded(this::recordAdd)
                .onMeterRemoved(this::recordRemove);
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
    public <HM extends io.helidon.metrics.api.Meter,
            HB extends io.helidon.metrics.api.Meter.Builder<HB, HM>> HM getOrCreate(HB builder) {

        // The Micrometer builders do not have a shared inherited declaration of the register method.
        // Each type of builder declares its own so we need to decide here which specific one to invoke.
        // That's so we can invoke the Micrometer builder's register method, which acts as
        // get-or-create.
        Meter meter;

        // Micrometer itself will throw an IllegalArgumentException if the caller specifies a builder that finds an existing
        // meter but it is of the wrong type.

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
        Meter match;

        if (io.helidon.metrics.api.Counter.class.isAssignableFrom(mClass)) {
            match = search.counter();
        } else if (io.helidon.metrics.api.DistributionSummary.class.isAssignableFrom(mClass)) {
            match = search.summary();
        } else if (io.helidon.metrics.api.Gauge.class.isAssignableFrom(mClass)) {
            match = search.gauge();
        } else if (io.helidon.metrics.api.Timer.class.isAssignableFrom(mClass)) {
            match = search.timer();
        } else {
            throw new IllegalArgumentException(
                    String.format("Provided class %s  is not recognized", mClass.getName()));
        }
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
}
