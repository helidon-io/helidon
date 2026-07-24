/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.spi.MetricsProgrammaticConfig;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        Config rootConfig = Config.just(ConfigSources.create(APP_ONLY_TAGS_SETTINGS));
        // Set up the metrics factory which applies the SE-specific programmatic defaults for the system tags manager.
        MetricsConfig metricsConfig = metricsConfig(rootConfig);
        SystemTagsManager mgr = SystemTagsManager.create(metricsConfig);

        Meter.Id meterId = MeterId.create("my-metric",
                                          Services.get(MetricsFactory.class).tagCreate(METRIC_TAG_NAME, METRIC_TAG_VALUE));
        Map<String, String> fullTags = new HashMap<>();
        mgr.displayTags().forEach(entry -> fullTags.put(entry.key(), entry.value()));

        assertThat("Global tags derived from tagless metric ID",
                   fullTags, allOf(not(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1)),
                                   not(hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2))));
        // By default, specifying just the app name in config should trigger the default or explicit app tag name and value.
        if (metricsConfig.appName().isPresent()) {
            assertThat("App tag", fullTags, hasKey(Services.get(MetricsProgrammaticConfig.class).appTagName().get()));
        }
    }

    @Test
    void checkForGlobalAndAppTags() {
        Config rootConfig = Config.just(ConfigSources.create(GLOBAL_AND_APP_TAG_SETTINGS));
        // Set up the metrics factory which applies the SE-specific programmatic defaults for the system tags manager.
        MetricsConfig metricsConfig = metricsConfig(rootConfig);
        SystemTagsManager mgr = SystemTagsManager.create(metricsConfig);

        Meter.Id meterId = MeterId.create("my-metric",
                                          Services.get(MetricsFactory.class).tagCreate(METRIC_TAG_NAME, METRIC_TAG_VALUE));
        Map<String, String> fullTags = new HashMap<>();
        mgr.displayTags().forEach(entry -> fullTags.put(entry.key(), entry.value()));

        assertThat("Global tags derived from tagless metric ID",
                   fullTags, allOf(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1),
                                   hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2)));
        // By default, specifying just the app name in config should trigger the default or explicit app tag name and value.
        if (metricsConfig.appName().isPresent()) {
            assertThat("App tag", fullTags, hasKey(Services.get(MetricsProgrammaticConfig.class).appTagName().get()));
        }
    }

    @Test
    void checkForNoTags() {
        MetricsConfig mConfig = MetricsConfig.create(Config.empty()); // no global tags
        SystemTagsManager mgr = SystemTagsManager.create(mConfig);

        Meter.Id meterId = MeterId.create("no-tags-metric", Set.of());
        Map<String, String> fullTags = new HashMap<>();
        mgr.displayTags().forEach(entry -> fullTags.put(entry.key(), entry.value()));

        assertThat("Global tags (with scope) size", fullTags.size(), is(0));
    }

    @Test
    void createReturnsNonGlobalSystemTagsManager() {
        SystemTagsManager shared = Services.get(SystemTagsManager.class);

        Config mConfig = Config.just(ConfigSources.create(GLOBAL_ONLY_TAGS_SETTINGS)).get("metrics");
        SystemTagsManager configured = SystemTagsManager.create(MetricsConfig.create(mConfig));
        assertThat(configured, not(sameInstance(shared)));

        Map<String, String> fullTags = new HashMap<>();
        configured.displayTags().forEach(entry -> fullTags.put(entry.key(), entry.value()));

        assertThat("Global tags from non-global manager",
                   fullTags, allOf(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1),
                                   hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2)));
    }

    @Test
    void customSystemTagsManagerUsesProvidedFactory() {
        AtomicBoolean tagCreated = new AtomicBoolean();
        MetricsFactory metricsFactory = new NoOpMetricsFactory() {
            @Override
            public Tag tagCreate(String key, String value) {
                tagCreated.set(true);
                return super.tagCreate(key, value);
            }
        };
        Config mConfig = Config.just(ConfigSources.create(GLOBAL_ONLY_TAGS_SETTINGS)).get("metrics");
        SystemTagsManager manager = SystemTagsManager.create(MetricsConfig.create(mConfig), metricsFactory);

        manager.displayTags();

        assertThat("Provided factory used for creating tags", tagCreated.get(), is(true));
    }

    @Test
    void customSystemTagsManagerUsesProvidedConfigForReservedNames() {
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .appTagName("customApp")
                .scoping(ScopingConfig.builder()
                                 .tagName("customScope"))
                .build();
        SystemTagsManager manager = SystemTagsManager.create(metricsConfig, new NoOpMetricsFactory());

        assertThat("Reserved names come from the provided metrics config",
                   Set.copyOf(manager.reservedTagNamesUsed(Set.of("customApp", "customScope", "other"))),
                   is(Set.of("customApp", "customScope")));
    }

    @Test
    @SuppressWarnings("removal")
    void legacyInstanceRejectsNullConfig() {
        assertThrows(NullPointerException.class, () -> SystemTagsManager.instance(null));
    }

    @Test
    @SuppressWarnings("removal")
    void legacyMutationMethodsDoNotChangeSharedManager() {
        SystemTagsManager shared = Services.get(SystemTagsManager.class);
        AtomicBoolean changed = new AtomicBoolean();

        SystemTagsManager.onChange(ignored -> changed.set(true));
        SystemTagsManager custom = SystemTagsManager.create(MetricsConfig.create());

        assertThat("Configured legacy instance",
                   SystemTagsManager.instance(MetricsConfig.create()),
                   sameInstance(shared));
        assertThat("Custom system tags manager", custom, not(sameInstance(shared)));
        assertThat("Ignored change listener", changed.get(), is(false));
    }

    @Test
    void checkForGlobalButNoMetricTags() {
        Config rootConfig = Config.just(ConfigSources.create(GLOBAL_AND_APP_TAG_SETTINGS));
        // Set up the metrics factory which applies the SE-specific programmatic defaults for the system tags manager.
        MetricsConfig metricsConfig = metricsConfig(rootConfig);
        SystemTagsManager mgr = SystemTagsManager.create(metricsConfig);

        Meter.Id meterId = MeterId.create("no-tags-metric", Set.of());
        Map<String, String> fullTags = new HashMap<>();
        mgr.displayTags().forEach(entry -> fullTags.put(entry.key(), entry.value()));

        assertThat("Global tags derived from tagless metric ID",
                   fullTags, allOf(hasEntry(GLOBAL_TAG_1, GLOBAL_VALUE_1),
                                   hasEntry(GLOBAL_TAG_2, GLOBAL_VALUE_2)));
        // By default, specifying just the app name in config should trigger the default or explicit app tag name and value.
        if (metricsConfig.appName().isPresent()) {
            assertThat("App tag", fullTags, hasKey(Services.get(MetricsProgrammaticConfig.class).appTagName().get()));
        }
    }

    private static MetricsConfig metricsConfig(Config rootConfig) {
        return Services.get(MetricsProgrammaticConfig.class)
                .apply(MetricsConfig.create(rootConfig.get(MetricsConfig.METRICS_CONFIG_KEY)));
    }
}
