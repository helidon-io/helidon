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
import io.helidon.service.inject.api.PerRequestScopeControl;
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
 * Test all lookup methods for all scopes (combination).
 */
class AllScopesLookupTest {
    private static final Lookup LOOKUP = Lookup.create(ContractCommon.class);
    private static final Class<ContractCommon> CONTRACT = ContractCommon.class;

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
        // we need to have request scope active, so we can access providers from everywhere
        requestScope = registry.get(PerRequestScopeControl.class)
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
        ContractCommon first = registry.get(LOOKUP);
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));
    }

    @Test
    void getTypeTest() {
        ContractCommon first = registry.get(CONTRACT);
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));
    }

    @Test
    void firstLookupTest() {
        Optional<ContractCommon> first = registry.first(LOOKUP);
        checkOptional(first, SingletonSupplierExample.First.class);
    }

    @Test
    void firstTypeTest() {
        Optional<ContractCommon> first = registry.first(CONTRACT);
        checkOptional(first, SingletonSupplierExample.First.class);
    }

    @Test
    void allLookupTest() {
        List<ContractCommon> all = registry.all(LOOKUP);

        assertThat(all, hasSize(12));
    }

    @Test
    void allTypeTest() {
        List<ContractCommon> all = registry.all(CONTRACT);

        assertThat(all, hasSize(12));
    }

    @Test
    void supplyLookupTest() {
        Supplier<ContractCommon> supply = registry.supply(LOOKUP);
        ContractCommon first = supply.get();
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));
    }

    @Test
    void supplyTypeTest() {
        Supplier<ContractCommon> supply = registry.supply(CONTRACT);
        ContractCommon first = supply.get();
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));
    }

    @Test
    void supplyFirstLookupTest() {
        Supplier<Optional<ContractCommon>> supply = registry.supplyFirst(LOOKUP);

        Optional<ContractCommon> first = supply.get();
        checkOptional(first, SingletonSupplierExample.First.class);
    }

    @Test
    void supplyFirstTypeTest() {
        Supplier<Optional<ContractCommon>> supply = registry.supplyFirst(CONTRACT);

        Optional<ContractCommon> first = supply.get();
        checkOptional(first, SingletonSupplierExample.First.class);
    }

    @Test
    void supplyAllLookupTest() {
        List<ContractCommon> all = registry.<ContractCommon>supplyAll(LOOKUP)
                .get();

        assertThat(all, hasSize(12));
    }

    @Test
    void supplyAllTypeTest() {
        List<ContractCommon> all = registry.supplyAll(CONTRACT)
                .get();

        assertThat(all, hasSize(12));
    }

    @Test
    void lookupServicesTest() {
        List<InjectServiceInfo> serviceDescriptors = registry.lookupServices(LOOKUP);

        /*
        Order:
        weight 102
        1.  SingletonSupplier
        weight 101
        2.  NoScopeSupplier
        3.  RequestScopeSupplier
        default weight, no qualifiers, alphabet
        4.  NoScopeDirect
        5.  RequestScopeDirect
        6.  SingletonDirect
        default weight, qualified, alphabet
        7.  NoScopeIp
        8.  NoScopeServices
        9.  RequestScopeIp
        10.  RequestScopeServices
        11. SingletonIp
        12. SingletonServices
         */

        assertThat(serviceDescriptors, hasSize(12));

        int i = 0;
        // 102
        assertThat(serviceDescriptors.get(i++), sameInstance(SingletonSupplierExample__ServiceDescriptor.INSTANCE));
        // 101
        assertThat(serviceDescriptors.get(i++), sameInstance(NoScopeSupplierExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(i++), sameInstance(RequestScopeSupplierExample__ServiceDescriptor.INSTANCE));
        // default weight, no qualifiers, alphabet
        assertThat(serviceDescriptors.get(i++), sameInstance(NoScopeDirectExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(i++), sameInstance(RequestScopeDirectExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(i++), sameInstance(SingletonDirectExample__ServiceDescriptor.INSTANCE));

        // default weight, qualified, ordered by class name (package also, but these share the same package)
        assertThat(serviceDescriptors.get(i++), sameInstance(NoScopeInjectionPointProviderExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(i++), sameInstance(NoScopeServicesProviderExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(i++),
                   sameInstance(RequestScopeInjectionPointProviderExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(i++), sameInstance(RequestScopeServicesProviderExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(i++), sameInstance(SingletonInjectionPointProviderExample__ServiceDescriptor.INSTANCE));
        assertThat(serviceDescriptors.get(i), sameInstance(SingletonServicesProviderExample__ServiceDescriptor.INSTANCE));
    }

    @Test
    void qualifiedServicesProviderTest() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractCommon.class)
                .addQualifier(SingletonServicesProviderExample.SECOND_QUALI)
                .build();

        ContractCommon first = registry.get(lookup);
        assertThat(first, instanceOf(SingletonServicesProviderExample.SecondClass.class));

        ContractCommon second = registry.get(lookup);
        assertThat(second, sameInstance(first));
    }

    @Test
    void qualifiedIpProviderTest() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractCommon.class)
                .addQualifier(SingletonInjectionPointProviderExample.SECOND_QUALI)
                .build();

        ContractCommon instance = registry.get(lookup);
        assertThat(instance, instanceOf(SingletonInjectionPointProviderExample.SecondClass.class));
    }

    private ContractCommon checkOptional(Optional<ContractCommon> first, Class<?> expectedType) {
        assertThat(first, optionalPresent());
        assertThat(first, optionalValue(instanceOf(expectedType)));
        return first.get();
    }
}
