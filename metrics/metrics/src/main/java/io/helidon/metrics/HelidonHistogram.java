/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

import io.helidon.metrics.api.LabeledSnapshot;
import io.helidon.metrics.api.SnapshotMetric;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Snapshot;

/**
 * Implementation of {@link Histogram}.
 */
final class HelidonHistogram extends MetricImpl implements Histogram, SnapshotMetric {
    private final Histogram delegate;

    private HelidonHistogram(String type, Metadata metadata, Histogram delegate) {
        super(type, metadata);
        this.delegate = delegate;
    }

    static HelidonHistogram create(String type, Metadata metadata) {
        return create(type, metadata, Clock.system());
    }

    static HelidonHistogram create(String type, Metadata metadata, Clock clock) {
        return new HelidonHistogram(type, metadata, new HistogramImpl(clock));
    }

    static HelidonHistogram create(String type, Metadata metadata, Histogram delegate) {
        return new HelidonHistogram(type, metadata, delegate);
    }

    @Override
    public long getSum() {
        return delegate.getSum();
    }

    @Override
    public void update(int value) {
        delegate.update(value);
    }

    @Override
    public void update(long value) {
        delegate.update(value);
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
    public LabeledSnapshot snapshot() {
        return (delegate instanceof HistogramImpl)
                ? ((HistogramImpl) delegate).snapshot()
                : WrappedSnapshot.create(delegate.getSnapshot());
    }

    /**
     * Returns underlying delegate. For testing purposes only.
     *
     * @return Underlying delegate.
     */
    HistogramImpl getDelegate() {
        return delegate instanceof HistogramImpl ? (HistogramImpl) delegate
                : delegate instanceof HelidonHistogram ? ((HelidonHistogram) delegate).getDelegate()
                : null;
    }

    static final class HistogramImpl implements Histogram {
        private final LongAdder counter = new LongAdder();
        private final LongAdder sum = new LongAdder();
        private final ExponentiallyDecayingReservoir reservoir;

        private HistogramImpl(Clock clock) {
            this.reservoir = new ExponentiallyDecayingReservoir(clock);
        }

        public void update(int value) {
            update((long) value);
        }

        @Override
        public long getSum() {
            return sum.sum();
        }

        @Override
        public void update(long value) {
            counter.increment();
            sum.add(value);
            reservoir.update(value, ExemplarServiceManager.exemplarLabel());
        }

        public void update(long value, long timestamp) {
            counter.increment();
            sum.add(value);
            reservoir.update(value, timestamp, ExemplarServiceManager.exemplarLabel());
        }

        @Override
        public long getCount() {
            return counter.sum();
        }

        @Override
        public Snapshot getSnapshot() {
            return reservoir.getSnapshot();
        }

        WeightedSnapshot snapshot() {
            return reservoir.getSnapshot();
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), getCount());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            HistogramImpl that = (HistogramImpl) o;
            return getCount() == that.getCount();
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
        HelidonHistogram that = (HelidonHistogram) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), delegate);
    }

    @Override
    protected String toStringDetails() {
        Snapshot snapshot = getSnapshot();
        StringBuilder sb = new StringBuilder();
        sb.append(", count='").append(getCount()).append('\'');
        if (null != snapshot) {
            sb.append(", min='").append(snapshot.getMin()).append('\'');
            sb.append(", max='").append(snapshot.getMax()).append('\'');
            sb.append(", mean='").append(snapshot.getMean()).append('\'');
            sb.append(", stddev='").append(snapshot.getStdDev()).append('\'');
            sb.append(", p50='").append(snapshot.getMedian()).append('\'');
            sb.append(", p75='").append(snapshot.get75thPercentile()).append('\'');
            sb.append(", p95='").append(snapshot.get95thPercentile()).append('\'');
            sb.append(", p98='").append(snapshot.get98thPercentile()).append('\'');
            sb.append(", p99='").append(snapshot.get99thPercentile()).append('\'');
            sb.append(", p999='").append(snapshot.get999thPercentile()).append('\'');
        }
        return sb.toString();
    }
}
