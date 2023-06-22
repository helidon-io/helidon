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

import io.helidon.metrics.api.SampledMetric;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Implementation of {@link Counter}.
 */
final class HelidonCounter extends MetricImpl implements Counter, SampledMetric {
    private final io.micrometer.core.instrument.Counter delegate;

    private HelidonCounter(String registryType, Metadata metadata, io.micrometer.core.instrument.Counter delegate) {
        super(registryType, metadata);
        this.delegate = delegate;
    }

    static HelidonCounter create(String registryType, Metadata metadata, Tag... tags) {
        return create(Metrics.globalRegistry, registryType, metadata, tags);
    }

    static HelidonCounter create(MeterRegistry meterRegistry, String registryType, Metadata metadata, Tag... tags) {
        return new HelidonCounter(registryType, metadata, io.micrometer.core.instrument.Counter.builder(metadata.getName())
                .baseUnit(metadata.getUnit())
                .description(metadata.getDescription())
                .tags(tags(tags))
                .register(meterRegistry));
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
    public int hashCode() {
        return Objects.hash(super.hashCode(), delegate);
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
