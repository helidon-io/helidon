/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
    public double getValue(double quantile) {
        return snapshot.getValue(quantile);
    }

    @Override
    public long[] getValues() {
        return snapshot.getValues();
    }

    @Override
    public int size() {
        return snapshot.size();
    }

    @Override
    public long getMax() {
        return snapshot.getMax();
    }

    @Override
    public double getMean() {
        return snapshot.getMean();
    }

    @Override
    public long getMin() {
        return snapshot.getMin();
    }

    @Override
    public double getStdDev() {
        return snapshot.getStdDev();
    }

    @Override
    public void dump(OutputStream output) {
        snapshot.dump(output);
    }

}
