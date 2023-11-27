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
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceInfo;
import io.helidon.inject.service.ServiceInstance;
import io.helidon.inject.testing.InjectionTestingSupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * Test all lookup methods for both noScope and no scope.
 */
class NoScopeLookupTest {
    private static final Lookup LOOKUP = Lookup.create(ContractNoScope.class);
    private static final Class<ContractNoScope> CONTRACT = ContractNoScope.class;
    private static final Lookup LOOKUP_NO_IP_PROVIDER = Lookup.builder()
            .addContract(ContractNoScopeNoIpProvider.class)
            .build();
    private static InjectionServices injectionServices;
    private static Services services;

    @BeforeAll
    static void init() {
        injectionServices = InjectionServices.create();
        services = injectionServices.services();
    }

    @AfterAll
    static void shutdown() {
        InjectionTestingSupport.shutdown(injectionServices);
    }

    @Test
    void getLookupTest() {
        ContractNoScope first = services.get(LOOKUP);
        assertThat(first, instanceOf(NoScopeSupplierExample.First.class));
        ContractNoScope second = services.get(LOOKUP);
        assertThat(first, not(sameInstance(second)));
    }

    @Test
    void getTypeTest() {
        ContractNoScope first = services.get(CONTRACT);
        assertThat(first, instanceOf(NoScopeSupplierExample.First.class));
        ContractNoScope second = services.get(CONTRACT);
        assertThat(first, not(sameInstance(second)));
    }

    @Test
    void firstLookupTest() {
        Optional<ContractNoScope> first = services.first(LOOKUP);
        ContractNoScope firstValue = checkOptional(first, NoScopeSupplierExample.First.class);

        Optional<ContractNoScope> second = services.first(LOOKUP);
        ContractNoScope secondValue = checkOptional(second, NoScopeSupplierExample.First.class);

        assertThat(firstValue, not(sameInstance(secondValue)));
    }

    @Test
    void firstTypeTest() {
        Optional<ContractNoScope> first = services.first(CONTRACT);
        ContractNoScope firstValue = checkOptional(first, NoScopeSupplierExample.First.class);

        Optional<ContractNoScope> second = services.first(CONTRACT);
        ContractNoScope secondValue = checkOptional(second, NoScopeSupplierExample.First.class);

        assertThat(firstValue, not(sameInstance(secondValue)));
    }

    @Test
    void allLookupTest() {
        List<ContractNoScope> all = services.all(LOOKUP);

        checkAll(all, 4);
    }

    @Test
    void allTypeTest() {
        List<ContractNoScope> all = services.all(CONTRACT);

        checkAll(all, 4);
    }

    @Test
    void supplyLookupTest() {
        Supplier<ContractNoScope> supply = services.supply(LOOKUP);
        ContractNoScope first = supply.get();
        assertThat(first, instanceOf(NoScopeSupplierExample.First.class));

        supply = services.supply(LOOKUP);
        ContractNoScope second = supply.get();

        assertThat(first, not(sameInstance(second)));
    }

    @Test
    void supplyTypeTest() {
        Supplier<ContractNoScope> supply = services.supply(CONTRACT);
        ContractNoScope first = supply.get();
        assertThat(first, instanceOf(NoScopeSupplierExample.First.class));

        supply = services.supply(CONTRACT);
        ContractNoScope second = supply.get();

        assertThat(first, not(sameInstance(second)));
    }

    @Test
    void supplyFirstLookupTest() {
        Supplier<Optional<ContractNoScope>> supply = services.supplyFirst(LOOKUP);

        Optional<ContractNoScope> first = supply.get();
        ContractNoScope firstValue = checkOptional(first, NoScopeSupplierExample.First.class);

        supply = services.supplyFirst(LOOKUP);
        Optional<ContractNoScope> second = supply.get();
        ContractNoScope secondValue = checkOptional(second, NoScopeSupplierExample.First.class);

        assertThat(firstValue, not(sameInstance(secondValue)));
    }

    @Test
    void supplyFirstTypeTest() {
        Supplier<Optional<ContractNoScope>> supply = services.supplyFirst(CONTRACT);

        Optional<ContractNoScope> first = supply.get();
        ContractNoScope firstValue = checkOptional(first, NoScopeSupplierExample.First.class);

        supply = services.supplyFirst(CONTRACT);
        Optional<ContractNoScope> second = supply.get();
        ContractNoScope secondValue = checkOptional(second, NoScopeSupplierExample.First.class);

        assertThat(firstValue, not(sameInstance(secondValue)));
    }

    @Test
    void supplyAllLookupTest() {
        List<ContractNoScope> all = services.<ContractNoScope>supplyAll(LOOKUP)
                .get();

        checkAll(all, 4);
    }

    @Test
    void supplyAllTypeTest() {
        List<ContractNoScope> all = services.supplyAll(CONTRACT)
                .get();

        checkAll(all, 4);
    }

    @Test
    void supplyFromDescriptorTest() {
        Supplier<ContractNoScope> supply = services.supply(NoScopeDirectExample__ServiceDescriptor.INSTANCE);

        ContractNoScope first = supply.get();
        assertThat(first, instanceOf(NoScopeDirectExample.class));

        supply = services.supply(NoScopeDirectExample__ServiceDescriptor.INSTANCE);
        ContractNoScope second = supply.get();

        assertThat(first, not(sameInstance(second)));
    }

    @Test
    void lookupInstancesTest() {
        List<ServiceInstance<ContractNoScope>> serviceInstances = services.lookupInstances(LOOKUP);
        Set<Class<?>> contracts = Set.of(ContractNoScopeNoIpProvider.class,
                                         ContractNoScope.class,
                                         ContractCommon.class,
                                         ContractNoIpProvider.class);
        /*
        Order:
        1. NoScopeSupplierExample (highest weight)
        2. NoScopeDirectExample (alphabet...)
        3. NoScopeInjectionPointExample - no instance, as we do not have a qualifier
        4. NoScopeServicesProviderExample - two qualified instances
         */
        assertThat(serviceInstances, hasSize(4));

        checkInstance(serviceInstances.getFirst(),
                      NoScopeSupplierExample.First.class, // instance type
                      NoScopeSupplierExample.class, // service type
                      contracts,
                      Set.of(), // qualifiers
                      Weighted.DEFAULT_WEIGHT + 1); // weight

        checkInstance(serviceInstances.get(1),
                      NoScopeDirectExample.class,
                      NoScopeDirectExample.class,
                      contracts,
                      Set.of(),
                      Weighted.DEFAULT_WEIGHT);

        checkInstance(serviceInstances.get(2),
                      NoScopeServicesProviderExample.FirstClass.class,
                      NoScopeServicesProviderExample.class,
                      contracts,
                      Set.of(NoScopeServicesProviderExample.FirstQuali.class),
                      Weighted.DEFAULT_WEIGHT);

        checkInstance(serviceInstances.get(3),
                      NoScopeServicesProviderExample.SecondClass.class,
                      NoScopeServicesProviderExample.class,
                      contracts,
                      Set.of(NoScopeServicesProviderExample.SecondQuali.class),
                      Weighted.DEFAULT_WEIGHT);

    }

    @Test
    void lookupServicesTest() {
        List<ServiceInfo> serviceDescriptors = services.lookupServices(LOOKUP);

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

        ContractNoScope first = services.get(lookup);
        assertThat(first, instanceOf(NoScopeServicesProviderExample.SecondClass.class));

        ContractNoScope second = services.get(lookup);
        assertThat(second, not(sameInstance(first)));
    }

    @Test
    void qualifiedIpProviderTest() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractNoScope.class)
                .addQualifier(NoScopeInjectionPointProviderExample.SECOND_QUALI)
                .build();

        ContractNoScope instance = services.get(lookup);
        assertThat(instance, instanceOf(NoScopeInjectionPointProviderExample.SecondClass.class));
    }

    private void checkInstance(ServiceInstance<ContractNoScope> serviceInstance,
                               Class<? extends ContractNoScope> instanceType,
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
