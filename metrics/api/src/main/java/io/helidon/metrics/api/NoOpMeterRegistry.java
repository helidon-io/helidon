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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * No-op implementation of {@link io.helidon.metrics.api.MeterRegistry}.
 * <p>
 * Note that the no-op meter registry implementation <em>does not</em> actually
 * store meters or their IDs, in line with the documented behavior of disabled metrics.
 * </p>
 */
class NoOpMeterRegistry implements MeterRegistry, NoOpWrapper {

    static Builder builder() {
        return new Builder();
    }

    @Override
    public List<Meter> meters() {
        return List.of();
    }

    @Override
    public Collection<Meter> meters(Predicate<Meter> filter) {
        return Set.of();
    }

    @Override
    public Iterable<Meter> meters(Iterable<String> scopeSelection) {
        return Set.of();
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
        return Optional.empty();
    }

    @Override
    public Optional<Meter> remove(Meter.Id id, String scope) {
        return Optional.empty();
    }

    @Override
    public Optional<Meter> remove(String name, Iterable<Tag> tags, String scope) {
        return Optional.empty();
    }

    @Override
    public <M extends Meter> Optional<M> meter(Class<M> mClass, String name, Iterable<Tag> tags) {
        return Optional.empty();
    }

    @Override
    public boolean isDeleted(Meter meter) {
        return false;
    }

    @Override
    public boolean isMeterEnabled(String name, Map<String, String> tags, Optional<String> scope) {
        return true;
    }

    @Override
    public Iterable<String> scopes() {
        return Set.of();
    }

    @Override
    public <B extends Meter.Builder<B, M>, M extends Meter> M getOrCreate(B builder) {
        NoOpMeter.Builder<?, ?> b = (NoOpMeter.Builder<?, ?>) builder;
        return findOrRegister(NoOpMeter.Id.create(b.name(),
                                                  b.tags()),
                              builder);
    }

    @Override
    public MeterRegistry onMeterAdded(Consumer<Meter> listener) {
        return this;
    }

    @Override
    public MeterRegistry onMeterRemoved(Consumer<Meter> listener) {
        return this;
    }

    static class Builder implements MeterRegistry.Builder<Builder, NoOpMeterRegistry> {

        @Override
        public NoOpMeterRegistry build() {
            return new NoOpMeterRegistry();
        }

        @Override
        public Builder clock(Clock clock) {
            return identity();
        }

        @Override
        public Builder metricsConfig(MetricsConfig metricsConfig) {
            return identity();
        }

        @Override
        public Builder onMeterAdded(Consumer<Meter> addListener) {
            return identity();
        }

        @Override
        public Builder onMeterRemoved(Consumer<Meter> removeListener) {
            return identity();
        }
    }

    private <M extends Meter> Optional<M> find(Meter.Id id, Class<M> mClass) {
        return Optional.empty();
    }

    private <M extends Meter, B extends Meter.Builder<B, M>> M findOrRegister(Meter.Id id, B builder) {
        NoOpMeter.Builder<?, ?> noOpBuilder = (NoOpMeter.Builder<?, ?>) builder;
        // The following cast will always succeed if we create the meter by invoking the builder,
        // it will succeed if we retrieved a previously-registered meter of a compatible type,
        // and it will (correctly) fail if we found a previously-registered meter of an incompatible
        // type compared to what the caller requested.
        return (M) noOpBuilder.build();
    }
}
