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
import java.util.Set;
import java.util.function.Function;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
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

        Set<ResolvedType> directContracts = new HashSet<>();
        Set<ResolvedType> providedContracts = new HashSet<>();
        ProviderType providerType = ProviderType.SERVICE;
        TypeName qualifiedProviderQualifier = null;
        TypeInfo providedTypeInfo = null;
        TypeName providedTypeName = null;

        Set<TypeName> eligibleExternalContracts = new HashSet<>();
        // gather eligible external contracts
        eligibleContracts(eligibleExternalContracts,
                          new HashSet<>(),
                          serviceTypeInfo);

        Function<TypeInfo, Boolean> isEligibleInfo = typeInfo -> isEligible(
                onlyAnnotatedAreEligible,
                eligibleExternalContracts,
                typeInfo);

        Function<TypeName, Boolean> isEligibleType = typeName -> isEligible(
                roundContext,
                onlyAnnotatedAreEligible,
                eligibleExternalContracts,
                typeName);

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
                                         isEligibleInfo,
                                         isEligibleType,
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
                                     isEligibleInfo,
                                     isEligibleType,
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
                                     isEligibleInfo,
                                     isEligibleType,
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
                                              isEligibleInfo,
                                              isEligibleType);
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

        addContracts(roundContext,
                     directContracts,
                     isEligibleInfo,
                     isEligibleType,
                     new HashSet<>(),
                     serviceTypeInfo);

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

    private static boolean isEligible(RegistryRoundContext ctx,
                                      boolean onlyAnnotatedAreEligible,
                                      Set<TypeName> eligibleExternalContracts,
                                      TypeName toCheck) {
        if (eligibleExternalContracts.contains(toCheck)) {
            return true;
        }
        // if the type info does not exist on classpath, and is not an external contract, return false
        return ctx.typeInfo(toCheck)
                .map(it -> isEligible(onlyAnnotatedAreEligible, eligibleExternalContracts, it))
                .orElse(false);
    }

    private static boolean isEligible(boolean onlyAnnotatedAreEligible,
                                      Set<TypeName> eligibleExternalContracts,
                                      TypeInfo toCheck) {
        if (eligibleExternalContracts.contains(toCheck.typeName())) {
            return true;
        }
        if (toCheck.hasAnnotation(SERVICE_ANNOTATION_CONTRACT)) {
            return true;
        }
        if (onlyAnnotatedAreEligible) {
            return false;
        }
        return toCheck.kind() == ElementKind.INTERFACE;
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

    /**
     * Gather eligible external contracts from this type and super types,
     * all other contracts are validated when encountered (and either onlyAnnotatedAreEligible is set to false,
     * or they have to be annotated with Contract).
     */
    private static void eligibleContracts(Set<TypeName> eligibleContracts,
                                          Set<String> processedFullyQualified,
                                          TypeInfo typeInfo) {

        if (!processedFullyQualified.add(typeInfo.typeName().resolvedName())) {
            // this type was already fully processed
            return;
        }

        addExternalContracts(eligibleContracts, typeInfo);

        // super type
        typeInfo.superTypeInfo()
                .ifPresent(it -> eligibleContracts(eligibleContracts, processedFullyQualified, it));

        // interfaces
        typeInfo.interfaceTypeInfo()
                .forEach(it -> eligibleContracts(eligibleContracts, processedFullyQualified, it));
    }

    private static ProviderAnalysis providerContracts(RegistryRoundContext ctx,
                                                      TypeInfo serviceTypeInfo,
                                                      Map<TypeName, TypeInfo> implementedInterfaceTypes,
                                                      Function<TypeInfo, Boolean> isEligibleInfo,
                                                      Function<TypeName, Boolean> isEligibleType,
                                                      TypeName providerInterface) {
        TypeInfo providerInfo = implementedInterfaceTypes.remove(providerInterface);
        if (providerInfo == null) {
            return new ProviderAnalysis();
        }

        TypeName contract = requiredTypeArgument(providerInfo);
        Set<ResolvedType> contracts = new HashSet<>();
        contracts.add(ResolvedType.create(contract));
        TypeInfo contractInfo = contractInfo(ctx, serviceTypeInfo, contract);

        addContracts(ctx,
                     contracts,
                     isEligibleInfo,
                     isEligibleType,
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
                                                               Function<TypeInfo, Boolean> isEligibleInfo,
                                                               Function<TypeName, Boolean> isEligibleType) {
        TypeInfo providerInfo = implementedInterfaceTypes.remove(INJECTION_QUALIFIED_PROVIDER);
        if (providerInfo == null) {
            return new ProviderAnalysis();
        }
        TypeName contract = requiredTypeArgument(providerInfo);
        TypeName qualifiedProviderQualifier = requiredTypeArgument(providerInfo, 1);
        Set<ResolvedType> contracts = new LinkedHashSet<>();
        contracts.add(ResolvedType.create(contract));

        TypeInfo providedInfo = contractInfo(ctx, serviceTypeInfo, contract);
        addContracts(ctx,
                     contracts,
                     isEligibleInfo,
                     isEligibleType,
                     new HashSet<>(),
                     providedInfo);

        return new ProviderAnalysis(contract,
                                    providedInfo,
                                    contracts,
                                    qualifiedProviderQualifier);
    }

    private static void addContracts(RegistryRoundContext ctx,
                                     Set<ResolvedType> contractSet,
                                     Function<TypeInfo, Boolean> isEligibleInfo,
                                     Function<TypeName, Boolean> isEligibleType,
                                     Set<ResolvedType> processed,
                                     TypeInfo typeInfo) {

        TypeName withGenerics = typeInfo.typeName();
        ResolvedType resolvedType = ResolvedType.create(withGenerics);

        if (!processed.add(resolvedType)) {
            // this type was already fully processed
            return;
        }

        if (!resolvedType.typeArguments().isEmpty()) {
            // we also need to add a contract for the type it implements
            // i.e. if this is Circle<Green>, we may want to add Circle<Color> as well
            ctx.typeInfo(withGenerics.genericTypeName())
                    .ifPresent(declaration -> {
                        TypeName tn = declaration.typeName();
                        for (int i = 0; i < withGenerics.typeArguments().size(); i++) {
                            TypeName declared = tn.typeArguments().get(i);
                            if (declared.generic()) {
                                // this is not ideal (this could be T extends Circle)
                                var asString = declared.toString();
                                int index = asString.indexOf(" extends ");
                                if (index != -1) {
                                    TypeName extendedType = TypeName.create(asString.substring(index + 9));
                                    if (isEligibleType.apply(extendedType)) {
                                        contractSet.add(ResolvedType.create(
                                                TypeName.builder()
                                                        .from(withGenerics)
                                                        .typeArguments(List.of(extendedType))
                                                        .build()));
                                    }
                                }
                            } else {
                                contractSet.add(ResolvedType.create(declared));
                            }
                        }
                    });
        }

        // add this type if eligible
        if (isEligibleInfo.apply(typeInfo)) {
            contractSet.add(ResolvedType.create(withGenerics));
        }

        // super type
        typeInfo.superTypeInfo()
                .ifPresent(it -> addContracts(
                        ctx,
                        contractSet,
                        isEligibleInfo,
                        isEligibleType,
                        processed,
                        it
                ));

        // interfaces
        typeInfo.interfaceTypeInfo()
                .forEach(it -> addContracts(
                        ctx,
                        contractSet,
                        isEligibleInfo,
                        isEligibleType,
                        processed,
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
                                    Set<ResolvedType> providedContracts,
                                    TypeName qualifiedProviderQualifier) {
        private ProviderAnalysis() {
            this(false, null, null, null, null);
        }

        private ProviderAnalysis(TypeName providedType, TypeInfo providedTypeInfo, Set<ResolvedType> providedContracts) {
            this(true, providedType, providedTypeInfo, providedContracts, null);
        }

        private ProviderAnalysis(TypeName providedType,
                                 TypeInfo providedTypeInfo,
                                 Set<ResolvedType> providedContracts,
                                 TypeName qualifiedProviderQualifier) {
            this(true, providedType, providedTypeInfo, providedContracts, qualifiedProviderQualifier);
        }
    }
}
