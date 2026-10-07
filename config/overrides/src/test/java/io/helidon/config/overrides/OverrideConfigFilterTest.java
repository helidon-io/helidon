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
import java.util.regex.Pattern;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigFilterProvider;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class OverrideConfigFilterTest {
    @Test
    void testBuilderReuseCapturesIndependentRules() {
        var builder = OverrideConfigFilterProvider.builder()
                .putOverrideExpression("services.*.endpoint", "https://canary.example/orders");
        var firstPrototype = builder.buildPrototype();
        var first = builder.build();
        var second = builder.putOverrideExpression("services.*.endpoint", "https://stable.example/orders").build();
        var key = Config.Key.create("services.orders.endpoint");
        var firstFactory = first.create(Config.empty());
        var secondFactory = second.create(Config.empty());
        try {
            assertThat(firstFactory.create(Config.empty()).apply(key, "https://primary.example/orders"),
                       is("https://canary.example/orders"));
            assertThat(secondFactory.create(Config.empty()).apply(key, "https://primary.example/orders"),
                       is("https://stable.example/orders"));
        } finally {
            firstFactory.stopChangeSupport();
            secondFactory.stopChangeSupport();
        }
        assertThat(first.prototype().overrideExpressions().get("services.*.endpoint"),
                   is("https://canary.example/orders"));
        assertThat(second.prototype().overrideExpressions().get("services.*.endpoint"),
                   is("https://stable.example/orders"));
        assertThat(firstPrototype.overrideExpressions().get("services.*.endpoint"),
                   is("https://canary.example/orders"));
    }

    @Test
    void testReplacementValueResolutionAndWildcardBoundary() {
        var provider = OverrideConfigFilterProvider.builder()
                .putOverrideExpression("services.*.endpoint", "${services.failover-endpoint}")
                .build();
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilterProvider(provider)
                .addSource(ConfigSources.create(Map.of("services.failover-endpoint", "https://failover.example/orders",
                                                      "services.orders.endpoint", "https://primary.example/orders",
                                                      "services.order-worker.endpoint",
                                                      "https://worker.example/orders")))
                .build();

        assertThat(config.get("services.orders.endpoint").asString().get(), is("https://failover.example/orders"));
        assertThat(config.get("services.order-worker.endpoint").asString().get(), is("https://worker.example/orders"));
    }

    @Test
    void testConfiguredProviderIgnoresTargetOverrideSettings() {
        var provider = OverrideConfigFilterProvider.builder()
                .putOverrideExpression("services.orders.endpoint", "https://canary.example/orders")
                .build();
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilterProvider(provider)
                .addSource(ConfigSources.create(Map.of("services.orders.endpoint", "https://primary.example/orders",
                                                      "overrides.expressions.services.orders.endpoint",
                                                      "https://target.example/orders")))
                .build();
        try {
            assertThat(config.get("services.orders.endpoint").asString().get(), is("https://canary.example/orders"));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void testProgrammaticPatternsPreserveFlagsAndFirstMatchPriority() {
        var provider = OverrideConfigFilterProvider.builder()
                .addOverridePattern(rule -> rule.pattern(Pattern.compile("services\\.orders\\.endpoint",
                                                                        Pattern.CASE_INSENSITIVE))
                        .value("https://canary.example/orders"))
                .addOverridePattern(rule -> rule.pattern(Pattern.compile("services\\.\\w+\\.endpoint"))
                        .value("https://stable.example/api"))
                .build();
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilterProvider(provider)
                .addSource(ConfigSources.create(Map.of("services.orders.endpoint", "https://primary.example/orders",
                                                      "services.ORDERS.endpoint", "https://uppercase.example/orders",
                                                      "services.payments.endpoint",
                                                      "https://primary.example/payments")))
                .build();
        try {
            assertThat(config.get("services.orders.endpoint").asString().get(), is("https://canary.example/orders"));
            assertThat(config.get("services.ORDERS.endpoint").asString().get(), is("https://canary.example/orders"));
            assertThat(config.get("services.payments.endpoint").asString().get(), is("https://stable.example/api"));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void testNoConfig() {
        Config config = Config.just(ConfigSources.classpath("/config.yaml"));

        assertThat(config.get("environments.prod.orders.batch-size").asString().get(), is("100"));
        assertThat(config.get("environments.prod.payments.batch-size").asString().get(), is("75"));
        assertThat(config.get("environments.test.orders.batch-size").asString().get(), is("50"));
    }

    @Test
    void testSourceReplacementValueUsesTargetResolution() {
        var provider = OverrideConfigFilterProvider.builder()
                .addConfigSource(ConfigSources.create(Map.of("services.orders.endpoint",
                                                            "${services.failover-endpoint}"))
                                         .build())
                .build();
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilterProvider(provider)
                .addSource(ConfigSources.create(Map.of("services.orders.endpoint", "https://primary.example/orders",
                                                      "services.failover-endpoint", "https://failover.example/orders")))
                .build();

        assertThat(config.get("services.orders.endpoint").asString().get(), is("https://failover.example/orders"));
    }

    @Test
    void testDocConfigPrototype() {
        var provider = OverrideConfigFilterProvider.builder()
                .putOverrideExpression("environments.prod.orders.batch-size", "150")
                .putOverrideExpression("environments.prod.*.batch-size", "200")
                .putOverrideExpression("environments.test.*.batch-size", "25")
                .build();

        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilterProvider(provider)
                .addSource(ConfigSources.classpath("/config.yaml"))
                .build();

        assertThat(config.get("environments.prod.orders.batch-size").asString().get(), is("150"));
        assertThat(config.get("environments.prod.payments.batch-size").asString().get(), is("200"));
        assertThat(config.get("environments.test.orders.batch-size").asString().get(), is("25"));
    }

    @Test
    void testDocConfigSource() {
        var provider = OverrideConfigFilterProvider.builder()
                .addConfigSource(ConfigSources.classpath("/overrides.properties").get())
                .build();

        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilterProvider(provider)
                .addSource(ConfigSources.classpath("/config.yaml"))
                .build();

        assertThat(config.get("environments.prod.orders.batch-size").asString().get(), is("150"));
        assertThat(config.get("environments.prod.payments.batch-size").asString().get(), is("200"));
        assertThat(config.get("environments.test.orders.batch-size").asString().get(), is("25"));
    }

    @Test
    void testGeneratedComponentConfigParsesExpressionsPatternsAndSources() {
        Config settings = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addSource(ConfigSources.create(Map.of("overrides.expressions.environments.prod.orders.batch-size",
                                                      "150",
                                                      "overrides.patterns.0.pattern",
                                                      "environments\\.test\\.\\w+\\.batch-size",
                                                      "overrides.patterns.0.value", "30",
                                                      "overrides.sources.0.type", "classpath",
                                                      "overrides.sources.0.properties.resource", "overrides.properties",
                                                      "app.batch-size", "999")))
                .build();
        var provider = OverrideConfigFilterProvider.builder().config(settings.get("overrides")).build();

        assertThat(provider.prototype().overrideExpressions().get("environments.prod.orders.batch-size"), is("150"));
        assertThat(provider.prototype().overridePatterns().getFirst().pattern().pattern(),
                   is("environments\\.test\\.\\w+\\.batch-size"));
        assertThat(provider.prototype().overridePatterns().getFirst().value(), is("30"));
        assertThat(provider.prototype().sourceDescriptors().getFirst().get("type").asString().get(), is("classpath"));

        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .addFilterProvider(provider)
                .addSource(ConfigSources.classpath("/config.yaml"))
                .build();

        assertThat(config.get("environments.prod.orders.batch-size").asString().get(), is("150"));
        assertThat(config.get("environments.prod.payments.batch-size").asString().get(), is("200"));
        assertThat(config.get("environments.test.orders.batch-size").asString().get(), is("30"));
    }

    @Test
    void testInlinedOverrides() {
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .addSource(ConfigSources.classpath("/config.yaml"))
                .addSource(ConfigSources.classpath("/config-with-overrides.yaml"))
                .build();

        assertThat(config.get("environments.prod.orders.batch-size").asString().get(), is("150"));
        assertThat(config.get("environments.prod.payments.batch-size").asString().get(), is("200"));
        assertThat(config.get("environments.test.orders.batch-size").asString().get(), is("25"));
    }

    @Test
    void testAutomaticSourceDescriptors() {
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .addSource(ConfigSources.create(Map.of("overrides.sources.0.type", "classpath",
                                                      "overrides.sources.0.properties.resource",
                                                      "overrides.properties")))
                .addSource(ConfigSources.classpath("/config.yaml"))
                .build();
        try {
            assertThat(config.get("environments.prod.orders.batch-size").asString().get(), is("150"));
            assertThat(config.get("environments.prod.payments.batch-size").asString().get(), is("200"));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void testAutomaticReplacementUsesTargetValueResolution() {
        Config config = Config.builder().disableEnvironmentVariablesSource().disableSystemPropertiesSource()
                .addSource(ConfigSources.create(Map.of("overrides.expressions.services.*.endpoint",
                                                      "${services.failover-endpoint}",
                                                      "services.failover-endpoint", "https://failover.example/orders",
                                                      "services.orders.endpoint", "https://primary.example/orders",
                                                      "services.order-worker.endpoint",
                                                      "https://worker.example/orders")))
                .build();
        try {
            assertThat(config.get("services.orders.endpoint").asString().get(), is("https://failover.example/orders"));
            assertThat(config.get("services.order-worker.endpoint").asString().get(),
                       is("https://worker.example/orders"));
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
            assertThat(provider, instanceOf(OverrideConfigFilterService.class));
            assertThat(registry.get(ConfigFilterProvider.class), sameInstance(provider));
            Config first = Config.builder().disableEnvironmentVariablesSource().disableSystemPropertiesSource()
                    .disableFilterServices().disableCaching().addFilterProvider(provider)
                    .addSource(ConfigSources.create(Map.of("services.orders.endpoint", "https://primary.example/orders",
                                                          "overrides.expressions.services.orders.endpoint",
                                                          "https://canary.example/orders"))).build();
            Config second = Config.builder().disableEnvironmentVariablesSource().disableSystemPropertiesSource()
                    .disableFilterServices().disableCaching().addFilterProvider(provider)
                    .addSource(ConfigSources.create(Map.of("services.orders.endpoint", "https://primary.example/orders",
                                                          "overrides.expressions.services.orders.endpoint",
                                                          "https://stable.example/orders"))).build();
            try {
                assertThat(first.get("services.orders.endpoint").asString().get(), is("https://canary.example/orders"));
                assertThat(second.get("services.orders.endpoint").asString().get(),
                           is("https://stable.example/orders"));
                assertThat(first.context().reload().get("services.orders.endpoint").asString().get(),
                           is("https://canary.example/orders"));
            } finally {
                first.context().stopChangeSupport();
                second.context().stopChangeSupport();
            }
        } finally {
            manager.shutdown();
        }
    }
}
