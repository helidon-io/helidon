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

package io.helidon.builder.test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.builder.test.testsubjects.ConditionalProvider;
import io.helidon.builder.test.testsubjects.ConditionalProviderRegistry;
import io.helidon.builder.test.testsubjects.SomeProvider;
import io.helidon.builder.test.testsubjects.SomeServiceProvider1;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConditionalProviderTest {
    @Test
    void disabledProviderIgnoresConfiguredService() {
        var config = config(Map.of("enabled", "false", "service.test.type", "unknown"));
        assertThat(ConditionalProvider.create(config).service(), optionalEmpty());
    }

    @Test
    void programmaticEnablementOverridesConfiguration() {
        var config = config(Map.of("enabled", "true", "service.test.type", "unknown"));
        assertThat(ConditionalProvider.builder().config(config).enabled(false).build().service(), optionalEmpty());
        assertThrows(ConfigException.class, () -> ConditionalProvider.create(config));
    }

    @Test
    void enabledProviderCreatesConfiguredService() {
        var config = config(Map.of("service.test.type", "some-1", "service.test.prop", "configured"));
        assertThat(ConditionalProvider.create(config).service().map(SomeProvider.SomeService::prop),
                   optionalValue(is("configured")));
    }

    @Test
    void disabledProviderPreservesExplicitService() {
        var service = new SomeServiceProvider1().create(Config.empty(), "explicit");
        var value = ConditionalProvider.builder().enabled(false).service(service).build();
        assertThat(value.service(), optionalValue(sameInstance(service)));
    }

    @Test
    void disabledProviderSkipsListValidation() {
        var config = config(Map.of("services.test.type", "some-1"));
        assertThat(ConditionalProvider.builder().config(config).enabled(false).build().services(), empty());
        assertThrows(ConfigException.class, () -> ConditionalProvider.create(config));
    }

    @Test
    void disabledProviderSkipsAutomaticDiscovery() {
        var value = ConditionalProvider.builder().enabled(false).serviceDiscoverServices(true).build();
        assertThat(value.service(), optionalEmpty());
        assertThat(ConditionalProvider.builder().serviceDiscoverServices(true).build().service()
                           .map(SomeProvider.SomeService::type),
                   optionalValue(is("some-1")));
    }

    @Test
    void disabledProviderSkipsNonConfiguredDiscovery() {
        assertThat(ConditionalProvider.builder().enabled(false).build().providers(), empty());
        assertThat(ConditionalProvider.builder().build().providers(), hasSize(2));
    }

    @Test
    void registryProviderIsCreatedOnlyWhenEnabled() {
        var calls = new AtomicInteger();
        SomeProvider provider = new SomeProvider() {
            @Override
            public String configKey() {
                return "registry-provider";
            }

            @Override
            public SomeService create(Config config, String name) {
                calls.incrementAndGet();
                return new SomeServiceProvider1().create(config, name);
            }
        };
        var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                            .putContractInstance(SomeProvider.class, provider)
                                                            .build());
        try {
            var config = config(Map.of("service.test.type", "registry-provider", "service.test.prop", "configured"));
            var disabled = ConditionalProviderRegistry.builder()
                    .config(config)
                    .serviceRegistry(manager.registry())
                    .enabled(false)
                    .build();
            assertThat(disabled.service(), optionalEmpty());
            assertThat(calls.get(), is(0));

            var enabled = ConditionalProviderRegistry.builder()
                    .config(config)
                    .serviceRegistry(manager.registry())
                    .build();
            assertThat(enabled.service().map(SomeProvider.SomeService::prop), optionalValue(is("configured")));
            assertThat(calls.get(), is(1));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void disabledRegistryProviderSkipsListValidation() {
        var config = config(Map.of("services.test.type", "some-1"));
        assertThat(ConditionalProviderRegistry.builder().config(config).enabled(false).build().services(), empty());
        assertThrows(ConfigException.class, () -> ConditionalProviderRegistry.create(config));
    }

    private static Config config(Map<String, String> values) {
        return Config.just(ConfigSources.create(values));
    }
}
