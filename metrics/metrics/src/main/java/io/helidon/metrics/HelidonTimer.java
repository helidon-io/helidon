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
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.metrics.api.LabeledSnapshot;
import io.helidon.metrics.api.SnapshotMetric;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Implementation of {@link Timer}.
 */
final class HelidonTimer extends MetricImpl implements Timer, SnapshotMetric {
    private final Timer delegate;

    private HelidonTimer(String type, Metadata metadata, Timer delegate) {
        super(type, metadata);
        this.delegate = delegate;
    }

    static HelidonTimer create(String repoType, Metadata metadata) {
        return create(repoType, metadata, Clock.system());
    }

    static HelidonTimer create(String repoType, Metadata metadata, Clock clock) {
        return create(repoType, metadata, new TimerImpl(repoType, metadata.getName(), clock));
    }

    static HelidonTimer create(String repoType, Metadata metadata, Timer metric) {
        return new HelidonTimer(repoType, metadata, metric);
    }

    @Override
    public void update(Duration duration) {
        delegate.update(duration);
    }

    @Override
    public Duration getElapsedTime() {
        return delegate.getElapsedTime();
    }

    @Override
    public <T> T time(Callable<T> event) throws Exception {
        return delegate.time(event);
    }

    @Override
    public void time(Runnable event) {
        delegate.time(event);
    }

    @Override
    public Context time() {
        return delegate.time();
    }

    @Override
    public long getCount() {
        return delegate.getCount();
    }

    @Override
    public Snapshot getSnapshot() {
        return delegate.getSnapshot();
    }

    @Override
    public LabeledSnapshot snapshot(){
        return (delegate instanceof TimerImpl)
                ? ((TimerImpl) delegate).histogram.snapshot()
                : WrappedSnapshot.create(delegate.getSnapshot());
    }

    private static final class ContextImpl implements Context {
        private final TimerImpl theTimer;
        private final long startTime;
        private final Clock clock;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private long elapsed;

        private ContextImpl(TimerImpl theTimer, Clock clock) {
            this.theTimer = theTimer;
            this.startTime = clock.nanoTick();
            this.clock = clock;
        }

        @Override
        public long stop() {
            if (running.compareAndSet(true, false)) {
                elapsed = clock.nanoTick() - startTime;
                theTimer.update(elapsed);
            }

            return elapsed;
        }

        @Override
        public void close() {
            stop();
        }
    }

    private static class TimerImpl implements Timer {
        private final HelidonHistogram histogram;
        private final Clock clock;
        private long elapsedTimeNanos;

        TimerImpl(String repoType, String name, Clock clock) {
            this.histogram = HelidonHistogram.create(repoType, Metadata.builder()
                    .withName(name)
                    .build(), clock);
            this.clock = clock;
        }

        @Override
        public void update(Duration duration) {
            update(duration.toNanos());
        }

        @Override
        public Duration getElapsedTime() {
            return Duration.ofNanos(elapsedTimeNanos);
        }

        @Override
        public <T> T time(Callable<T> event) throws Exception {
            long t = clock.nanoTick();

            try {
                return event.call();
            } finally {
                update(clock.nanoTick() - t);
            }
        }

        @Override
        public void time(Runnable event) {
            long t = clock.nanoTick();

            try {
                event.run();
            } finally {
                update(clock.nanoTick() - t);
            }
        }

        @Override
        public Context time() {
            return new ContextImpl(this, clock);
        }

        @Override
        public long getCount() {
            return histogram.getCount();
        }

        @Override
        public Snapshot getSnapshot() {
            return histogram.getSnapshot();
        }

        private void update(long nanos) {
            if (nanos >= 0) {
                histogram.update(nanos);
                elapsedTimeNanos += nanos;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), histogram, elapsedTimeNanos);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TimerImpl that = (TimerImpl) o;
            return histogram.equals(that.histogram) && elapsedTimeNanos == that.elapsedTimeNanos;
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
