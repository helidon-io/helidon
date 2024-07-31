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

import java.util.Objects;

import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.LabeledSnapshot;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.SnapshotMetric;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Implementation of {@link Histogram}.
 */
final class HelidonHistogram extends MetricImpl<DistributionSummary> implements Histogram, SnapshotMetric {
    private final DistributionSummary delegate;

    private HelidonHistogram(String scope, Metadata metadata, DistributionSummary delegate) {
        super(scope, metadata);
        this.delegate = delegate;
    }

    static HelidonHistogram create(MeterRegistry meterRegistry, String scope, Metadata metadata, Tag... tags) {
        return create(scope,
                      metadata,
                      meterRegistry.getOrCreate(DistributionCustomizations
                                                        .apply(DistributionSummary.builder(metadata.getName())
                                                                       .scope(scope)
                                                                       .description(metadata.getDescription())
                                                                       .baseUnit(sanitizeUnit(metadata.getUnit()))
                                                                       .tags(allTags(scope, tags)))));
    }

    static HelidonHistogram create(String scope,
                                   Metadata metadata,
                                   io.helidon.metrics.api.DistributionSummary delegate) {
        return new HelidonHistogram(scope,
                                    metadata,
                                    delegate);
    }

    static HelidonHistogram create(io.helidon.metrics.api.DistributionSummary delegate) {
        return new HelidonHistogram(resolvedScope(delegate),
                                    Registry.metadata(delegate),
                                    delegate);
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
        return HelidonSnapshot.create(delegate.snapshot());
    }

    @Override
    public LabeledSnapshot snapshot() {
        return WrappedSnapshot.create(getSnapshot());
    }

    @Override
    public DistributionSummary delegate() {
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

    @Override
    public Class<DistributionSummary> delegateType() {
        return DistributionSummary.class;
    }
}
