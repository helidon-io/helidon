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

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Snapshot;

/**
 * Implementation of the MicroProfile Metrics {@link org.eclipse.microprofile.metrics.Histogram}.
 */
public class MpHistogram extends MpMetric<DistributionSummary> implements Histogram {

    /**
     * Creates a new instance.
     *
     * @param delegate meter which actually records data
     */
    MpHistogram(DistributionSummary delegate, MeterRegistry meterRegistry) {
        super(delegate, meterRegistry);
    }

    @Override
    public void update(int i) {
        delegate().record(i);
    }

    @Override
    public void update(long l) {
        delegate().record(l);
    }

    @Override
    public long getCount() {
        return delegate().count();
    }

    @Override
    public long getSum() {
        return (long) delegate().totalAmount();
    }

    @Override
    public Snapshot getSnapshot() {
        return new MpSnapshot(delegate().takeSnapshot());
    }
}
