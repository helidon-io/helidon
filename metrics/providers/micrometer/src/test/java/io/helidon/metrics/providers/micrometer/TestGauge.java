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
package io.helidon.metrics.providers.micrometer;

import java.util.concurrent.atomic.AtomicLong;

import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestGauge {

    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.globalRegistry();
    }

    @Test
    void testUnwrap() {
        long initialValue = 4L;
        long incr = 2L;
        AtomicLong value = new AtomicLong(initialValue);
        Gauge.Builder<Double> builder = Gauge.builder("a", value, v -> (double) v.get());
        builder.unwrap(io.micrometer.core.instrument.Gauge.Builder.class).strongReference(true);
        Gauge<Double> g = meterRegistry.getOrCreate(builder);

        io.micrometer.core.instrument.Gauge mGauge = g.unwrap(io.micrometer.core.instrument.Gauge.class);
        assertThat("Initial value", mGauge.value(), is((double) initialValue));
        value.addAndGet(incr);
        assertThat("Updated value", mGauge.value(), is((double) initialValue + incr));
    }
}
