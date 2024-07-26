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
import io.helidon.service.inject.api.Lookup;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * Test all lookup methods for both noScope and no scope.
 */
class NoScopeLookupTest {
    private static final Lookup LOOKUP = Lookup.create(ContractNoScope.class);
    private static final Class<ContractNoScope> CONTRACT = ContractNoScope.class;
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
        ContractNoScope first = registry.get(LOOKUP);
        assertThat(first, instanceOf(NoScopeSupplierExample.First.class));
        ContractNoScope second = registry.get(LOOKUP);
        assertThat(first, not(sameInstance(second)));
    }

    @Test
    void getTypeTest() {
        ContractNoScope first = registry.get(CONTRACT);
        assertThat(first, instanceOf(NoScopeSupplierExample.First.class));
        ContractNoScope second = registry.get(CONTRACT);
        assertThat(first, not(sameInstance(second)));
    }

    @Test
    void firstLookupTest() {
        Optional<ContractNoScope> first = registry.first(LOOKUP);
        ContractNoScope firstValue = checkOptional(first, NoScopeSupplierExample.First.class);

        Optional<ContractNoScope> second = registry.first(LOOKUP);
        ContractNoScope secondValue = checkOptional(second, NoScopeSupplierExample.First.class);

        assertThat(firstValue, not(sameInstance(secondValue)));
    }

    @Test
    void firstTypeTest() {
        Optional<ContractNoScope> first = registry.first(CONTRACT);
        ContractNoScope firstValue = checkOptional(first, NoScopeSupplierExample.First.class);

        Optional<ContractNoScope> second = registry.first(CONTRACT);
        ContractNoScope secondValue = checkOptional(second, NoScopeSupplierExample.First.class);

        assertThat(firstValue, not(sameInstance(secondValue)));
    }

    @Test
    void allLookupTest() {
        List<ContractNoScope> all = registry.all(LOOKUP);

        checkAll(all, 4);
    }

    @Test
    void allTypeTest() {
        List<ContractNoScope> all = registry.all(CONTRACT);

        checkAll(all, 4);
    }

    @Test
    void supplyLookupTest() {
        Supplier<ContractNoScope> supply = registry.supply(LOOKUP);
        ContractNoScope first = supply.get();
        assertThat(first, instanceOf(NoScopeSupplierExample.First.class));

        supply = registry.supply(LOOKUP);
        ContractNoScope second = supply.get();

        assertThat(first, not(sameInstance(second)));
    }

    @Test
    void supplyTypeTest() {
        Supplier<ContractNoScope> supply = registry.supply(CONTRACT);
        ContractNoScope first = supply.get();
        assertThat(first, instanceOf(NoScopeSupplierExample.First.class));

        supply = registry.supply(CONTRACT);
        ContractNoScope second = supply.get();

        assertThat(first, not(sameInstance(second)));
    }

    @Test
    void supplyFirstLookupTest() {
        Supplier<Optional<ContractNoScope>> supply = registry.supplyFirst(LOOKUP);

        Optional<ContractNoScope> first = supply.get();
        ContractNoScope firstValue = checkOptional(first, NoScopeSupplierExample.First.class);

        supply = registry.supplyFirst(LOOKUP);
        Optional<ContractNoScope> second = supply.get();
        ContractNoScope secondValue = checkOptional(second, NoScopeSupplierExample.First.class);

        assertThat(firstValue, not(sameInstance(secondValue)));
    }

    @Test
    void supplyFirstTypeTest() {
        Supplier<Optional<ContractNoScope>> supply = registry.supplyFirst(CONTRACT);

        Optional<ContractNoScope> first = supply.get();
        ContractNoScope firstValue = checkOptional(first, NoScopeSupplierExample.First.class);

        supply = registry.supplyFirst(CONTRACT);
        Optional<ContractNoScope> second = supply.get();
        ContractNoScope secondValue = checkOptional(second, NoScopeSupplierExample.First.class);

        assertThat(firstValue, not(sameInstance(secondValue)));
    }

    @Test
    void supplyAllLookupTest() {
        List<ContractNoScope> all = registry.<ContractNoScope>supplyAll(LOOKUP)
                .get();

        checkAll(all, 4);
    }

    @Test
    void supplyAllTypeTest() {
        List<ContractNoScope> all = registry.supplyAll(CONTRACT)
                .get();

        checkAll(all, 4);
    }

    @Test
    void supplyFromDescriptorTest() {
        Supplier<NoScopeDirectExample> supply = registry.supply(NoScopeDirectExample.class);

        ContractNoScope first = supply.get();
        assertThat(first, instanceOf(NoScopeDirectExample.class));

        supply = registry.supply(NoScopeDirectExample.class);
        ContractNoScope second = supply.get();

        assertThat(first, not(sameInstance(second)));
    }

    @Test
    void lookupServicesTest() {
        List<InjectServiceInfo> serviceDescriptors = registry.lookupServices(LOOKUP);

        /*
        Order:
        1. NoScopeSupplierExample (highest weight)
        2. NoScopeDirectExample (alphabet...)
        3. NoScopeInjectionPointExample
        4. NoScopeServicesProviderExample
         */

        assertThat(serviceDescriptors, hasSize(4));

        assertThat(serviceDescriptors.getFirst(), sameInstance(NoScopeSupplierExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(1), sameInstance(NoScopeDirectExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(2), sameInstance(NoScopeInjectionPointProviderExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(3), sameInstance(NoScopeServicesProviderExample__ServiceDescriptor.INSTANCE));
    }

    @Test
    void qualifiedServicesProviderTest() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractNoScope.class)
                .addQualifier(NoScopeServicesProviderExample.SECOND_QUALI)
                .build();

        ContractNoScope first = registry.get(lookup);
        assertThat(first, instanceOf(NoScopeServicesProviderExample.SecondClass.class));

        ContractNoScope second = registry.get(lookup);
        assertThat(second, not(sameInstance(first)));
    }

    @Test
    void qualifiedIpProviderTest() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractNoScope.class)
                .addQualifier(NoScopeInjectionPointProviderExample.SECOND_QUALI)
                .build();

        ContractNoScope instance = registry.get(lookup);
        assertThat(instance, instanceOf(NoScopeInjectionPointProviderExample.SecondClass.class));
    }

    private void checkAll(List<ContractNoScope> all, int size) {
        /*
        Order:
        1. NoScopeSupplierExample (highest weight)
        2. NoScopeDirectExample (alphabet...)
        3. NoScopeInjectionPointExample - no instance, as we do not have a qualifier
        4. NoScopeServicesProviderExample - two qualified instances
         */
        assertThat(all, hasSize(size));

        assertThat(all.getFirst(), instanceOf(NoScopeSupplierExample.First.class));
        assertThat(all.get(1), instanceOf(NoScopeDirectExample.class));
        assertThat(all.get(2), instanceOf(NoScopeServicesProviderExample.FirstClass.class));
        if (size > 3) {
            assertThat(all.get(3), instanceOf(NoScopeServicesProviderExample.SecondClass.class));
        }
    }

    private ContractNoScope checkOptional(Optional<ContractNoScope> first, Class<?> expectedType) {
        assertThat(first, optionalPresent());
        assertThat(first, optionalValue(instanceOf(expectedType)));
        return first.get();
    }
}
