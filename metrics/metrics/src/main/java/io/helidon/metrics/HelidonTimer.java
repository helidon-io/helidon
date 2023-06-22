/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.metrics;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.LabeledSnapshot;
import io.helidon.metrics.api.SnapshotMetric;

import io.micrometer.core.instrument.Metrics;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Implementation of {@link Timer}.
 */
final class HelidonTimer extends MetricImpl implements Timer, SnapshotMetric {

    private final io.micrometer.core.instrument.Timer delegate;

    private HelidonTimer(String type,
                         Metadata metadata,
                         io.micrometer.core.instrument.Timer delegate) {
        super(type, metadata);
        this.delegate = delegate;
    }

    static HelidonTimer create(String repoType, Metadata metadata, Tag... tags) {
        return new HelidonTimer(repoType,
                                metadata,
                                io.micrometer.core.instrument.Timer.builder(metadata.getName())
                                        .description(metadata.getDescription())
                                        .tags(tags(tags))
                                        .publishPercentiles(DEFAULT_PERCENTILES)
                                        .percentilePrecision(DEFAULT_PERCENTILE_PRECISION)
                                        .register(Metrics.globalRegistry));
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
        return delegate.recordCallable(event);
    }

    @Override
    public void time(Runnable event) {
        delegate.record(event);
    }

    @Override
    public Context time() {
        return new ContextImpl(io.micrometer.core.instrument.Timer.start(Metrics.globalRegistry));
    }

    @Override
    public long getCount() {
        return delegate.count();
    }

    @Override
    public Snapshot getSnapshot() {
        return HelidonSnapshot.create(delegate.takeSnapshot());
    }

    @Override
    public LabeledSnapshot snapshot() {
        return WrappedSnapshot.create(getSnapshot());
    }

    private final class ContextImpl implements Context {
        private final io.micrometer.core.instrument.Timer.Sample delegate;

        private ContextImpl(io.micrometer.core.instrument.Timer.Sample delegate) {
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
}
