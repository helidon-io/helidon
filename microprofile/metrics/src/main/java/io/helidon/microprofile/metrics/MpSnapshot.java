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
package io.helidon.microprofile.metrics;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Arrays;

import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import org.eclipse.microprofile.metrics.Snapshot;

/**
 * Implementation of {@link org.eclipse.microprofile.metrics.Snapshot}.
 */
public class MpSnapshot extends Snapshot {

    private final HistogramSnapshot delegate;

    /**
     * Creates a new instance.
     *
     * @param delegate histogram snapshot which provides the actual data
     */
    MpSnapshot(HistogramSnapshot delegate) {
        this.delegate = delegate;
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
        return Arrays.stream(delegate.percentileValues())
                .map(vap -> new PercentileValue(vap.percentile(), vap.value()))
                .toArray(PercentileValue[]::new);
    }

    @Override
    public void dump(OutputStream outputStream) {
        delegate.outputSummary(new PrintStream(outputStream, false, Charset.defaultCharset()), 1);
    }
}
