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
package io.helidon.metrics.testing;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class TestCounter {

    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.createMeterRegistry(MetricsConfig.create());
    }

    @Test
    void testIncr() {
        Counter c = meterRegistry.getOrCreate(Counter.builder("c1"));
        assertThat("Initial counter value", c.count(), is(0D));
        c.increment();
        assertThat("After increment", c.count(), is(1D));
    }

    @Test
    void incrWithValue() {
        Counter c = meterRegistry.getOrCreate(Counter.builder("c2"));
        assertThat("Initial counter value", c.count(), is(0D));
        c.increment(3D);
        assertThat("After increment", c.count(), is(3D));
    }

    @Test
    void incrBoth() {
        Counter c = meterRegistry.getOrCreate(Counter.builder("c3"));
        assertThat("Initial counter value", c.count(), is(0D));
        c.increment(2D);
        assertThat("After increment", c.count(), is(2D));

        Counter cAgain = meterRegistry.getOrCreate(Counter.builder("c3"));
        assertThat("Looked up instance", cAgain, is(sameInstance(c)));
        assertThat("Value after one update", cAgain.count(), is(2D));

        cAgain.increment(3D);
        assertThat("Value after second update", cAgain.count(), is(5D));
    }

//    @Test
//    void testUnwrap() {
//        Counter c = meterRegistry.getOrCreate(Counter.builder("c4"));
//        io.micrometer.core.instrument.Counter mCounter = c.unwrap(io.micrometer.core.instrument.Counter.class);
//        assertThat("Initial value", c.count(), is(0D));
//        mCounter.increment();
//        assertThat("Updated value", c.count(), is(1D));
//    }
}
