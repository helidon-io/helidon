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

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

@Testing.Test
class TestCounter {

    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.globalRegistry();
    }

    @Test
    void testIncr() {
        Counter c = meterRegistry.getOrCreate(Counter.builder("c1"));
        assertThat("Initial counter value", c.count(), is(0L));
        c.increment();
        assertThat("After increment", c.count(), is(1L));
    }

    @Test
    void incrWithValue() {
        Counter c = meterRegistry.getOrCreate(Counter.builder("c2"));
        assertThat("Initial counter value", c.count(), is(0L));
        c.increment(3L);
        assertThat("After increment", c.count(), is(3L));
    }

    @Test
    void incrBoth() {
        long initialValue = 0;
        long incr = 2L;
        Counter c = meterRegistry.getOrCreate(Counter.builder("c3"));
        assertThat("Initial counter value", c.count(), is(initialValue));
        c.increment(incr);
        assertThat("After increment", c.count(), is(initialValue + incr));

        initialValue += incr;
        incr = 3L;

        Counter cAgain = meterRegistry.getOrCreate(Counter.builder("c3"));
        assertThat("Looked up instance", cAgain, is(sameInstance(c)));
        assertThat("Value after one update", cAgain.count(), is(initialValue));

        cAgain.increment(incr);
        assertThat("Value after second update", cAgain.count(), is(initialValue + incr));
    }
}
