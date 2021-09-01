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
import java.util.concurrent.atomic.AtomicLong;

import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;

/**
 * Implementation of {@link ConcurrentGauge}.
 */
final class HelidonConcurrentGauge extends MetricImpl implements ConcurrentGauge {

    private static final String PROMETHEUS_TYPE = "gauge";

    private final ConcurrentGauge delegate;

    private HelidonConcurrentGauge(String registryType, Metadata metadata, ConcurrentGauge delegate) {
        super(registryType, metadata);
        this.delegate = delegate;
    }

    static HelidonConcurrentGauge create(String registryType, Metadata metadata) {
        return create(registryType, metadata, Clock.system());
    }

    static HelidonConcurrentGauge create(String registryType, Metadata metadata, Clock clock) {
        return create(registryType, metadata, new ConcurrentGaugeImpl(clock));
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
    public String prometheusNameWithUnits(MetricID metricID) {
        return prometheusName(metricID.getName());
    }

    @Override
    public String prometheusValue() {
        return Long.toString(getCount());
    }

    @Override
    public void jsonData(JsonObjectBuilder builder, MetricID metricID) {
        final JsonObjectBuilder myBuilder = JSON.createObjectBuilder()
                .add(jsonFullKey("current", metricID), getCount())
                .add(jsonFullKey("max", metricID), getMax())
                .add(jsonFullKey("min", metricID), getMin());
        builder.add(metricID.getName(), myBuilder);
    }

    @Override
    public void prometheusData(StringBuilder sb, MetricID metricID, boolean withHelpType) {
        String name = prometheusNameWithUnits(metricID);
        final String nameCurrent = name + "_current";
        if (withHelpType) {
            prometheusType(sb, nameCurrent, metadata().getType());
            prometheusHelp(sb, nameCurrent);
        }
        sb.append(nameCurrent).append(prometheusTags(metricID.getTags()))
                .append(" ").append(prometheusValue()).append('\n');
        final String nameMin = name + "_min";
        if (withHelpType) {
            prometheusType(sb, nameMin, metadata().getType());
        }
        sb.append(nameMin).append(prometheusTags(metricID.getTags()))
                .append(" ").append(getMin()).append('\n');
        final String nameMax = name + "_max";
        if (withHelpType) {
            prometheusType(sb, nameMax, metadata().getType());
        }
        sb.append(nameMax).append(prometheusTags(metricID.getTags()))
                .append(" ").append(getMax()).append('\n');
    }

    @Override
    void prometheusType(StringBuilder sb, String nameWithUnits, String type) {
        super.prometheusType(sb, nameWithUnits, PROMETHEUS_TYPE);
    }

    static class ConcurrentGaugeImpl implements ConcurrentGauge {
        private final AtomicLong count;
        private final AtomicLong lastMax;
        private final AtomicLong lastMin;
        private final AtomicLong currentMax;
        private final AtomicLong currentMin;
        private final AtomicLong lastMinute;
        private final Clock clock;

        ConcurrentGaugeImpl(Clock clock) {
            this.clock = clock;
            count = new AtomicLong(0L);
            lastMax = new AtomicLong(Long.MIN_VALUE);
            lastMin = new AtomicLong(Long.MAX_VALUE);
            currentMax = new AtomicLong(Long.MIN_VALUE);
            currentMin = new AtomicLong(Long.MAX_VALUE);
            lastMinute = new AtomicLong(currentTimeMinute());
        }

        @Override
        public long getCount() {
            return count.get();
        }

        @Override
        public long getMax() {
            updateState();
            final long max = lastMax.get();
            return max == Long.MIN_VALUE ? 0L : max;
        }

        @Override
        public long getMin() {
            updateState();
            final long min = lastMin.get();
            return min == Long.MAX_VALUE ? 0L : min;
        }

        @Override
        public synchronized void inc() {
            updateState();
            count.incrementAndGet();
            final long count = getCount();
            if (count > currentMax.get()) {
                currentMax.set(count);
            }
        }

        @Override
        public synchronized void dec() {
            updateState();
            count.decrementAndGet();
            final long count = getCount();
            if (count < currentMin.get()) {
                currentMin.set(count);
            }
        }

        public synchronized void updateState() {
            long currentMinute = currentTimeMinute();
            long diff = currentMinute - lastMinute.get();
            if (diff >= 1L) {
                lastMax.set(currentMax.get());
                lastMin.set(currentMin.get());
                lastMinute.set(currentMinute);
            }
        }

        private long currentTimeMinute() {
            return clock.milliTime() / 1000 / 60;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), count, lastMin, lastMax);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConcurrentGaugeImpl that = (ConcurrentGaugeImpl) o;
            return count.equals(that.count) && lastMin.equals(that.lastMin) && lastMax.equals(that.lastMax);
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
        HelidonConcurrentGauge that = (HelidonConcurrentGauge) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), delegate);
    }
}
