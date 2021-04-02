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

import io.helidon.metrics.WeightedSnapshot.DerivedSample;
import io.helidon.metrics.WeightedSnapshot.WeightedSample;

import org.eclipse.microprofile.metrics.Snapshot;

class WrappedSnapshot implements DisplayableLabeledSnapshot {

    private final Snapshot delegate;

    private final WeightedSample[] samples;
    private final WeightedSnapshot.DerivedSample median;
    private final WeightedSample max;
    private final WeightedSample min;
    private final WeightedSnapshot.DerivedSample mean;
    private final DerivedSample stdDev;

    private final WeightedSnapshot.DerivedSample sample75th;
    private final WeightedSnapshot.DerivedSample sample95th;
    private final DerivedSample sample98th;
    private final WeightedSnapshot.DerivedSample sample99th;
    private final DerivedSample sample999th;

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
        median = new DerivedSample(delegate.getMedian());
        max = new WeightedSample(delegate.getMax());
        min = new WeightedSample(delegate.getMin());
        mean = new WeightedSnapshot.DerivedSample(delegate.getMean());
        stdDev = new DerivedSample(delegate.getStdDev());

        sample75th = new WeightedSnapshot.DerivedSample(delegate.get75thPercentile());
        sample95th = new WeightedSnapshot.DerivedSample(delegate.get95thPercentile());
        sample98th = new DerivedSample(delegate.get98thPercentile());
        sample99th = new WeightedSnapshot.DerivedSample(delegate.get99thPercentile());
        sample999th = new DerivedSample(delegate.get999thPercentile());
    }

    @Override
    public DerivedSample value(double quantile) {
        return new DerivedSample(delegate.getValue(quantile));
    }

    @Override
    public WeightedSnapshot.DerivedSample median() {
        return median;
    }

    @Override
    public DerivedSample sample75thPercentile() {
        return sample75th;
    }

    @Override
    public DerivedSample sample95thPercentile() {
        return sample95th;
    }

    @Override
    public DerivedSample sample98thPercentile() {
        return sample98th;
    }

    @Override
    public DerivedSample sample99thPercentile() {
        return sample99th;
    }

    @Override
    public DerivedSample sample999thPercentile() {
        return sample999th;
    }

    @Override
    public WeightedSample max() {
        return max;
    }

    @Override
    public DerivedSample mean() {
        return mean;
    }

    @Override
    public WeightedSample min() {
        return min;
    }

    @Override
    public DerivedSample stdDev() {
        return stdDev;
    }
}
