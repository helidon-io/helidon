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

package io.helidon.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;
import io.helidon.messaging.spi.MessagingConnector;
import io.helidon.messaging.spi.MessagingConnectorProvider;
import io.helidon.messaging.spi.MessagingConnectorProviderConfig;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

final class RegistryTestSupport implements AutoCloseable {
    private final List<ServiceRegistryManager> managers = new ArrayList<>();

    static MessagingConnectorProviderConfig prototype(String name) {
        return new TestConnectorProviderConfig(name);
    }

    ChannelRegistry registry(List<ConsumerRegistration> consumers,
                             List<EmitterRegistration> emitters,
                             Config config,
                             List<MessagingConnector> connectors) {
        var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                           .discoverServices(false)
                                                           .discoverServicesFromServiceLoader(false)
                                                           .putContractInstance(MessagingConnectorProvider.class,
                                                                                new TestConnectorProvider(connectors))
                                                           .build());
        managers.add(manager);
        Config runtimeConfig = config;
        if (!config.get("messaging.connector").exists() && !connectors.isEmpty()) {
            ConfigNode.ListNode.Builder configuredConnectors = ConfigNode.ListNode.builder();
            for (int i = 0; i < connectors.size(); i++) {
                configuredConnectors.addObject(ConfigNode.ObjectNode.builder()
                                                       .addValue("type", "test")
                                                       .addValue("name", "fixture-" + i)
                                                       .addValue("index", Integer.toString(i))
                                                       .build());
            }
            ConfigNode.ObjectNode messaging = ConfigNode.ObjectNode.builder()
                    .addList("connector", configuredConnectors.build())
                    .build();
            ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder().addObject("messaging", messaging).build();
            runtimeConfig = Config.just(ConfigSources.create(root),
                                        ConfigSources.create(config));
        }
        MessagingConfig messagingConfig = MessagingConfig.builder()
                .serviceRegistry(manager.registry())
                .config(runtimeConfig.get("messaging"))
                .consumerRegistrations(consumers)
                .emitterRegistrations(emitters)
                .buildPrototype();
        return new ChannelRegistry(messagingConfig, new MessagingLifecycleGuard());
    }

    @Override
    public void close() {
        for (int i = managers.size() - 1; i >= 0; i--) {
            managers.get(i).shutdown();
        }
        managers.clear();
    }

    private record TestConnectorProviderConfig(String name) implements MessagingConnectorProviderConfig {
        @Override
        public Optional<Config> config() {
            return Optional.empty();
        }
    }

    private record TestConnectorProvider(List<MessagingConnector> connectors) implements MessagingConnectorProvider {
        @Override
        public String configKey() {
            return "test";
        }

        @Override
        public MessagingConnector create(Config config, String name) {
            return connectors.get(config.get("index").asInt().get());
        }
    }
}
