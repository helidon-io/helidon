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

import java.util.List;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
class SimpleMeterRegistryTests {

    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.globalRegistry();
    }

    @Test
    void testConflictingMeterType() {
        assertThat("MeterRegistry class name",
                   meterRegistry.getClass().getSimpleName(),
                   equalTo("MMeterRegistry"));

        Counter counter = meterRegistry.getOrCreate(Counter.builder("b"));
        assertThat("Counter", counter, notNullValue());
        assertThat("Counter", counter.getClass().getSimpleName(), equalTo("MCounter"));

        assertThrows(IllegalArgumentException.class, () -> {
            assertThat("Meter registry before adding timer", meterRegistry.meters(), not(empty()));
            meterRegistry.getOrCreate(Timer.builder("b"));
        });
    }

    @Test
    void testSameNameNoTags() {
        Counter counter1 = meterRegistry.getOrCreate(Counter.builder("a"));
        Counter counter2 = meterRegistry.getOrCreate(Counter.builder("a"));
        assertThat("Counter with same name, no tags", counter1, is(sameInstance(counter2)));
    }

    @Test
    void testSameNameSameTwoTags() {
        var tags = List.of(Tag.create("foo", "1"),
                           Tag.create("bar", "1"));

        Counter counter1 = meterRegistry.getOrCreate(Counter.builder("c")
                                                             .tags(tags));
        Counter counter2 = meterRegistry.getOrCreate(Counter.builder("c")
                                                             .tags(tags));
        assertThat("Counter with same name, same two tags", counter1, is(sameInstance(counter2)));
    }
}
