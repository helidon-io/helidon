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

package io.helidon.service.inject.tests.lookup;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Injection.InjectionPointProvider;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.ProviderType;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * Test all lookup methods for singleton.
 */
class SingletonLookupTest {
    private static final Lookup LOOKUP = Lookup.create(ContractSingleton.class);
    private static final Class<ContractSingleton> CONTRACT = ContractSingleton.class;
    private static InjectRegistryManager registryManager;
    private static InjectRegistry registry;

    @BeforeAll
    static void init() {
        registryManager = InjectRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterAll
    static void shutdown() {
        if (registryManager != null) {
            registryManager.shutdown();
        }
    }

    @Test
    void getLookupTest() {
        ContractSingleton first = registry.get(LOOKUP);
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));
        ContractSingleton second = registry.get(LOOKUP);
        assertThat(first, sameInstance(second));
    }

    @Test
    void getTypeTest() {
        ContractSingleton first = registry.get(CONTRACT);
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));
        ContractSingleton second = registry.get(CONTRACT);
        assertThat(first, sameInstance(second));
    }

    @Test
    void firstLookupTest() {
        Optional<ContractSingleton> first = registry.first(LOOKUP);
        ContractSingleton firstValue = checkOptional(first, SingletonSupplierExample.First.class);

        Optional<ContractSingleton> second = registry.first(LOOKUP);
        ContractSingleton secondValue = checkOptional(second, SingletonSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void firstTypeTest() {
        Optional<ContractSingleton> first = registry.first(CONTRACT);
        ContractSingleton firstValue = checkOptional(first, SingletonSupplierExample.First.class);

        Optional<ContractSingleton> second = registry.first(CONTRACT);
        ContractSingleton secondValue = checkOptional(second, SingletonSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void allLookupTest() {
        List<ContractSingleton> all = registry.all(LOOKUP);

        checkAll(all, 4);
    }

    @Test
    void allTypeTest() {
        List<ContractSingleton> all = registry.all(CONTRACT);

        checkAll(all, 4);
    }

    @Test
    void supplyLookupTest() {
        Supplier<ContractSingleton> supply = registry.supply(LOOKUP);
        ContractSingleton first = supply.get();
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));

        supply = registry.supply(LOOKUP);
        ContractSingleton second = supply.get();

        assertThat(first, sameInstance(second));
    }

    @Test
    void supplyTypeTest() {
        Supplier<ContractSingleton> supply = registry.supply(CONTRACT);
        ContractSingleton first = supply.get();
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));

        supply = registry.supply(CONTRACT);
        ContractSingleton second = supply.get();

        assertThat(first, sameInstance(second));
    }

    @Test
    void supplyFirstLookupTest() {
        Supplier<Optional<ContractSingleton>> supply = registry.supplyFirst(LOOKUP);

        Optional<ContractSingleton> first = supply.get();
        ContractSingleton firstValue = checkOptional(first, SingletonSupplierExample.First.class);

        supply = registry.supplyFirst(LOOKUP);
        Optional<ContractSingleton> second = supply.get();
        ContractSingleton secondValue = checkOptional(second, SingletonSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void supplyFirstTypeTest() {
        Supplier<Optional<ContractSingleton>> supply = registry.supplyFirst(CONTRACT);

        Optional<ContractSingleton> first = supply.get();
        ContractSingleton firstValue = checkOptional(first, SingletonSupplierExample.First.class);

        supply = registry.supplyFirst(CONTRACT);
        Optional<ContractSingleton> second = supply.get();
        ContractSingleton secondValue = checkOptional(second, SingletonSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void supplyAllLookupTest() {
        List<ContractSingleton> all = registry.<ContractSingleton>supplyAll(LOOKUP)
                .get();

        checkAll(all, 4);
    }

    @Test
    void supplyAllTypeTest() {
        List<ContractSingleton> all = registry.supplyAll(CONTRACT)
                .get();

        checkAll(all, 4);
    }

    @Test
    void supplyFromDescriptorTest() {
        Supplier<SingletonDirectExample> supply = registry.supply(SingletonDirectExample.class);

        ContractSingleton first = supply.get();
        assertThat(first, instanceOf(SingletonDirectExample.class));

        supply = registry.supply(SingletonDirectExample.class);
        ContractSingleton second = supply.get();

        assertThat(first, sameInstance(second));
    }

    @Test
    void lookupServicesTest() {
        List<InjectServiceInfo> serviceDescriptors = registry.lookupServices(LOOKUP);

        /*
        Order:
        1. SingletonSupplierExample (highest weight)
        2. SingletonDirectExample (alphabet...)
        3. SingletonInjectionPointExample
        4. SingletonServicesProviderExample
         */

        assertThat(serviceDescriptors, hasSize(4));

        assertThat(serviceDescriptors.getFirst(), sameInstance(SingletonSupplierExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(1), sameInstance(SingletonDirectExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(2), sameInstance(SingletonInjectionPointProviderExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(3), sameInstance(SingletonServicesProviderExample__ServiceDescriptor.INSTANCE));
    }

    @Test
    void qualifiedServicesProviderTest() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractSingleton.class)
                .addQualifier(SingletonServicesProviderExample.SECOND_QUALI)
                .build();

        ContractSingleton first = registry.get(lookup);
        assertThat(first, instanceOf(SingletonServicesProviderExample.SecondClass.class));

        ContractSingleton second = registry.get(lookup);
        assertThat(second, sameInstance(first));
    }

    @Test
    void qualifiedIpProviderTest() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractSingleton.class)
                .addQualifier(SingletonInjectionPointProviderExample.SECOND_QUALI)
                .build();

        ContractSingleton instance = registry.get(lookup);
        assertThat(instance, instanceOf(SingletonInjectionPointProviderExample.SecondClass.class));
    }

    @Test
    void testIpProviderLookup() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractSingleton.class)
                .addProviderType(ProviderType.IP_PROVIDER)
                .build();
        InjectionPointProvider<?> instance = registry.get(lookup);

        assertThat(instance, instanceOf(SingletonInjectionPointProviderExample.class));
    }

    @Test
    void testSupplierLookup() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractSingleton.class)
                .addProviderType(ProviderType.SUPPLIER)
                .build();
        Supplier<?> instance = registry.get(lookup);

        assertThat(instance, instanceOf(SingletonSupplierExample.class));
    }

    private void checkAll(List<ContractSingleton> all, int size) {
        /*
        Order:
        1. SingletonSupplierExample (highest weight)
        2. SingletonDirectExample (alphabet...)
        3. SingletonInjectionPointExample - no instance, as we do not have a qualifier
        4. SingletonServicesProviderExample - two qualified instances
         */
        assertThat(all, hasSize(size));

        assertThat(all.getFirst(), instanceOf(SingletonSupplierExample.First.class));
        assertThat(all.get(1), instanceOf(SingletonDirectExample.class));
        assertThat(all.get(2), instanceOf(SingletonServicesProviderExample.FirstClass.class));
        if (size > 3) {
            assertThat(all.get(3), instanceOf(SingletonServicesProviderExample.SecondClass.class));
        }
    }

    private ContractSingleton checkOptional(Optional<ContractSingleton> first, Class<?> expectedType) {
        assertThat(first, optionalPresent());
        assertThat(first, optionalValue(instanceOf(expectedType)));
        return first.get();
    }
}
