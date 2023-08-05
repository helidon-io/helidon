/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.micrometer;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;

class MHistogramSnapshot implements io.helidon.metrics.api.HistogramSnapshot {

    static MHistogramSnapshot of(HistogramSnapshot delegate) {
        return new MHistogramSnapshot(delegate);
    }

    private final HistogramSnapshot delegate;

    private MHistogramSnapshot(HistogramSnapshot delegate) {
        this.delegate = delegate;
    }

    @Override
    public long count() {
        return delegate.count();
    }

    @Override
    public double total() {
        return delegate.total();
    }

    @Override
    public double total(TimeUnit timeUnit) {
        return delegate.total(timeUnit);
    }

    @Override
    public double max() {
        return delegate.max();
    }

    @Override
    public double mean() {
        return delegate.mean();
    }

    @Override
    public double mean(TimeUnit timeUnit) {
        return delegate.mean(timeUnit);
    }

    @Override
    public Iterable<io.helidon.metrics.api.ValueAtPercentile> percentileValues() {
        return () -> new Iterator<>() {

            private final ValueAtPercentile[] values = delegate.percentileValues();
            private int slot;

            @Override
            public boolean hasNext() {
                return slot < values.length;
            }

            @Override
            public io.helidon.metrics.api.ValueAtPercentile next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return MValueAtPercentile.of(values[slot++]);
            }
        };
    }

    @Override
    public Iterable<io.helidon.metrics.api.CountAtBucket> histogramCounts() {
        return () -> new Iterator<>() {

            private final CountAtBucket[] counts = delegate.histogramCounts();
            private int slot;

            @Override
            public boolean hasNext() {
                return slot < counts.length;
            }

            @Override
            public io.helidon.metrics.api.CountAtBucket next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return MCountAtBucket.of(counts[slot++]);
            }
        };
    }

    @Override
    public void outputSummary(PrintStream out, double scale) {
        delegate.outputSummary(out, scale);

    }
}
