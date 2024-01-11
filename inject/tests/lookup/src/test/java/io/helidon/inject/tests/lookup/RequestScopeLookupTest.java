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

import io.helidon.common.Weighted;
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
 * Test all lookup methods for requestScope.
 */
class RequestScopeLookupTest {
    private static final Lookup LOOKUP = Lookup.create(ContractRequestScope.class);
    private static final Class<ContractRequestScope> CONTRACT = ContractRequestScope.class;
    private static final Lookup LOOKUP_NO_IP_PROVIDER = Lookup.builder()
            .addContract(ContractRequestScopeNoIpProvider.class)
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
        ContractRequestScope first = services.get(LOOKUP);
        assertThat(first, instanceOf(RequestScopeSupplierExample.First.class));
        ContractRequestScope second = services.get(LOOKUP);
        assertThat(first, sameInstance(second));
    }

    @Test
    void getTypeTest() {
        ContractRequestScope first = services.get(CONTRACT);
        assertThat(first, instanceOf(RequestScopeSupplierExample.First.class));
        ContractRequestScope second = services.get(CONTRACT);
        assertThat(first, sameInstance(second));
    }

    @Test
    void firstLookupTest() {
        Optional<ContractRequestScope> first = services.first(LOOKUP);
        ContractRequestScope firstValue = checkOptional(first, RequestScopeSupplierExample.First.class);

        Optional<ContractRequestScope> second = services.first(LOOKUP);
        ContractRequestScope secondValue = checkOptional(second, RequestScopeSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void firstTypeTest() {
        Optional<ContractRequestScope> first = services.first(CONTRACT);
        ContractRequestScope firstValue = checkOptional(first, RequestScopeSupplierExample.First.class);

        Optional<ContractRequestScope> second = services.first(CONTRACT);
        ContractRequestScope secondValue = checkOptional(second, RequestScopeSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void allLookupTest() {
        List<ContractRequestScope> all = services.all(LOOKUP);

        checkAll(all, 4);
    }

    @Test
    void allTypeTest() {
        List<ContractRequestScope> all = services.all(CONTRACT);

        checkAll(all, 4);
    }

    @Test
    void supplyLookupTest() {
        Supplier<ContractRequestScope> supply = services.supply(LOOKUP);
        ContractRequestScope first = supply.get();
        assertThat(first, instanceOf(RequestScopeSupplierExample.First.class));

        supply = services.supply(LOOKUP);
        ContractRequestScope second = supply.get();

        assertThat(first, sameInstance(second));
    }

    @Test
    void supplyTypeTest() {
        Supplier<ContractRequestScope> supply = services.supply(CONTRACT);
        ContractRequestScope first = supply.get();
        assertThat(first, instanceOf(RequestScopeSupplierExample.First.class));

        supply = services.supply(CONTRACT);
        ContractRequestScope second = supply.get();

        assertThat(first, sameInstance(second));
    }

    @Test
    void supplyFirstLookupTest() {
        Supplier<Optional<ContractRequestScope>> supply = services.supplyFirst(LOOKUP);

        Optional<ContractRequestScope> first = supply.get();
        ContractRequestScope firstValue = checkOptional(first, RequestScopeSupplierExample.First.class);

        supply = services.supplyFirst(LOOKUP);
        Optional<ContractRequestScope> second = supply.get();
        ContractRequestScope secondValue = checkOptional(second, RequestScopeSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void supplyFirstTypeTest() {
        Supplier<Optional<ContractRequestScope>> supply = services.supplyFirst(CONTRACT);

        Optional<ContractRequestScope> first = supply.get();
        ContractRequestScope firstValue = checkOptional(first, RequestScopeSupplierExample.First.class);

        supply = services.supplyFirst(CONTRACT);
        Optional<ContractRequestScope> second = supply.get();
        ContractRequestScope secondValue = checkOptional(second, RequestScopeSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void supplyAllLookupTest() {
        List<ContractRequestScope> all = services.<ContractRequestScope>supplyAll(LOOKUP)
                .get();

        checkAll(all, 4);
    }

    @Test
    void supplyAllTypeTest() {
        List<ContractRequestScope> all = services.supplyAll(CONTRACT)
                .get();

        checkAll(all, 4);
    }

    @Test
    void supplyFromDescriptorTest() {
        Supplier<ContractRequestScope> supply = services.supply(RequestScopeDirectExample__ServiceDescriptor.INSTANCE);

        ContractRequestScope first = supply.get();
        assertThat(first, instanceOf(RequestScopeDirectExample.class));

        supply = services.supply(RequestScopeDirectExample__ServiceDescriptor.INSTANCE);
        ContractRequestScope second = supply.get();

        assertThat(first, sameInstance(second));
    }

    @Test
    void lookupInstancesTest() {
        List<ServiceInstance<ContractRequestScope>> serviceInstances = services.lookupInstances(LOOKUP);
        Set<Class<?>> contracts = Set.of(ContractRequestScopeNoIpProvider.class,
                                         ContractRequestScope.class,
                                         ContractCommon.class,
                                         ContractNoIpProvider.class);
        /*
        Order:
        1. RequestScopeSupplierExample (highest weight)
        2. RequestScopeDirectExample (alphabet...)
        3. RequestScopeInjectionPointExample - no instance, as we do not have a qualifier
        4. RequestScopeServicesProviderExample - two qualified instances
         */
        assertThat(serviceInstances, hasSize(4));

        checkInstance(serviceInstances.getFirst(),
                      RequestScopeSupplierExample.First.class, // instance type
                      RequestScopeSupplierExample.class, // service type
                      contracts,
                      Set.of(), // qualifiers
                      Weighted.DEFAULT_WEIGHT + 1); // weight

        checkInstance(serviceInstances.get(1),
                      RequestScopeDirectExample.class,
                      RequestScopeDirectExample.class,
                      contracts,
                      Set.of(),
                      Weighted.DEFAULT_WEIGHT);

        checkInstance(serviceInstances.get(2),
                      RequestScopeServicesProviderExample.FirstClass.class,
                      RequestScopeServicesProviderExample.class,
                      contracts,
                      Set.of(RequestScopeServicesProviderExample.FirstQuali.class),
                      Weighted.DEFAULT_WEIGHT);

        checkInstance(serviceInstances.get(3),
                      RequestScopeServicesProviderExample.SecondClass.class,
                      RequestScopeServicesProviderExample.class,
                      contracts,
                      Set.of(RequestScopeServicesProviderExample.SecondQuali.class),
                      Weighted.DEFAULT_WEIGHT);

    }

    @Test
    void lookupServicesTest() {
        List<ServiceInfo> serviceDescriptors = services.lookupServices(LOOKUP);

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

        ContractRequestScope first = services.get(lookup);
        assertThat(first, instanceOf(RequestScopeServicesProviderExample.SecondClass.class));

        ContractRequestScope second = services.get(lookup);
        assertThat(second, sameInstance(first));
    }

    @Test
    void qualifiedIpProviderTest() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractRequestScope.class)
                .addQualifier(RequestScopeInjectionPointProviderExample.SECOND_QUALI)
                .build();

        ContractRequestScope instance = services.get(lookup);
        assertThat(instance, instanceOf(RequestScopeInjectionPointProviderExample.SecondClass.class));
    }

    private void checkInstance(ServiceInstance<ContractRequestScope> serviceInstance,
                               Class<? extends ContractRequestScope> instanceType,
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
