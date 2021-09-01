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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricID;

/*
 * This class is inspired by:
 * Meter
 *
 * From Dropwizard Metrics v 3.2.3.
 * Distributed under Apache License, Version 2.0
 *
 */

/**
 * Implementation of {@link Meter}.
 */
final class HelidonMeter extends MetricImpl implements Meter {
    private static final long TICK_INTERVAL = TimeUnit.SECONDS.toNanos(5);

    private final Meter delegate;

    private HelidonMeter(String type, Metadata metadata, Meter delegate) {
        super(type, metadata);
        this.delegate = delegate;
    }

    static HelidonMeter create(String type, Metadata metadata) {
        return create(type, metadata, Clock.system());
    }

    static HelidonMeter create(String type, Metadata metadata, Clock clock) {
        return new HelidonMeter(type, metadata, new MeterImpl(clock));
    }

    static HelidonMeter create(String type, Metadata metadata, Meter delegate) {
        return new HelidonMeter(type, metadata, delegate);
    }

    /*
    From spec:
    # TYPE application:requests_total counter
    # HELP application:requests_total Tracks the number of requests to the server
    application:requests_total 29382
    # TYPE application:requests_rate_per_second gauge
    application:requests_rate_per_second 12.223
    # TYPE application:requests_one_min_rate_per_second gauge
    application:requests_one_min_rate_per_second 12.563
    # TYPE application:requests_five_min_rate_per_second gauge
    application:requests_five_min_rate_per_second 12.364
    # TYPE application:requests_fifteen_min_rate_per_second gauge
    application:requests_fifteen_min_rate_per_second 12.126
    */
    @Override
    public void prometheusData(StringBuilder sb, MetricID metricID, boolean withHelpType) {
        String name = metricID.getName();
        String nameUnits = prometheusNameWithUnits(name, Optional.empty()) + "_total";
        String tags = prometheusTags(metricID.getTags());

        if (withHelpType) {
            prometheusType(sb, nameUnits, "counter");
            prometheusHelp(sb, nameUnits);
        }
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(getCount())
                .append("\n");

        nameUnits = prometheusNameWithUnits(name, Optional.empty()) + "_rate_per_second";
        if (withHelpType) {
            prometheusType(sb, nameUnits, "gauge");
        }
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(getMeanRate())
                .append("\n");

        nameUnits = prometheusNameWithUnits(name, Optional.empty()) + "_one_min_rate_per_second";
        if (withHelpType) {
            prometheusType(sb, nameUnits, "gauge");
        }
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(getOneMinuteRate())
                .append("\n");

        nameUnits = prometheusNameWithUnits(name, Optional.empty()) + "_five_min_rate_per_second";
        if (withHelpType) {
            prometheusType(sb, nameUnits, "gauge");
        }
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(getFiveMinuteRate())
                .append("\n");

        nameUnits = prometheusNameWithUnits(name, Optional.empty()) + "_fifteen_min_rate_per_second";
        if (withHelpType) {
            prometheusType(sb, nameUnits, "gauge");
        }
        sb.append(nameUnits)
                .append(tags)
                .append(" ")
                .append(getFifteenMinuteRate())
                .append("\n");
    }

    @Override
    public String prometheusValue() {
        throw new UnsupportedOperationException("Not supported.");
    }

    /*
    From spec:
    {
      "requests": {
      "count": 29382,
      "meanRate": 12.223,
      "oneMinRate": 12.563,
      "fiveMinRate": 12.364,
      "fifteenMinRate": 12.126,
      }
    }
    */
    @Override
    public void jsonData(JsonObjectBuilder builder, MetricID metricID) {
        JsonObjectBuilder myBuilder = JSON.createObjectBuilder()

                .add(jsonFullKey("count", metricID), getCount())
                .add(jsonFullKey("meanRate", metricID), getMeanRate())
                .add(jsonFullKey("oneMinRate", metricID), getOneMinuteRate())
                .add(jsonFullKey("fiveMinRate", metricID), getFiveMinuteRate())
                .add(jsonFullKey("fifteenMinRate", metricID), getFifteenMinuteRate());

        builder.add(metricID.getName(), myBuilder);
    }

    @Override
    public void mark() {
        delegate.mark();
    }

    @Override
    public void mark(long n) {
        delegate.mark(n);
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

    private static final class MeterImpl implements Meter {
        private final EWMA m1Rate = EWMA.oneMinuteEWMA();
        private final EWMA m5Rate = EWMA.fiveMinuteEWMA();
        private final EWMA m15Rate = EWMA.fifteenMinuteEWMA();

        private final LongAdder count = new LongAdder();
        private final long startTime;
        private final AtomicLong lastTick;
        private final Clock clock;

        private MeterImpl(Clock clock) {
            this.startTime = clock.nanoTick();
            this.lastTick = new AtomicLong(startTime);
            this.clock = clock;
        }

        @Override
        public void mark() {
            mark(1);
        }

        @Override
        public void mark(long n) {
            tickIfNecessary();
            count.add(n);
            m1Rate.update(n);
            m5Rate.update(n);
            m15Rate.update(n);
        }

        @Override
        public long getCount() {
            return count.sum();
        }

        @Override
        public double getFifteenMinuteRate() {
            tickIfNecessary();
            return m15Rate.getRate(TimeUnit.SECONDS);
        }

        @Override
        public double getFiveMinuteRate() {
            tickIfNecessary();
            return m5Rate.getRate(TimeUnit.SECONDS);
        }

        @Override
        public double getMeanRate() {
            if (getCount() == 0) {
                return 0.0;
            } else {
                final double elapsed = (clock.nanoTick() - startTime);
                return (getCount() / elapsed) * TimeUnit.SECONDS.toNanos(1);
            }
        }

        @Override
        public double getOneMinuteRate() {
            tickIfNecessary();
            return m1Rate.getRate(TimeUnit.SECONDS);
        }

        private void tickIfNecessary() {
            final long oldTick = lastTick.get();
            final long newTick = clock.nanoTick();
            final long age = newTick - oldTick;
            if (age > TICK_INTERVAL) {
                final long newIntervalStartTick = newTick - (age % TICK_INTERVAL);
                if (lastTick.compareAndSet(oldTick, newIntervalStartTick)) {
                    final long requiredTicks = age / TICK_INTERVAL;
                    for (long i = 0; i < requiredTicks; i++) {
                        m1Rate.tick();
                        m5Rate.tick();
                        m15Rate.tick();
                    }
                }
            }
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
            MeterImpl that = (MeterImpl) o;
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
        HelidonMeter that = (HelidonMeter) o;
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
        sb.append(", fifteenMinuteRate='").append(getFifteenMinuteRate()).append('\'');
        sb.append(", fiveMinuteRate='").append(getFiveMinuteRate()).append('\'');
        sb.append(", meanRate='").append(getMeanRate()).append('\'');
        return sb.toString();
    }
}
