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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.json.JsonObjectBuilder;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
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
    public void update(Duration duration) {
        delegate.update(duration);
    }

    @Override
    public Duration getElapsedTime() {
        return delegate.getElapsedTime();
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

    DisplayableLabeledSnapshot snapshot(){
        return (delegate instanceof TimerImpl)
                ? ((TimerImpl) delegate).histogram.snapshot()
                : WrappedSnapshot.create(delegate.getSnapshot());
    }

    @Override
    public void prometheusData(StringBuilder sb, MetricID metricID, boolean withHelpType, boolean isStrictExemplar) {

        // In Prometheus, times are always expressed in seconds. So force the TimeUnits value accordingly, ignoring
        // whatever units were specified in the timer's metadata.
        PrometheusName name = PrometheusName.create(this, metricID, TimeUnits.PROMETHEUS_TIMER_CONVERSION_TIME_UNITS);

        appendPrometheusTimerStatElement(sb, name, "rate_per_second", withHelpType, "gauge", getMeanRate());
        appendPrometheusTimerStatElement(sb, name, "one_min_rate_per_second", withHelpType, "gauge", getOneMinuteRate());
        appendPrometheusTimerStatElement(sb, name, "five_min_rate_per_second", withHelpType, "gauge", getFiveMinuteRate());
        appendPrometheusTimerStatElement(sb, name, "fifteen_min_rate_per_second", withHelpType, "gauge", getFifteenMinuteRate());

        DisplayableLabeledSnapshot snap = snapshot();
        appendPrometheusHistogramElements(sb, name, withHelpType, getCount(), getElapsedTime(), snap, isStrictExemplar);
    }

    @Override
    public String prometheusValue() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void jsonData(JsonObjectBuilder builder, MetricID metricID) {
        Snapshot snapshot = getSnapshot();
        // Convert snapshot output according to units.
        long divisor = conversionFactor();
        JsonObjectBuilder myBuilder = JSON.createObjectBuilder()
                .add(jsonFullKey("count", metricID), getCount())
                .add(jsonFullKey("elapsedTime", metricID), jsonDuration(getElapsedTime()))
                .add(jsonFullKey("meanRate", metricID), getMeanRate())
                .add(jsonFullKey("oneMinRate", metricID), getOneMinuteRate())
                .add(jsonFullKey("fiveMinRate", metricID), getFiveMinuteRate())
                .add(jsonFullKey("fifteenMinRate", metricID), getFifteenMinuteRate())
                .add(jsonFullKey("min", metricID), snapshot.getMin() / divisor)
                .add(jsonFullKey("max", metricID), snapshot.getMax() / divisor)
                .add(jsonFullKey("mean", metricID), snapshot.getMean() / divisor)
                .add(jsonFullKey("stddev", metricID), snapshot.getStdDev() / divisor)
                .add(jsonFullKey("p50", metricID), snapshot.getMedian() / divisor)
                .add(jsonFullKey("p75", metricID), snapshot.get75thPercentile() / divisor)
                .add(jsonFullKey("p95", metricID), snapshot.get95thPercentile() / divisor)
                .add(jsonFullKey("p98", metricID), snapshot.get98thPercentile() / divisor)
                .add(jsonFullKey("p99", metricID), snapshot.get99thPercentile() / divisor)
                .add(jsonFullKey("p999", metricID), snapshot.get999thPercentile() / divisor);

        builder.add(metricID.getName(), myBuilder);
    }

    void appendPrometheusTimerStatElement(StringBuilder sb,
            PrometheusName name,
            String statName,
            boolean withHelpType,
            String typeName,
            double value) {

        // For the timer stats output, suppress any units conversion; just emit the value directly.
        if (withHelpType) {
            prometheusType(sb, name.nameStat(statName), typeName);
        }
        sb.append(name.nameStatTags(statName))
                .append(" ")
                .append(value)
                .append("\n");
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
                theTimer.update(elapsed);
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
        private final HelidonHistogram histogram;
        private final Clock clock;
        private long elapsedTimeNanos;

        TimerImpl(String repoType, String name, Clock clock) {
            this.meter = HelidonMeter.create(repoType, Metadata.builder()
                    .withName(name)
                    .withType(MetricType.METERED)
                    .build(), clock);
            this.histogram = HelidonHistogram.create(repoType, Metadata.builder()
                    .withName(name)
                    .withType(MetricType.HISTOGRAM)
                    .build(), clock);
            this.clock = clock;
        }

        @Override
        public void update(Duration duration) {
            update(duration.toNanos());
        }

        @Override
        public Duration getElapsedTime() {
            return Duration.ofNanos(elapsedTimeNanos);
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
                elapsedTimeNanos += nanos;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), meter, histogram, elapsedTimeNanos);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TimerImpl that = (TimerImpl) o;
            return meter.equals(that.meter) && histogram.equals(that.histogram) && elapsedTimeNanos == that.elapsedTimeNanos;
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
        HelidonTimer that = (HelidonTimer) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), delegate);
    }

    @Override
    protected String toStringDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append(", count='").append(getCount()).append('\'');
        sb.append(", elapsedTime='").append(getElapsedTime()).append('\'');
        sb.append(", fifteenMinuteRate='").append(getFifteenMinuteRate()).append('\'');
        sb.append(", fiveMinuteRate='").append(getFiveMinuteRate()).append('\'');
        sb.append(", meanRate='").append(getMeanRate()).append('\'');
        return sb.toString();
    }
}
