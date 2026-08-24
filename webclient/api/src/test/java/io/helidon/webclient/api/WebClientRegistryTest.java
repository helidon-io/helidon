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

package io.helidon.webclient.api;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class WebClientRegistryTest {
    @Test
    void registryCreatesDefaultClientWhenConfigurationIsMissing() {
        ServiceRegistryManager manager = manager(Config.empty());
        try {
            ServiceRegistry registry = manager.registry();
            WebClient defaultClient = registry.get(WebClient.class);
            WebClient namedDefault = registry.getNamed(WebClient.class, Service.Named.DEFAULT_NAME);

            assertThat(namedDefault, sameInstance(defaultClient));
            assertThat(defaultClient.prototype().baseUri().isEmpty(), is(true));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void registryCreatesConfiguredDefaultAndNamedClients() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "clients.@default.base-uri", "http://default.example",
                "clients.one.base-uri", "http://one.example")));
        TestHttpClientSpiProvider.reset();
        ServiceRegistryManager manager = manager(config);
        try {
            ServiceRegistry registry = manager.registry();
            WebClient defaultClient = registry.get(WebClient.class);
            WebClient namedDefault = registry.getNamed(WebClient.class, Service.Named.DEFAULT_NAME);
            WebClient namedClient = registry.getNamed(WebClient.class, "one");

            assertThat(namedDefault, sameInstance(defaultClient));
            assertThat(defaultClient.prototype().baseUri().orElseThrow().toString(), is("http://default.example:80/"));
            assertThat(namedClient.prototype().baseUri().orElseThrow().toString(), is("http://one.example:80/"));
        } finally {
            manager.shutdown();
        }
        assertThat(TestHttpClientSpiProvider.closeCount(), is(2));
    }

    @Test
    void registryCreatesClientsFromListConfiguration() {
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addList("clients", ConfigNode.ListNode.builder()
                        .addObject(ConfigNode.ObjectNode.builder()
                                           .addValue("name", Service.Named.DEFAULT_NAME)
                                           .addValue("base-uri", "http://default.example")
                                           .build())
                        .addObject(ConfigNode.ObjectNode.builder()
                                           .addValue("name", "one")
                                           .addValue("base-uri", "http://one.example")
                                           .build())
                        .build())
                .build();
        ServiceRegistryManager manager = manager(Config.just(ConfigSources.create(root)));
        try {
            ServiceRegistry registry = manager.registry();

            assertThat(registry.get(WebClient.class).prototype().baseUri().orElseThrow().toString(),
                       is("http://default.example:80/"));
            assertThat(registry.getNamed(WebClient.class, "one").prototype().baseUri().orElseThrow().toString(),
                       is("http://one.example:80/"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void explicitClientReplacesBuiltInDefault() {
        WebClient explicitClient = WebClient.create();
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Config.class,
                                                                                                     Config.empty())
                                                                                .putContractInstance(WebClient.class,
                                                                                                     explicitClient)
                                                                                .build());
        try {
            assertThat(manager.registry().get(WebClient.class), sameInstance(explicitClient));
        } finally {
            manager.shutdown();
            explicitClient.closeResource();
        }
    }

    private static ServiceRegistryManager manager(Config config) {
        return ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                     .putContractInstance(Config.class, config)
                                                     .build());
    }
}
