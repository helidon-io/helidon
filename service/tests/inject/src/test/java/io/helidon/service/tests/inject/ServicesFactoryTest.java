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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

public class ServicesFactoryTest {
    private static ServiceRegistryManager registryManager;
    private static ServiceRegistry registry;

    @BeforeAll
    public static void initRegistry() {
        var injectConfig = ServiceRegistryConfig.builder()
                .addServiceDescriptor(ServicesFactoryTypes_ContractFactory__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ServicesFactoryTypes_QualifiedReceiver__ServiceDescriptor.INSTANCE)
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
    void testInjected() {
        var injected = registry.get(ServicesFactoryTypes.QualifiedReceiver.class);

        assertThat(injected.first().description(), is("first"));
        assertThat(injected.second().description(), is("second"));
        assertThat(injected.third().description(), is("both"));
    }

    @Test
    void testServicesFactory() {
        List<ServicesFactoryTypes.QualifiedContract> targetTypes =
                registry.all(Lookup.builder()
                                     .addQualifier(Qualifier.WILDCARD_NAMED)
                                     .addContract(ServicesFactoryTypes.QualifiedContract.class)
                                     .build());

        assertThat(targetTypes, hasSize(3));
        var names = targetTypes.stream()
                .map(ServicesFactoryTypes.QualifiedContract::description)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(names, hasItems("first", "second", "both"));
    }

    @Test
    void testServicesFactoryFirst() {
        ServicesFactoryTypes.QualifiedContract instance =
                registry.get(Lookup.builder()
                                     .addQualifier(ServicesFactoryTypes.FirstQualifier.QUALIFIER)
                                     .addContract(ServicesFactoryTypes.QualifiedContract.class)
                                     .build());

        assertThat(instance.description(), is("first"));
    }

    @Test
    void testServicesFactorySecond() {
        ServicesFactoryTypes.QualifiedContract instance =
                registry.get(Lookup.builder()
                                     .addQualifier(ServicesFactoryTypes.SecondQualifier.QUALIFIER)
                                     .addContract(ServicesFactoryTypes.QualifiedContract.class)
                                     .build());

        assertThat(instance.description(), is("second"));
    }

    @Test
    void testServicesFactoryThird() {
        ServicesFactoryTypes.QualifiedContract instance =
                registry.get(Lookup.builder()
                                     .addQualifier(ServicesFactoryTypes.FirstQualifier.QUALIFIER)
                                     .addQualifier(ServicesFactoryTypes.SecondQualifier.QUALIFIER)
                                     .addContract(ServicesFactoryTypes.QualifiedContract.class)
                                     .build());

        assertThat(instance.description(), is("both"));
    }
}
