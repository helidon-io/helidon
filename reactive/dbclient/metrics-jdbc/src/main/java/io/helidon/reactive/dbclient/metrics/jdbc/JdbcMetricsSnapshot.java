/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.reactive.dbclient.metrics.jdbc;

import java.io.OutputStream;

import org.eclipse.microprofile.metrics.Snapshot;

/**
 * Metric {@link Snapshot} wrapper for Hikari CP metric.
 */
public class JdbcMetricsSnapshot extends Snapshot {

    private final com.codahale.metrics.Snapshot snapshot;

    JdbcMetricsSnapshot(final com.codahale.metrics.Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public PercentileValue[] percentileValues() {
        return new PercentileValue[] {
                new PercentileValue(0.5, snapshot.getMean()),
                new PercentileValue(0.75, snapshot.get75thPercentile()),
                new PercentileValue(0.95, snapshot.get95thPercentile()),
                new PercentileValue(0.98, snapshot.get98thPercentile()),
                new PercentileValue(0.99, snapshot.get99thPercentile()),
                new PercentileValue(0.999, snapshot.get999thPercentile())};
    }

    @Override
    public long size() {
        return snapshot.size();
    }

    @Override
    public double getMax() {
        return snapshot.getMax();
    }

    @Override
    public double getMean() {
        return snapshot.getMean();
    }

    @Override
    public void dump(OutputStream output) {
        snapshot.dump(output);
    }

}
