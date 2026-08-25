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

import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ConfigSourceProvider;
import io.helidon.service.registry.ServiceRegistry;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigProviderRegistryTest {
    private static final String PROVIDER_TYPE = "registry-provider";
    private static final String NAMED_SOURCE_TYPE = "registry-named-source";

    @Test
    void builderUsesConfiguredRegistryForMetaConfigSources() {
        ServiceRegistry registry = mock(ServiceRegistry.class);
        when(registry.all(ConfigSourceProvider.class))
                .thenReturn(List.of(new RegistryConfigSourceProvider()));

        Config config = Config.builder()
                .serviceRegistry(registry)
                .config(metaConfig(ObjectNode.builder()
                                    .addValue("type", PROVIDER_TYPE)
                                    .build()))
                .disableParserServices()
                .disableFilterServices()
                .disableMapperServices()
                .build();
        try {
            assertThat(config.get("registry-provider-value").asString().get(), is("from-registry-provider"));
        } finally {
            config.context().stopChangeSupport();
        }
    }

    @Test
    void managedProviderUsesRegistryForNestedPrefixedSource() {
        ServiceRegistry registry = mock(ServiceRegistry.class);
        when(registry.all(ConfigSourceProvider.class)).thenReturn(List.of());
        when(registry.firstNamed(ConfigSource.class, NAMED_SOURCE_TYPE))
                .thenReturn(Optional.of(ConfigSources.create(Map.of("value", "from-named-source")).build()));

        Config metaConfig = metaConfig(ObjectNode.builder()
                                               .addValue("type", "prefixed")
                                               .addObject("properties", ObjectNode.builder()
                                                       .addValue("key", "registry")
                                                       .addValue("type", NAMED_SOURCE_TYPE)
                                                       .build())
                                               .build());
        ConfigProvider provider = new ConfigProvider(() -> Optional.of(new MetaConfig(metaConfig)),
                                                     List::of,
                                                     List::of,
                                                     List::of,
                                                     List::of,
                                                     registry);
        try {
            assertThat(provider.get().get("registry.value").asString().get(), is("from-named-source"));
        } finally {
            provider.preDestroy();
        }
    }

    private static Config metaConfig(ObjectNode source) {
        return Config.just(ConfigSources.create(ObjectNode.builder()
                                                        .addList("sources", ListNode.builder()
                                                                .addObject(source)
                                                                .build())
                                                        .build()));
    }

    private static final class RegistryConfigSourceProvider implements ConfigSourceProvider {
        @Override
        public boolean supports(String type) {
            return PROVIDER_TYPE.equals(type);
        }

        @Override
        public ConfigSource create(String type, Config metaConfig) {
            return ConfigSources.create(Map.of("registry-provider-value", "from-registry-provider")).build();
        }

        @Override
        public Set<String> supported() {
            return Set.of(PROVIDER_TYPE);
        }
    }
}
