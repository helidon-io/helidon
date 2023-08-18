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
package io.helidon.metrics.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Timer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

class TestScopeManagement {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testExplicitScopeOnMetersWithNoDefaultScope(boolean scopeTagEnabled) {
        MeterRegistry reg = MetricsFactory.getInstance().createMeterRegistry(MetricsConfig.builder()
                                                                                     .scopeTagEnabled(scopeTagEnabled)
                                                                                     .build());

        // We explicitly set the scope for the counter and not for the timer.
        // With no default scope set in the config used to init the MeterRegistry, only the counter will have a scope.

        Counter c1 = reg.getOrCreate(Counter.builder("c1")
                                             .scope("app"));
        Timer t1 = reg.getOrCreate(Timer.builder("t1"));

        List<Meter> scopedMeters = new ArrayList<>();
        reg.meters(Set.of("app")).forEach(scopedMeters::add);

        // Expect to see the counter but not the timer.
        assertThat("Scope-qualified fetch: just app",
                   scopedMeters,
                   allOf(
                           hasItem((Meter) c1),
                           not(hasItem((Meter) t1))
                       ));

        scopedMeters.clear();
        reg.meters(Set.of("app", "def-scope")).forEach(scopedMeters::add);

        // Again, expect the counter but not the timer. (def-scope could have been anything; the timer has no scope)
        assertThat("Scope-qualified fetch: app and def-scope",
                   scopedMeters,
                   allOf(
                           hasItem((Meter) c1),
                           not(hasItem((Meter) t1))
                   ));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testExplicitScopeOnMetersWithDefaultScope(boolean scopeTagEnabled) {
        MeterRegistry reg = MetricsFactory.getInstance().createMeterRegistry(MetricsConfig.builder()
                                                                                     .scopeTagEnabled(scopeTagEnabled)
                                                                                     .scopeDefaultValue("def-scope")
                                                                                     .build());

        // The config sets a default scope value of def-scope. So the counter gets its explicit setting
        // and the timer gets the default scope value because it has no explicit setting.

        Counter c1 = reg.getOrCreate(Counter.builder("c1")
                                             .scope("app"));
        Timer t1 = reg.getOrCreate(Timer.builder("t1"));

        List<Meter> scopedMeters = new ArrayList<>();
        reg.meters(Set.of("app")).forEach(scopedMeters::add);

        // Fetch of "app" should give just the counter.
        assertThat("Scope-qualified fetch: app",
                   scopedMeters,
                   allOf(
                           hasItem((Meter) c1),
                           not(hasItem((Meter) t1))
                   ));

        scopedMeters.clear();
        reg.meters(Set.of("def-scope")).forEach(scopedMeters::add);

        // Fetch of "def-scope" should give just the timer.
        assertThat("Scope-qualified fetch: def-scope",
                   scopedMeters,
                   allOf(
                           not(hasItem((Meter) c1)),
                           hasItem((Meter) t1)
                   ));

        scopedMeters.clear();
        reg.meters(Set.of("app", "def-scope")).forEach(scopedMeters::add);

        // Fetch of both should give both meters.
        assertThat("Scope-qualified fetch: app and def-scope",
                   scopedMeters,
                   allOf(
                           hasItem((Meter) c1),
                           hasItem((Meter) t1)
                   ));
    }
}
