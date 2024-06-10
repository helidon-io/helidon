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
package io.helidon.metrics.providers.micrometer;

import java.util.List;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;

import io.micrometer.core.instrument.Meter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestCounter {

    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.globalRegistry();
    }

    @Test
    void testUnwrap() {
        Counter.Builder builder = Counter.builder("c4");
        String descr = "Example counter";
        builder.unwrap(io.micrometer.core.instrument.Counter.Builder.class).description(descr);
        Counter c = meterRegistry.getOrCreate(builder);
        io.micrometer.core.instrument.Counter mCounter = c.unwrap(io.micrometer.core.instrument.Counter.class);
        assertThat("Initial value", c.count(), is(0L));
        mCounter.increment();
        assertThat("Updated value", c.count(), is(1L));
        assertThat("Description", c.description(), OptionalMatcher.optionalValue(is(descr)));
    }
}
