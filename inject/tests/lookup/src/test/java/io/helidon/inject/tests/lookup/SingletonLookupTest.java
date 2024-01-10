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
import io.helidon.inject.InjectionServiceProviderException;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.service.InjectionPointProvider;
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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test all lookup methods for singleton.
 */
class SingletonLookupTest {
    private static final Lookup LOOKUP = Lookup.create(ContractSingleton.class);
    private static final Class<ContractSingleton> CONTRACT = ContractSingleton.class;
    private static final Lookup LOOKUP_NO_IP_PROVIDER = Lookup.builder()
            .addContract(ContractSingletonNoIpProvider.class)
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
        ContractSingleton first = services.get(LOOKUP);
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));
        ContractSingleton second = services.get(LOOKUP);
        assertThat(first, sameInstance(second));
    }

    @Test
    void getTypeTest() {
        ContractSingleton first = services.get(CONTRACT);
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));
        ContractSingleton second = services.get(CONTRACT);
        assertThat(first, sameInstance(second));
    }

    @Test
    void firstLookupTest() {
        Optional<ContractSingleton> first = services.first(LOOKUP);
        ContractSingleton firstValue = checkOptional(first, SingletonSupplierExample.First.class);

        Optional<ContractSingleton> second = services.first(LOOKUP);
        ContractSingleton secondValue = checkOptional(second, SingletonSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void firstTypeTest() {
        Optional<ContractSingleton> first = services.first(CONTRACT);
        ContractSingleton firstValue = checkOptional(first, SingletonSupplierExample.First.class);

        Optional<ContractSingleton> second = services.first(CONTRACT);
        ContractSingleton secondValue = checkOptional(second, SingletonSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void allLookupTest() {
        List<ContractSingleton> all = services.all(LOOKUP);

        checkAll(all, 4);
    }

    @Test
    void allTypeTest() {
        List<ContractSingleton> all = services.all(CONTRACT);

        checkAll(all, 4);
    }

    @Test
    void allSuppliersLookupTest() {
        List<Supplier<ContractSingleton>> suppliers = services.allSuppliers(LOOKUP);

        // this cannot succeed, as the injection point provider does not return a value for unqualified requests
        assertThrows(InjectionServiceProviderException.class, () -> suppliers.stream()
                .map(Supplier::get)
                .toList());

        List<Supplier<ContractSingleton>> noIpProviderSuppliers = services.allSuppliers(LOOKUP_NO_IP_PROVIDER);

        // we can only get 3, as the servicesProvider is a single service, and we cannot know at the time of lookup
        // how many instances it may provide
        checkAll(noIpProviderSuppliers.stream()
                         .map(Supplier::get)
                         .toList(), 3);
    }

    @Test
    void allSuppliersTypeTest() {
        List<Supplier<ContractSingleton>> suppliers = services.allSuppliers(CONTRACT);

        // this cannot succeed, as the injection point provider does not return a value for unqualified requests
        assertThrows(InjectionServiceProviderException.class, () -> suppliers.stream()
                .map(Supplier::get)
                .toList());
    }

    @Test
    void supplyLookupTest() {
        Supplier<ContractSingleton> supply = services.supply(LOOKUP);
        ContractSingleton first = supply.get();
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));

        supply = services.supply(LOOKUP);
        ContractSingleton second = supply.get();

        assertThat(first, sameInstance(second));
    }

    @Test
    void supplyTypeTest() {
        Supplier<ContractSingleton> supply = services.supply(CONTRACT);
        ContractSingleton first = supply.get();
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));

        supply = services.supply(CONTRACT);
        ContractSingleton second = supply.get();

        assertThat(first, sameInstance(second));
    }

    @Test
    void supplyFirstLookupTest() {
        Supplier<Optional<ContractSingleton>> supply = services.supplyFirst(LOOKUP);

        Optional<ContractSingleton> first = supply.get();
        ContractSingleton firstValue = checkOptional(first, SingletonSupplierExample.First.class);

        supply = services.supplyFirst(LOOKUP);
        Optional<ContractSingleton> second = supply.get();
        ContractSingleton secondValue = checkOptional(second, SingletonSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void supplyFirstTypeTest() {
        Supplier<Optional<ContractSingleton>> supply = services.supplyFirst(CONTRACT);

        Optional<ContractSingleton> first = supply.get();
        ContractSingleton firstValue = checkOptional(first, SingletonSupplierExample.First.class);

        supply = services.supplyFirst(CONTRACT);
        Optional<ContractSingleton> second = supply.get();
        ContractSingleton secondValue = checkOptional(second, SingletonSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    @Test
    void supplyAllLookupTest() {
        List<ContractSingleton> all = services.<ContractSingleton>supplyAll(LOOKUP)
                .get();

        checkAll(all, 4);
    }

    @Test
    void supplyAllTypeTest() {
        List<ContractSingleton> all = services.supplyAll(CONTRACT)
                .get();

        checkAll(all, 4);
    }

    @Test
    void supplyFromDescriptorTest() {
        Supplier<ContractSingleton> supply = services.supply(SingletonDirectExample__ServiceDescriptor.INSTANCE);

        ContractSingleton first = supply.get();
        assertThat(first, instanceOf(SingletonDirectExample.class));

        supply = services.supply(SingletonDirectExample__ServiceDescriptor.INSTANCE);
        ContractSingleton second = supply.get();

        assertThat(first, sameInstance(second));
    }

    @Test
    void lookupInstancesTest() {
        List<ServiceInstance<ContractSingleton>> serviceInstances = services.lookupInstances(LOOKUP);
        Set<Class<?>> contracts = Set.of(ContractSingletonNoIpProvider.class,
                                         ContractSingleton.class,
                                         ContractCommon.class,
                                         ContractNoIpProvider.class);
        /*
        Order:
        1. SingletonSupplierExample (highest weight)
        2. SingletonDirectExample (alphabet...)
        3. SingletonInjectionPointExample - no instance, as we do not have a qualifier
        4. SingletonServicesProviderExample - two qualified instances
         */
        assertThat(serviceInstances, hasSize(4));

        checkInstance(serviceInstances.getFirst(),
                      SingletonSupplierExample.First.class, // instance type
                      SingletonSupplierExample.class, // service type
                      contracts,
                      Set.of(), // qualifiers
                      Weighted.DEFAULT_WEIGHT + 2); // weight

        checkInstance(serviceInstances.get(1),
                      SingletonDirectExample.class,
                      SingletonDirectExample.class,
                      contracts,
                      Set.of(),
                      Weighted.DEFAULT_WEIGHT);

        checkInstance(serviceInstances.get(2),
                      SingletonServicesProviderExample.FirstClass.class,
                      SingletonServicesProviderExample.class,
                      contracts,
                      Set.of(SingletonServicesProviderExample.FirstQuali.class),
                      Weighted.DEFAULT_WEIGHT);

        checkInstance(serviceInstances.get(3),
                      SingletonServicesProviderExample.SecondClass.class,
                      SingletonServicesProviderExample.class,
                      contracts,
                      Set.of(SingletonServicesProviderExample.SecondQuali.class),
                      Weighted.DEFAULT_WEIGHT);

    }

    @Test
    void lookupServicesTest() {
        List<ServiceInfo> serviceDescriptors = services.lookupServices(LOOKUP);

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

        ContractSingleton first = services.get(lookup);
        assertThat(first, instanceOf(SingletonServicesProviderExample.SecondClass.class));

        ContractSingleton second = services.get(lookup);
        assertThat(second, sameInstance(first));
    }

    @Test
    void qualifiedIpProviderTest() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractSingleton.class)
                .addQualifier(SingletonInjectionPointProviderExample.SECOND_QUALI)
                .build();

        ContractSingleton instance = services.get(lookup);
        assertThat(instance, instanceOf(SingletonInjectionPointProviderExample.SecondClass.class));
    }

    @Test
    void testIpProviderLookup() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractSingleton.class)
                .addContract(InjectionPointProvider.class)
                .build();
        InjectionPointProvider<?> instance = services.get(lookup);

        assertThat(instance, instanceOf(SingletonInjectionPointProviderExample.class));
    }

    @Test
    void testSupplierLookup() {
        Lookup lookup = Lookup.builder()
                .addContract(ContractSingleton.class)
                .addContract(Supplier.class)
                .build();
        Supplier<?> instance = services.get(lookup);

        assertThat(instance, instanceOf(SingletonSupplierExample.class));
    }

    private void checkInstance(ServiceInstance<ContractSingleton> serviceInstance,
                               Class<? extends ContractSingleton> instanceType,
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
