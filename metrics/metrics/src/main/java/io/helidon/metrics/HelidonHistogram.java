/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
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
    protected void prometheusData(StringBuilder sb, String name, String tags) {
        Units units = getUnits();

        String nameUnits;
        Optional<String> unit = units.getPrometheusUnit();

        Snapshot snap = getSnapshot();

        // # TYPE application:file_sizes_mean_bytes gauge
        // application:file_sizes_mean_bytes 4738.231
        nameUnits = prometheusNameWithUnits(name + "_mean", unit);
        prometheusType(sb, nameUnits, "gauge");
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(units.convert(snap.getMean()))
                .append("\n");

        // # TYPE application:file_sizes_max_bytes gauge
        // application:file_sizes_max_bytes 31716
        nameUnits = prometheusNameWithUnits(name + "_max", unit);
        prometheusType(sb, nameUnits, "gauge");
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(units.convert(snap.getMax()))
                .append("\n");

        // # TYPE application:file_sizes_min_bytes gauge
        // application:file_sizes_min_bytes 180
        nameUnits = prometheusNameWithUnits(name + "_min", unit);
        prometheusType(sb, nameUnits, "gauge");
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(units.convert(snap.getMin()))
                .append("\n");

        // # TYPE application:file_sizes_stddev_bytes gauge
        // application:file_sizes_stddev_bytes 1054.7343037063602
        nameUnits = prometheusNameWithUnits(name + "_stddev", unit);
        prometheusType(sb, nameUnits, "gauge");
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(units.convert(snap.getStdDev()))
                .append("\n");

        // # TYPE application:file_sizes_bytes summary
        // # HELP application:file_sizes_bytes Users file size
        // application:file_sizes_bytes_count 2037
        nameUnits = prometheusNameWithUnits(name, unit);
        prometheusType(sb, nameUnits, "summary");
        prometheusHelp(sb, nameUnits);
        nameUnits = prometheusNameWithUnits(name, unit) + "_count";
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(getCount())
                .append('\n');

        // application:file_sizes_bytes{quantile="0.5"} 4201
        nameUnits = prometheusNameWithUnits(name, unit);
        // for each supported quantile
        prometheusQuantile(sb, tags, units, nameUnits, "0.5", snap::getMedian);
        prometheusQuantile(sb, tags, units, nameUnits, "0.75", snap::get75thPercentile);
        prometheusQuantile(sb, tags, units, nameUnits, "0.95", snap::get95thPercentile);
        prometheusQuantile(sb, tags, units, nameUnits, "0.98", snap::get98thPercentile);
        prometheusQuantile(sb, tags, units, nameUnits, "0.99", snap::get99thPercentile);
        prometheusQuantile(sb, tags, units, nameUnits, "0.999", snap::get999thPercentile);
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
    public void jsonData(JsonObjectBuilder builder) {
        JsonObjectBuilder myBuilder = Json.createObjectBuilder();

        myBuilder.add("count", getCount());
        Snapshot snapshot = getSnapshot();
        myBuilder.add("min", snapshot.getMin());
        myBuilder.add("max", snapshot.getMax());
        myBuilder.add("mean", snapshot.getMean());
        myBuilder.add("stddev", snapshot.getStdDev());
        myBuilder.add("p50", snapshot.getMedian());
        myBuilder.add("p75", snapshot.get75thPercentile());
        myBuilder.add("p95", snapshot.get95thPercentile());
        myBuilder.add("p98", snapshot.get98thPercentile());
        myBuilder.add("p99", snapshot.get99thPercentile());
        myBuilder.add("p999", snapshot.get999thPercentile());

        builder.add(getName(), myBuilder.build());
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
            reservoir.update(value);
        }

        public void update(long value, long timestamp) {
            counter.increment();
            reservoir.update(value, timestamp);
        }

        @Override
        public long getCount() {
            return counter.sum();
        }

        @Override
        public Snapshot getSnapshot() {
            return reservoir.getSnapshot();
        }
    }
}
