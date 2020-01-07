/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.metrics.jdbc;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;

/**
 * {@link Timer} metric wrapper for Hikari CP metric.
 */
public class JdbcMetricsTimer implements Timer {

    private final com.codahale.metrics.Timer meter;

    JdbcMetricsTimer(final com.codahale.metrics.Timer meter) {
        this.meter = meter;
    }

    @Override
    public void update(long duration, TimeUnit unit) {
        meter.update(duration, unit);
    }

    @Override
    public <T> T time(Callable<T> event) throws Exception {
        return meter.time(event);
    }

    @Override
    public void time(Runnable event) {
        meter.time(event);
    }

    @Override
    public Context time() {
        return new JdbcMetricsTimerContext(meter.time());
    }

    @Override
    public long getCount() {
        return meter.getCount();
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
        return new JdbcMetricsSnapshot(meter.getSnapshot());
    }

}
