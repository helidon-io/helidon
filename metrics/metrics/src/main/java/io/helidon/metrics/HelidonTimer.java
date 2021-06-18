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

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Implementation of {@link Timer}.
 */
final class HelidonTimer extends MetricImpl implements Timer {
    private final Timer delegate;

    private HelidonTimer(String type, Metadata metadata, Timer delegate) {
        super(type, metadata);
        this.delegate = delegate;
    }

    static HelidonTimer create(String repoType, Metadata metadata) {
        return create(repoType, metadata, Clock.system());
    }

    static HelidonTimer create(String repoType, Metadata metadata, Clock clock) {
        return create(repoType, metadata, new TimerImpl(repoType, metadata.getName(), clock));
    }

    static HelidonTimer create(String repoType, Metadata metadata, Timer metric) {
        return new HelidonTimer(repoType, metadata, metric);
    }

    @Override
    public void update(long duration, TimeUnit unit) {
        delegate.update(duration, unit);
    }

    @Override
    public <T> T time(Callable<T> event) throws Exception {
        return delegate.time(event);
    }

    @Override
    public void time(Runnable event) {
        delegate.time(event);
    }

    @Override
    public Context time() {
        return delegate.time();
    }

    @Override
    public long getCount() {
        return delegate.getCount();
    }

    @Override
    public double getFifteenMinuteRate() {
        return delegate.getFifteenMinuteRate();
    }

    @Override
    public double getFiveMinuteRate() {
        return delegate.getFiveMinuteRate();
    }

    @Override
    public double getMeanRate() {
        return delegate.getMeanRate();
    }

    @Override
    public double getOneMinuteRate() {
        return delegate.getOneMinuteRate();
    }

    @Override
    public Snapshot getSnapshot() {
        return delegate.getSnapshot();
    }

    @Override
    protected void prometheusData(StringBuilder sb, String name, String tags) {
        String nameUnits;

        nameUnits = prometheusNameWithUnits(name, Optional.empty()) + "_rate_per_second";
        prometheusType(sb, nameUnits, "gauge");
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(getMeanRate())
                .append("\n");

        nameUnits = prometheusNameWithUnits(name, Optional.empty()) + "_one_min_rate_per_second";
        prometheusType(sb, nameUnits, "gauge");
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(getOneMinuteRate())
                .append("\n");

        nameUnits = prometheusNameWithUnits(name, Optional.empty()) + "_five_min_rate_per_second";
        prometheusType(sb, nameUnits, "gauge");
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(getFiveMinuteRate())
                .append("\n");

        nameUnits = prometheusNameWithUnits(name, Optional.empty()) + "_fifteen_min_rate_per_second";
        prometheusType(sb, nameUnits, "gauge");
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(getFifteenMinuteRate())
                .append("\n");

        Units units = getUnits();
        Optional<String> unit = units.getPrometheusUnit();
        Snapshot snap = getSnapshot();

        nameUnits = prometheusNameWithUnits(name + "_mean", unit);
        prometheusType(sb, nameUnits, "gauge");
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(units.convert(snap.getMean()))
                .append("\n");

        nameUnits = prometheusNameWithUnits(name + "_max", unit);
        prometheusType(sb, nameUnits, "gauge");
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(units.convert(snap.getMax()))
                .append("\n");

        nameUnits = prometheusNameWithUnits(name + "_min", unit);
        prometheusType(sb, nameUnits, "gauge");
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(units.convert(snap.getMin()))
                .append("\n");

        nameUnits = prometheusNameWithUnits(name + "_stddev", unit);
        prometheusType(sb, nameUnits, "gauge");
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(units.convert(snap.getStdDev()))
                .append("\n");

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

    @Override
    public void jsonData(JsonObjectBuilder builder) {
        JsonObjectBuilder myBuilder = JSON.createObjectBuilder();

        myBuilder.add("count", getCount());
        myBuilder.add("meanRate", getMeanRate());
        myBuilder.add("oneMinRate", getOneMinuteRate());
        myBuilder.add("fiveMinRate", getFiveMinuteRate());
        myBuilder.add("fifteenMinRate", getFifteenMinuteRate());
        Snapshot snapshot = getSnapshot();
        // Convert snapshot output according to units.
        long divisor = conversionFactor();
        myBuilder.add("min", snapshot.getMin() / divisor);
        myBuilder.add("max", snapshot.getMax() / divisor);
        myBuilder.add("mean", snapshot.getMean() / divisor);
        myBuilder.add("stddev", snapshot.getStdDev() / divisor);
        myBuilder.add("p50", snapshot.getMedian() / divisor);
        myBuilder.add("p75", snapshot.get75thPercentile() / divisor);
        myBuilder.add("p95", snapshot.get95thPercentile() / divisor);
        myBuilder.add("p98", snapshot.get98thPercentile() / divisor);
        myBuilder.add("p99", snapshot.get99thPercentile() / divisor);
        myBuilder.add("p999", snapshot.get999thPercentile() / divisor);

        builder.add(getName(), myBuilder.build());
    }

    private long conversionFactor() {
        Units units = getUnits();
        String metricUnit = units.getMetricUnit();
        if (metricUnit == null) {
            return 1;
        }
        long divisor = 1;
        switch (metricUnit) {
            case MetricUnits.NANOSECONDS:
                divisor = 1;
                break;

            case MetricUnits.MICROSECONDS:
                divisor = 1000;
                break;

            case MetricUnits.MILLISECONDS:
                divisor = 1000 * 1000;
                break;

            case MetricUnits.SECONDS:
                divisor = 1000 * 1000 * 1000;
                break;

            case MetricUnits.MINUTES:
                divisor = 1000 * 1000 * 1000 * 60;
                break;

            case MetricUnits.HOURS:
                divisor = 1000 * 1000 * 1000 * 60 * 60;
                break;

            case MetricUnits.DAYS:
                divisor = 1000 * 1000 * 1000 * 60 * 60 * 24;
                break;

            default:
                divisor = 1;
        }
        return divisor;
    }

    private static final class ContextImpl implements Context {
        private final TimerImpl theTimer;
        private final long startTime;
        private final Clock clock;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private long elapsed;

        private ContextImpl(TimerImpl theTimer, Clock clock) {
            this.theTimer = theTimer;
            this.startTime = clock.nanoTick();
            this.clock = clock;
        }

        @Override
        public long stop() {
            if (running.compareAndSet(true, false)) {
                elapsed = clock.nanoTick() - startTime;
                theTimer.update(elapsed, TimeUnit.NANOSECONDS);
            }

            return elapsed;
        }

        @Override
        public void close() {
            stop();
        }
    }

    private static class TimerImpl implements Timer {
        private final Meter meter;
        private final Histogram histogram;
        private final Clock clock;

        TimerImpl(String repoType, String name, Clock clock) {
            this.meter = HelidonMeter.create(repoType, new Metadata(name, MetricType.METERED), clock);
            this.histogram = HelidonHistogram.create(repoType, new Metadata(name, MetricType.HISTOGRAM));
            this.clock = clock;
        }

        @Override
        public void update(long duration, TimeUnit unit) {
            update(unit.toNanos(duration));
        }

        @Override
        public <T> T time(Callable<T> event) throws Exception {
            long t = clock.nanoTick();

            try {
                return event.call();
            } finally {
                update(clock.nanoTick() - t);
            }
        }

        @Override
        public void time(Runnable event) {
            long t = clock.nanoTick();

            try {
                event.run();
            } finally {
                update(clock.nanoTick() - t);
            }
        }

        @Override
        public Context time() {
            return new ContextImpl(this, clock);
        }

        @Override
        public long getCount() {
            return histogram.getCount();
        }

        @Override
        public double getFifteenMinuteRate() {
            return meter.getFifteenMinuteRate();
        }

        @Override
        public double getFiveMinuteRate() {
            return meter.getFiveMinuteRate();
        }

        @Override
        public double getMeanRate() {
            return meter.getMeanRate();
        }

        @Override
        public double getOneMinuteRate() {
            return meter.getOneMinuteRate();
        }

        @Override
        public Snapshot getSnapshot() {
            return histogram.getSnapshot();
        }

        private void update(long nanos) {
            if (nanos >= 0) {
                histogram.update(nanos);
                meter.mark();
            }
        }

    }
}
