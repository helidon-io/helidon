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

import java.util.List;
import java.util.Optional;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class TestDeletions {

    private static final String COMMON_COUNTER_NAME = "theCounter";

    private MetricsFactory metricsFactory;
    private MeterRegistry reg;

    @BeforeEach
    void setup() {
        metricsFactory = Services.get(MetricsFactory.class);
        metricsFactory.close();
        reg = metricsFactory.globalRegistry();
    }

    @Test
    void testDeleteByNameAndTags() {
        Counter counter1 = reg.getOrCreate(metricsFactory.counterBuilder("a")
                                                   .tags(List.of(tag("a"))));
        Counter counter2 = reg.getOrCreate(metricsFactory.counterBuilder("b")
                                                   .tags(List.of(tag("b"))));

        Optional<Counter> counter1Again = reg.counter("a", List.of(tag("a")));
        assertThat("Counter before removal", counter1Again, OptionalMatcher.optionalValue(is(sameInstance(counter1))));
        Optional<Meter> del1 = reg.remove("a", List.of(tag("a")));

        assertThat("Deleted meter", del1, OptionalMatcher.optionalValue(sameInstance(counter1)));
        assertThat("Check for deleted meter",
                   reg.counter("a",
                               List.of(tag("a"))),
                   OptionalMatcher.optionalEmpty());

        assertThat("Check for undeleted meter",
                   reg.counter("b",
                               List.of(tag("b"))),
                   OptionalMatcher.optionalValue(sameInstance(counter2)));
    }

    @Test
    void testDeleteByMeter() {
        Counter counter1 = reg.getOrCreate(metricsFactory.counterBuilder("a")
                                                   .tags(List.of(tag("a"))));
        Counter counter2 = reg.getOrCreate(metricsFactory.counterBuilder("b")
                                                   .tags(List.of(tag("b"))));

        Optional<Counter> counter1Again = reg.counter("a", List.of(tag("a")));
        assertThat("Counter before removal", counter1Again, OptionalMatcher.optionalValue(is(sameInstance(counter1))));
        Optional<Meter> del1 = reg.remove(counter1);

        assertThat("Deleted meter", del1, OptionalMatcher.optionalValue(sameInstance(counter1)));
        assertThat("Check for deleted meter",
                   reg.counter("a",
                               List.of(tag("a"))),
                   OptionalMatcher.optionalEmpty());

        assertThat("Check for undeleted meter",
                   reg.counter("b",
                               List.of(tag("b"))),
                   OptionalMatcher.optionalValue(sameInstance(counter2)));
    }

    @Test
    void testDeleteById() {
        Counter counter1 = reg.getOrCreate(metricsFactory.counterBuilder("a")
                                                   .tags(List.of(tag("a"))));
        Counter counter2 = reg.getOrCreate(metricsFactory.counterBuilder("b")
                                                   .tags(List.of(tag("b"))));

        Optional<Counter> counter1Again = reg.counter("a", List.of(tag("a")));
        assertThat("Counter before removal", counter1Again, OptionalMatcher.optionalValue(is(sameInstance(counter1))));
        Optional<Meter> del1 = reg.remove(counter1.id());

        assertThat("Deleted meter", del1, OptionalMatcher.optionalValue(sameInstance(counter1)));
        assertThat("Check for deleted meter",
                   reg.counter("a",
                               List.of(tag("a"))),
                   OptionalMatcher.optionalEmpty());

        assertThat("Check for undeleted meter",
                   reg.counter("b",
                               List.of(tag("b"))),
                   OptionalMatcher.optionalValue(sameInstance(counter2)));
    }

    private Tag tag(String value) {
        return metricsFactory.tagCreate("myTag", value);
    }
}
