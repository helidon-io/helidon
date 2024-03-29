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
package io.helidon.metrics.providers.micrometer;

import java.time.Duration;
import java.util.List;

import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestDistributionSummary {

    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.globalRegistry();
    }

    @Test
    void testUnwrap() {
        DistributionSummary.Builder builder = DistributionSummary.builder("a");
        builder.unwrap(io.micrometer.core.instrument.DistributionSummary.Builder.class)
                .distributionStatisticExpiry(Duration.ofMinutes(10));
        DistributionSummary summary = meterRegistry.getOrCreate(builder);
        List.of(1D, 3D, 5D)
                .forEach(summary::record);
        io.micrometer.core.instrument.DistributionSummary mSummary =
                summary.unwrap(io.micrometer.core.instrument.DistributionSummary.class);
        mSummary.record(7D);

        assertThat("Mean", summary.mean(), is(4D));
        assertThat("Min", summary.max(), is(7D));
        assertThat("Count", summary.count(), is(4L));
        assertThat("Total", summary.totalAmount(), is(16D));

        assertThat("Mean (Micrometer)", mSummary.mean(), is(4D));
        assertThat("Min (Micrometer)", mSummary.max(), is(7D));
        assertThat("Count (Micrometer)", mSummary.count(), is(4L));
        assertThat("Total (Micrometer)", mSummary.totalAmount(), is(16D));
    }
}
