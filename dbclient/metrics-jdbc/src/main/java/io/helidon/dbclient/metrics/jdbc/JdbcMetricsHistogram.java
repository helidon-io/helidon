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

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Snapshot;

/**
 * {@link Histogram} metric wrapper for Hikari CP metric.
 */
public class JdbcMetricsHistogram implements Histogram {

    private final com.codahale.metrics.Histogram histogram;

    JdbcMetricsHistogram(final com.codahale.metrics.Histogram histogram) {
        this.histogram = histogram;
    }

    @Override
    public void update(int value) {
        histogram.update(value);
    }

    @Override
    public void update(long value) {
        histogram.update(value);
    }

    @Override
    public long getCount() {
        return histogram.getCount();
    }

    @Override
    public Snapshot getSnapshot() {
        return new JdbcMetricsSnapshot(histogram.getSnapshot());
    }

}
