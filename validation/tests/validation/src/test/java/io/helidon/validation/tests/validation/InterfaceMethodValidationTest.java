/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.validation.tests.validation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.FactoryType;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.testing.junit5.Testing;
import io.helidon.validation.ConstraintViolation.Location;
import io.helidon.validation.ConstraintViolation.PathElement;
import io.helidon.validation.Validation;
import io.helidon.validation.ValidationException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
class InterfaceMethodValidationTest {
    private final ServiceRegistry registry;

    InterfaceMethodValidationTest(ServiceRegistry registry) {
        this.registry = registry;
    }

    @Test
    void interfaceParameterConstraintIsAppliedFromRegistryService() {
        assertThat(new InterfaceConstrainedServiceImpl().validate(""), is(""));
        assertThat(new InterfaceConstrainedServiceImpl().validateDefault(""), is(""));
        assertThat(new InterfaceConstrainedServiceImpl().validateList(List.of("")), is(List.of("")));
        assertThat(new InterfaceConstrainedServiceImpl().validateNestedList(List.of(List.of(""))), is(List.of(List.of(""))));
        assertThat(new InterfaceConstrainedServiceImpl().validateMap(Map.of("", "valid")), is(Map.of("", "valid")));
        assertThat(new InterfaceConstrainedServiceImpl().validateNestedMapKey(Map.of(List.of(""), "valid")),
                   is(Map.of(List.of(""), "valid")));
        assertThat(new InterfaceConstrainedServiceImpl().validateNestedMapValue(Map.of("key", List.of(""))),
                   is(Map.of("key", List.of(""))));
        assertThat(new InterfaceConstrainedServiceImpl().validateOptional(Optional.of("")), is(Optional.of("")));
        String[] array = {""};
        assertThat(new InterfaceConstrainedServiceImpl().validateArray(array), sameInstance(array));
        assertThat(new InterfaceConstrainedServiceImpl().validateIntegerList(List.of(5)), is(List.of(5)));
        assertThat(new InterfaceConstrainedServiceImpl().invalidNames(), is(List.of("")));
        assertThat(new InterfaceConstrainedServiceImpl().validateMinimum(5), is(5));
        assertThat(new InterfaceConstrainedServiceImpl().validateShared(""), is(""));
        assertThat(new InterfaceConstrainedServiceImpl().validateDuplicate(""), is(""));
        assertThat(new InterfaceConstrainedServiceImpl().validateGeneric(""), is(""));
        assertThat(new InterfaceConstrainedServiceImpl().validateOrderedGeneric("", 42), is(42));
        assertThat(new InterfaceConstrainedServiceImpl().validateInheritedGeneric(""), is(""));
        assertThat(new DefaultInterfaceConstrainedServiceImpl().validateInheritedDefault(""), is(""));

        InterfaceConstrainedService service = registry.get(InterfaceConstrainedService.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        GenericInterfaceConstrainedService<String> genericService =
                (GenericInterfaceConstrainedService) registry.get(GenericInterfaceConstrainedService.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        OrderedGenericInterfaceConstrainedService<String, Integer> orderedGenericService =
                (OrderedGenericInterfaceConstrainedService) registry.get(OrderedGenericInterfaceConstrainedService.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        ParentGenericInterfaceConstrainedService<String> parentGenericService =
                (ParentGenericInterfaceConstrainedService) registry.get(ParentGenericInterfaceConstrainedService.class);
        ConstrainedDefaultInterfaceConstrainedService defaultMethodService =
                registry.get(ConstrainedDefaultInterfaceConstrainedService.class);

        assertThat(service.validate("valid"), is("valid"));
        LegacyValidateDefaultInterceptor.reset();
        assertThat(service.validateDefault("valid"), is("valid"));
        assertThat(LegacyValidateDefaultInterceptor.count(), is(1));
        assertThat(service.validateList(List.of("valid")), is(List.of("valid")));
        assertThat(service.validateNestedList(List.of(List.of("valid"))), is(List.of(List.of("valid"))));
        assertThat(service.validateMap(Map.of("key", "valid")), is(Map.of("key", "valid")));
        assertThat(service.validateNestedMapKey(Map.of(List.of("valid"), "valid")),
                   is(Map.of(List.of("valid"), "valid")));
        assertThat(service.validateNestedMapValue(Map.of("key", List.of("valid"))),
                   is(Map.of("key", List.of("valid"))));
        assertThat(service.validateOptional(Optional.of("valid")), is(Optional.of("valid")));
        array = new String[] {"valid"};
        assertThat(service.validateArray(array), sameInstance(array));
        assertThat(service.validateIntegerList(List.of(10)), is(List.of(10)));
        assertThat(service.validateMinimum(10), is(10));
        assertThat(service.validateShared("valid"), is("valid"));
        assertThat(service.validateDuplicate("valid"), is("valid"));
        assertThat(service.validateCustomStringList(List.of("good")), is(List.of("good")));
        assertThat(service.validateCustomIntegerList(List.of(42)), is(List.of(42)));
        assertThat(genericService.validateGeneric("valid"), is("valid"));
        assertThat(orderedGenericService.validateOrderedGeneric("valid", 42), is(42));
        assertThat(parentGenericService.validateInheritedGeneric("valid"), is("valid"));
        assertThat(defaultMethodService.validateInheritedDefault("valid"), is("valid"));

        var result = assertThrows(ValidationException.class, () -> service.validate(""));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validate(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> service.validateCustomStringList(List.of("bad")));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateCustomStringList(java.util.List)"),
                                PathElement.create(Location.PARAMETER, "values")),
                        "Must be \"good\" string",
                        List.of("bad"),
                        TypeName.create(CustomConstraint.class));

        result = assertThrows(ValidationException.class, () -> service.validateCustomIntegerList(List.of(41)));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateCustomIntegerList(java.util.List)"),
                                PathElement.create(Location.PARAMETER, "values")),
                        "Must be \"good\" string",
                        List.of(41),
                        TypeName.create(CustomConstraint.class));

        result = assertThrows(ValidationException.class, () -> service.validateDefault(""));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateDefault(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> service.validateList(List.of("")));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateList(java.util.List)"),
                                PathElement.create(Location.PARAMETER, "values"),
                                PathElement.create(Location.ELEMENT, "element")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> service.validateNestedList(List.of(List.of(""))));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateNestedList(java.util.List)"),
                                PathElement.create(Location.PARAMETER, "values"),
                                PathElement.create(Location.ELEMENT, "element")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> service.validateMap(Map.of("", "valid")));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateMap(java.util.Map)"),
                                PathElement.create(Location.PARAMETER, "values"),
                                PathElement.create(Location.KEY, "key")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> service.validateMap(Map.of("key", "")));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateMap(java.util.Map)"),
                                PathElement.create(Location.PARAMETER, "values"),
                                PathElement.create(Location.ELEMENT, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> service.validateNestedMapKey(Map.of(List.of(""), "valid")));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateNestedMapKey(java.util.Map)"),
                                PathElement.create(Location.PARAMETER, "values"),
                                PathElement.create(Location.KEY, "key"),
                                PathElement.create(Location.ELEMENT, "element")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class,
                              () -> service.validateNestedMapValue(Map.of("key", List.of(""))));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateNestedMapValue(java.util.Map)"),
                                PathElement.create(Location.PARAMETER, "values"),
                                PathElement.create(Location.ELEMENT, "value"),
                                PathElement.create(Location.ELEMENT, "element")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> service.validateOptional(Optional.of("")));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateOptional(java.util.Optional)"),
                                PathElement.create(Location.PARAMETER, "value"),
                                PathElement.create(Location.ELEMENT, "element")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> service.validateArray(new String[] {""}));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateArray(java.lang.String[])"),
                                PathElement.create(Location.PARAMETER, "values"),
                                PathElement.create(Location.ELEMENT, "element")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> service.validateIntegerList(List.of(5)));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateIntegerList(java.util.List)"),
                                PathElement.create(Location.PARAMETER, "values"),
                                PathElement.create(Location.ELEMENT, "element")),
                        "is less than 10",
                        5,
                        TypeName.create(Validation.Integer.Min.class));

        result = assertThrows(ValidationException.class, service::invalidNames);
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "invalidNames()"),
                                PathElement.create(Location.RETURN_VALUE, "List"),
                                PathElement.create(Location.ELEMENT, "element")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> service.validateMinimum(5));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateMinimum(int)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is less than 10",
                        5,
                        TypeName.create(Validation.Integer.Min.class));

        result = assertThrows(ValidationException.class, () -> service.validateShared(""));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateShared(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> service.validateDuplicate(""));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateDuplicate(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> genericService.validateGeneric(""));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateGeneric(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> orderedGenericService.validateOrderedGeneric("", 42));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD,
                                                   "validateOrderedGeneric(java.lang.String,java.lang.Integer)"),
                                PathElement.create(Location.PARAMETER, "key")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> parentGenericService.validateInheritedGeneric(""));
        assertViolation(result,
                        InterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, InterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateInheritedGeneric(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> defaultMethodService.validateInheritedDefault(""));
        assertViolation(result,
                        DefaultInterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, DefaultInterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateInheritedDefault(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));
    }

    @Test
    void injectOnlyServiceUsesInterfaceParameterConstraint() {
        assertThat(new InjectOnlyInterfaceConstrainedServiceImpl().validate(""), is(""));

        InjectOnlyInterfaceConstrainedService service = registry.get(InjectOnlyInterfaceConstrainedService.class);

        assertThat(service.validate("valid"), is("valid"));

        var result = assertThrows(ValidationException.class, () -> service.validate(""));
        assertViolation(result,
                        InjectOnlyInterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   InjectOnlyInterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validate(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));
    }

    @Test
    void supplierProvidedServiceUsesInterfaceParameterConstraint() {
        assertThat(new SupplierProvidedInterfaceConstrainedServiceProvider().get().validate(""), is(""));

        SupplierProvidedInterfaceConstrainedService service =
                registry.get(SupplierProvidedInterfaceConstrainedService.class);

        assertThat(service.validate("valid"), is("valid"));

        var result = assertThrows(ValidationException.class, () -> service.validate(""));
        assertViolation(result,
                        SupplierProvidedInterfaceConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   SupplierProvidedInterfaceConstrainedServiceProvider.class.getName()),
                                PathElement.create(Location.METHOD, "validate(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));
    }

    @Test
    void supplierProvidedServiceUsesInterfaceReturnConstraintOnSameSignatureMethod() {
        assertThat(new SupplierProvidedSameSignatureInterfaceConstrainedServiceProvider().get().get(), is(""));

        SupplierProvidedSameSignatureInterfaceConstrainedService service =
                registry.get(SupplierProvidedSameSignatureInterfaceConstrainedService.class);

        var result = assertThrows(ValidationException.class, service::get);
        assertViolation(result,
                        SupplierProvidedSameSignatureInterfaceConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   SupplierProvidedSameSignatureInterfaceConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD, "get()"),
                                PathElement.create(Location.RETURN_VALUE, "String")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        Lookup supplierLookup = Lookup.builder()
                .addContract(SupplierProvidedSameSignatureInterfaceConstrainedService.class)
                .addFactoryType(FactoryType.SUPPLIER)
                .build();
        Supplier<SupplierProvidedSameSignatureInterfaceConstrainedService> supplier = registry.get(supplierLookup);

        FactoryProviderGetInterceptor.reset();
        SupplierProvidedSameSignatureInterfaceConstrainedService suppliedService = supplier.get();
        assertThat(FactoryProviderGetInterceptor.count(), is(1));

        result = assertThrows(ValidationException.class, suppliedService::get);
        assertViolation(result,
                        SupplierProvidedSameSignatureInterfaceConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   SupplierProvidedSameSignatureInterfaceConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD, "get()"),
                                PathElement.create(Location.RETURN_VALUE, "String")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        SupplierProvidedSameSignatureProviderContract providerContract =
                registry.get(SupplierProvidedSameSignatureProviderContract.class);

        FactoryProviderGetInterceptor.reset();
        assertThat(providerContract.get(), instanceOf(SupplierProvidedSameSignatureInterfaceConstrainedServiceImpl.class));
        assertThat(FactoryProviderGetInterceptor.count(), is(1));
    }

    @Test
    void supplierProviderDirectContractAndProvidedContractUseIndependentSameSignatureConstraints() {
        assertThat(new SupplierProviderSameSignatureConstrainedServiceProvider().get().validate(""), is(""));
        assertThat(new SupplierProviderSameSignatureConstrainedServiceProvider().validate("abc"), is("abc"));

        SupplierProviderSameSignatureConstrainedService service =
                registry.get(SupplierProviderSameSignatureConstrainedService.class);

        assertThat(service.validate("abc"), is("abc"));

        var result = assertThrows(ValidationException.class, () -> service.validate(""));
        assertViolation(result,
                        SupplierProviderSameSignatureConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   SupplierProviderSameSignatureConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD, "validate(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        SupplierProviderSameSignatureConstrainedServiceProvider provider =
                registry.get(SupplierProviderSameSignatureConstrainedServiceProvider.class);

        assertThat(provider.validate("123"), is("123"));

        result = assertThrows(ValidationException.class, () -> provider.validate("abc"));
        assertViolation(result,
                        SupplierProviderSameSignatureConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   SupplierProviderSameSignatureConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD, "validate(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "pattern",
                        "abc",
                        TypeName.create(Validation.String.Pattern.class));

        SupplierProviderSameSignatureDirectService directService =
                registry.get(SupplierProviderSameSignatureDirectService.class);
        List<ServiceInstance<SupplierProviderSameSignatureDirectService>> directInstances =
                registry.lookupInstances(Lookup.create(SupplierProviderSameSignatureDirectService.class));

        assertThat(directService.validate("123"), is("123"));
        assertThat(directInstances, hasSize(1));
        assertThat(directInstances.getFirst()
                           .contracts()
                           .contains(ResolvedType.create(SupplierProviderSameSignatureDirectService.class)),
                   is(true));

        result = assertThrows(ValidationException.class, () -> directService.validate("abc"));
        assertViolation(result,
                        SupplierProviderSameSignatureConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   SupplierProviderSameSignatureConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD, "validate(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "pattern",
                        "abc",
                        TypeName.create(Validation.String.Pattern.class));
    }

    @Test
    void optionalSupplierProvidedServiceUsesInterfaceParameterConstraint() {
        assertThat(new OptionalSupplierProvidedInterfaceConstrainedServiceProvider()
                           .get()
                           .orElseThrow()
                           .validate(""),
                   is(""));

        OptionalSupplierProvidedInterfaceConstrainedService service =
                registry.get(OptionalSupplierProvidedInterfaceConstrainedService.class);

        assertThat(service.validate("valid"), is("valid"));

        var result = assertThrows(ValidationException.class, () -> service.validate(""));
        assertViolation(result,
                        OptionalSupplierProvidedInterfaceConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   OptionalSupplierProvidedInterfaceConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD, "validate(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        Lookup supplierLookup = Lookup.builder()
                .addContract(OptionalSupplierProvidedInterfaceConstrainedService.class)
                .addFactoryType(FactoryType.SUPPLIER)
                .build();
        Supplier<Optional<OptionalSupplierProvidedInterfaceConstrainedService>> supplier = registry.get(supplierLookup);

        assertThat(supplier.get().orElseThrow().validate("valid"), is("valid"));

        result = assertThrows(ValidationException.class, () -> supplier.get().orElseThrow().validate(""));
        assertViolation(result,
                        OptionalSupplierProvidedInterfaceConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   OptionalSupplierProvidedInterfaceConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD, "validate(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));
    }

    @Test
    void concreteFactoryProviderLookupRemainsAvailable() {
        assertConcreteProviderIsAvailable(SupplierProvidedInterfaceConstrainedServiceProvider.class, FactoryType.SUPPLIER);
        assertConcreteProviderIsAvailable(OptionalSupplierProvidedInterfaceConstrainedServiceProvider.class,
                                          FactoryType.SUPPLIER);
        assertConcreteProviderIsAvailable(ServicesFactoryProvidedInterfaceConstrainedServiceProvider.class,
                                          FactoryType.SERVICES);
        assertConcreteProviderIsAvailable(InjectionPointFactoryProvidedInterfaceConstrainedServiceProvider.class,
                                          FactoryType.INJECTION_POINT);
        assertConcreteProviderIsAvailable(QualifiedFactoryProvidedInterfaceConstrainedServiceProvider.class,
                                          FactoryType.QUALIFIED);

        Lookup supplierLookup = Lookup.builder()
                .addContract(SupplierProvidedInterfaceConstrainedService.class)
                .addFactoryType(FactoryType.SUPPLIER)
                .build();
        Supplier<SupplierProvidedInterfaceConstrainedService> supplier = registry.get(supplierLookup);
        assertThrows(ValidationException.class, () -> supplier.get().validate(""));
    }

    @Test
    void servicesFactoryProvidedServiceUsesInterfaceParameterConstraint() {
        assertThat(new ServicesFactoryProvidedInterfaceConstrainedServiceProvider()
                           .services()
                           .getFirst()
                           .get()
                           .validate(""),
                   is(""));

        ServicesFactoryProvidedInterfaceConstrainedService service =
                registry.get(ServicesFactoryProvidedInterfaceConstrainedService.class);
        Lookup factoryLookup = Lookup.builder()
                .addContract(ServicesFactoryProvidedInterfaceConstrainedService.class)
                .addFactoryType(FactoryType.SERVICES)
                .build();
        Service.ServicesFactory<ServicesFactoryProvidedInterfaceConstrainedService> factory = registry.get(factoryLookup);
        List<Service.QualifiedInstance<ServicesFactoryProvidedInterfaceConstrainedService>> factoryServices =
                factory.services();

        assertThat(service.validate("valid"), is("valid"));
        assertThat(factoryServices, hasSize(1));
        assertThat(factoryServices.getFirst().get().validate("valid"), is("valid"));
        assertThrows(ValidationException.class, () -> factoryServices.getFirst().get().validate(""));

        var result = assertThrows(ValidationException.class, () -> service.validate(""));
        assertViolation(result,
                        ServicesFactoryProvidedInterfaceConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   ServicesFactoryProvidedInterfaceConstrainedServiceProvider.class.getName()),
                                PathElement.create(Location.METHOD, "validate(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));
    }

    @Test
    void broadLookupDoesNotBypassFactoryInterceptionWrappers() {
        List<Object> allServices = registry.all(Lookup.EMPTY);
        List<SupplierProvidedInterfaceConstrainedService> supplierServices =
                allServices.stream()
                        .filter(SupplierProvidedInterfaceConstrainedService.class::isInstance)
                        .map(SupplierProvidedInterfaceConstrainedService.class::cast)
                        .toList();
        List<OptionalSupplierProvidedInterfaceConstrainedService> optionalSupplierServices =
                allServices.stream()
                        .filter(OptionalSupplierProvidedInterfaceConstrainedService.class::isInstance)
                        .map(OptionalSupplierProvidedInterfaceConstrainedService.class::cast)
                        .toList();
        List<ServicesFactoryProvidedInterfaceConstrainedService> servicesFactoryServices =
                allServices.stream()
                        .filter(ServicesFactoryProvidedInterfaceConstrainedService.class::isInstance)
                        .map(ServicesFactoryProvidedInterfaceConstrainedService.class::cast)
                        .toList();

        assertThat(supplierServices.isEmpty(), is(false));
        assertThat(optionalSupplierServices.isEmpty(), is(false));
        assertThat(servicesFactoryServices.isEmpty(), is(false));
        supplierServices.forEach(it -> assertThrows(ValidationException.class, () -> it.validate("")));
        optionalSupplierServices.forEach(it -> assertThrows(ValidationException.class, () -> it.validate("")));
        servicesFactoryServices.forEach(it -> assertThrows(ValidationException.class, () -> it.validate("")));
    }

    @Test
    void injectionPointFactoryProvidedServiceUsesInterfaceParameterConstraint() {
        assertThat(new InjectionPointFactoryProvidedInterfaceConstrainedServiceProvider()
                           .first(Lookup.create(InjectionPointFactoryProvidedInterfaceConstrainedService.class))
                           .orElseThrow()
                           .get()
                           .validate(""),
                   is(""));

        InjectionPointFactoryProvidedInterfaceConstrainedService service =
                registry.get(InjectionPointFactoryProvidedInterfaceConstrainedService.class);
        List<InjectionPointFactoryProvidedInterfaceConstrainedService> services =
                registry.all(InjectionPointFactoryProvidedInterfaceConstrainedService.class);
        Lookup factoryLookup = Lookup.builder()
                .addContract(InjectionPointFactoryProvidedInterfaceConstrainedService.class)
                .addFactoryType(FactoryType.INJECTION_POINT)
                .build();
        Service.InjectionPointFactory<InjectionPointFactoryProvidedInterfaceConstrainedService> factory =
                registry.get(factoryLookup);
        InjectionPointFactoryDirectContract directFactory = registry.get(InjectionPointFactoryDirectContract.class);
        Service.QualifiedInstance<InjectionPointFactoryProvidedInterfaceConstrainedService> factoryFirst =
                factory.first(Lookup.create(InjectionPointFactoryProvidedInterfaceConstrainedService.class))
                        .orElseThrow();
        List<Service.QualifiedInstance<InjectionPointFactoryProvidedInterfaceConstrainedService>> factoryServices =
                factory.list(Lookup.create(InjectionPointFactoryProvidedInterfaceConstrainedService.class));
        InjectionPointFactoryOverloadedDirectContract overloadedFactory = registry.get(
                InjectionPointFactoryOverloadedDirectContract.class);

        assertThat(service.validate("valid"), is("valid"));
        assertThat(services, hasSize(2));
        assertThat(factoryFirst.get().validate("valid"), is("valid"));
        assertThat(factoryServices, hasSize(2));
        assertThat(overloadedFactory.first("overloaded").orElseThrow().get().validate("valid"), is("valid"));
        for (InjectionPointFactoryProvidedInterfaceConstrainedService listedService : services) {
            assertThat(listedService.validate("valid"), is("valid"));
            assertThrows(ValidationException.class, () -> listedService.validate(""));
        }

        var result = assertThrows(ValidationException.class, () -> service.validate(""));
        assertViolation(result,
                        InjectionPointFactoryProvidedInterfaceConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   InjectionPointFactoryProvidedInterfaceConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD, "validate(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> factory.list(null));
        assertViolation(result,
                        InjectionPointFactoryProvidedInterfaceConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   InjectionPointFactoryProvidedInterfaceConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD, "list(io.helidon.service.registry.Lookup)"),
                                PathElement.create(Location.PARAMETER, "lookup")),
                        "is null",
                        null,
                        TypeName.create(Validation.NotNull.class));

        result = assertThrows(ValidationException.class, () -> directFactory.list(null));
        assertViolation(result,
                        InjectionPointFactoryProvidedInterfaceConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   InjectionPointFactoryProvidedInterfaceConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD, "list(io.helidon.service.registry.Lookup)"),
                                PathElement.create(Location.PARAMETER, "lookup")),
                        "is null",
                        null,
                        TypeName.create(Validation.NotNull.class));

        result = assertThrows(ValidationException.class, () -> overloadedFactory.first(null));
        assertViolation(result,
                        InjectionPointFactoryProvidedInterfaceConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   InjectionPointFactoryProvidedInterfaceConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD, "first(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "name")),
                        "is null",
                        null,
                        TypeName.create(Validation.NotNull.class));
    }

    @Test
    void injectionPointFactoryListPreservesCustomListWhenOnlyFirstIsIntercepted() {
        Lookup factoryLookup = Lookup.builder()
                .addContract(InjectionPointFactoryFirstOnlyProvidedService.class)
                .addFactoryType(FactoryType.INJECTION_POINT)
                .build();
        Service.InjectionPointFactory<InjectionPointFactoryFirstOnlyProvidedService> factory = registry.get(factoryLookup);

        List<Service.QualifiedInstance<InjectionPointFactoryFirstOnlyProvidedService>> factoryServices =
                factory.list(Lookup.create(InjectionPointFactoryFirstOnlyProvidedService.class));

        assertThat(factoryServices, hasSize(2));
        for (Service.QualifiedInstance<InjectionPointFactoryFirstOnlyProvidedService> factoryService : factoryServices) {
            assertThat(factoryService.get().validate("valid"), is("valid"));
            assertThrows(ValidationException.class, () -> factoryService.get().validate(""));
        }
    }

    @Test
    void qualifiedFactoryListPreservesCustomListWhenOnlyFirstIsIntercepted() {
        Qualifier qualifier = Qualifier.createNamed("qualified-first-only");
        Lookup lookup = Lookup.builder()
                .addQualifier(qualifier)
                .addContract(QualifiedFactoryFirstOnlyProvidedService.class)
                .build();
        Lookup factoryLookup = Lookup.builder()
                .addContract(QualifiedFactoryFirstOnlyProvidedService.class)
                .addFactoryType(FactoryType.QUALIFIED)
                .build();
        Service.QualifiedFactory<QualifiedFactoryFirstOnlyProvidedService, Service.Named> factory =
                registry.get(factoryLookup);
        GenericType<QualifiedFactoryFirstOnlyProvidedService> contractType =
                GenericType.create(QualifiedFactoryFirstOnlyProvidedService.class);

        List<Service.QualifiedInstance<QualifiedFactoryFirstOnlyProvidedService>> factoryServices =
                factory.list(qualifier, lookup, contractType);

        assertThat(factoryServices, hasSize(2));
        for (Service.QualifiedInstance<QualifiedFactoryFirstOnlyProvidedService> factoryService : factoryServices) {
            assertThat(factoryService.get().validate("valid"), is("valid"));
            assertThrows(ValidationException.class, () -> factoryService.get().validate(""));
        }
    }

    @Test
    void qualifiedFactoryProvidedServiceUsesInterfaceParameterConstraint() {
        Qualifier qualifier = Qualifier.createNamed("qualified");
        Lookup lookup = Lookup.builder()
                .addQualifier(qualifier)
                .addContract(QualifiedFactoryProvidedInterfaceConstrainedService.class)
                .build();
        GenericType<QualifiedFactoryProvidedInterfaceConstrainedService> contractType =
                GenericType.create(QualifiedFactoryProvidedInterfaceConstrainedService.class);
        assertThat(new QualifiedFactoryProvidedInterfaceConstrainedServiceProvider()
                           .first(qualifier,
                                  lookup,
                                  contractType)
                           .orElseThrow()
                           .get()
                           .validate(""),
                   is(""));

        QualifiedFactoryProvidedInterfaceConstrainedService service = registry.get(lookup);
        var serviceInfo = registry.lookupServices(lookup).getFirst();
        Lookup factoryLookup = Lookup.builder()
                .addQualifier(qualifier)
                .addContract(QualifiedFactoryProvidedInterfaceConstrainedService.class)
                .addFactoryType(FactoryType.QUALIFIED)
                .build();
        Service.QualifiedFactory<QualifiedFactoryProvidedInterfaceConstrainedService, Service.Named> factory =
                registry.get(factoryLookup);
        QualifiedFactoryDirectContract directFactory = registry.get(QualifiedFactoryDirectContract.class);
        Service.QualifiedInstance<QualifiedFactoryProvidedInterfaceConstrainedService> factoryFirst =
                factory.first(qualifier, lookup, contractType).orElseThrow();
        List<Service.QualifiedInstance<QualifiedFactoryProvidedInterfaceConstrainedService>> factoryServices =
                factory.list(qualifier, lookup, contractType);

        FactoryProviderLegacyValidateInterceptor.reset();
        assertThat(service.validate("valid"), is("valid"));
        assertThat(FactoryProviderLegacyValidateInterceptor.count(), is(0));
        assertThat(serviceInfo.weight(), is(42.0));
        assertThat(serviceInfo.runLevel(), is(Optional.of(43.0)));
        assertThat(factoryFirst.get().validate("valid"), is("valid"));
        assertThat(factoryServices, hasSize(2));
        assertThat(factoryServices.getFirst().get().validate("valid"), is("valid"));
        assertThrows(ValidationException.class, () -> factoryFirst.get().validate(""));
        assertThrows(ValidationException.class, () -> factoryServices.getFirst().get().validate(""));

        var result = assertThrows(ValidationException.class, () -> service.validate(""));
        assertViolation(result,
                        QualifiedFactoryProvidedInterfaceConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   QualifiedFactoryProvidedInterfaceConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD, "validate(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));

        result = assertThrows(ValidationException.class, () -> factory.list(null, lookup, contractType));
        assertViolation(result,
                        QualifiedFactoryProvidedInterfaceConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   QualifiedFactoryProvidedInterfaceConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD,
                                                   "list(io.helidon.service.registry.Qualifier,"
                                                           + "io.helidon.service.registry.Lookup,"
                                                           + "io.helidon.common.GenericType)"),
                                PathElement.create(Location.PARAMETER, "qualifier")),
                        "is null",
                        null,
                        TypeName.create(Validation.NotNull.class));

        result = assertThrows(ValidationException.class, () -> directFactory.list(null, lookup, contractType));
        assertViolation(result,
                        QualifiedFactoryProvidedInterfaceConstrainedServiceProvider.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   QualifiedFactoryProvidedInterfaceConstrainedServiceProvider.class
                                                           .getName()),
                                PathElement.create(Location.METHOD,
                                                   "list(io.helidon.service.registry.Qualifier,"
                                                           + "io.helidon.service.registry.Lookup,"
                                                           + "io.helidon.common.GenericType)"),
                                PathElement.create(Location.PARAMETER, "qualifier")),
                        "is null",
                        null,
                        TypeName.create(Validation.NotNull.class));
    }

    @Test
    void perInstanceServiceUsesInterfaceParameterConstraint() {
        var directService = new PerInstanceInterfaceConstrainedServiceImpl(new PerInstanceInterfaceConstrainedConfig());
        assertThat(directService.validate(""),
                   is(""));

        PerInstanceInterfaceConstrainedService service = registry.get(PerInstanceInterfaceConstrainedService.class);

        assertThat(service.validate("valid"), is("valid"));

        var result = assertThrows(ValidationException.class, () -> service.validate(""));
        assertViolation(result,
                        PerInstanceInterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE,
                                                   PerInstanceInterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validate(java.lang.String)"),
                                PathElement.create(Location.PARAMETER, "value")),
                        "is blank",
                        "",
                        TypeName.create(Validation.String.NotBlank.class));
    }

    @Test
    void validInterfaceParameterTriggersValidation() {
        ValidatedType invalidValue = new ValidatedType("invalid_text", 42, new BigDecimal("1.10"));
        assertThat(new ValidInterfaceConstrainedServiceImpl().validate(invalidValue), is("ok"));
        assertThat(new ValidInterfaceConstrainedServiceImpl().validateAll(List.of(invalidValue)), is("ok"));

        ValidInterfaceConstrainedService service = registry.get(ValidInterfaceConstrainedService.class);

        assertThat(service.validate(new ValidatedType("good_test_value", 42, new BigDecimal("1.10"))), is("ok"));
        assertThat(service.validateAll(List.of(new ValidatedType("good_test_value", 42, new BigDecimal("1.10")))), is("ok"));

        var result = assertThrows(ValidationException.class, () -> service.validate(invalidValue));
        assertViolation(result,
                        ValidInterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, ValidInterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD,
                                                   "validate(io.helidon.validation.tests.validation.ValidatedType)"),
                                PathElement.create(Location.PARAMETER, "value"),
                                PathElement.create(Location.TYPE, ValidatedType.class.getName()),
                                PathElement.create(Location.RECORD_COMPONENT, "first")),
                        "pattern",
                        "invalid_text",
                        TypeName.create(Validation.String.Pattern.class));

        result = assertThrows(ValidationException.class, () -> service.validateAll(List.of(invalidValue)));
        assertViolation(result,
                        ValidInterfaceConstrainedServiceImpl.class,
                        List.of(PathElement.create(Location.TYPE, ValidInterfaceConstrainedServiceImpl.class.getName()),
                                PathElement.create(Location.METHOD, "validateAll(java.util.List)"),
                                PathElement.create(Location.PARAMETER, "values"),
                                PathElement.create(Location.ELEMENT, "element"),
                                PathElement.create(Location.TYPE, ValidatedType.class.getName()),
                                PathElement.create(Location.RECORD_COMPONENT, "first")),
                        "pattern",
                        "invalid_text",
                        TypeName.create(Validation.String.Pattern.class));
    }

    @Test
    void serviceDescriptorMetadataIncludesInterfaceMethodConstraints() {
        TypeName notBlank = TypeName.create(Validation.String.NotBlank.class);
        TypeName min = TypeName.create(Validation.Integer.Min.class);

        var validateParameter = InterfaceConstrainedServiceImpl__ServiceDescriptor.METHOD_VALIDATE
                .parameterArguments()
                .getFirst();
        assertThat(validateParameter.hasAnnotation(notBlank), is(true));

        var validateListElementType = InterfaceConstrainedServiceImpl__ServiceDescriptor.METHOD_VALIDATE_LIST
                .parameterArguments()
                .getFirst()
                .typeName()
                .typeArguments()
                .getFirst();
        assertThat(validateListElementType.hasAnnotation(notBlank), is(true));

        var invalidNamesElementType = InterfaceConstrainedServiceImpl__ServiceDescriptor.METHOD_INVALID_NAMES
                .typeName()
                .typeArguments()
                .getFirst();
        assertThat(invalidNamesElementType.hasAnnotation(notBlank), is(true));

        var inheritedGenericParameter = InterfaceConstrainedServiceImpl__ServiceDescriptor.METHOD_VALIDATE_INHERITED_GENERIC
                .parameterArguments()
                .getFirst();
        assertThat(inheritedGenericParameter.typeName(), is(TypeName.create(String.class)));
        assertThat(inheritedGenericParameter.hasAnnotation(notBlank), is(true));

        var validateMinimumParameter = InterfaceConstrainedServiceImpl__ServiceDescriptor.METHOD_VALIDATE_MINIMUM
                .parameterArguments()
                .getFirst();
        assertThat(validateMinimumParameter.annotation(min).objectValue(), is(Optional.of(1)));
        assertThat(validateMinimumParameter.annotations()
                           .stream()
                           .filter(it -> it.typeName().equals(min))
                           .map(it -> it.objectValue().orElseThrow())
                           .toList(),
                   contains(1, 10));
    }

    private void assertConcreteProviderIsAvailable(Class<?> providerType, FactoryType factoryType) {
        assertThat(providerType.isInstance(registry.first(providerType).orElseThrow()), is(true));

        Lookup serviceTypeLookup = Lookup.builder()
                .serviceType(providerType)
                .build();
        assertThat(providerType.isInstance(registry.first(serviceTypeLookup).orElseThrow()), is(true));

        Lookup factoryServiceTypeLookup = Lookup.builder()
                .serviceType(providerType)
                .addFactoryType(factoryType)
                .build();
        assertThat(providerType.isInstance(registry.first(factoryServiceTypeLookup).orElseThrow()), is(true));
        List<ServiceInstance<Object>> factoryServiceTypeInstances = registry.lookupInstances(factoryServiceTypeLookup);
        assertThat(factoryServiceTypeInstances, hasSize(1));
        assertThat(providerType.isInstance(factoryServiceTypeInstances.getFirst().get()), is(true));
        assertThat(factoryServiceTypeInstances.getFirst()
                           .contracts()
                           .contains(ResolvedType.create(providerType)),
                   is(true));
    }

    private static void assertViolation(ValidationException result,
                                        Class<?> serviceType,
                                        List<PathElement> expectedLocation,
                                        String expectedMessage,
                                        Object expectedInvalidValue,
                                        TypeName expectedAnnotation) {
        var violations = result.violations();

        assertThat(violations, hasSize(1));
        var violation = violations.getFirst();

        assertThat(violation.location(), contains(expectedLocation.toArray(PathElement[]::new)));
        assertThat(violation.message(), containsString(expectedMessage));
        assertThat(violation.invalidValue(), is(expectedInvalidValue));
        assertThat(violation.rootType(), sameInstance(serviceType));
        assertThat(violation.annotation().typeName(), is(expectedAnnotation));
    }
}
