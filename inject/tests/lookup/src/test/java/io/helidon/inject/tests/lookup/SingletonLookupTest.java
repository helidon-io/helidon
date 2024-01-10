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

/**
 * Test all lookup methods for both singleton and no scope.
 */
class SingletonLookupTest {
    private static final Lookup UNQUALIFIED_LOOKUP = Lookup.create(ContractSingleton.class);
    private static final Class<ContractSingleton> CONTRACT = ContractSingleton.class;
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
        ContractSingleton first = services.get(UNQUALIFIED_LOOKUP);
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));
        ContractSingleton second = services.get(UNQUALIFIED_LOOKUP);
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
        Optional<ContractSingleton> first = services.first(UNQUALIFIED_LOOKUP);
        ContractSingleton firstValue = checkOptional(first, SingletonSupplierExample.First.class);

        Optional<ContractSingleton> second = services.first(UNQUALIFIED_LOOKUP);
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
        List<ContractSingleton> all = services.all(UNQUALIFIED_LOOKUP);

        checkAll(all);
    }

    void allTypeTest() {
        List<ContractSingleton> all = services.all(CONTRACT);

        checkAll(all);
    }

    void allSuppliersLookupTest() {
        List<Supplier<ContractSingleton>> suppliers = services.allSuppliers(UNQUALIFIED_LOOKUP);

        checkAll(suppliers.stream()
                         .map(Supplier::get)
                         .toList());
    }

    void allSuppliersTypeTest() {
        List<Supplier<ContractSingleton>> suppliers = services.allSuppliers(CONTRACT);

        checkAll(suppliers.stream()
                         .map(Supplier::get)
                         .toList());
    }

    void supplyLookupTest() {
        Supplier<ContractSingleton> supply = services.supply(UNQUALIFIED_LOOKUP);
        ContractSingleton first = supply.get();
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));

        supply = services.supply(UNQUALIFIED_LOOKUP);
        ContractSingleton second = supply.get();

        assertThat(first, sameInstance(second));
    }

    void supplyTypeTest() {
        Supplier<ContractSingleton> supply = services.supply(CONTRACT);
        ContractSingleton first = supply.get();
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));

        supply = services.supply(CONTRACT);
        ContractSingleton second = supply.get();

        assertThat(first, sameInstance(second));
    }

    void supplyFirstLookupTest() {
        Supplier<Optional<ContractSingleton>> supply = services.supplyFirst(UNQUALIFIED_LOOKUP);

        Optional<ContractSingleton> first = supply.get();
        ContractSingleton firstValue = checkOptional(first, SingletonSupplierExample.First.class);

        supply = services.supplyFirst(UNQUALIFIED_LOOKUP);
        Optional<ContractSingleton> second = supply.get();
        ContractSingleton secondValue = checkOptional(second, SingletonSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    void supplyFirstTypeTest() {
        Supplier<Optional<ContractSingleton>> supply = services.supplyFirst(CONTRACT);

        Optional<ContractSingleton> first = supply.get();
        ContractSingleton firstValue = checkOptional(first, SingletonSupplierExample.First.class);

        supply = services.supplyFirst(CONTRACT);
        Optional<ContractSingleton> second = supply.get();
        ContractSingleton secondValue = checkOptional(second, SingletonSupplierExample.First.class);

        assertThat(firstValue, sameInstance(secondValue));
    }

    void supplyAllLookupTest() {
        List<ContractSingleton> all = services.<ContractSingleton>supplyAll(UNQUALIFIED_LOOKUP)
                .get();

        checkAll(all);
    }

    void supplyAllTypeTest() {
        List<ContractSingleton> all = services.supplyAll(CONTRACT)
                .get();

        checkAll(all);
    }

    void supplyFromDescriptorTest() {
        Supplier<ContractSingleton> supply = services.supply(SingletonDirectExample__ServiceDescriptor.INSTANCE);

        ContractSingleton first = supply.get();
        assertThat(first, instanceOf(SingletonSupplierExample.First.class));

        supply = services.supply(SingletonDirectExample__ServiceDescriptor.INSTANCE);
        ContractSingleton second = supply.get();

        assertThat(first, sameInstance(second));
    }

    void lookupInstancesTest() {
        List<ServiceInstance<ContractSingleton>> serviceInstances = services.lookupInstances(UNQUALIFIED_LOOKUP);

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
                      Set.of(ContractSingleton.class, ContractCommon.class), // contracts
                      Set.of(), // qualifiers
                      Weighted.DEFAULT_WEIGHT + 2); // weight

        checkInstance(serviceInstances.get(1),
                      SingletonDirectExample.class,
                      SingletonDirectExample.class,
                      Set.of(ContractSingleton.class, ContractCommon.class),
                      Set.of(),
                      Weighted.DEFAULT_WEIGHT);

        checkInstance(serviceInstances.get(2),
                      SingletonServicesProviderExample.FirstClass.class,
                      SingletonServicesProviderExample.class,
                      Set.of(ContractSingleton.class, ContractCommon.class),
                      Set.of(SingletonServicesProviderExample.FirstQuali.class),
                      Weighted.DEFAULT_WEIGHT);

        checkInstance(serviceInstances.get(2),
                      SingletonServicesProviderExample.SecondClass.class,
                      SingletonServicesProviderExample.class,
                      Set.of(ContractSingleton.class, ContractCommon.class),
                      Set.of(SingletonServicesProviderExample.SecondQuali.class),
                      Weighted.DEFAULT_WEIGHT);

    }

    void lookupServicesTest() {
        // services.lookupInstances(Lookup.EMPTY);
    }

    void qualifiedServicesProviderTest() {

    }

    void qualifiedIpProviderTest() {

    }

    void directTest() {
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

    private void checkAll(List<ContractSingleton> all) {
        /*
        Order:
        1. SingletonSupplierExample (highest weight)
        2. SingletonDirectExample (alphabet...)
        3. SingletonInjectionPointExample - no instance, as we do not have a qualifier
        4. SingletonServicesProviderExample - two qualified instances
         */
        assertThat(all, hasSize(4));

        assertThat(all.getFirst(), instanceOf(SingletonSupplierExample.First.class));
        assertThat(all.get(1), instanceOf(SingletonDirectExample.class));
        assertThat(all.get(2), instanceOf(SingletonServicesProviderExample.FirstClass.class));
        assertThat(all.get(3), instanceOf(SingletonServicesProviderExample.SecondClass.class));
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
