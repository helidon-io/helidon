/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.hamcrest.collection.IsMapContaining.hasKey;

class TestSystemTagsManager {

    private static final String GLOBAL_TAG_1 = "gt1";
    private static final String GLOBAL_VALUE_1 = "v1";
    private static final String GLOBAL_TAG_2 = "gt2";
    private static final String GLOBAL_VALUE_2 = "v2";

    private static final String METRIC_TAG_NAME = "myTag";
    private static final String METRIC_TAG_VALUE = "myValue";

    private static final String APP_TAG_VALUE = "my-app";

    private static final Map<String, String> GLOBAL_ONLY_TAGS_SETTINGS = Map.of(
            MetricsSettings.Builder.METRICS_CONFIG_KEY
                    + "." + MetricsSettings.Builder.GLOBAL_TAGS_CONFIG_KEY,
            String.format("%s=%s,%s=%s", GLOBAL_TAG_1, GLOBAL_VALUE_1, GLOBAL_TAG_2, GLOBAL_VALUE_2));

    private static final Map<String, String> APP_ONLY_TAGS_SETTINGS = Map.of(
            MetricsSettings.Builder.METRICS_CONFIG_KEY
                    + "." + MetricsSettings.Builder.APP_TAG_CONFIG_KEY,
            APP_TAG_VALUE);

    private static final Map<String, String> GLOBAL_AND_APP_TAG_SETTINGS;

    static {
        GLOBAL_AND_APP_TAG_SETTINGS = new HashMap<>(GLOBAL_ONLY_TAGS_SETTINGS);
        GLOBAL_AND_APP_TAG_SETTINGS.putAll(APP_ONLY_TAGS_SETTINGS);
    }

    @Test
    void checkMetricsSettingsForGlobalTagsConfig() {
        Config metricsConfig = Config.just(ConfigSources.create(GLOBAL_ONLY_TAGS_SETTINGS)).get("metrics");
        MetricsSettings metricsSettings = MetricsSettings.create(metricsConfig);
        Map<String, String> tags = metricsSettings.globalTags();

        assertThat("Global tags in settings",
                   tags,
                   allOf(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1),
                         hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2),
                         not(hasKey(MetricsSettings.Builder.APP_TAG_CONFIG_KEY))));
    }

    @Test
    void checkSystemTagsManagerForGlobalTags() {
        Config metricsConfig = Config.just(ConfigSources.create(GLOBAL_ONLY_TAGS_SETTINGS)).get("metrics");
        MetricsSettings metricsSettings = MetricsSettings.create(metricsConfig);
        SystemTagsManager mgr = SystemTagsManager.create(metricsSettings);

        MetricID metricID = new MetricID("my-metric", new Tag(METRIC_TAG_NAME, METRIC_TAG_VALUE));
        Map<String, String> fullTags = new HashMap<>();
        mgr.allTags(metricID).forEach(entry -> fullTags.put(entry.getKey(), entry.getValue()));

        assertThat("Global tags derived from tagless metric ID",
                   fullTags, allOf(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1),
                                   hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2),
                                   hasEntry(METRIC_TAG_NAME, METRIC_TAG_VALUE),
                                   not(hasKey(SystemTagsManager.APP_TAG))));

    }

    @Test
    void checkForAppTag() {
        Config metricsConfig = Config.just(ConfigSources.create(APP_ONLY_TAGS_SETTINGS)).get("metrics");
        MetricsSettings metricsSettings = MetricsSettings.create(metricsConfig);
        SystemTagsManager mgr = SystemTagsManager.create(metricsSettings);

        MetricID metricID = new MetricID("my-metric", new Tag(METRIC_TAG_NAME, METRIC_TAG_VALUE));
        Map<String, String> fullTags = new HashMap<>();
        mgr.allTags(metricID).forEach(entry -> fullTags.put(entry.getKey(), entry.getValue()));

        assertThat("Global tags derived from tagless metric ID",
                   fullTags, allOf(not(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1)),
                                   not(hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2)),
                                   hasEntry(METRIC_TAG_NAME, METRIC_TAG_VALUE),
                                   hasKey(SystemTagsManager.APP_TAG)));
    }

    @Test
    void checkForGlobalAndAppTags() {
        Config metricsConfig = Config.just(ConfigSources.create(GLOBAL_AND_APP_TAG_SETTINGS)).get("metrics");
        MetricsSettings metricsSettings = MetricsSettings.create(metricsConfig);
        SystemTagsManager mgr = SystemTagsManager.create(metricsSettings);

        MetricID metricID = new MetricID("my-metric", new Tag(METRIC_TAG_NAME, METRIC_TAG_VALUE));
        Map<String, String> fullTags = new HashMap<>();
        mgr.allTags(metricID).forEach(entry -> fullTags.put(entry.getKey(), entry.getValue()));

        assertThat("Global tags derived from tagless metric ID",
                   fullTags, allOf(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1),
                                   hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2),
                                   hasEntry(METRIC_TAG_NAME, METRIC_TAG_VALUE),
                                   hasKey(SystemTagsManager.APP_TAG)));
    }

    @Test
    void checkForNoTags() {
        MetricsSettings metricsSettings = MetricsSettings.create(); // no global tags
        SystemTagsManager mgr = SystemTagsManager.create(metricsSettings);

        MetricID metricID = new MetricID("no-tags-metric");
        Map<String, String> fullTags = new HashMap<>();
        mgr.allTags(metricID).forEach(entry -> fullTags.put(entry.getKey(), entry.getValue()));

        assertThat("All tags for metric ID with no tags itself and no global tags is empty",
                   fullTags.isEmpty(),
                   is(true));
    }

    @Test
    void checkForGlobalButNoMetricTags() {
        Config metricsConfig = Config.just(ConfigSources.create(GLOBAL_AND_APP_TAG_SETTINGS)).get("metrics");
        MetricsSettings metricsSettings = MetricsSettings.create(metricsConfig);
        SystemTagsManager mgr = SystemTagsManager.create(metricsSettings);

        MetricID metricID = new MetricID("no-tags-metric");
        Map<String, String> fullTags = new HashMap<>();
        mgr.allTags(metricID).forEach(entry -> fullTags.put(entry.getKey(), entry.getValue()));

        assertThat("Global tags derived from tagless metric ID",
                   fullTags, allOf(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1),
                                   hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2),
                                   hasKey(SystemTagsManager.APP_TAG)));
    }
}
