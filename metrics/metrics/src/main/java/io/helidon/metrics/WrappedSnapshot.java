/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import io.helidon.metrics.api.LabeledSnapshot;
import io.helidon.metrics.api.Sample.Derived;
import io.helidon.metrics.api.Sample.Labeled;

import org.eclipse.microprofile.metrics.Snapshot;

import static io.helidon.metrics.api.Sample.derived;
import static io.helidon.metrics.api.Sample.labeled;

class WrappedSnapshot implements LabeledSnapshot {

    private final Labeled max;
    private final Derived mean;
    private final long size;

    static WrappedSnapshot create(Snapshot delegate) {
        return new WrappedSnapshot(delegate);
    }

    private WrappedSnapshot(Snapshot delegate) {

        Snapshot.PercentileValue[] percentileValues = delegate.percentileValues();

        // We cannot access the weight of each sample to create a faithful array of WeightedSamples for each original sample,
        // so we pre-store the typical calculations.
        max = labeled(delegate.getMax());
        mean = derived(delegate.getMean());
        size = percentileValues.length;
    }

    @Override
    public Labeled max() {
        return max;
    }

    @Override
    public Derived mean() {
        return mean;
    }

    @Override
    public long size() {
        return size;
    }
}
