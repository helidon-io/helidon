/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.overrides;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigFilterProvider;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class OverrideConfigFilterTest {
    @Test
    void testBuilderReuseCapturesIndependentRules() {
        var builder = OverrideConfigFilter.builder()
                .putOverrideExpression("service.*.level", "first");
        var first = builder.build();
        var second = builder.putOverrideExpression("service.*.level", "second").build();
        var key = Config.Key.create("service.alpha.level");

        assertThat(first.apply(key, "original"), is("first"));
        assertThat(second.apply(key, "original"), is("second"));
        assertThat(first.prototype().overrideExpressions().get("service.*.level"), is("first"));
        assertThat(second.prototype().overrideExpressions().get("service.*.level"), is("second"));
    }

    @Test
    void testReplacementValueResolutionAndWildcardBoundary() {
        var filter = OverrideConfigFilter.builder()
                .putOverrideExpression("service.*.level", "${replacement}")
                .build();
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilter(filter)
                .addSource(ConfigSources.create(Map.of("replacement", "resolved",
                                                      "service.alpha.level", "original",
                                                      "service.my-pod.level", "hyphenated")))
                .build();

        assertThat(config.get("service.alpha.level").asString().get(), is("resolved"));
        assertThat(config.get("service.my-pod.level").asString().get(), is("hyphenated"));
    }

    @Test
    void testNoConfig() {
        Config config = Config.just(ConfigSources.classpath("/config.yaml"));

        assertThat(config.get("prod.abcdef.logging.level").asString().get(), is("ERROR"));
        assertThat(config.get("prod.efgh.logging.level").asString().get(), is("ERROR"));
        assertThat(config.get("test.abcdef.logging.level").asString().get(), is("ERROR"));
    }

    @Test
    void testSourceReplacementValueUsesTargetResolution() {
        var filter = OverrideConfigFilter.builder()
                .addConfigSource(ConfigSources.create(Map.of("service.level", "${replacement}")).build())
                .build();
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilter(filter)
                .addSource(ConfigSources.create(Map.of("service.level", "original", "replacement", "resolved")))
                .build();

        assertThat(config.get("service.level").asString().get(), is("resolved"));
    }

    @Test
    void testDocConfigPrototype() {
        var filter = OverrideConfigFilter.builder()
                .putOverrideExpression("prod.abcdef.logging.level", "FINEST")
                .putOverrideExpression("prod.*.logging.level", "WARNING")
                .putOverrideExpression("test.*.logging.level", "FINE")
                .build();

        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilter(filter)
                .addSource(ConfigSources.classpath("/config.yaml"))
                .build();

        assertThat(config.get("prod.abcdef.logging.level").asString().get(), is("FINEST"));
        assertThat(config.get("prod.efgh.logging.level").asString().get(), is("WARNING"));
        assertThat(config.get("test.abcdef.logging.level").asString().get(), is("FINE"));
    }

    @Test
    void testDocConfigSource() {
        var filter = OverrideConfigFilter.builder()
                .addConfigSource(ConfigSources.classpath("/overrides.properties").get())
                .build();

        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilter(filter)
                .addSource(ConfigSources.classpath("/config.yaml"))
                .build();

        assertThat(config.get("prod.abcdef.logging.level").asString().get(), is("FINEST"));
        assertThat(config.get("prod.efgh.logging.level").asString().get(), is("WARNING"));
        assertThat(config.get("test.abcdef.logging.level").asString().get(), is("FINE"));
    }

    @Test
    void testDocConfigInstance() {
        var filter = OverrideConfigFilter.create(Config.builder()
                                            .addSource(ConfigSources.classpath("/overrides.properties"))
                                            .disableEnvironmentVariablesSource()
                                            .disableSystemPropertiesSource()
                                            // we do not want to use an override filter for its own config source
                                            .disableFilterServices()
                                            .build());

        assertThat(filter.prototype().overrideExpressions().get("prod.*.logging.level"), is("WARNING"));

        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilter(filter)
                .addSource(ConfigSources.classpath("/config.yaml"))
                .build();

        assertThat(config.get("prod.abcdef.logging.level").asString().get(), is("FINEST"));
        assertThat(config.get("prod.efgh.logging.level").asString().get(), is("WARNING"));
        assertThat(config.get("test.abcdef.logging.level").asString().get(), is("FINE"));
    }

    @Test
    void testInlinedOverrides() {
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .addSource(ConfigSources.classpath("/config.yaml"))
                .addSource(ConfigSources.classpath("/config-with-overrides.yaml"))
                .build();

        assertThat(config.get("prod.abcdef.logging.level").asString().get(), is("FINEST"));
        assertThat(config.get("prod.efgh.logging.level").asString().get(), is("WARNING"));
        assertThat(config.get("test.abcdef.logging.level").asString().get(), is("FINE"));
    }

    @Test
    void testAutomaticSourceDescriptors() {
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .addSource(ConfigSources.create(Map.of("overrides.sources.0.type", "classpath",
                                                      "overrides.sources.0.properties.resource", "overrides.properties")))
                .addSource(ConfigSources.classpath("/config.yaml"))
                .build();
        try {
            assertThat(config.get("prod.abcdef.logging.level").asString().get(), is("FINEST"));
            assertThat(config.get("prod.efgh.logging.level").asString().get(), is("WARNING"));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void testAutomaticReplacementUsesTargetValueResolution() {
        Config config = Config.builder().disableEnvironmentVariablesSource().disableSystemPropertiesSource()
                .addSource(ConfigSources.create(Map.of("overrides.expressions.service.*.level", "${replacement}",
                                                      "replacement", "resolved",
                                                      "service.alpha.level", "original",
                                                      "service.my-pod.level", "hyphenated")))
                .build();
        try {
            assertThat(config.get("service.alpha.level").asString().get(), is("resolved"));
            assertThat(config.get("service.my-pod.level").asString().get(), is("hyphenated"));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void testRegistrySingletonProviderHasIndependentRuntimeSettings() {
        var manager = ServiceRegistryManager.create();
        try {
            var registry = manager.registry();
            var provider = registry.get(ConfigFilterProvider.class);
            assertThat(registry.get(ConfigFilterProvider.class), sameInstance(provider));
            Config first = Config.builder().disableEnvironmentVariablesSource().disableSystemPropertiesSource()
                    .disableFilterServices().disableCaching().addFilterProvider(provider)
                    .addSource(ConfigSources.create(Map.of("service.level", "original",
                                                          "overrides.expressions.service.level", "first"))).build();
            Config second = Config.builder().disableEnvironmentVariablesSource().disableSystemPropertiesSource()
                    .disableFilterServices().disableCaching().addFilterProvider(provider)
                    .addSource(ConfigSources.create(Map.of("service.level", "original",
                                                          "overrides.expressions.service.level", "second"))).build();
            try {
                assertThat(first.get("service.level").asString().get(), is("first"));
                assertThat(second.get("service.level").asString().get(), is("second"));
                assertThat(first.context().reload().get("service.level").asString().get(), is("first"));
            } finally {
                first.context().stopChangeSupport();
                second.context().stopChangeSupport();
            }
        } finally {
            manager.shutdown();
        }
    }
}
