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

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestDeletions {

    private static final String COMMON_COUNTER_NAME = "theCounter";

    private static MetricRegistry reg;

    @BeforeAll
    static void setup() {
        reg = RegistryFactory.getInstance().getRegistry(Registry.APPLICATION_SCOPE);
    }

    @BeforeEach
    void clear() {
        reg.removeMatching(MetricFilter.ALL);
    }

    @Test
    void addCounterWithTag() {
        Counter counter = reg.counter(COMMON_COUNTER_NAME, new Tag("myTag", "a"));
        assertThat("New counter value", counter.getCount(), is(0L));
        counter.inc();
    }

    @Test
    void addCounterWithoutTag() {
        Counter counter = reg.counter(COMMON_COUNTER_NAME);
        assertThat("New counter value", counter.getCount(), is(0L));
        counter.inc();
    }
}
