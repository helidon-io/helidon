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

import java.util.AbstractMap;

import io.helidon.config.Config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasItem;

class TestGlobalTags {

    @Test
    @DisabledIfSystemProperty(named = "testSelection", matches = "topLevelAndMetricsLevel")
    void testTopLevelTagsIgnoredForMetrics() {
        MetricsSettings metricsSettings = MetricsSettings.create(Config.create().get(MetricsSettings.Builder.METRICS_CONFIG_KEY));
        assertThat("Global tags with top-level 'tags' assigned", metricsSettings.globalTags().entrySet(), emptyIterable());
    }

    @Test
    @EnabledIfSystemProperty(named = "testSelection", matches = "topLevelAndMetricsLevel")
    void testGlobalTagsForMetrics() {
        String tag = "myTag";
        String value = "myValue";
        String globalTags = tag + "=" + value;
        MetricsSettings metricsSettings = MetricsSettings.create(Config.create().get(MetricsSettings.Builder.METRICS_CONFIG_KEY));
        assertThat("Global tags with top-level and metrics 'tags' assigned",
                   metricsSettings.globalTags().entrySet(),
                   hasItem(new AbstractMap.SimpleEntry<>(tag, value)));
    }
}
