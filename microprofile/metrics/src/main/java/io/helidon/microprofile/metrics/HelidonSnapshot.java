/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.stream.StreamSupport;

import io.helidon.metrics.api.HistogramSnapshot;

import org.eclipse.microprofile.metrics.Snapshot;

/**
 * Snapshot implementation.
 */
class HelidonSnapshot extends Snapshot {

    /**
     * Creates a new snapshot.
     *
     * @param histogramSnapshot the underlying snapshot data
     * @return new snapshot wrapper around the data
     */
    public static HelidonSnapshot create(HistogramSnapshot histogramSnapshot) {
        return new HelidonSnapshot(histogramSnapshot);
    }

    private final HistogramSnapshot delegate;

    private HelidonSnapshot(HistogramSnapshot histogramSnapshot) {
        delegate = histogramSnapshot;
    }

    @Override
    public long size() {
        return delegate.count();
    }

    @Override
    public double getMax() {
        return delegate.max();
    }

    @Override
    public double getMean() {
        return delegate.mean();
    }

    @Override
    public PercentileValue[] percentileValues() {
        return StreamSupport.stream(delegate.percentileValues().spliterator(), false)
                .map(pv -> new PercentileValue(pv.percentile(), pv.value()))
                .toArray(PercentileValue[]::new);
    }

    @Override
    public HistogramBucket[] bucketValues() {
        return StreamSupport.stream(delegate.histogramCounts().spliterator(), false)
                .map(bucket -> new HistogramBucket(bucket.boundary(), (long) bucket.count()))
                .toArray(HistogramBucket[]::new);
    }

    @Override
    public void dump(OutputStream output) {
        delegate.outputSummary(new PrintStream(output, false, Charset.defaultCharset()), 1);
    }
}
