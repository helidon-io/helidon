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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;

/**
 * Implementation of {@link ConcurrentGauge}.
 */
final class HelidonConcurrentGauge extends MetricImpl implements ConcurrentGauge {
    private final ConcurrentGauge delegate;

    private HelidonConcurrentGauge(String registryType, Metadata metadata, ConcurrentGauge delegate) {
        super(registryType, metadata);

        this.delegate = delegate;
    }

    static HelidonConcurrentGauge create(String registryType, Metadata metadata) {
        return create(registryType, metadata, new ConcurrentGaugeImpl());
    }

    static HelidonConcurrentGauge create(String registryType, Metadata metadata, ConcurrentGauge metric) {
        return new HelidonConcurrentGauge(registryType, metadata, metric);
    }

    @Override
    public void inc() {
        delegate.inc();
    }

    @Override
    public void dec() {
        delegate.dec();
    }

    @Override
    public long getCount() {
        return delegate.getCount();
    }

    @Override
    public long getMax() {
        return delegate.getMax();
    }

    @Override
    public long getMin() {
        return delegate.getMin();
    }

    @Override
    protected void prometheusData(StringBuilder sb, String name, String tags) {
        String nameWithUnits = prometheusNameWithUnits(name, Optional.empty());
        prometheusType(sb, nameWithUnits, getType());
        prometheusHelp(sb, nameWithUnits);
        sb.append(nameWithUnits).append(tags).append(" ").append(getCount()).append('\n');
    }

    @Override
    public void jsonData(JsonObjectBuilder builder, MetricID metricID) {
        builder.add(jsonFullKey(metricID), getCount());
    }

    static class ConcurrentGaugeImpl implements ConcurrentGauge {
        private final LongAdder adder;
        private AtomicLong max;
        private AtomicLong min;

        ConcurrentGaugeImpl() {
            adder = new LongAdder();
            max = new AtomicLong(0L);
            min = new AtomicLong(0L);
        }

        @Override
        public long getCount() {
            return adder.sum();
        }

        @Override
        public long getMax() {
            return max.get();
        }

        @Override
        public long getMin() {
            return min.get();
        }

        @Override
        public void inc() {
            adder.increment();
            final long count = getCount();
            if (count > max.get()) {
                max.set(count);
            }
        }

        @Override
        public void dec() {
            adder.decrement();
            final long count = getCount();
            if (count < min.get()) {
                min.set(count);
            }
        }
    }
}
