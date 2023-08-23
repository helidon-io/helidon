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
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.Bucket;

import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;

class MHistogramSnapshot implements io.helidon.metrics.api.HistogramSnapshot {

    private final HistogramSnapshot delegate;

    private MHistogramSnapshot(HistogramSnapshot delegate) {
        this.delegate = delegate;
    }

    static MHistogramSnapshot create(HistogramSnapshot delegate) {
        return new MHistogramSnapshot(delegate);
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
    public String toString() {
        return new StringJoiner(",", getClass().getSimpleName() + "[", "]")
                .add("count=" + count())
                .add("total=" + total())
                .add("mean=" + mean())
                .add("max=" + max())
                .toString();
    }

    @Override
    public Iterable<? extends io.helidon.metrics.api.ValueAtPercentile> percentileValues() {
        return Arrays.stream(delegate.percentileValues())
                .map(MValueAtPercentile::create)
                .toList();
    }

    @Override
    public Iterable<Bucket> histogramCounts() {
        return () -> new Iterator<>() {

            private final CountAtBucket[] counts = delegate.histogramCounts();
            private int slot;

            @Override
            public boolean hasNext() {
                return slot < counts.length;
            }

            @Override
            public Bucket next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return MBucket.create(counts[slot++]);
            }
        };
    }

    @Override
    public void outputSummary(PrintStream out, double scale) {
        delegate.outputSummary(out, scale);

    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return c.cast(delegate);
    }
}
