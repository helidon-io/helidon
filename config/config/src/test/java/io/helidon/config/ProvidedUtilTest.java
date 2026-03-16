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
import java.util.Optional;
import java.util.Set;

import io.helidon.config.spi.ConfigNode;
import io.helidon.service.registry.ExistingInstanceDescriptor;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@Testing.Test
public class ProvidedUtilTest {

    private static Config config;

    @BeforeAll
    static void init() {
        /*
        Create config that has a list under "services" to trigger the buggy path. It doesn't matter
        that both are of type "weighted." We cannot use Properties text because that creates a
        ConfigObject, not a list.
         */
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addList("services", ConfigNode.ListNode.builder()
                        .addObject(ConfigNode.ObjectNode.builder()
                                           .addValue("type", "weighted")
                                           .build())
                        .addObject(ConfigNode.ObjectNode.builder()
                                           .addValue("type", "weighted")
                                           .build())
                        .build())
                .build();

        config = Config.just(ConfigSources.create(root));
    }

    @Test
    void testServiceRegistryOrdering() {

        ServiceDescriptor<?> higher = ExistingInstanceDescriptor.create(
                new WeightedServiceProviderImpl("higher"),
                Set.of(WeightedServiceProvider.class),
                2.0);
        ServiceDescriptor<?> lower = ExistingInstanceDescriptor.create(
                new WeightedServiceProviderImpl("lower"),
                Set.of(WeightedServiceProvider.class),
                1.0);

        ServiceRegistry registry = ServiceRegistryManager.create(
                        ServiceRegistryConfig.builder()
                                .addServiceDescriptor(higher)
                                .addServiceDescriptor(lower)
                                .build())
                .registry();

        List<WeightedServiceImpl> matchedServices =
                ProvidedUtil.discoverServices(config,
                                              "services",
                                              Optional.of(registry),
                                              WeightedServiceProvider.class,
                                              WeightedServiceImpl.class,
                                              false,
                                              List.of());

        assertThat("Matched services", matchedServices.getFirst().nickname(), is(equalTo("higher")));
    }

    @Service.Contract
    interface WeightedServiceProvider extends ConfiguredProvider<WeightedServiceImpl> {
    }

    static class WeightedServiceProviderImpl implements WeightedServiceProvider {

        private final String nickname;

        WeightedServiceProviderImpl() {
            nickname = "unknown";
        }

        WeightedServiceProviderImpl(String nickname) {
            this.nickname = nickname;
        }

        @Override
        public String configKey() {
            return "weighted";
        }

        @Override
        public WeightedServiceImpl create(Config config, String name) {
            return new WeightedServiceImpl(config, name, nickname);
        }

    }

    static class WeightedServiceImpl implements NamedService {

        private final String name;
        private final String nickname;

        WeightedServiceImpl(Config config, String name, String nickname) {
            this.name = name;
            this.nickname = nickname;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String type() {
            return "weighted";
        }

        String nickname() {
            return nickname;
        }
    }

}
