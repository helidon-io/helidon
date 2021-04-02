/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Snapshot;

/**
 * Implementation of {@link Histogram}.
 */
final class HelidonHistogram extends MetricImpl implements Histogram {
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
    public void prometheusData(StringBuilder sb, MetricID metricID, boolean withHelpType) {
        Units units = getUnits();

        DisplayableLabeledSnapshot snap = delegate instanceof HistogramImpl
                ? ((HistogramImpl) delegate).snapshot()
                : WrappedSnapshot.create(delegate.getSnapshot());

        PrometheusName name = PrometheusName.create(this, metricID);

        // # TYPE application:file_sizes_mean_bytes gauge
        // application:file_sizes_mean_bytes 4738.231
        appendPrometheusElement(sb, name, "mean",  withHelpType, "gauge", snap.mean());

        // # TYPE application:file_sizes_max_bytes gauge
        // application:file_sizes_max_bytes 31716
        appendPrometheusElement(sb, name, "max", withHelpType, "gauge", snap.max());

        // # TYPE application:file_sizes_min_bytes gauge
        // application:file_sizes_min_bytes 180
        appendPrometheusElement(sb, name, "min", withHelpType, "gauge", snap.min());

        // # TYPE application:file_sizes_stddev_bytes gauge
        // application:file_sizes_stddev_bytes 1054.7343037063602
        appendPrometheusElement(sb, name, "stddev", withHelpType, "gauge", snap.stdDev());

        // # TYPE application:file_sizes_bytes summary
        // # HELP application:file_sizes_bytes Users file size
        // application:file_sizes_bytes_count 2037

        if (withHelpType) {
            prometheusType(sb, name.nameUnits(), "summary");
            prometheusHelp(sb, name.nameUnits());
        }
        sb.append(name.nameUnitsSuffixTags("count"))
                .append(" ")
                .append(getCount())
                .append('\n');

        // application:file_sizes_bytes{quantile="0.5"} 4201
        // for each supported quantile
        prometheusQuantile(sb, name, units, "0.5", snap.median());
        prometheusQuantile(sb, name, units, "0.75", snap.sample75thPercentile());
        prometheusQuantile(sb, name, units, "0.95", snap.sample95thPercentile());
        prometheusQuantile(sb, name, units, "0.98", snap.sample98thPercentile());
        prometheusQuantile(sb, name, units, "0.99", snap.sample99thPercentile());
        prometheusQuantile(sb, name, units, "0.999", snap.sample999thPercentile());
    }

    @Override
    public String prometheusValue() {
        throw new UnsupportedOperationException("Not supported.");
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

    @Override
    public void jsonData(JsonObjectBuilder builder, MetricID metricID) {
        JsonObjectBuilder myBuilder = JSON.createObjectBuilder()
                .add(jsonFullKey("count", metricID), getCount());
        Snapshot snapshot = getSnapshot();
        myBuilder = myBuilder.add(jsonFullKey("min", metricID), snapshot.getMin())
                .add(jsonFullKey("max", metricID), snapshot.getMax())
                .add(jsonFullKey("mean", metricID), snapshot.getMean())
                .add(jsonFullKey("stddev", metricID), snapshot.getStdDev())
                .add(jsonFullKey("p50", metricID), snapshot.getMedian())
                .add(jsonFullKey("p75", metricID), snapshot.get75thPercentile())
                .add(jsonFullKey("p95", metricID), snapshot.get95thPercentile())
                .add(jsonFullKey("p98", metricID), snapshot.get98thPercentile())
                .add(jsonFullKey("p99", metricID), snapshot.get99thPercentile())
                .add(jsonFullKey("p999", metricID), snapshot.get999thPercentile());

        builder.add(metricID.getName(), myBuilder);
    }

    static final class HistogramImpl implements Histogram {
        private final LongAdder counter = new LongAdder();
        private final ExponentiallyDecayingReservoir reservoir;

        private HistogramImpl(Clock clock) {
            this.reservoir = new ExponentiallyDecayingReservoir(clock);
        }

        public void update(int value) {
            update((long) value);
        }

        @Override
        public void update(long value) {
            counter.increment();
            reservoir.update(value, ExemplarServiceManager.exemplar());
        }

        public void update(long value, long timestamp) {
            counter.increment();
            reservoir.update(value, timestamp, ExemplarServiceManager.exemplar());
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
}
