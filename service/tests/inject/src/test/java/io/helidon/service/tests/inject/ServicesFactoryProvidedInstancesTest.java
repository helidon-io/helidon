/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject;

import java.util.List;
import java.util.stream.Collectors;

import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

public class ServicesFactoryProvidedInstancesTest {
    private static ServiceRegistryManager registryManager;
    private static ServiceRegistry registry;

    @BeforeAll
    public static void initRegistry() {
        var injectConfig = ServiceRegistryConfig.builder()
                .addServiceDescriptor(NamedServicesFactoryTypes_TargetTypeProvider__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(NamedServicesFactoryTypes_ConfigFactory__ServiceDescriptor.INSTANCE)
                .putServiceInstance(NamedServicesFactoryTypes_ConfigFactory__ServiceDescriptor.INSTANCE,
                                    new NamedServicesFactoryTypes.ConfigFactory(List.of(new NamedServicesFactoryTypes.NamedConfigImpl(
                                            "custom"))))
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .build();
        registryManager = ServiceRegistryManager.create(injectConfig);
        registry = registryManager.registry();
    }

    @AfterAll
    public static void tearDownRegistry() {
        if (registryManager != null) {
            registryManager.shutdown();
        }
    }

    @Test
    void testServicesFactory() {
        List<NamedServicesFactoryTypes.TargetType> targetTypes =
                registry.all(Lookup.builder()
                                     .addQualifier(Qualifier.WILDCARD_NAMED)
                                     .addContract(NamedServicesFactoryTypes.TargetType.class)
                                     .build());

        assertThat(targetTypes, hasSize(1));
        var names = targetTypes.stream()
                .map(NamedServicesFactoryTypes.TargetType::config)
                .map(NamedServicesFactoryTypes.NamedConfig::name)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(names, hasItems("custom"));
    }
}
