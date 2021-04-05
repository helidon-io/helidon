/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.metrics;

import io.helidon.metrics.LabeledSample.Derived;
import io.helidon.metrics.WeightedSnapshot.WeightedSample;

import org.eclipse.microprofile.metrics.Snapshot;

import static io.helidon.metrics.LabeledSample.derived;

class WrappedSnapshot implements DisplayableLabeledSnapshot {

    private final Snapshot delegate;

    private final WeightedSample[] samples;
    private final Derived median;
    private final WeightedSample max;
    private final WeightedSample min;
    private final Derived mean;
    private final Derived stdDev;

    private final Derived sample75th;
    private final Derived sample95th;
    private final Derived sample98th;
    private final Derived sample99th;
    private final Derived sample999th;

    static WrappedSnapshot create(Snapshot delegate) {
        return new WrappedSnapshot(delegate);
    }

    private WrappedSnapshot(Snapshot delegate) {
        this.delegate = delegate;

        long[] values = delegate.getValues();
        samples = new WeightedSample[values.length];

        for (int i = 0; i < values.length; i++) {
            samples[i] = new WeightedSample(values[i]);
        }

        // We cannot get to the weight of each sample to create a faithful array of WeightedSamples for each original sample,
        // so we pre-store the typical calculations.
        median = derived(delegate.getMedian());
        max = new WeightedSample(delegate.getMax());
        min = new WeightedSample(delegate.getMin());
        mean = derived(delegate.getMean());
        stdDev = derived(delegate.getStdDev());

        sample75th = derived(delegate.get75thPercentile());
        sample95th = derived(delegate.get95thPercentile());
        sample98th = derived(delegate.get98thPercentile());
        sample99th = derived(delegate.get99thPercentile());
        sample999th = derived(delegate.get999thPercentile());
    }

    @Override
    public Derived value(double quantile) {
        return derived(delegate.getValue(quantile));
    }

    @Override
    public Derived median() {
        return median;
    }

    @Override
    public Derived sample75thPercentile() {
        return sample75th;
    }

    @Override
    public Derived sample95thPercentile() {
        return sample95th;
    }

    @Override
    public Derived sample98thPercentile() {
        return sample98th;
    }

    @Override
    public Derived sample99thPercentile() {
        return sample99th;
    }

    @Override
    public Derived sample999thPercentile() {
        return sample999th;
    }

    @Override
    public WeightedSample max() {
        return max;
    }

    @Override
    public Derived mean() {
        return mean;
    }

    @Override
    public WeightedSample min() {
        return min;
    }

    @Override
    public Derived stdDev() {
        return stdDev;
    }
}
