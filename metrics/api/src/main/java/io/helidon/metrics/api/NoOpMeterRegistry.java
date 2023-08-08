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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

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
    public Collection<Meter> meters(Predicate<Meter> filter) {
        List<Meter> result = new ArrayList<>();
        meters.values()
                .forEach(m -> {
                    if (filter.test(m)) {
                        result.add(m);
                    }
                });
        return result;
    }

    @Override
    public Optional<Meter> remove(Meter.Id id) {
        return Optional.ofNullable(meters.remove(id));
    }

    @Override
    public Optional<Meter> remove(Meter meter) {
        return Optional.ofNullable(meters.remove(meter.id()));
    }

    @Override
    public Optional<Meter> remove(String name, Iterable<Tag> tags) {
        return remove(NoOpMeter.Id.create(name, tags));
    }

    @Override
    public <M extends Meter> Optional<M> get(Class<M> mClass, String name, Iterable<Tag> tags) {
        return Optional.ofNullable(mClass.cast(meters.get(NoOpMeter.Id.create(name, tags))));
    }

    @Override
    public <M extends Meter, B extends Meter.Builder<B, M>> M getOrCreate(B builder) {
        NoOpMeter.Builder<?, ?> b = (NoOpMeter.Builder<?, ?>) builder;
        return findOrRegister(NoOpMeter.Id.create(b.name(),
                                                  b.tags()),
                              builder);
    }

    private <M extends Meter> Optional<M> find(Meter.Id id, Class<M> mClass) {
        return Optional.ofNullable(mClass.cast(meters.get(id)));
    }

    private <M extends Meter, B extends Meter.Builder<B, M>> M findOrRegister(Meter.Id id, B builder) {
        NoOpMeter.Builder<?, ?> noOpBuilder = (NoOpMeter.Builder<?, ?>) builder;
        return (M) meters.computeIfAbsent(id,
                                          thidId -> noOpBuilder.build());
    }
// TODO
//    private <M extends Meter> M findOrRegister(Meter.Id id, Class<M> mClass, Supplier<M> meterSupplier) {
//        // This next step is atomic because we are using a ConcurrentHashMap.
//        Meter result = meters.computeIfAbsent(id,
//                                              theId -> meterSupplier.get());
//
//        // Check the type in case we retrieved a previously-registered meter with the specified ID. The type will always
//        // be correct if we ran the supplier, in which this test is unneeded by mostly harmless.
//        // We could just attempt the cast and let Java throw a class cast exception itself if needed, but this is nicer.
//        if (!mClass.isInstance(result)) {
//            throw new IllegalArgumentException(
//                    String.format("Found previously-registered meter with ID %s of type %s when expecting %s",
//                                  id,
//                                  result.getClass().getName(),
//                                  mClass.getName()));
//        }
//
//        return mClass.cast(meters.put(id, meterSupplier.get()));
//    }
}
