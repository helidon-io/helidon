/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.spi.MetricsProgrammaticConfig;

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

    private static final String APP_NAME = "my-app";

    private static final Map<String, String> GLOBAL_ONLY_TAGS_SETTINGS = Map.of(
            MetricsConfig.METRICS_CONFIG_KEY
                    + ".tags",
            String.format("%s=%s,%s=%s", GLOBAL_TAG_1, GLOBAL_VALUE_1, GLOBAL_TAG_2, GLOBAL_VALUE_2));

    private static final Map<String, String> APP_ONLY_TAGS_SETTINGS = Map.of(
            MetricsConfig.METRICS_CONFIG_KEY
                    + "." + "app-name",
            APP_NAME);

    private static final Map<String, String> GLOBAL_AND_APP_TAG_SETTINGS;

    static {
        GLOBAL_AND_APP_TAG_SETTINGS = new HashMap<>(GLOBAL_ONLY_TAGS_SETTINGS);
        GLOBAL_AND_APP_TAG_SETTINGS.putAll(APP_ONLY_TAGS_SETTINGS);
    }

    @Test
    void checkMetricsSettingsForGlobalTagsConfig() {
        Config mConfig = Config.just(ConfigSources.create(GLOBAL_ONLY_TAGS_SETTINGS)).get("metrics");
        MetricsConfig metricsConfig = MetricsConfig.create(mConfig);
        Map<String, String> tags = new HashMap<>();

        metricsConfig.tags().forEach(t -> tags.put(t.key(), t.value()));

        assertThat("Global tags in settings",
                   tags,
                   allOf(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1),
                         hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2),
                         not(hasKey("app-name"))));
    }

    @Test
    void checkSystemTagsManagerForGlobalTags() {
        Config mConfig = Config.just(ConfigSources.create(GLOBAL_ONLY_TAGS_SETTINGS)).get("metrics");
        MetricsConfig metricsConfig = MetricsConfig.create(mConfig);
        SystemTagsManager mgr = SystemTagsManager.create(metricsConfig);

        Map<String, String> fullTags = new HashMap<>();
        mgr.displayTags().forEach(entry -> fullTags.put(entry.key(), entry.value()));

        assertThat("Global tags derived from tagless metric ID",
                   fullTags, allOf(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1),
                                   hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2),
                                   not(hasKey("app-name"))));
    }

    @Test
    void checkForAppTag() {
        Config mConfig = Config.just(ConfigSources.create(APP_ONLY_TAGS_SETTINGS)).get("metrics");
        // Set up the metrics factory which applies the SE-specific programmatic defaults for the system tags manager.
        MetricsFactory metricsFactory = MetricsFactory.getInstance(mConfig);
        MetricsConfig metricsConfig = metricsFactory.metricsConfig();
        SystemTagsManager mgr = SystemTagsManager.instance(metricsConfig);

        Meter.Id meterId = MeterId.create("my-metric", io.helidon.metrics.api.Tag.create(METRIC_TAG_NAME,
                                                                                         METRIC_TAG_VALUE));
        Map<String, String> fullTags = new HashMap<>();
        mgr.displayTags().forEach(entry -> fullTags.put(entry.key(), entry.value()));

        assertThat("Global tags derived from tagless metric ID",
                   fullTags, allOf(not(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1)),
                                   not(hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2))));
        // By default, specifying just the app name in config should trigger the default or explicit app tag name and value.
        if (metricsConfig.appName().isPresent()) {
            assertThat("App tag", fullTags, hasKey(MetricsProgrammaticConfig.instance().appTagName().get()));
        }
    }

    @Test
    void checkForGlobalAndAppTags() {
        Config mConfig = Config.just(ConfigSources.create(GLOBAL_AND_APP_TAG_SETTINGS)).get("metrics");
        // Set up the metrics factory which applies the SE-specific programmatic defaults for the system tags manager.
        MetricsFactory metricsFactory = MetricsFactory.getInstance(mConfig);
        MetricsConfig metricsConfig = metricsFactory.metricsConfig();
        SystemTagsManager mgr = SystemTagsManager.instance();

        Meter.Id meterId = MeterId.create("my-metric", io.helidon.metrics.api.Tag.create(METRIC_TAG_NAME,
                                                                                         METRIC_TAG_VALUE));
        Map<String, String> fullTags = new HashMap<>();
        mgr.displayTags().forEach(entry -> fullTags.put(entry.key(), entry.value()));

        assertThat("Global tags derived from tagless metric ID",
                   fullTags, allOf(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1),
                                   hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2)));
        // By default, specifying just the app name in config should trigger the default or explicit app tag name and value.
        if (metricsConfig.appName().isPresent()) {
            assertThat("App tag", fullTags, hasKey(MetricsProgrammaticConfig.instance().appTagName()));
        }
    }

    @Test
    void checkForNoTags() {
        MetricsConfig mConfig = MetricsConfig.create(); // no global tags
        SystemTagsManager mgr = SystemTagsManager.create(mConfig);

        Meter.Id meterId = MeterId.create("no-tags-metric", Set.of());
        Map<String, String> fullTags = new HashMap<>();
        mgr.displayTags().forEach(entry -> fullTags.put(entry.key(), entry.value()));

        assertThat("Global tags (with scope) size", fullTags.size(), is(0));
    }

    @Test
    void checkForGlobalButNoMetricTags() {
        Config mConfig = Config.just(ConfigSources.create(GLOBAL_AND_APP_TAG_SETTINGS)).get("metrics");
        // Set up the metrics factory which applies the SE-specific programmatic defaults for the system tags manager.
        MetricsFactory metricsFactory = MetricsFactory.getInstance(mConfig);
        MetricsConfig metricsConfig = metricsFactory.metricsConfig();
        SystemTagsManager mgr = SystemTagsManager.instance();

        Meter.Id meterId = MeterId.create("no-tags-metric", Set.of());
        Map<String, String> fullTags = new HashMap<>();
        mgr.displayTags().forEach(entry -> fullTags.put(entry.key(), entry.value()));

        assertThat("Global tags derived from tagless metric ID",
                   fullTags, allOf(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1),
                                   hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2)));
        // By default, specifying just the app name in config should trigger the default or explicit app tag name and value.
        if (metricsConfig.appName().isPresent()) {
            assertThat("App tag", fullTags, hasKey(MetricsProgrammaticConfig.instance().appTagName()));
        }
    }
}
