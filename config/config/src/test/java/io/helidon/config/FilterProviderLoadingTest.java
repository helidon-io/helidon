/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.spi.ConfigFilterProvider;
import io.helidon.config.spi.ConfigSource;
import io.helidon.service.registry.ExistingInstanceDescriptor;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

class FilterProviderLoadingTest {
    @Test
    void providersAreDiscoveredAndRunBeforeValueResolution() {
        Config config = builder(Map.of(AutoLoadedConfigFilterProvider.VALUE_KEY, "original",
                                       AutoLoadedConfigFilterProvider.REFERENCE_KEY, "unused",
                                       "auto-provider-target", "resolved"))
                .build();
        try {
            assertThat(config.get(AutoLoadedConfigFilterProvider.VALUE_KEY).asString().get(), is("original:1"));
            assertThat(config.get(AutoLoadedConfigFilterProvider.REFERENCE_KEY).asString().get(), is("resolved"));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void discoveryCanBeDisabled() {
        Config config = builder(Map.of(AutoLoadedConfigFilterProvider.VALUE_KEY, "original"))
                .disableFilterServices()
                .build();
        try {
            assertThat(config.get(AutoLoadedConfigFilterProvider.VALUE_KEY).asString().get(), is("original"));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void explicitProvidersPrecedeWeightedDiscoveredProvidersAndLegacyFilters() {
        Config config = builder(Map.of(AutoLoadedConfigFilterProvider.PRIORITY_KEY, "original"))
                .addFilter((key, value) -> AutoLoadedConfigFilterProvider.PRIORITY_KEY.equals(key.toString())
                        ? value + ":legacy" : value)
                .addFilterProvider(initial -> raw -> (key, value) ->
                        AutoLoadedConfigFilterProvider.PRIORITY_KEY.equals(key.toString()) ? value + ":explicit" : value)
                .build();
        try {
            assertThat(config.get(AutoLoadedConfigFilterProvider.PRIORITY_KEY).asString().get(),
                       is("original:explicit:high:low:legacy"));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void reusedBuilderDiscoversIndependentFactoriesWithoutAccumulatingFilters() {
        Config.Builder builder = builder(Map.of(AutoLoadedConfigFilterProvider.VALUE_KEY, "original"));
        Config first = builder.build();
        Config second = builder.build();
        try {
            assertThat(first.get(AutoLoadedConfigFilterProvider.VALUE_KEY).asString().get(), is("original:1"));
            assertThat(second.get(AutoLoadedConfigFilterProvider.VALUE_KEY).asString().get(), is("original:1"));
            assertThat(first.context().reload().get(AutoLoadedConfigFilterProvider.VALUE_KEY).asString().get(), is("original:2"));
            assertThat(second.get(AutoLoadedConfigFilterProvider.VALUE_KEY).asString().get(), is("original:1"));
        } finally {
            first.context().stopChangeSupport();
            second.context().stopChangeSupport();
        }
    }

    @Test
    void sharedExplicitProviderCreatesIndependentFactories() {
        ConfigFilterProvider provider = new AutoLoadedConfigFilterProvider();
        Config first = builder(Map.of(AutoLoadedConfigFilterProvider.VALUE_KEY, "original"))
                .disableFilterServices().addFilterProvider(provider).build();
        Config second = builder(Map.of(AutoLoadedConfigFilterProvider.VALUE_KEY, "original"))
                .disableFilterServices().addFilterProvider(provider).build();
        try {
            assertThat(first.context().reload().get(AutoLoadedConfigFilterProvider.VALUE_KEY).asString().get(), is("original:2"));
            assertThat(second.get(AutoLoadedConfigFilterProvider.VALUE_KEY).asString().get(), is("original:1"));
        } finally {
            first.context().stopChangeSupport();
            second.context().stopChangeSupport();
        }
    }

    @Test
    void managedProviderUsesRegistryProvidersWithoutJavaDiscovery() {
        var factories = new AtomicInteger();
        ConfigFilterProvider filterProvider = initial -> {
            factories.incrementAndGet();
            return raw -> (key, value) -> AutoLoadedConfigFilterProvider.VALUE_KEY.equals(key.toString())
                    ? value + ":registry" : value;
        };
        ConfigProvider provider = new ConfigProvider(Optional::empty,
                                                     () -> List.of(ConfigSources.create(Map.of(
                                                             AutoLoadedConfigFilterProvider.VALUE_KEY, "original")).build()),
                                                     List::of,
                                                     List::of,
                                                     () -> List.of(filterProvider),
                                                     List::of,
                                                     mock(ServiceRegistry.class));
        try {
            assertThat(provider.get().get(AutoLoadedConfigFilterProvider.VALUE_KEY).asString().get(), is("original:registry"));
            assertThat(provider.get().context().reload().get(AutoLoadedConfigFilterProvider.VALUE_KEY).asString().get(),
                       is("original:registry"));
            assertThat(factories.get(), is(1));
        } finally {
            provider.preDestroy();
        }
    }

    @Test
    void serviceRegistryInjectsFilterProvidersIntoManagedConfig() {
        ConfigFilterProvider provider = initial -> raw -> (key, value) ->
                AutoLoadedConfigFilterProvider.VALUE_KEY.equals(key.toString()) ? value + ":registry" : value;
        ConfigSource source = ConfigSources.create(Map.of(AutoLoadedConfigFilterProvider.VALUE_KEY, "original")).build();
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(ConfigProvider__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ExistingInstanceDescriptor.create(provider, Set.of(ConfigFilterProvider.class), 100))
                .addServiceDescriptor(ExistingInstanceDescriptor.create(source, Set.of(ConfigSource.class), 100))
                .build());
        try {
            Config config = manager.registry().get(Config.class);
            assertThat(config.get(AutoLoadedConfigFilterProvider.VALUE_KEY).asString().get(), is("original:registry"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void serviceRegistryBridgesJavaOnlyFilterProvidersIntoManagedConfig() {
        ConfigSource source = ConfigSources.create(Map.of(AutoLoadedConfigFilterProvider.VALUE_KEY, "original",
                                                          AutoLoadedConfigFilterProvider.PRIORITY_KEY, "original")).build();
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(true)
                .addServiceDescriptor(ConfigProvider__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ExistingInstanceDescriptor.create(source, Set.of(ConfigSource.class), 100))
                .build());
        try {
            Config config = manager.registry().get(Config.class);
            assertThat(config.get(AutoLoadedConfigFilterProvider.VALUE_KEY).asString().get(), is("original:1"));
            assertThat(config.get(AutoLoadedConfigFilterProvider.PRIORITY_KEY).asString().get(), is("original:high:low"));
            assertThat(config.context().reload().get(AutoLoadedConfigFilterProvider.VALUE_KEY).asString().get(),
                       is("original:2"));
        } finally {
            manager.shutdown();
        }
    }

    private static Config.Builder builder(Map<String, String> values) {
        return Config.builder(ConfigSources.create(values))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource();
    }
}
