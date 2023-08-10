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
package io.helidon.metrics.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.testing.junit5.OptionalMatcher;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

public class SimpleApiTest {

    private static final String COUNTER_1_DESC = "counter 1 description";

    private static Counter counter1;
    private static Counter counter2;
    private static Timer timer1;

    @BeforeAll
    static void prep() {
        MeterRegistry registry = Metrics.globalRegistry();
        assertThat("Global registry", registry, notNullValue());
        counter1 = Metrics.getOrCreate(Counter.builder("counter1")
                                                       .description(COUNTER_1_DESC));
        counter2 = Metrics.getOrCreate(Counter.builder("counter2"));

        timer1 = Metrics.getOrCreate(Timer.builder("timer1")
                                                   .tags(Metrics.tags("t1", "v1",
                                                                      "t2", "v2")));
    }

    @Test
    void testNoOpRegistrations() {

        Optional<Counter> fetchedCounter = Metrics.getCounter("counter1");
        assertThat("Fetched counter 1", fetchedCounter, OptionalMatcher.optionalEmpty());
        fetchedCounter = Metrics.getCounter("counter2", Set.of());
        assertThat("Fetched counter 2", fetchedCounter, OptionalMatcher.optionalEmpty());

        Optional<Timer> fetchedTimer = Metrics.getTimer("timer1", Metrics.tags("t1", "v1",
                                                                               "t2", "v2"));
        assertThat("Fetched timer", fetchedTimer, OptionalMatcher.optionalEmpty());
    }
}
