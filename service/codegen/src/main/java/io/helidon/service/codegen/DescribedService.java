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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.service.codegen.InjectCodegenTypes.INJECTION_NAMED;
import static io.helidon.service.codegen.InjectCodegenTypes.INJECTION_PER_INSTANCE;
import static io.helidon.service.codegen.InjectCodegenTypes.INJECTION_POINT_PROVIDER;
import static io.helidon.service.codegen.InjectCodegenTypes.INJECTION_QUALIFIED_PROVIDER;
import static io.helidon.service.codegen.InjectCodegenTypes.INJECTION_SERVICES_PROVIDER;
import static io.helidon.service.codegen.InjectCodegenTypes.INTERCEPT_G_WRAPPER_IP_PROVIDER;
import static io.helidon.service.codegen.InjectCodegenTypes.INTERCEPT_G_WRAPPER_QUALIFIED_PROVIDER;
import static io.helidon.service.codegen.InjectCodegenTypes.INTERCEPT_G_WRAPPER_SERVICES_PROVIDER;
import static io.helidon.service.codegen.InjectCodegenTypes.INTERCEPT_G_WRAPPER_SUPPLIER_PROVIDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_CONTRACT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_EXTERNAL_CONTRACTS;

/**
 * A service (as declared and annotated with a scope by the user).
 * It may be a service provider (if it implements one of the provider interfaces), or it is a contract
 * implementation on its own.
 */
class DescribedService {
    private static final Annotation WILDCARD_NAMED = Annotation.create(INJECTION_NAMED, "*");
    private static final TypeInfo OBJECT_INFO = TypeInfo.builder()
            .typeName(TypeNames.OBJECT)
            .kind(ElementKind.CLASS)
            .accessModifier(AccessModifier.PUBLIC)
            .build();

    private final DescribedType serviceType;
    private final DescribedType providedType;

    /*
     the following is only relevant on service itself (not on provided type)
     */
    // type of provider (if this is a provider at all)
    private final ProviderType providerType;
    // qualifiers of provided type are inherited from service
    private final Set<Annotation> qualifiers;
    // provided type does not have a descriptor, only service does
    private final TypeName descriptorType;
    // scope of provided type is inherited from the service
    private final TypeName scope;
    // required for descriptor generation
    private final SuperType superType;
    // in case this service is a qualified provider, we also get the qualifier it handles
    private final TypeName qualifiedProviderQualifier;

    private DescribedService(DescribedType serviceType,
                             DescribedType providedType,
                             SuperType superType,
                             TypeName scope,
                             TypeName descriptorType,
                             Set<Annotation> qualifiers,
                             ProviderType providerType,
                             TypeName qualifiedProviderQualifier) {

        this.serviceType = serviceType;
        this.providedType = providedType;
        this.superType = superType;
        this.descriptorType = descriptorType;
        this.scope = scope;
        this.qualifiers = Set.copyOf(qualifiers);
        this.providerType = providerType;
        this.qualifiedProviderQualifier = qualifiedProviderQualifier;
    }

    static DescribedService create(RegistryCodegenContext ctx,
                                   RegistryRoundContext roundContext,
                                   Interception interception,
                                   TypeInfo serviceTypeInfo,
                                   SuperType superType,
                                   TypeName scope,
                                   boolean onlyAnnotatedAreEligible) {
        TypeName serviceType = serviceTypeInfo.typeName();
        TypeName descriptorType = ctx.descriptorType(serviceType);

        Set<TypeName> directContracts = new HashSet<>();
        Set<TypeName> providedContracts = new HashSet<>();
        ProviderType providerType = ProviderType.SERVICE;
        TypeName qualifiedProviderQualifier = null;
        TypeInfo providedTypeInfo = null;
        TypeName providedTypeName = null;

        Set<TypeName> eligibleContracts = new HashSet<>();
        Set<String> processedFullyQualified = new HashSet<>();
        // now navigate the whole hierarchy to discover external contracts and contracts
        eligibleContracts(ctx,
                          onlyAnnotatedAreEligible,
                          eligibleContracts,
                          processedFullyQualified,
                          serviceTypeInfo);

        // remove special cases (these are not user contracts)
        eligibleContracts.remove(TypeNames.SUPPLIER);
        eligibleContracts.remove(INJECTION_SERVICES_PROVIDER);
        eligibleContracts.remove(INJECTION_POINT_PROVIDER);
        eligibleContracts.remove(INJECTION_QUALIFIED_PROVIDER);

        // now we know which contracts are OK to use, and we can check the service types and real contracts
        // service is a provider only if it implements the interface directly; this is never inherited
        List<TypeInfo> typeInfos = serviceTypeInfo.interfaceTypeInfo();
        Map<TypeName, TypeInfo> implementedInterfaceTypes = new HashMap<>();
        typeInfos.forEach(it -> implementedInterfaceTypes.put(it.typeName(), it));

        /*
        For each service type we support, gather contracts
         */
        var response = providerContracts(roundContext,
                                         serviceTypeInfo,
                                         implementedInterfaceTypes,
                                         eligibleContracts,
                                         TypeNames.SUPPLIER);
        if (response.valid) {
            providerType = ProviderType.SUPPLIER;
            providedContracts.addAll(response.providedContracts());
            qualifiedProviderQualifier = response.qualifiedProviderQualifier();
            providedTypeName = response.providedType();
            providedTypeInfo = response.providedTypeInfo();
        }
        response = providerContracts(roundContext,
                                     serviceTypeInfo,
                                     implementedInterfaceTypes,
                                     eligibleContracts,
                                     INJECTION_SERVICES_PROVIDER);
        if (response.valid) {
            // if this is not a service type, throw
            if (providerType != ProviderType.SERVICE) {
                throw new CodegenException("Service implements more than one provider type: "
                                                   + providerType + ", and services provider.",
                                           serviceTypeInfo.originatingElementValue());
            }
            providerType = ProviderType.SERVICES_PROVIDER;
            providedContracts.addAll(response.providedContracts());
            qualifiedProviderQualifier = response.qualifiedProviderQualifier();
            providedTypeName = response.providedType();
            providedTypeInfo = response.providedTypeInfo();
        }
        response = providerContracts(roundContext,
                                     serviceTypeInfo,
                                     implementedInterfaceTypes,
                                     eligibleContracts,
                                     INJECTION_POINT_PROVIDER);
        if (response.valid) {
            // if this is not a service type, throw
            if (providerType != ProviderType.SERVICE) {
                throw new CodegenException("Service implements more than one provider type: "
                                                   + providerType + ", and injection point provider.",
                                           serviceTypeInfo.originatingElementValue());
            }
            providerType = ProviderType.IP_PROVIDER;
            providedContracts.addAll(response.providedContracts());
            qualifiedProviderQualifier = response.qualifiedProviderQualifier();
            providedTypeName = response.providedType();
            providedTypeInfo = response.providedTypeInfo();
        }
        response = qualifiedProviderContracts(roundContext,
                                              serviceTypeInfo,
                                              implementedInterfaceTypes,
                                              eligibleContracts);
        if (response.valid()) {
            // if this is not a service type, throw
            if (providerType != ProviderType.SERVICE) {
                throw new CodegenException("Service implements more than one provider type: "
                                                   + providerType + ", and qualified provider.",
                                           serviceTypeInfo.originatingElementValue());
            }
            providerType = ProviderType.QUALIFIED_PROVIDER;
            providedContracts.addAll(response.providedContracts());
            qualifiedProviderQualifier = response.qualifiedProviderQualifier();
            providedTypeName = response.providedType();
            providedTypeInfo = response.providedTypeInfo();
        }

        addContracts(directContracts, eligibleContracts, new HashSet<>(), serviceTypeInfo);

        for (TypeInfo contract : implementedInterfaceTypes.values()) {
            addContracts(directContracts, eligibleContracts, new HashSet<>(), contract);
        }

        DescribedType serviceDescriptor;
        DescribedType providedDescriptor;

        if (providerType == ProviderType.SERVICE) {
            var serviceElements = DescribedElements.create(ctx, interception, directContracts, serviceTypeInfo);
            serviceDescriptor = new DescribedType(serviceTypeInfo,
                                                  serviceTypeInfo.typeName(),
                                                  directContracts,
                                                  serviceElements);

            providedDescriptor = null;
        } else {
            var serviceElements = DescribedElements.create(ctx, interception, Set.of(), serviceTypeInfo);
            serviceDescriptor = new DescribedType(serviceTypeInfo,
                                                  serviceTypeInfo.typeName(),
                                                  directContracts,
                                                  serviceElements);
            DescribedElements providedElements = DescribedElements.create(ctx, interception, providedContracts, providedTypeInfo);

            providedDescriptor = new DescribedType(providedTypeInfo,
                                                   providedTypeName,
                                                   providedContracts,
                                                   providedElements);
        }

        return new DescribedService(
                serviceDescriptor,
                providedDescriptor,
                superType,
                scope,
                descriptorType,
                gatherQualifiers(serviceTypeInfo),
                providerType,
                qualifiedProviderQualifier
        );
    }

    @Override
    public String toString() {
        return serviceType.typeName().fqName();
    }

    TypeName interceptionWrapperSuperType() {
        return switch (providerType()) {
            case NONE, SERVICE -> serviceType.typeName();
            case SUPPLIER -> createType(INTERCEPT_G_WRAPPER_SUPPLIER_PROVIDER, providedType.typeName());
            case SERVICES_PROVIDER -> createType(INTERCEPT_G_WRAPPER_SERVICES_PROVIDER, providedType.typeName());
            case IP_PROVIDER -> createType(INTERCEPT_G_WRAPPER_IP_PROVIDER, providedType.typeName());
            case QUALIFIED_PROVIDER ->
                    createType(INTERCEPT_G_WRAPPER_QUALIFIED_PROVIDER, providedType.typeName(), qualifiedProviderQualifier);
        };
    }

    TypeName providerInterface() {
        return switch (providerType()) {
            case NONE, SERVICE -> serviceType.typeName();
            case SUPPLIER -> createType(TypeNames.SUPPLIER, providedType.typeName());
            case SERVICES_PROVIDER -> createType(INJECTION_SERVICES_PROVIDER, providedType.typeName());
            case IP_PROVIDER -> createType(INJECTION_POINT_PROVIDER, providedType.typeName());
            case QUALIFIED_PROVIDER ->
                    createType(INJECTION_QUALIFIED_PROVIDER, providedType.typeName(), qualifiedProviderQualifier);
        };
    }

    boolean isProvider() {
        return providerType() != ProviderType.SERVICE && providerType() != ProviderType.NONE;
    }

    DescribedType providedDescriptor() {
        return providedType;
    }

    DescribedType serviceDescriptor() {
        return serviceType;
    }

    SuperType superType() {
        return superType;
    }

    TypeName descriptorType() {
        return descriptorType;
    }

    TypeName scope() {
        return scope;
    }

    Set<Annotation> qualifiers() {
        return Set.copyOf(qualifiers);
    }

    ProviderType providerType() {
        return providerType;
    }

    TypeName qualifiedProviderQualifier() {
        return qualifiedProviderQualifier;
    }

    private static TypeName createType(TypeName... types) {
        TypeName.Builder builder = TypeName.builder()
                .from(types[0]);

        for (int i = 1; i < types.length; i++) {
            builder.addTypeArgument(types[i]);
        }

        return builder.build();
    }

    private static Set<Annotation> gatherQualifiers(TypeInfo serviceTypeInfo) {
        Set<Annotation> qualifiers = new LinkedHashSet<>();
        if (serviceTypeInfo.hasAnnotation(INJECTION_PER_INSTANCE)) {
            qualifiers.add(WILDCARD_NAMED);
        }

        for (Annotation annotation : serviceTypeInfo.annotations()) {
            if (serviceTypeInfo.hasMetaAnnotation(annotation.typeName(),
                                                  InjectCodegenTypes.INJECTION_QUALIFIER)) {
                qualifiers.add(annotation);
            }
        }
        return qualifiers;
    }

    private static void eligibleContracts(CodegenContext ctx,
                                          boolean onlyAnnotatedAreEligible,
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
                || withoutGenerics.equals(INJECTION_SERVICES_PROVIDER)) {

            // this may be the interface itself, and then it does not have a type argument
            if (!withGenerics.typeArguments().isEmpty()) {
                // provider must have a type argument (and the type argument is an automatic contract
                TypeName contract = withGenerics.typeArguments().getFirst();
                if (!contract.generic()) {
                    Optional<TypeInfo> providedTypeInfo = ctx.typeInfo(contract);
                    if (providedTypeInfo.isPresent()) {
                        eligibleContracts(
                                ctx,
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
        } else if (withoutGenerics.equals(INJECTION_QUALIFIED_PROVIDER)) {
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
            eligibleContracts.add(INJECTION_QUALIFIED_PROVIDER);
            if (TypeNames.OBJECT.equals(providedType)) {
                eligibleContracts.add(TypeNames.OBJECT);
            } else {
                Optional<TypeInfo> providedTypeInfo = ctx.typeInfo(providedType);
                eligibleContracts.add(providedType);
                if (providedTypeInfo.isPresent()) {
                    eligibleContracts(ctx,
                                      onlyAnnotatedAreEligible,
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
                        ctx,
                        onlyAnnotatedAreEligible,
                        eligibleContracts,
                        processedFullyQualified,
                        it
                ));

        // interfaces
        typeInfo.interfaceTypeInfo()
                .forEach(it -> eligibleContracts(
                        ctx,
                        onlyAnnotatedAreEligible,
                        eligibleContracts,
                        processedFullyQualified,
                        it
                ));
    }

    private static ProviderAnalysis providerContracts(RegistryRoundContext ctx,
                                                      TypeInfo serviceTypeInfo,
                                                      Map<TypeName, TypeInfo> implementedInterfaceTypes,
                                                      Set<TypeName> eligibleContracts,
                                                      TypeName providerInterface) {
        TypeInfo providerInfo = implementedInterfaceTypes.remove(providerInterface);
        if (providerInfo == null) {
            return new ProviderAnalysis();
        }

        TypeName contract = requiredTypeArgument(providerInfo);
        Set<TypeName> contracts = new HashSet<>();
        contracts.add(contract);
        TypeInfo contractInfo = contractInfo(ctx, serviceTypeInfo, contract);

        addContracts(contracts,
                     eligibleContracts,
                     new HashSet<>(),
                     contractInfo);
        return new ProviderAnalysis(contract,
                                    contractInfo,
                                    contracts);
    }

    private static TypeInfo contractInfo(RegistryRoundContext ctx, TypeInfo serviceTypeInfo, TypeName contract) {
        if (TypeNames.OBJECT.equals(contract)) {
            return OBJECT_INFO;
        }
        var typeInfo = ctx.typeInfo(contract);
        if (typeInfo.isPresent()) {
            return typeInfo.get();
        }
        throw new CodegenException("Failed to discover type info for " + contract.fqName(),
                                   serviceTypeInfo.originatingElementValue());
    }

    private static void addExternalContracts(Set<TypeName> eligibleContracts, TypeInfo typeInfo) {
        typeInfo.findAnnotation(SERVICE_ANNOTATION_EXTERNAL_CONTRACTS)
                .flatMap(Annotation::typeValues)
                .ifPresent(eligibleContracts::addAll);
    }

    private static ProviderAnalysis qualifiedProviderContracts(RegistryRoundContext ctx,
                                                               TypeInfo serviceTypeInfo,
                                                               Map<TypeName, TypeInfo> implementedInterfaceTypes,
                                                               Set<TypeName> eligibleContracts) {
        TypeInfo providerInfo = implementedInterfaceTypes.remove(INJECTION_QUALIFIED_PROVIDER);
        if (providerInfo == null) {
            return new ProviderAnalysis();
        }
        TypeName contract = requiredTypeArgument(providerInfo);
        TypeName qualifiedProviderQualifier = requiredTypeArgument(providerInfo, 1);
        Set<TypeName> contracts = new LinkedHashSet<>();
        contracts.add(contract);

        TypeInfo providedInfo = contractInfo(ctx, serviceTypeInfo, contract);
        addContracts(contracts,
                     eligibleContracts,
                     new HashSet<>(),
                     providedInfo);

        return new ProviderAnalysis(contract,
                                    providedInfo,
                                    contracts,
                                    qualifiedProviderQualifier);
    }

    private static void addContracts(Set<TypeName> contractSet,
                                     Set<TypeName> eligibleContracts,
                                     Set<String> processedFullyQualified,
                                     TypeInfo typeInfo) {

        TypeName withGenerics = typeInfo.typeName();

        if (!processedFullyQualified.add(withGenerics.resolvedName())) {
            // this type was already fully processed
            return;
        }

        // add this type if eligible
        if (eligibleContracts.contains(withGenerics)) {
            contractSet.add(withGenerics);
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

    private static TypeName requiredTypeArgument(TypeInfo typeInfo) {
        return requiredTypeArgument(typeInfo, 0);
    }

    private static TypeName requiredTypeArgument(TypeInfo typeInfo, int index) {
        TypeName withGenerics = typeInfo.typeName();
        List<TypeName> typeArguments = withGenerics.typeArguments();
        if (typeArguments.isEmpty()) {
            throw new CodegenException("Type arguments cannot be empty for implemented interface " + withGenerics,
                                       typeInfo.originatingElementValue());
        }
        if (typeArguments.size() < (index + 1)) {
            throw new CodegenException("There must be at least " + (index + 1) + " type arguments for implemented interface "
                                               + withGenerics.resolvedName(),
                                       typeInfo.originatingElementValue());
        }

        // provider must have a type argument (and the type argument is an automatic contract
        TypeName contract = typeArguments.get(index);
        if (contract.generic()) {
            // probably just T (such as Supplier<T>)
            throw new CodegenException("Type argument must be a concrete type for implemented interface " + withGenerics,
                                       typeInfo.originatingElementValue());
        }
        return contract;
    }

    private record ProviderAnalysis(boolean valid,
                                    TypeName providedType,
                                    TypeInfo providedTypeInfo,
                                    Set<TypeName> providedContracts,
                                    TypeName qualifiedProviderQualifier) {
        private ProviderAnalysis() {
            this(false, null, null, null, null);
        }

        private ProviderAnalysis(TypeName providedType, TypeInfo providedTypeInfo, Set<TypeName> providedContracts) {
            this(true, providedType, providedTypeInfo, providedContracts, null);
        }

        private ProviderAnalysis(TypeName providedType,
                                 TypeInfo providedTypeInfo,
                                 Set<TypeName> providedContracts,
                                 TypeName qualifiedProviderQualifier) {
            this(true, providedType, providedTypeInfo, providedContracts, qualifiedProviderQualifier);
        }
    }
}
