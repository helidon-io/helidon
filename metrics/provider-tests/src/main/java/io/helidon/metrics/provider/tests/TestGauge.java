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
package io.helidon.metrics.provider.tests;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testing.Test
class TestGauge {

    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.globalRegistry();
    }

    @Test
    void testGaugeAroundObject() {

        long initial = 4L;
        long incr = 3L;
        Custom c = new Custom(initial);
        Gauge<Double> g = meterRegistry.getOrCreate(Gauge.builder("a",
                                                                  c::value));

        assertThat("Gauge before update", g.value(), is((double) initial));

        c.add(3L);

        assertThat("Gauge after update", g.value(), is((double) initial + incr));
    }

    @Test
    void testGaugeWithLamdba() {
        int initial = 11;
        int incr = 4;
        AtomicInteger i = new AtomicInteger(initial);
        Gauge g = meterRegistry.getOrCreate(Gauge.builder("b",
                                                          i,
                                                          theInt -> (double) theInt.get()));
        assertThat("Gauge before update", i.get(), is(initial));

        i.getAndAdd(incr);

        assertThat("Gauge after update", g.value(), is((double) initial + incr));
    }

    private static class Custom {

        private long value;

        private Custom(long initialValue) {
            value = initialValue;
        }

        private void add(long delta) {
            value += delta;
        }

        private double value() {
            return value;
        }
    }
}
