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
import java.util.Map;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimpleMeterRegistryTests {

    private static MetricsFactory metricsFactory;
    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void prep() {
        metricsFactory = Services.get(MetricsFactory.class);
        meterRegistry = Services.get(MeterRegistry.class);
    }

    @Test
    void testConflictingMeterType() {
        assertThat("MeterRegistry class name",
                   meterRegistry.getClass().getSimpleName(),
                   equalTo("MMeterRegistry"));

        Counter counter = meterRegistry.getOrCreate(metricsFactory.counterBuilder("b"));
        assertThat("Counter", counter, notNullValue());
        assertThat("Counter", counter.getClass().getSimpleName(), equalTo("MCounter"));

        assertThrows(IllegalArgumentException.class, () -> {
            assertThat("Meter registry before adding timer", meterRegistry.meters(), not(empty()));
            meterRegistry.getOrCreate(metricsFactory.timerBuilder("b"));
        });
    }

    @Test
    void testSameNameNoTags() {
        Counter counter1 = meterRegistry.getOrCreate(metricsFactory.counterBuilder("a"));
        Counter counter2 = meterRegistry.getOrCreate(metricsFactory.counterBuilder("a"));
        assertThat("Counter with same name, no tags", counter1, is(sameInstance(counter2)));
    }

    @Test
    void testSameNameSameTwoTags() {
        var tags = List.of(metricsFactory.tagCreate("foo", "1"),
                           metricsFactory.tagCreate("bar", "1"));

        Counter counter1 = meterRegistry.getOrCreate(metricsFactory.counterBuilder("c")
                                                             .tags(tags));
        Counter counter2 = meterRegistry.getOrCreate(metricsFactory.counterBuilder("c")
                                                             .tags(tags));
        assertThat("Counter with same name, same two tags", counter1, is(sameInstance(counter2)));
    }

    @Test
    void testDisabledYieldsNoOp() {
        // Disable metrics using config.
        Config metricsDisabledConfig = Config.just(ConfigSources.create(Map.of("enabled", "false")));
        MeterRegistry shouldBeNoOp = metricsFactory.createMeterRegistry(MetricsConfig.create(metricsDisabledConfig));

        Counter shouldBeNoOpCounter = shouldBeNoOp.getOrCreate(metricsFactory.counterBuilder("shouldBeNoOpCounter"));
        assertThat("Counters after registration", shouldBeNoOp.meters(), is(emptyIterable()));

        shouldBeNoOpCounter.increment();
        assertThat("Counter value after increment", shouldBeNoOpCounter.count(), is(0L));
    }

    @Test
    @SuppressWarnings("removal")
    void testScopingFiltersAreIgnored() {
        Config config = Config.just(ConfigSources.create(Map.of("scoping.scopes.0.name", "application",
                                                                 "scoping.scopes.0.filter.exclude", ".*Ignore.*")));
        MeterRegistry selectiveRegistry = metricsFactory.createMeterRegistry(MetricsConfig.create(config));
        try {
            Counter formerlyExcluded = selectiveRegistry.getOrCreate(metricsFactory.counterBuilder("pleaseIgnoreThis"));
            Counter included = selectiveRegistry.getOrCreate(metricsFactory.counterBuilder("pleaseIncludeThis"));
            assertThat("Scoping filter does not suppress registration", selectiveRegistry.meters(), hasSize(2));
            assertThat("Formerly excluded counter retrieved",
                       selectiveRegistry.counter("pleaseIgnoreThis", List.of()),
                       OptionalMatcher.optionalPresent());

            formerlyExcluded.increment();
            included.increment();

            assertThat("Incremented formerly excluded counter", formerlyExcluded.count(), is(1L));
            assertThat("Incremented included counter", included.count(), is(1L));
        } finally {
            selectiveRegistry.close();
        }
    }

    @Test
    @SuppressWarnings("removal")
    void testConfiguredScopeDoesNotAffectMeter() {
        Config config = Config.just(ConfigSources.create(Map.of("scoping.default", "custom",
                                                                 "scoping.scopes.0.name", "custom",
                                                                 "scoping.scopes.0.filter.exclude", "disabled")));
        MeterRegistry customRegistry = metricsFactory.createMeterRegistry(MetricsConfig.create(config));
        try {
            Counter counter = customRegistry.getOrCreate(metricsFactory.counterBuilder("disabled"));
            counter.increment();

            assertThat("Configured default scope is ignored", counter.scope(), OptionalMatcher.optionalEmpty());
            assertThat("Configured default scope does not add tags", counter.id().tags(), emptyIterable());
            assertThat("Configured scope filter is ignored", counter.count(), is(1L));
        } finally {
            customRegistry.close();
        }
    }
}
