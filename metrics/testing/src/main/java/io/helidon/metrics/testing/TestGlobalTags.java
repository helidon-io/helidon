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

import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Tag;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;

class TestGlobalTags {

    @Test
    void testWithoutConfig() {
        List<Tag> globalTags = List.of(Tag.create("g1", "v1"),
                                       Tag.create("g2", "v2"));
        MeterRegistry meterRegistry = Metrics.createMeterRegistry(
                MetricsConfig.builder()
                        .globalTags(globalTags)
                        .build());

        Counter counter1 = meterRegistry.getOrCreate(Counter.builder("a")
                .tags(List.of(Tag.create("local1", "a"))));
        assertThat("New counter's global tags",
                   counter1.id().tags(),
                   hasItems(globalTags.toArray(new Tag[0])));
        assertThat("New counter's original tags",
                   counter1.id().tags(),
                   hasItem(Tag.create("local1", "a")));
    }

    @Test
    void testWithConfig() {
        var settings = Map.of("metrics.global-tags", "g1=v1,g2=v2");

        Config config = Config.just(ConfigSources.create(settings));

        MeterRegistry meterRegistry = Metrics.createMeterRegistry(
                MetricsConfig.create(config.get("metrics")));

        Counter counter1 = meterRegistry.getOrCreate(Counter.builder("a")
                                                             .tags(List.of(Tag.create("local1", "a"))));
        assertThat("New counter's global tags",
                   counter1.id().tags(),
                   hasItems(List.of(Tag.create("g1", "v1"),
                                    Tag.create("g2", "v2")).toArray(new Tag[0])));
        assertThat("New counter's explicit tags",
                   counter1.id().tags(),
                   hasItem(Tag.create("local1", "a")));

    }
}
