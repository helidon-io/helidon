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
package io.helidon.metrics.micrometer;

import java.util.Set;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class TestScopes {

    private static final String SCOPE_TAG_NAME = "my-tag";


    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        meterRegistry = Metrics.createMeterRegistry(MetricsConfig.builder()
                                                            .scopeTagName(SCOPE_TAG_NAME)
                                                            .build());
    }

    @Test
    void testScopeManagement() {
        Counter c = meterRegistry.getOrCreate(Counter.builder("c1"));
        Timer t = meterRegistry.getOrCreate(Timer.builder("t1")
                                                    .tags(Set.of(Tag.create("color", "red"))));
        io.micrometer.core.instrument.Counter mCounter = c.unwrap(io.micrometer.core.instrument.Counter.class);
        assertThat("Initial value", c.count(), is(0L));
        mCounter.increment();
        assertThat("Updated value", c.count(), is(1L));

        // If scope is not explicitly set, "application" should be automatically added.
        assertThat("Scopes in meter registry", meterRegistry.scopes(), allOf(contains("application"),
                                                                             not(contains("color"))));
    }
}
