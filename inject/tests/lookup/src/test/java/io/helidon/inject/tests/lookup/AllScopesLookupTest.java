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

package io.helidon.inject.tests.lookup;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.RequestScopeControl;
import io.helidon.inject.Scope;
import io.helidon.inject.Services;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceInfo;
import io.helidon.inject.service.ServiceInstance;
import io.helidon.inject.testing.InjectionTestingSupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * Test all lookup methods for all scopes (combination).
 */
class AllScopesLookupTest {
    private static final Lookup LOOKUP = Lookup.create(ContractCommon.class);
    private static final Class<ContractCommon> CONTRACT = ContractCommon.class;
    private static final Lookup LOOKUP_NO_IP_PROVIDER = Lookup.builder()
            .addContract(ContractNoIpProvider.class)
            .build();
    private static InjectionServices injectionServices;
    private static Services services;
    private Scope requestScope;

    @BeforeAll
    static void init() {
        injectionServices = InjectionServices.create();
        services = injectionServices.services();
    }

    @AfterAll
    static void shutdown() {
        InjectionTestingSupport.shutdown(injectionServices);
    }

    @BeforeEach
    void startRequestScope() {
        // we need to have request scope active, so we can access providers from everywhere
        requestScope = services.get(RequestScopeControl.class)
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
        ContractCommon first = services.get(LOOKUP);
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));
    }

    @Test
    void getTypeTest() {
        ContractCommon first = services.get(CONTRACT);
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));
    }

    @Test
    void firstLookupTest() {
        Optional<ContractCommon> first = services.first(LOOKUP);
        checkOptional(first, SingletonSupplierExample.First.class);
    }

    @Test
    void firstTypeTest() {
        Optional<ContractCommon> first = services.first(CONTRACT);
        checkOptional(first, SingletonSupplierExample.First.class);
    }

    @Test
    void allLookupTest() {
        List<ContractCommon> all = services.all(LOOKUP);

        assertThat(all, hasSize(12));
    }

    @Test
    void allTypeTest() {
        List<ContractCommon> all = services.all(CONTRACT);

        assertThat(all, hasSize(12));
    }

    @Test
    void supplyLookupTest() {
        Supplier<ContractCommon> supply = services.supply(LOOKUP);
        ContractCommon first = supply.get();
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));
    }

    @Test
    void supplyTypeTest() {
        Supplier<ContractCommon> supply = services.supply(CONTRACT);
        ContractCommon first = supply.get();
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));
    }

    @Test
    void supplyFirstLookupTest() {
        Supplier<Optional<ContractCommon>> supply = services.supplyFirst(LOOKUP);

        Optional<ContractCommon> first = supply.get();
        checkOptional(first, SingletonSupplierExample.First.class);
    }

    @Test
    void supplyFirstTypeTest() {
        Supplier<Optional<ContractCommon>> supply = services.supplyFirst(CONTRACT);

        Optional<ContractCommon> first = supply.get();
        checkOptional(first, SingletonSupplierExample.First.class);
    }

    @Test
    void supplyAllLookupTest() {
        List<ContractCommon> all = services.<ContractCommon>supplyAll(LOOKUP)
                .get();

        assertThat(all, hasSize(12));
    }

    @Test
    void supplyAllTypeTest() {
        List<ContractCommon> all = services.supplyAll(CONTRACT)
                .get();

        assertThat(all, hasSize(12));
    }

    @Test
    void lookupInstancesTest() {
        List<ServiceInstance<ContractCommon>> serviceInstances = services.lookupInstances(LOOKUP);
        Set<Class<?>> contracts = Set.of(ContractNoIpProvider.class,
                                         ContractCommon.class);
        /*
        Order:
        1. SingletonSupplierExample (highest weight)
        2. SingletonDirectExample (alphabet...)
        3. SingletonInjectionPointExample - no instance, as we do not have a qualifier
        4. SingletonServicesProviderExample - two qualified instances
         */
        assertThat(serviceInstances, hasSize(12));
    }

    @Test
    void lookupServicesTest() {
        List<ServiceInfo> serviceDescriptors = services.lookupServices(LOOKUP);

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

        ContractCommon first = services.get(lookup);
        assertThat(first, instanceOf(SingletonServicesProviderExample.SecondClass.class));

        ContractCommon second = services.get(lookup);
        assertThat(second, sameInstance(first));
    }

    @Test
    void qualifiedIpProviderTest() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractCommon.class)
                .addQualifier(SingletonInjectionPointProviderExample.SECOND_QUALI)
                .build();

        ContractCommon instance = services.get(lookup);
        assertThat(instance, instanceOf(SingletonInjectionPointProviderExample.SecondClass.class));
    }

    private void checkInstance(ServiceInstance<ContractCommon> serviceInstance,
                               Class<? extends ContractCommon> instanceType,
                               Class<?> serviceType,
                               Set<Class<?>> contracts,
                               Set<Class<? extends Annotation>> qualifiers,
                               double weight) {

        assertThat(serviceInstance, notNullValue());
        assertThat(serviceInstance.get(), instanceOf(instanceType));
        assertThat(serviceInstance.serviceType(), is(typeName(serviceType)));
        assertThat(serviceInstance.contracts(), is(typeNames(contracts)));
        assertThat(serviceInstance.qualifiers(), is(qualifiers(qualifiers)));
        assertThat(serviceInstance.weight(), is(weight));
    }

    private ContractCommon checkOptional(Optional<ContractCommon> first, Class<?> expectedType) {
        assertThat(first, optionalPresent());
        assertThat(first, optionalValue(instanceOf(expectedType)));
        return first.get();
    }

    private TypeName typeName(Class<?> type) {
        return TypeName.create(type);
    }

    private Set<TypeName> typeNames(Set<Class<?>> types) {
        return types.stream()
                .map(TypeName::create)
                .collect(Collectors.toSet());
    }

    private Set<Qualifier> qualifiers(Set<Class<? extends Annotation>> qualifiers) {
        return qualifiers.stream()
                .map(Qualifier::create)
                .collect(Collectors.toSet());
    }
}
