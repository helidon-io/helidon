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
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;

@Testing.Test
class TestGlobalTags {

    @Test
    void testWithoutConfig() {
        Tag g1 = Tag.create("g1", "v1");
        Tag g2 = Tag.create("g2", "v2");

        List<Tag> globalTags = List.of(g1, g2);
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .tags(globalTags)
                .build();

        MeterRegistry meterRegistry = Metrics.globalRegistry();

        assertThat("Global tags from the config used to init the meter registry",
                   metricsConfig.tags(),
                   hasItems(g2, g1));

        Counter counter1 = meterRegistry.getOrCreate(Counter.builder("a")
                                                             .tags(List.of(Tag.create("local1", "a"))));

        // Global tags should appear only on output, not in the actual tags in the meter's ID.
        assertThat("New counter's tags",
                   counter1.id().tags(),
                   not(hasItems(globalTags.toArray(new Tag[0]))));
        assertThat("New counter's tags",
                   counter1.id().tags(),
                   hasItem(Tag.create("local1", "a")));
    }

    @Test
    void testWithConfig() {
        var settings = Map.of("metrics.tags", "g1=v1,g2=v2");

        Config config = Config.just(ConfigSources.create(settings));

        MeterRegistry meterRegistry = MetricsFactory.getInstance().globalRegistry(
                MetricsConfig.create(config.get("metrics")));

        Counter counter1 = meterRegistry.getOrCreate(Counter.builder("a")
                                                             .tags(List.of(Tag.create("local1", "a"))));

        Tag g1 = Tag.create("g1", "v1");
        Tag g2 = Tag.create("g2", "v2");

        // Global tags should appear only in output, not in the meter's ID itself.
        assertThat("New counter's tags",
                   counter1.id().tags(),
                   not(hasItems(g1, g2)));

        assertThat("New counter's explicit tags",
                   counter1.id().tags(),
                   hasItem(Tag.create("local1", "a")));

    }
}
