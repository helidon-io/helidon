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

import java.util.Objects;

import io.helidon.metrics.api.LabeledSnapshot;
import io.helidon.metrics.api.SnapshotMetric;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Implementation of {@link Histogram}.
 */
final class HelidonHistogram extends MetricImpl implements Histogram, SnapshotMetric {
    private final DistributionSummary delegate;

    private HelidonHistogram(String scope, Metadata metadata, io.micrometer.core.instrument.DistributionSummary delegate) {
        super(scope, metadata);
        this.delegate = delegate;
    }

    static HelidonHistogram create(String type, Metadata metadata, Tag... tags) {
        return create(Metrics.globalRegistry, type, metadata, tags);
    }

    static HelidonHistogram create(MeterRegistry meterRegistry, String scope, Metadata metadata, Tag... tags) {
        return new HelidonHistogram(scope, metadata, io.micrometer.core.instrument.DistributionSummary.builder(metadata.getName())
                .description(metadata.getDescription())
                .baseUnit(sanitizeUnit(metadata.getUnit()))
                .publishPercentiles(DEFAULT_PERCENTILES)
                .percentilePrecision(DEFAULT_PERCENTILE_PRECISION)
                .tags(allTags(scope, tags))
                .register(meterRegistry));
    }

    @Override
    public long getSum() {
        return (long) delegate.totalAmount();
    }

    @Override
    public void update(int value) {
        delegate.record(value);
    }

    @Override
    public void update(long value) {
        delegate.record(value);
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

    /**
     * Returns underlying delegate. For testing purposes only.
     *
     * @return Underlying delegate.
     */
    io.micrometer.core.instrument.DistributionSummary getDelegate() {
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
            sb.append(", max='").append(snapshot.getMax()).append('\'');
            sb.append(", mean='").append(snapshot.getMean()).append('\'');
        }
        return sb.toString();
    }
}
