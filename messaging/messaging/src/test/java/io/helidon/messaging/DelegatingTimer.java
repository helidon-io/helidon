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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.Timer;

// A typed provider adapter allows recording failures without inspecting messaging internals.
class DelegatingTimer implements Timer {
    private final Timer delegate;

    DelegatingTimer(Timer delegate) {
        this.delegate = delegate;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        delegate.record(amount, unit);
    }

    @Override
    public void record(Duration duration) {
        delegate.record(duration);
    }

    @Override
    public <T> T record(Supplier<T> action) {
        return delegate.record(action);
    }

    @Override
    public <T> T record(Callable<T> action) throws Exception {
        return delegate.record(action);
    }

    @Override
    public void record(Runnable action) {
        delegate.record(action);
    }

    @Override
    public Runnable wrap(Runnable action) {
        return delegate.wrap(action);
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> action) {
        return delegate.wrap(action);
    }

    @Override
    public <T> Supplier<T> wrap(Supplier<T> action) {
        return delegate.wrap(action);
    }

    @Override
    public long count() {
        return delegate.count();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return delegate.totalTime(unit);
    }

    @Override
    public double mean(TimeUnit unit) {
        return delegate.mean(unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return delegate.max(unit);
    }

    @Override
    public Id id() {
        return delegate.id();
    }

    @Override
    public Optional<String> baseUnit() {
        return delegate.baseUnit();
    }

    @Override
    public Optional<String> description() {
        return delegate.description();
    }

    @Override
    public Type type() {
        return delegate.type();
    }

    @Override
    public HistogramSnapshot snapshot() {
        return delegate.snapshot();
    }

    @Override
    public <R> R unwrap(Class<? extends R> type) {
        return delegate.unwrap(type);
    }
}
