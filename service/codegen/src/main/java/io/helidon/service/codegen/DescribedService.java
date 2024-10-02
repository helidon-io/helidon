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

package io.helidon.service.codegen;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_POINT_PROVIDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.QUALIFIED_PROVIDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICES_PROVIDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_CONTRACT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_EXTERNAL_CONTRACTS;

class DescribedService {
    private final CodegenContext ctx;
    private final TypeInfo serviceTypeInfo;
    private final TypeName serviceType;

    private final Set<ProviderType> serviceTypes = EnumSet.noneOf(ProviderType.class);
    private final Set<TypeName> directContracts = new HashSet<>();
    private final Set<TypeName> supplierContracts = new HashSet<>();
    private final Set<TypeName> servicesProviderContracts = new HashSet<>();
    private final Set<TypeName> injectionPointProviderContracts = new HashSet<>();
    private final Set<TypeName> qualifiedProviderContracts = new HashSet<>();

    private TypeName qualifiedProviderQualifier;

    DescribedService(CodegenContext ctx, TypeInfo serviceTypeInfo) {
        this.ctx = ctx;
        this.serviceTypeInfo = serviceTypeInfo;
        this.serviceType = serviceTypeInfo.typeName();
    }

    void analyze(boolean onlyAnnotatedAreEligible) {
        Set<TypeName> eligibleContracts = new HashSet<>();
        Set<String> processedFullyQualified = new HashSet<>();
        // now navigate the whole hierarchy to discover external contracts and contracts
        eligibleContracts(onlyAnnotatedAreEligible,
                          eligibleContracts,
                          processedFullyQualified,
                          serviceTypeInfo);

        // remove special cases (there are not user contracts, we add them explicitly
        eligibleContracts.remove(TypeNames.SUPPLIER);
        eligibleContracts.remove(SERVICES_PROVIDER);
        eligibleContracts.remove(INJECTION_POINT_PROVIDER);
        eligibleContracts.remove(QUALIFIED_PROVIDER);

        // now we know which contracts are OK to use, and we can check the service types and real contracts
        // service is a provider only if it implements the interface directly; this is never inherited
        List<TypeInfo> typeInfos = serviceTypeInfo.interfaceTypeInfo();
        Map<TypeName, TypeInfo> implementedInterfaceTypes = new HashMap<>();
        typeInfos.forEach(it -> implementedInterfaceTypes.put(it.typeName(), it));

        /*
        For each service type we support, gather contracts
         */
        providerContracts(implementedInterfaceTypes,
                          eligibleContracts,
                          supplierContracts,
                          TypeNames.SUPPLIER,
                          ProviderType.SUPPLIER);
        providerContracts(implementedInterfaceTypes,
                          eligibleContracts,
                          servicesProviderContracts,
                          SERVICES_PROVIDER,
                          ProviderType.SERVICES_PROVIDER);
        providerContracts(implementedInterfaceTypes,
                          eligibleContracts,
                          injectionPointProviderContracts,
                          INJECTION_POINT_PROVIDER,
                          ProviderType.IP_PROVIDER);
        qualifiedProviderContracts(implementedInterfaceTypes, eligibleContracts);

        // all interfaces left in the implementedInterfaceTypes are now actual contracts (probably)
        if (!implementedInterfaceTypes.isEmpty()) {
            serviceTypes.add(ProviderType.SERVICE);
        }

        addContracts(directContracts, eligibleContracts, new HashSet<>(), serviceTypeInfo);

        for (TypeInfo contract : implementedInterfaceTypes.values()) {
            addContracts(directContracts, eligibleContracts, new HashSet<>(), contract);
        }

        if (serviceTypes.isEmpty()) {
            serviceTypes.add(ProviderType.SERVICE);
        } else if (serviceTypes.size() > 1) {
            throw new CodegenException("Multiple service types found: " + serviceTypes
                                               + ", we currently only support a single service type per service class",
                                       serviceTypeInfo.originatingElementValue());
        }
    }

    TypeName serviceTypeName() {
        return serviceType;
    }

    ProviderType providerType() {
        return serviceTypes.stream().findFirst().orElseThrow(
                () -> new CodegenException("Service types not initialized, analyze(boolean) must be call first")
        );
    }

    Set<TypeName> contracts(ProviderType type) {
        return switch (type) {
            case NONE -> Set.of();
            case SERVICE -> directContracts;
            case SUPPLIER -> supplierContracts;
            case SERVICES_PROVIDER -> servicesProviderContracts;
            case IP_PROVIDER -> injectionPointProviderContracts;
            case QUALIFIED_PROVIDER -> qualifiedProviderContracts;
        };
    }

    TypeName qualifiedProviderQualifier() {
        return qualifiedProviderQualifier;
    }

    private void qualifiedProviderContracts(Map<TypeName, TypeInfo> implementedInterfaceTypes, Set<TypeName> eligibleContracts) {
        TypeInfo providerInfo = implementedInterfaceTypes.remove(QUALIFIED_PROVIDER);
        if (providerInfo == null) {
            return;
        }
        serviceTypes.add(ProviderType.QUALIFIED_PROVIDER);
        qualifiedProviderQualifier = requiredTypeArgument(providerInfo);
        TypeName contract = requiredTypeArgument(providerInfo, 1);
        qualifiedProviderContracts.add(contract);
        ctx.typeInfo(contract)
                .ifPresent(typeInfo ->
                                   addContracts(qualifiedProviderContracts,
                                                eligibleContracts,
                                                new HashSet<>(),
                                                typeInfo));
    }

    private void providerContracts(Map<TypeName, TypeInfo> implementedInterfaceTypes,
                                   Set<TypeName> eligibleContracts,
                                   Set<TypeName> providerSpecificContracts,
                                   TypeName providerInterface,
                                   ProviderType serviceType) {
        TypeInfo providerInfo = implementedInterfaceTypes.remove(providerInterface);
        if (providerInfo == null) {
            return;
        }
        serviceTypes.add(serviceType);
        TypeName contract = requiredTypeArgument(providerInfo);
        providerSpecificContracts.add(contract);
        ctx.typeInfo(contract)
                .ifPresent(typeInfo ->
                                   addContracts(providerSpecificContracts,
                                                eligibleContracts,
                                                new HashSet<>(),
                                                typeInfo));
    }

    private void addContracts(Set<TypeName> contractSet,
                              Set<TypeName> eligibleContracts,
                              Set<String> processedFullyQualified,
                              TypeInfo typeInfo) {

        TypeName withGenerics = typeInfo.typeName();
        TypeName withoutGenerics = withGenerics.genericTypeName();

        if (!processedFullyQualified.add(withGenerics.resolvedName())) {
            // this type was already fully processed
            return;
        }

        // add this type if eligible
        if (eligibleContracts.contains(withGenerics)) {
            contractSet.add(withoutGenerics);
        }

        // super type
        typeInfo.superTypeInfo()
                .ifPresent(it -> addContracts(
                        contractSet,
                        eligibleContracts,
                        processedFullyQualified,
                        it
                ));

        // interfaces
        typeInfo.interfaceTypeInfo()
                .forEach(it -> addContracts(
                        contractSet,
                        eligibleContracts,
                        processedFullyQualified,
                        it
                ));
    }

    private void eligibleContracts(boolean onlyAnnotatedAreEligible,
                                   Set<TypeName> eligibleContracts,
                                   Set<String> processedFullyQualified,
                                   TypeInfo typeInfo) {
        TypeName withGenerics = typeInfo.typeName();
        TypeName withoutGenerics = withGenerics.genericTypeName();

        if (!processedFullyQualified.add(withGenerics.resolvedName())) {
            // this type was already fully processed
            return;
        }

        // add this type, if annotated with @Contract
        if (onlyAnnotatedAreEligible && typeInfo.hasAnnotation(SERVICE_ANNOTATION_CONTRACT)) {
            eligibleContracts.add(typeInfo.typeName());
        } else {
            if (typeInfo.kind() == ElementKind.INTERFACE) {
                eligibleContracts.add(withoutGenerics);
            }
        }

        // add external contracts annotated on this type
        addExternalContracts(eligibleContracts, typeInfo);

        // specific cases
        if (withGenerics.isSupplier()
                || withoutGenerics.equals(INJECTION_POINT_PROVIDER)
                || withoutGenerics.equals(SERVICES_PROVIDER)) {

            // this may be the interface itself, and then it does not have a type argument
            if (!withGenerics.typeArguments().isEmpty()) {
                // provider must have a type argument (and the type argument is an automatic contract
                TypeName contract = withGenerics.typeArguments().getFirst();
                if (!contract.generic()) {
                    Optional<TypeInfo> providedTypeInfo = ctx.typeInfo(contract);
                    if (providedTypeInfo.isPresent()) {
                        eligibleContracts(
                                onlyAnnotatedAreEligible,
                                eligibleContracts,
                                processedFullyQualified,
                                providedTypeInfo.get());
                    } else {
                        eligibleContracts.add(contract);
                        processedFullyQualified.add(contract.resolvedName());
                    }
                }
            }
        } else if (withoutGenerics.equals(QUALIFIED_PROVIDER)) {
            // a very special case
            if (withGenerics.typeArguments().isEmpty()) {
                // this is the QualifiedProvider interface itself, no need to do anything
                return;
            }
            TypeName providedType = withGenerics.typeArguments().get(1); // second type
            if (providedType.generic()) {
                // just a <T> or similar
                return;
            }
            eligibleContracts.add(QUALIFIED_PROVIDER);
            if (TypeNames.OBJECT.equals(providedType)) {
                eligibleContracts.add(TypeNames.OBJECT);
            } else {
                Optional<TypeInfo> providedTypeInfo = ctx.typeInfo(providedType);
                eligibleContracts.add(providedType);
                if (providedTypeInfo.isPresent()) {
                    eligibleContracts(onlyAnnotatedAreEligible,
                                      eligibleContracts,
                                      processedFullyQualified,
                                      providedTypeInfo.get());
                } else {
                    processedFullyQualified.add(providedType.resolvedName());
                }
            }
        }

        // super type
        typeInfo.superTypeInfo()
                .ifPresent(it -> eligibleContracts(
                        onlyAnnotatedAreEligible,
                        eligibleContracts,
                        processedFullyQualified,
                        it
                ));

        // interfaces
        typeInfo.interfaceTypeInfo()
                .forEach(it -> eligibleContracts(
                        onlyAnnotatedAreEligible,
                        eligibleContracts,
                        processedFullyQualified,
                        it
                ));
    }

    private void addExternalContracts(Set<TypeName> eligibleContracts, TypeInfo typeInfo) {
        typeInfo.findAnnotation(SERVICE_ANNOTATION_EXTERNAL_CONTRACTS)
                .flatMap(Annotation::typeValues)
                .ifPresent(eligibleContracts::addAll);
    }

    private TypeName requiredTypeArgument(TypeInfo typeInfo) {
        return requiredTypeArgument(typeInfo, 0);
    }

    private TypeName requiredTypeArgument(TypeInfo typeInfo, int index) {
        TypeName withGenerics = typeInfo.typeName();
        List<TypeName> typeArguments = withGenerics.typeArguments();
        if (typeArguments.isEmpty()) {
            throw new CodegenException("Type arguments cannot be empty for implemented interface " + withGenerics,
                                       serviceTypeInfo.originatingElementValue());
        }
        if (typeArguments.size() < (index + 1)) {
            throw new CodegenException("There must be at least " + (index + 1) + " type arguments for implemented interface "
                                               + withGenerics.resolvedName(),
                                       serviceTypeInfo.originatingElementValue());
        }

        // provider must have a type argument (and the type argument is an automatic contract
        TypeName contract = typeArguments.get(index);
        if (contract.generic()) {
            // probably just T (such as Supplier<T>)
            throw new CodegenException("Type argument must be a concrete type for implemented interface " + withGenerics,
                                       serviceTypeInfo.originatingElementValue());
        }
        return contract;
    }
}
