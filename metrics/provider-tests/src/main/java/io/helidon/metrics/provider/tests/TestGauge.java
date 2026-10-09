/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestGauge {

    private static MetricsFactory metricsFactory;
    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        metricsFactory = Services.get(MetricsFactory.class);
        meterRegistry = metricsFactory.createMeterRegistry(MetricsConfig.create());
    }

    @AfterAll
    static void closeRegistry() {
        meterRegistry.close();
    }

    @Test
    void testGaugeAroundObject() {

        long initial = 4L;
        long incr = 3L;
        Custom c = new Custom(initial);
        Gauge<Double> g = meterRegistry.getOrCreate(metricsFactory.gaugeBuilder("a",
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
        Gauge g = meterRegistry.getOrCreate(metricsFactory.gaugeBuilder("b",
                                                                        i,
                                                                        theInt -> (double) theInt.get()));
        assertThat("Gauge before update", i.get(), is(initial));

        i.getAndAdd(incr);

        assertThat("Gauge after update", g.value(), is((double) initial + incr));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void supplierGaugesRejectNullSamples(boolean enabled) {
        var sample = new AtomicReference<BigDecimal>();
        var calls = new AtomicInteger();
        MeterRegistry registry = metricsFactory.createMeterRegistry(MetricsConfig.builder().enabled(enabled).build());
        try {
            Gauge<BigDecimal> gauge = registry.getOrCreate(metricsFactory.gaugeBuilder("non-null-gauge", () -> {
                calls.incrementAndGet();
                return sample.get();
            }));

            assertThat("Registration must not sample the gauge", calls.get(), is(0));
            assertThrows(NullPointerException.class, gauge::value);
            assertThat("A rejected sample invokes the supplier once", calls.get(), is(1));
            BigDecimal first = new BigDecimal("9007199254740993.25");
            sample.set(first);
            assertThat("Sampling preserves the supplied Number instance", gauge.value(), sameInstance(first));
            sample.set(null);
            assertThrows(NullPointerException.class, gauge::value);
            BigDecimal recovered = new BigDecimal("9007199254740994.75");
            sample.set(recovered);
            assertThat("The gauge recovers after an invalid sample", gauge.value(), sameInstance(recovered));
            assertThat("Each read invokes the supplier once", calls.get(), is(4));
        } finally {
            registry.close();
        }
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
