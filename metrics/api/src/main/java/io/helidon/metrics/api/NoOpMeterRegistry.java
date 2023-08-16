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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * No-op implementation of {@link io.helidon.metrics.api.MeterRegistry}.
 * <p>
 *     Note that the no-op meter registry implementation <em>does not</em> actually
 *     store meters or their IDs, in line with the documented behavior of disabled metrics.
 * </p>
 */
class NoOpMeterRegistry implements MeterRegistry, NoOpWrapper {

    @Override
    public List<Meter> meters() {
        return List.of();
    }

    @Override
    public Collection<Meter> meters(Predicate<Meter> filter) {
        return Set.of();
    }

    @Override
    public Iterable<String> scopes() {
        return Set.of();
    }

    @Override
    public boolean isMeterEnabled(io.helidon.metrics.api.Meter.Id meterId) {
        return true;
    }

    @Override
    public Clock clock() {
        return Clock.system();
    }

    @Override
    public Optional<Meter> remove(Meter.Id id) {
        return Optional.empty();
    }

    @Override
    public Optional<Meter> remove(Meter meter) {
        return Optional.empty();
    }

    @Override
    public Optional<Meter> remove(String name, Iterable<Tag> tags) {
        return remove(NoOpMeter.Id.create(name, tags));
    }

    @Override
    public <M extends Meter> Optional<M> get(Class<M> mClass, String name, Iterable<Tag> tags) {
        return Optional.empty();
    }

    @Override
    public <M extends Meter, B extends Meter.Builder<B, M>> M getOrCreate(B builder) {
        NoOpMeter.Builder<?, ?> b = (NoOpMeter.Builder<?, ?>) builder;
        return findOrRegister(NoOpMeter.Id.create(b.name(),
                                                  b.tags()),
                              builder);
    }

    private <M extends Meter> Optional<M> find(Meter.Id id, Class<M> mClass) {
        return Optional.empty();
    }

    private <M extends Meter, B extends Meter.Builder<B, M>> M findOrRegister(Meter.Id id, B builder) {
        NoOpMeter.Builder<?, ?> noOpBuilder = (NoOpMeter.Builder<?, ?>) builder;
        // The following cast will always succeed if we create the meter by invoking the builder,
        // it will success if we retrieved a previously-registered meter of a compatible type,
        // and it will (correctly) fail if we found a previously-registered meter of an incompatible
        // type compared to what the caller requested.
        return (M) noOpBuilder.build();
    }
}
