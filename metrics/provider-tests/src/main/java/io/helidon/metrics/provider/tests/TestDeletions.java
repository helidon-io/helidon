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
import java.util.Optional;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Tag;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

@Testing.Test
class TestDeletions {

    private static final String COMMON_COUNTER_NAME = "theCounter";

    private static MeterRegistry reg;

    @BeforeAll
    static void setup() {
        reg = Metrics.globalRegistry();
    }

    @BeforeEach
    void clear() {
        reg.meters().forEach(reg::remove);
    }

    @Test
    void testDeleteByNameAndTags() {
        Counter counter1 = reg.getOrCreate(Counter.builder("a")
                                                   .tags(List.of(Tag.create("myTag", "a"))));
        Counter counter2 = reg.getOrCreate(Counter.builder("b")
                                                   .tags(List.of(Tag.create("myTag", "b"))));

        Optional<Counter> counter1Again = reg.counter("a", List.of(Tag.create("myTag", "a")));
        assertThat("Counter before removal", counter1Again, OptionalMatcher.optionalValue(is(sameInstance(counter1))));
        Optional<Meter> del1 = reg.remove("a", List.of(Tag.create("myTag", "a")));

        assertThat("Deleted meter", del1, OptionalMatcher.optionalValue(sameInstance(counter1)));
        assertThat("Check for deleted meter",
                   reg.counter("a",
                               List.of(Tag.create("myTag", "a"))),
                   OptionalMatcher.optionalEmpty());

        assertThat("Check for undeleted meter",
                   reg.counter("b",
                               List.of(Tag.create("myTag", "b"))),
                   OptionalMatcher.optionalValue(sameInstance(counter2)));
    }

    @Test
    void testDeleteByMeter() {
        Counter counter1 = reg.getOrCreate(Counter.builder("a")
                                                   .tags(List.of(Tag.create("myTag", "a"))));
        Counter counter2 = reg.getOrCreate(Counter.builder("b")
                                                   .tags(List.of(Tag.create("myTag", "b"))));

        Optional<Counter> counter1Again = reg.counter("a", List.of(Tag.create("myTag", "a")));
        assertThat("Counter before removal", counter1Again, OptionalMatcher.optionalValue(is(sameInstance(counter1))));
        Optional<Meter> del1 = reg.remove(counter1);

        assertThat("Deleted meter", del1, OptionalMatcher.optionalValue(sameInstance(counter1)));
        assertThat("Check for deleted meter",
                   reg.counter("a",
                               List.of(Tag.create("myTag", "a"))),
                   OptionalMatcher.optionalEmpty());

        assertThat("Check for undeleted meter",
                   reg.counter("b",
                               List.of(Tag.create("myTag", "b"))),
                   OptionalMatcher.optionalValue(sameInstance(counter2)));
    }

    @Test
    void testDeleteById() {
        Counter counter1 = reg.getOrCreate(Counter.builder("a")
                                                   .tags(List.of(Tag.create("myTag", "a"))));
        Counter counter2 = reg.getOrCreate(Counter.builder("b")
                                                   .tags(List.of(Tag.create("myTag", "b"))));

        Optional<Counter> counter1Again = reg.counter("a", List.of(Tag.create("myTag", "a")));
        assertThat("Counter before removal", counter1Again, OptionalMatcher.optionalValue(is(sameInstance(counter1))));
        Optional<Meter> del1 = reg.remove(counter1.id());

        assertThat("Deleted meter", del1, OptionalMatcher.optionalValue(sameInstance(counter1)));
        assertThat("Check for deleted meter",
                   reg.counter("a",
                               List.of(Tag.create("myTag", "a"))),
                   OptionalMatcher.optionalEmpty());

        assertThat("Check for undeleted meter",
                   reg.counter("b",
                               List.of(Tag.create("myTag", "b"))),
                   OptionalMatcher.optionalValue(sameInstance(counter2)));
    }
}
