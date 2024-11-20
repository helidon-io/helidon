/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.service.inject.InjectConfig;
import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

public class ServicesFactoryProvidedInstancesTest {
    private static InjectRegistryManager registryManager;
    private static InjectRegistry registry;

    @BeforeAll
    public static void initRegistry() {
        var injectConfig = InjectConfig.builder()
                .addServiceDescriptor(ServicesFactoryTypes_TargetTypeProvider__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ServicesFactoryTypes_ConfigFactory__ServiceDescriptor.INSTANCE)
                .putServiceInstance(ServicesFactoryTypes_ConfigFactory__ServiceDescriptor.INSTANCE,
                                    new ServicesFactoryTypes.ConfigFactory(List.of(new ServicesFactoryTypes.NamedConfigImpl("custom"))))
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .build();
        registryManager = InjectRegistryManager.create(injectConfig);
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
        List<ServicesFactoryTypes.TargetType> targetTypes =
                registry.all(Lookup.builder()
                                     .addQualifier(Qualifier.WILDCARD_NAMED)
                                     .addContract(ServicesFactoryTypes.TargetType.class)
                                     .build());

        assertThat(targetTypes, hasSize(1));
        var names = targetTypes.stream()
                .map(ServicesFactoryTypes.TargetType::config)
                .map(ServicesFactoryTypes.NamedConfig::name)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(names, hasItems("custom"));
    }
}
