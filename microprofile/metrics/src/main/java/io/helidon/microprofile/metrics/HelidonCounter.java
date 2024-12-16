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

import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.SampledMetric;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Implementation of {@link Counter}.
 */
class HelidonCounter extends MetricImpl<io.helidon.metrics.api.Counter> implements Counter, SampledMetric {
    private final io.helidon.metrics.api.Counter delegate;

    private HelidonCounter(String scope, Metadata metadata, io.helidon.metrics.api.Counter delegate) {
        super(scope, metadata);
        this.delegate = delegate;
    }

    static HelidonCounter create(MeterRegistry meterRegistry, String scope, Metadata metadata, Tag... tags) {
        return create(scope,
                      metadata,
                      meterRegistry.getOrCreate(io.helidon.metrics.api.Counter.builder(metadata.getName())
                                                        .scope(scope)
                                                        .baseUnit(sanitizeUnit(metadata.getUnit()))
                                                        .description(metadata.getDescription())
                                                        .tags(allTags(scope, tags))));
    }

    static HelidonCounter create(String scope,
                                 Metadata metadata,
                                 io.helidon.metrics.api.Counter delegate) {
        return new HelidonCounter(scope,
                                  metadata,
                                  delegate);
    }

    static HelidonCounter create(io.helidon.metrics.api.Counter delegate) {
        return new HelidonCounter(resolvedScope(delegate),
                                  Registry.metadata(delegate),
                                  delegate);
    }

    @Override
    public void inc() {
        delegate.increment();
    }

    @Override
    public void inc(long n) {
        delegate.increment(n);
    }

    @Override
    public long getCount() {
        return (long) delegate.count();
    }

    @Override
    public io.helidon.metrics.api.Counter delegate() {
        return delegate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), delegate);
    }

    @Override
    public Class<io.helidon.metrics.api.Counter> delegateType() {
        return io.helidon.metrics.api.Counter.class;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass() || !super.equals(o)) {
            return false;
        }
        HelidonCounter that = (HelidonCounter) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    protected String toStringDetails() {
        return ", counter='" + getCount() + '\'';
    }

}
