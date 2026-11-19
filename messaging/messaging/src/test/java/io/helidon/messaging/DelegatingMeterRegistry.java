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

package io.helidon.messaging;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;

// Tests override only the provider operation whose failure messaging must tolerate.
class DelegatingMeterRegistry implements MeterRegistry {
    private final MeterRegistry delegate;

    DelegatingMeterRegistry(MeterRegistry delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Meter> meters() {
        return delegate.meters();
    }

    @Override
    public Collection<Meter> meters(Predicate<Meter> filter) {
        return delegate.meters(filter);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean isMeterEnabled(String name) {
        return delegate.isMeterEnabled(name);
    }

    @Override
    public boolean isMeterEnabled(String name, Map<String, String> tags) {
        return delegate.isMeterEnabled(name, tags);
    }

    @Override
    public Clock clock() {
        return delegate.clock();
    }

    @Override
    public <B extends Meter.Builder<B, M>, M extends Meter> M getOrCreate(B builder) {
        return delegate.getOrCreate(builder);
    }

    @Override
    public <M extends Meter> Optional<M> meter(Class<M> type, String name, Iterable<Tag> tags) {
        return delegate.meter(type, name, tags);
    }

    @Override
    public Optional<Meter> remove(Meter meter) {
        return delegate.remove(meter);
    }

    @Override
    public Optional<Meter> remove(Meter.Id id) {
        return delegate.remove(id);
    }

    @Override
    public Optional<Meter> remove(String name, Iterable<Tag> tags) {
        return delegate.remove(name, tags);
    }

    @Override
    public boolean isDeleted(Meter meter) {
        return delegate.isDeleted(meter);
    }

    @Override
    public MeterRegistry onMeterAdded(Consumer<Meter> listener) {
        delegate.onMeterAdded(listener);
        return this;
    }

    @Override
    public MeterRegistry onMeterRemoved(Consumer<Meter> listener) {
        delegate.onMeterRemoved(listener);
        return this;
    }

    @Override
    public MetricsFactory metricsFactory() {
        return delegate.metricsFactory();
    }

    @Override
    public <R> R unwrap(Class<? extends R> type) {
        return delegate.unwrap(type);
    }
}
