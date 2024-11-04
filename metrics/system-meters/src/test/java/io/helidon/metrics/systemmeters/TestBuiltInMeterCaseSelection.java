/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.metrics.systemmeters;

import java.util.Collection;
import java.util.Map;

import io.helidon.common.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MetricsFactory;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

class TestBuiltInMeterCaseSelection {

    @Test
    void testDefaults() {
        SystemMetersProvider provider = new SystemMetersProvider();
        MetricsFactory metricsFactory = MetricsFactory.getInstance(Config.empty());
        Collection<Meter.Builder<?, ?>> meterBuilders = provider.meterBuilders(metricsFactory);

        assertThat("Default used heap name", meterBuilders, allOf(
                hasItem(MeterBuilderMatcher.withName(equalTo("memory.usedHeap"))),
                not(hasItem(MeterBuilderMatcher.withName(equalTo("memory.used_heap"))))));
    }

    @Test
    void testWithSnakeConfigured() {
        SystemMetersProvider provider = new SystemMetersProvider();
        Config config = io.helidon.config.Config.just(ConfigSources.create(Map.of("metrics.built-in-meter-name-format", "SNAKE")));
        MetricsFactory metricsFactory = MetricsFactory.getInstance(config.get("metrics"));
        Collection<Meter.Builder<?, ?>> meterBuilders = provider.meterBuilders(metricsFactory);

        assertThat("Configured used heap name", meterBuilders, allOf(
                hasItem(MeterBuilderMatcher.withName(equalTo("memory.used_heap"))),
                not(hasItem(MeterBuilderMatcher.withName(equalTo("memory.usedHeap"))))));
    }
}
