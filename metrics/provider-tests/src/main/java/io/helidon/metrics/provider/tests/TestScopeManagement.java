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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.ScopingConfig;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class TestScopeManagement {

    @Test
    @SuppressWarnings("removal")
    void scopeApisAreNoOps() {
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .scoping(ScopingConfig.builder()
                                 .tagName("scope-tag")
                                 .defaultValue("configured-scope"))
                .build();
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MeterRegistry reg = metricsFactory.createMeterRegistry(metricsConfig);
        try {
            Counter counter = reg.getOrCreate(metricsFactory.counterBuilder("scopeNoOpCounter")
                                                       .scope("explicit-scope"));
            Counter sameCounter = reg.getOrCreate(metricsFactory.counterBuilder("scopeNoOpCounter")
                                                           .scope("different-scope"));
            Timer timer = reg.getOrCreate(metricsFactory.timerBuilder("scopeNoOpTimer"));

            assertThat("Scope does not contribute to meter identity", sameCounter, sameInstance(counter));
            assertThat("Explicit scope", counter.scope(), OptionalMatcher.optionalEmpty());
            assertThat("Configured default scope", timer.scope(), OptionalMatcher.optionalEmpty());
            assertThat("Registry scopes", reg.scopes(), emptyIterable());
            assertThat("Explicit scope does not add tags", counter.id().tags(), emptyIterable());
            assertThat("Configured scope does not add tags", timer.id().tags(), emptyIterable());

            List<Meter> scopeSelectedMeters = new ArrayList<>();
            reg.meters(Set.of("missing-scope")).forEach(scopeSelectedMeters::add);
            assertThat("Scope selection is ignored",
                       scopeSelectedMeters,
                       containsInAnyOrder(counter, timer));

            assertThat("Scope-qualified removal ignores scope",
                       reg.remove(counter.id(), "different-scope").orElseThrow(),
                       sameInstance(counter));
            assertThat("Remaining meters", reg.meters(), is(List.of(timer)));
            assertThat("Scope-qualified removal by name ignores scope",
                       reg.remove(timer.id().name(), timer.id().tags(), "different-scope").orElseThrow(),
                       sameInstance(timer));
            assertThat("Removed meter", reg.meters(), empty());
        } finally {
            reg.close();
        }
    }

}
