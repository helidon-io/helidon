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
package io.helidon.metrics.api;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * No-op implementation of {@link io.helidon.metrics.api.MeterRegistry}.
 */
class NoOpMeterRegistry implements MeterRegistry {

    private final Map<Meter.Id, Meter> meters = new ConcurrentHashMap<>();

    private final ReentrantLock metersAccess = new ReentrantLock();

    @Override
    public List<Meter> meters() {
        return List.of(meters.values().toArray(new Meter[0]));
    }

    @Override
    public Iterable<Meter> meters(Predicate<Meter> filter) {
        return () -> new Iterator<>() {

            private final Iterator<Meter> iter = meters.values().iterator();
            private Meter nextMatch = nextMatch();

            private Meter nextMatch() {
                while (iter.hasNext()) {
                    Meter candidate = iter.next();
                    if (filter.test(candidate)) {
                        return candidate;
                    }
                }
                return null;
            }

            @Override
            public boolean hasNext() {
                return nextMatch != null;
            }

            @Override
            public Meter next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Meter result = nextMatch;
                nextMatch = nextMatch();
                return result;
            }
        };
    }

    @Override
    public Meter remove(Meter.Id id) {
        return meters.remove(id);
    }

    @Override
    public Meter remove(Meter meter) {
        return meters.remove(meter.id());
    }

    @Override
    public Meter remove(String name, Iterable<Tag> tags) {
        return remove(NoOpMeter.Id.create(name, tags));
    }

    @Override
    public Counter counter(Meter.Id id) {
        return findOrRegister(id,
                              Counter.class,
                              () -> NoOpMeter.Counter.builder(id.name())
                                      .tags(id.tags())
                                      .build());
    }

    @Override
    public <T> Counter counter(Meter.Id id,
                               T target,
                               ToDoubleFunction<T> fn) {
        return findOrRegister(id,
                              Counter.class,
                              () -> NoOpMeter.FunctionalCounter.builder(id.name(), target, fn)
                                      .tags(id.tags())
                                      .build());
    }

    @Override
    public <T> Counter counter(String name,
                               Iterable<Tag> tags,
                               String baseUnit,
                               String description,
                               T target,
                               ToDoubleFunction<T> fn) {
        return findOrRegister(NoOpMeter.Id.create(name, tags),
                              Counter.class,
                              () -> NoOpMeter.FunctionalCounter.builder(name, target, fn)
                                      .tags(tags)
                                      .baseUnit(baseUnit)
                                      .description(description)
                                      .build());
    }

    @Override
    public <T> Counter counter(Meter.Id id,
                               String baseUnit,
                               String description,
                               T target,
                               ToDoubleFunction<T> fn) {
        return findOrRegister(id,
                              Counter.class,
                              () -> NoOpMeter.FunctionalCounter.builder(id.name(), target, fn)
                                      .tags(id.tags())
                                      .baseUnit(baseUnit)
                                      .description(description)
                                      .build());
    }

    @Override
    public Optional<Counter> getCounter(String name, Iterable<Tag> tags) {
        return find(NoOpMeter.Id.create(name, tags), Counter.class);
    }


    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        return findOrRegister(NoOpMeter.Id.create(name, tags),
                              Counter.class,
                              () -> NoOpMeter.Counter.builder(name)
                                      .tags(tags)
                                      .build());
    }

    @Override
    public Counter counter(String name, String... tags) {
        Meter.Id id = NoOpMeter.Id.create(name, NoOpTag.tags(tags));
        return findOrRegister(id,
                              Counter.class,
                              () -> NoOpMeter.Counter.builder(name)
                                      .tags(id.tags())
                                      .build());
    }

    @Override
    public Counter counter(String name,
                           Iterable<Tag> tags,
                           String baseUnit,
                           String description) {
        return findOrRegister(NoOpMeter.Id.create(name, tags),
                              Counter.class,
                              () -> NoOpMeter.Counter.builder(name)
                                      .tags(tags)
                                      .baseUnit(baseUnit)
                                      .description(description)
                                      .build());
    }

    @Override
    public <T> Counter counter(String name,
                               Iterable<Tag> tags,
                               T target,
                               ToDoubleFunction<T> fn) {
        return findOrRegister(NoOpMeter.Id.create(name, tags),
                              Counter.class,
                              () -> NoOpMeter.Counter.builder(name, target, fn)
                                      .tags(tags)
                                      .build());
    }

    @Override
    public Optional<Counter> getCounter(String name, String... tags) {
        return find(NoOpMeter.Id.create(name, NoOpTag.tags(tags)),
                    Counter.class);
    }



    @Override
    public DistributionSummary summary(String name, Iterable<Tag> tags) {
        return findOrRegister(NoOpMeter.Id.create(name, tags),
                              DistributionSummary.class,
                              () -> NoOpMeter.DistributionSummary.builder(name)
                                      .tags(tags)
                                      .build());
    }

    @Override
    public DistributionSummary summary(Meter.Id id,
                                       String baseUnit,
                                       String description,
                                       DistributionStatisticsConfig distributionStatisticsConfig,
                                       double scale) {
        return findOrRegister(id,
                              DistributionSummary.class,
                              () -> NoOpMeter.DistributionSummary.builder(id.name())
                                      .baseUnit(baseUnit)
                                      .description(description)
                                      .build());
    }

    @Override
    public Optional<DistributionSummary> getSummary(String name, Iterable<Tag> tags) {
        return find(NoOpMeter.Id.create(name, tags),
                    DistributionSummary.class);
    }

    @Override
    public Optional<DistributionSummary> getSummary(String name, Tag... tags) {
        return find(NoOpMeter.Id.create(name, tags),
                    DistributionSummary.class);
    }



    @Override
    public <T> T gauge(String name, T stateObject, ToDoubleFunction<T> valueFunction) {
        // We don't need a variant of the builder to handle the state object and function because the no-op gauges
        // don't need to operate anyway.
        findOrRegister(NoOpMeter.Id.create(name, Set.of()),
                              Gauge.class,
                              () -> NoOpMeter.Gauge.builder(name)
                                      .build());
        return stateObject;
    }

    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T stateObject, ToDoubleFunction<T> valueFunction) {
        findOrRegister(NoOpMeter.Id.create(name, tags),
                       Gauge.class,
                       () -> NoOpMeter.Gauge.builder(name)
                               .build());
        return stateObject;
    }

    @Override
    public <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
        findOrRegister(NoOpMeter.Id.create(name, tags),
                       Gauge.class,
                       () -> NoOpMeter.Gauge.builder(name)
                               .build());
        return number;
    }

    @Override
    public <T extends Number> T gauge(String name, T number) {
        findOrRegister(NoOpMeter.Id.create(name, Set.of()),
                       Gauge.class,
                       () -> NoOpMeter.Gauge.builder(name)
                               .build());
        return number;
    }

    @Override
    public <T> T gauge(String name,
                       Iterable<Tag> tags,
                       String baseUnit,
                       String description,
                       T stateObject,
                       ToDoubleFunction<T> valueFunction) {
        findOrRegister(NoOpMeter.Id.create(name, tags),
                       Gauge.class,
                       () -> NoOpMeter.Gauge.builder(name)
                               .baseUnit(baseUnit)
                               .description(description)
                               .build());
        return stateObject;
    }

    @Override
    public Optional<Gauge> getGauge(String name, Iterable<Tag> tags) {
        return find(NoOpMeter.Id.create(name, tags),
                    Gauge.class);
    }

    @Override
    public Optional<Gauge> getGauge(String name, Tag... tags) {
        return find(NoOpMeter.Id.create(name, tags),
                    Gauge.class);
    }

    @Override
    public DistributionSummary summary(String name, String... tags) {
        Meter.Id id = NoOpMeter.Id.create(name, NoOpTag.tags(tags));
        return findOrRegister(id,
                              DistributionSummary.class,
                              () -> NoOpMeter.DistributionSummary.builder(name)
                                      .tags(id.tags())
                                      .build());
    }

    @Override
    public Timer timer(String name, Iterable<Tag> tags) {
        return findOrRegister(NoOpMeter.Id.create(name, tags),
                              Timer.class,
                              () -> NoOpMeter.Timer.builder(name)
                                      .tags(tags)
                                      .build());
    }

    @Override
    public Timer timer(String name, String... tags) {
        Meter.Id id = NoOpMeter.Id.create(name, NoOpTag.tags(tags));
        return findOrRegister(id,
                              Timer.class,
                              () -> NoOpMeter.Timer.builder(name)
                                      .tags(id.tags())
                                      .build());
    }

    @Override
    public Timer timer(String name,
                       Iterable<Tag> tags,
                       String baseUnit,
                       String description,
                       DistributionStatisticsConfig distributionStatisticsConfig) {
        // The distribution is also a no-op, so we ignore the statistics config.
        return findOrRegister(NoOpMeter.Id.create(name, tags),
                              Timer.class,
                              () -> NoOpMeter.Timer.builder(name)
                                      .baseUnit(baseUnit)
                                      .description(description)
                                      .build());
    }

    @Override
    public Optional<Timer> getTimer(String name, Tag... tags) {
        return find(NoOpMeter.Id.create(name, tags),
                    Timer.class);
    }

    @Override
    public Optional<Timer> getTimer(String name, Iterable<Tag> tags) {
        return find(NoOpMeter.Id.create(name, tags),
                    Timer.class);
    }

    private <M extends Meter> Optional<M> find(Meter.Id id, Class<M> mClass) {
        return Optional.ofNullable(mClass.cast(meters.get(id)));
    }

    private <M extends Meter> M findOrRegister(Meter.Id id, Class<M> mClass, Supplier<M> meterSupplier) {
        // This next step is atomic because we are using a ConcurrentHashMap.
        Meter result = meters.computeIfAbsent(id,
                                              theId -> meterSupplier.get());

        // Check the type in case we retrieved a previously-registered meter with the specified ID. The type will always
        // be correct if we ran the supplier, in which this test is unneeded by mostly harmless.
        // We could just attempt the cast and let Java throw a class cast exception itself if needed, but this is nicer.
        if (!mClass.isInstance(result)) {
            throw new IllegalArgumentException(
                    String.format("Found previously-registered meter with ID %s of type %s when expecting %s",
                                  id,
                                  result.getClass().getName(),
                                  mClass.getName()));
        }

        return mClass.cast(meters.put(id, meterSupplier.get()));
    }
}
