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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Tag;

class NoOpTestRegistry implements MeterRegistry {
    @Override
    public List<Meter> meters() {
        return List.of();
    }

    @Override
    public Collection<Meter> meters(Predicate<Meter> filter) {
        return List.of();
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isMeterEnabled(String name, Map<String, String> tags) {
        return true;
    }

    @Override
    public Clock clock() {
        return HelidonClock.SYSTEM;
    }

    @Override
    public <B extends Meter.Builder<B, M>, M extends Meter> M getOrCreate(B builder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <M extends Meter> Optional<M> meter(Class<M> mClass, String name, Iterable<Tag> tags) {
        return Optional.empty();
    }

    @Override
    public Optional<Meter> remove(Meter meter) {
        return Optional.empty();
    }

    @Override
    public Optional<Meter> remove(Meter.Id id) {
        return Optional.empty();
    }

    @Override
    public Optional<Meter> remove(String name, Iterable<Tag> tags) {
        return Optional.empty();
    }

    @Override
    public boolean isDeleted(Meter meter) {
        return false;
    }

    @Override
    public MeterRegistry onMeterAdded(Consumer<Meter> onAddListener) {
        return this;
    }

    @Override
    public MeterRegistry onMeterRemoved(Consumer<Meter> onRemoveListener) {
        return this;
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return c.cast(this);
    }
}
