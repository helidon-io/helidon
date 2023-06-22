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
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;

import io.helidon.metrics.api.Sample;
import io.helidon.metrics.api.SampledMetric;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;

/**
 * Implementation of {@link Counter}.
 */
final class HelidonCounter extends MetricImpl implements Counter, SampledMetric {
    private final io.micrometer.core.instrument.Counter delegate;

    private HelidonCounter(String registryType, Metadata metadata, io.micrometer.core.instrument.Counter delegate) {
        super(registryType, metadata);
        this.delegate = delegate;
    }

    static HelidonCounter create(String registryType, Metadata metadata) {
        return create(registryType, metadata, io.micrometer.core.instrument.Counter.builder(metadata.getName())
                .baseUnit(metadata.getUnit())
                .description(metadata.getDescription())
                .);
    }

    static HelidonCounter create(String registryType, Metadata metadata, Counter metric) {
        return new HelidonCounter(registryType, metadata, metric);
    }

    @Override
    public void inc() {
        delegate.inc();
    }

    @Override
    public void inc(long n) {
        delegate.inc(n);
    }

    @Override
    public long getCount() {
        return delegate.getCount();
    }

    @Override
    public Optional<Sample.Labeled> sample() {
        if (delegate instanceof CounterImpl ci) {
            return Optional.ofNullable(ci.sample);
        }
        return Optional.empty();
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

    private static class CounterImpl implements Counter {
        private final LongAdder adder = new LongAdder();

        private Sample.Labeled sample = null;

        @Override
        public void inc() {
            inc(1);
        }

        @Override
        public void inc(long n) {
            adder.add(n);
            sample = Sample.labeled(n);
        }

        @Override
        public long getCount() {
            return adder.sum();
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
            CounterImpl that = (CounterImpl) o;
            return getCount() == that.getCount();
        }
    }
}
