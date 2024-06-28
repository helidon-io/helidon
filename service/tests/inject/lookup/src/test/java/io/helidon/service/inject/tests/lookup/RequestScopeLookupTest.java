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
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.RequestScopeControl;
import io.helidon.service.inject.api.Scope;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * Test all lookup methods for requestScope.
 */
class RequestScopeLookupTest {
    private static final Lookup LOOKUP = Lookup.create(ContractRequestScope.class);
    private static final Class<ContractRequestScope> CONTRACT = ContractRequestScope.class;
    private static final Lookup LOOKUP_NO_IP_PROVIDER = Lookup.builder()
            .addContract(ContractRequestScopeNoIpProvider.class)
            .build();
    private static InjectRegistryManager registryManager;
    private static InjectRegistry registry;
    private Scope requestScope;

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

    @BeforeEach
    void startRequestScope() {
        requestScope = registry.get(RequestScopeControl.class)
                .startRequestScope("unit-test", Map.of());
    }

    @AfterEach
    void stopRequestScope() {
        if (requestScope != null) {
            requestScope.close();
        }
    }

    @Test
    void getLookupTest() {
        ContractRequestScope first = registry.get(LOOKUP);
        assertThat(first, instanceOf(RequestScopeSupplierExample.First.class));
        ContractRequestScope second = registry.get(LOOKUP);
        assertThat(first, sameInstance(second));
    }

    @Test
    void getTypeTest() {
        ContractRequestScope first = registry.get(CONTRACT);
        assertThat(first, instanceOf(RequestScopeSupplierExample.First.class));
        ContractRequestScope second = registry.get(CONTRACT);
        assertThat(first, sameInstance(second));
    }

    @Test
    void firstLookupTest() {
        Optional<ContractRequestScope> first = registry.first(LOOKUP);
        ContractRequestScope firstValue = checkOptional(first, RequestScopeSupplierExample.First.class);

        Optional<ContractRequestScope> second = registry.first(LOOKUP);
        ContractRequestScope secondValue = checkOptional(second, RequestScopeSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void firstTypeTest() {
        Optional<ContractRequestScope> first = registry.first(CONTRACT);
        ContractRequestScope firstValue = checkOptional(first, RequestScopeSupplierExample.First.class);

        Optional<ContractRequestScope> second = registry.first(CONTRACT);
        ContractRequestScope secondValue = checkOptional(second, RequestScopeSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void allLookupTest() {
        List<ContractRequestScope> all = registry.all(LOOKUP);

        checkAll(all, 4);
    }

    @Test
    void allTypeTest() {
        List<ContractRequestScope> all = registry.all(CONTRACT);

        checkAll(all, 4);
    }

    @Test
    void supplyLookupTest() {
        Supplier<ContractRequestScope> supply = registry.supply(LOOKUP);
        ContractRequestScope first = supply.get();
        assertThat(first, instanceOf(RequestScopeSupplierExample.First.class));

        supply = registry.supply(LOOKUP);
        ContractRequestScope second = supply.get();

        assertThat(first, sameInstance(second));
    }

    @Test
    void supplyTypeTest() {
        Supplier<ContractRequestScope> supply = registry.supply(CONTRACT);
        ContractRequestScope first = supply.get();
        assertThat(first, instanceOf(RequestScopeSupplierExample.First.class));

        supply = registry.supply(CONTRACT);
        ContractRequestScope second = supply.get();

        assertThat(first, sameInstance(second));
    }

    @Test
    void supplyFirstLookupTest() {
        Supplier<Optional<ContractRequestScope>> supply = registry.supplyFirst(LOOKUP);

        Optional<ContractRequestScope> first = supply.get();
        ContractRequestScope firstValue = checkOptional(first, RequestScopeSupplierExample.First.class);

        supply = registry.supplyFirst(LOOKUP);
        Optional<ContractRequestScope> second = supply.get();
        ContractRequestScope secondValue = checkOptional(second, RequestScopeSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void supplyFirstTypeTest() {
        Supplier<Optional<ContractRequestScope>> supply = registry.supplyFirst(CONTRACT);

        Optional<ContractRequestScope> first = supply.get();
        ContractRequestScope firstValue = checkOptional(first, RequestScopeSupplierExample.First.class);

        supply = registry.supplyFirst(CONTRACT);
        Optional<ContractRequestScope> second = supply.get();
        ContractRequestScope secondValue = checkOptional(second, RequestScopeSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void supplyAllLookupTest() {
        List<ContractRequestScope> all = registry.<ContractRequestScope>supplyAll(LOOKUP)
                .get();

        checkAll(all, 4);
    }

    @Test
    void supplyAllTypeTest() {
        List<ContractRequestScope> all = registry.supplyAll(CONTRACT)
                .get();

        checkAll(all, 4);
    }

    @Test
    void supplyFromDescriptorTest() {
        Supplier<RequestScopeDirectExample> supply = registry.supply(RequestScopeDirectExample.class);

        ContractRequestScope first = supply.get();
        assertThat(first, instanceOf(RequestScopeDirectExample.class));

        supply = registry.supply(RequestScopeDirectExample.class);
        ContractRequestScope second = supply.get();

        assertThat(first, sameInstance(second));
    }

    @Test
    void lookupServicesTest() {
        List<InjectServiceInfo> serviceDescriptors = registry.lookupServices(LOOKUP);

        /*
        Order:
        1. RequestScopeSupplierExample (highest weight)
        2. RequestScopeDirectExample (alphabet...)
        3. RequestScopeInjectionPointExample
        4. RequestScopeServicesProviderExample
         */

        assertThat(serviceDescriptors, hasSize(4));

        assertThat(serviceDescriptors.getFirst(), sameInstance(RequestScopeSupplierExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(1), sameInstance(RequestScopeDirectExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(2),
                   sameInstance(RequestScopeInjectionPointProviderExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(3), sameInstance(RequestScopeServicesProviderExample__ServiceDescriptor.INSTANCE));
    }

    @Test
    void qualifiedServicesProviderTest() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractRequestScope.class)
                .addQualifier(RequestScopeServicesProviderExample.SECOND_QUALI)
                .build();

        ContractRequestScope first = registry.get(lookup);
        assertThat(first, instanceOf(RequestScopeServicesProviderExample.SecondClass.class));

        ContractRequestScope second = registry.get(lookup);
        assertThat(second, sameInstance(first));
    }

    @Test
    void qualifiedIpProviderTest() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractRequestScope.class)
                .addQualifier(RequestScopeInjectionPointProviderExample.SECOND_QUALI)
                .build();

        ContractRequestScope instance = registry.get(lookup);
        assertThat(instance, instanceOf(RequestScopeInjectionPointProviderExample.SecondClass.class));
    }

    private void checkAll(List<ContractRequestScope> all, int size) {
        /*
        Order:
        1. RequestScopeSupplierExample (highest weight)
        2. RequestScopeDirectExample (alphabet...)
        3. RequestScopeInjectionPointExample - no instance, as we do not have a qualifier
        4. RequestScopeServicesProviderExample - two qualified instances
         */
        assertThat(all, hasSize(size));

        assertThat(all.getFirst(), instanceOf(RequestScopeSupplierExample.First.class));
        assertThat(all.get(1), instanceOf(RequestScopeDirectExample.class));
        assertThat(all.get(2), instanceOf(RequestScopeServicesProviderExample.FirstClass.class));
        if (size > 3) {
            assertThat(all.get(3), instanceOf(RequestScopeServicesProviderExample.SecondClass.class));
        }
    }

    private ContractRequestScope checkOptional(Optional<ContractRequestScope> first, Class<?> expectedType) {
        assertThat(first, optionalPresent());
        assertThat(first, optionalValue(instanceOf(expectedType)));
        return first.get();
    }
}
