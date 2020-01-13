/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;

/**
 * Implementation of {@link Counter}.
 */
final class HelidonCounter extends MetricImpl implements Counter {
    private final Counter delegate;

    private HelidonCounter(String registryType, Metadata metadata, Counter delegate) {
        super(registryType, metadata);

        this.delegate = delegate;
    }

    static HelidonCounter create(String registryType, Metadata metadata) {
        return create(registryType, metadata, new CounterImpl());
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
    public String prometheusNameWithUnits(MetricID metricID) {
        String metricName = prometheusName(metricID.getName());
        return metricName.endsWith("total") ? metricName : metricName + "_total";
    }

    @Override
    public String prometheusValue() {
        return Long.toString(getCount());
    }

    @Override
    public void jsonData(JsonObjectBuilder builder, MetricID metricID) {
        builder.add(jsonFullKey(metricID), getCount());
    }

    private static class CounterImpl implements Counter {
        private final LongAdder adder = new LongAdder();

        @Override
        public void inc() {
            adder.increment();
        }

        @Override
        public void inc(long n) {
            adder.add(n);
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
    public int hashCode() {
        return Objects.hash(super.hashCode(), delegate);
    }

    @Override
    public String toString() {
        return "HelidonCounter{"
                + "delegate=" + delegate + ","
                + "metadata=" + metadata()
                + '}';
    }
}
