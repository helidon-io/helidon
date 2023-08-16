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
package io.helidon.nima.observe.metrics;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Timer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class TestJsonFormatting {
    private static MeterRegistry meterRegistry;
    private static MetricsFeature feature;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.createMeterRegistry(MetricsConfig.create());
        feature = MetricsFeature.builder()
                .meterRegistry(meterRegistry)
                .build();
    }

    @Test
    void testRetrievingAll() {
        Counter c = meterRegistry.getOrCreate(Counter.builder("c1"));
        assertThat("Initial counter value", c.count(), is(0L));
        c.increment();
        assertThat("After increment", c.count(), is(1L));

        Timer d = meterRegistry.getOrCreate(Timer.builder("t1"));
        d.record(3, TimeUnit.SECONDS);


        Optional<String> output = (Optional<String>) feature.output(MediaTypes.TEXT_PLAIN,
                                                                    Set.of(),
                                                                    Set.of());

        assertThat("Formatted output",
                   output,
                   OptionalMatcher.optionalValue(
                           allOf(containsString("c1_total 1.0"),
                                 containsString("t1_seconds_count 1.0"),
                                 containsString("t1_seconds_sum 3.0"))));
    }

    @Test
    void testRetrievingByName() {
        Counter c = meterRegistry.getOrCreate(Counter.builder("c2"));
        assertThat("Initial counter value", c.count(), is(0L));
        c.increment();
        assertThat("After increment", c.count(), is(1L));

        Timer d = meterRegistry.getOrCreate(Timer.builder("t2"));
        d.record(7, TimeUnit.SECONDS);

        Optional<String> output = (Optional<String>) feature.output(MediaTypes.TEXT_PLAIN,
                                                                    Set.of(),
                                                                    Set.of("c2"));

        assertThat("Formatted output",
                   output,
                   OptionalMatcher.optionalValue(
                           allOf(containsString("c2_total 1.0"),
                                 not(containsString("t2_seconds_count 1.0")),
                                 not(containsString("t2_seconds_sum 7.0")))));

    }
}
