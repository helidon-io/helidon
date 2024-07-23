/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.metrics;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.LabeledSnapshot;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.SnapshotMetric;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Implementation of {@link Timer}.
 */
final class HelidonTimer extends MetricImpl<io.helidon.metrics.api.Timer> implements Timer, SnapshotMetric {

    private final io.helidon.metrics.api.Timer delegate;
    private final MeterRegistry meterRegistry;

    private HelidonTimer(MeterRegistry meterRegistry,
                         String type,
                         Metadata metadata,
                         io.helidon.metrics.api.Timer delegate) {
        super(type, metadata);
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    static HelidonTimer create(MeterRegistry meterRegistry, String scope, Metadata metadata, Tag... tags) {
        return create(meterRegistry,
                      scope,
                      metadata,
                      meterRegistry.getOrCreate(DistributionCustomizations
                                                        .apply(io.helidon.metrics.api.Timer.builder(metadata.getName())
                                                                       .description(metadata.getDescription())
                                                                       .baseUnit(sanitizeUnit(metadata.getUnit()))
                                                                       .tags(allTags(scope, tags)))));
    }

    static HelidonTimer create(MeterRegistry meterRegistry,
                               String scope,
                               Metadata metadata,
                               io.helidon.metrics.api.Timer delegate) {
        return new HelidonTimer(meterRegistry,
                                scope,
                                metadata,
                                delegate);
    }

    static HelidonTimer create(MeterRegistry meterRegistry, io.helidon.metrics.api.Timer delegate) {
        return new HelidonTimer(meterRegistry,
                                resolvedScope(delegate),
                                Registry.metadata(delegate),
                                delegate);
    }

    @Override
    public void update(Duration duration) {
        delegate.record(duration);
    }

    @Override
    public Duration getElapsedTime() {
        return Duration.ofNanos((long) delegate.totalTime(TimeUnit.NANOSECONDS));
    }

    @Override
    public <T> T time(Callable<T> event) throws Exception {
        return delegate.record(event);
    }

    @Override
    public void time(Runnable event) {
        delegate.record(event);
    }

    @Override
    public Context time() {
        return new ContextImpl(io.helidon.metrics.api.Timer.start(meterRegistry));
    }

    @Override
    public long getCount() {
        return delegate.count();
    }

    @Override
    public Snapshot getSnapshot() {
        return HelidonSnapshot.create(delegate.snapshot());
    }

    @Override
    public LabeledSnapshot snapshot() {
        return WrappedSnapshot.create(getSnapshot());
    }

    @Override
    public io.helidon.metrics.api.Timer delegate() {
        return delegate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass() || !super.equals(o)) {
            return false;
        }
        HelidonTimer that = (HelidonTimer) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), delegate);
    }

    @Override
    protected String toStringDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append(", count='").append(getCount()).append('\'');
        sb.append(", elapsedTime='").append(getElapsedTime()).append('\'');
        return sb.toString();
    }

    @Override
    public Class<io.helidon.metrics.api.Timer> delegateType() {
        return io.helidon.metrics.api.Timer.class;
    }

    private final class ContextImpl implements Context {
        private final io.helidon.metrics.api.Timer.Sample delegate;

        private ContextImpl(io.helidon.metrics.api.Timer.Sample delegate) {
            this.delegate = delegate;
        }

        @Override
        public long stop() {
            return delegate.stop(HelidonTimer.this.delegate);
        }

        @Override
        public void close() {
            stop();
        }
    }
}
