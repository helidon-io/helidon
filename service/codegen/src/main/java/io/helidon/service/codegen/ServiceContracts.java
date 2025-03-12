/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_CONTRACT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_EXTERNAL_CONTRACTS;

/**
 * Handling of eligible contracts.
 * <p>
 * Contract is eligible if it is annotated with {@code Service.Contract}, or referenced
 * from service type or its super types via {@code Service.ExternalContracts}.
 * <p>
 * In case the option {@link io.helidon.service.codegen.ServiceOptions#AUTO_ADD_NON_CONTRACT_INTERFACES} is set to
 * true, all types are eligible (including classes and not contract annotated types).
 */
public class ServiceContracts {
    private static final TypeInfo OBJECT_INFO = TypeInfo.builder()
            .typeName(TypeNames.OBJECT)
            .kind(ElementKind.CLASS)
            .accessModifier(AccessModifier.PUBLIC)
            .build();

    private final TypeInfo serviceInfo;
    private final Function<TypeInfo, Boolean> isEligibleInfo;
    private final Function<TypeName, Boolean> isEligibleType;
    private final Function<TypeName, Optional<TypeInfo>> typeInfoFactory;

    private ServiceContracts(Function<TypeName, Optional<TypeInfo>> typeInfoFactory,
                             TypeInfo serviceInfo,
                             Function<TypeInfo, Boolean> isEligibleInfo,
                             Function<TypeName, Boolean> isEligibleType) {
        this.typeInfoFactory = typeInfoFactory;
        this.serviceInfo = serviceInfo;
        this.isEligibleInfo = isEligibleInfo;
        this.isEligibleType = isEligibleType;
    }

    /**
     * Create new eligible contracts.
     *
     * @param options         codegen options
     * @param typeInfoFactory function to obtain a type info (if possible) based on type name
     * @param serviceInfo     service info to analyze
     * @return a new instance to check if an implemented interface or a super type is a contract or not
     */
    public static ServiceContracts create(CodegenOptions options,
                                          Function<TypeName, Optional<TypeInfo>> typeInfoFactory,
                                          TypeInfo serviceInfo) {
        Set<TypeName> eligibleExternalContracts = new HashSet<>();
        // gather eligible external contracts
        eligibleContracts(eligibleExternalContracts,
                          new HashSet<>(),
                          serviceInfo);

        boolean onlyAnnotatedContracts = !ServiceOptions.AUTO_ADD_NON_CONTRACT_INTERFACES.value(options);
        var nonContractTypes = ServiceOptions.NON_CONTRACT_TYPES.value(options);

        Function<TypeInfo, Boolean> isEligibleInfo = typeInfo -> isEligible(
                nonContractTypes,
                onlyAnnotatedContracts,
                eligibleExternalContracts,
                typeInfo);

        Function<TypeName, Boolean> isEligibleType = typeName -> isEligible(
                typeInfoFactory,
                nonContractTypes,
                onlyAnnotatedContracts,
                eligibleExternalContracts,
                typeName);

        return new ServiceContracts(typeInfoFactory, serviceInfo, isEligibleInfo, isEligibleType);
    }

    /**
     * Get the desired type parameter of the type info provided.
     *
     * @param typeInfo type info to analyze (such as an implemented interface {@code Supplier<String>}
     * @param index    index of the type arguments (such as {@code 0})
     * @return the type argument at the requested index, such as {@code java.lang.String}
     * @throws io.helidon.codegen.CodegenException in case the type argument is not available
     */
    public static TypeName requiredTypeArgument(TypeInfo typeInfo, int index) {
        Objects.requireNonNull(typeInfo);

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

        // factory must have a type argument (and the type argument is an automatic contract
        TypeName contract = typeArguments.get(index);
        if (contract.generic()) {
            // probably just T (such as Supplier<T>)
            throw new CodegenException("Type argument must be a concrete type for implemented interface " + withGenerics,
                                       typeInfo.originatingElementValue());
        }
        return contract;
    }

    /**
     * Check if a type info is eligible to be a contract.
     *
     * @param contractInfo candidate type info
     * @return whether the candidate is a contract or not
     */
    public boolean isEligible(TypeInfo contractInfo) {
        return this.isEligibleInfo.apply(contractInfo);
    }

    /**
     * Check if a type is eligible to be a contract.
     *
     * @param contractType candidate type
     * @return whether the candidate is a contract or not
     */
    public boolean isEligible(TypeName contractType) {
        return this.isEligibleType.apply(contractType);
    }

    /**
     * Analyse the service info if it is in fact a factory of the expected type.
     *
     * @param factoryInterface the provider we check, the provided contract must be the first type argument
     * @return result of the analysis
     */
    public FactoryAnalysis analyseFactory(TypeName factoryInterface) {
        Optional<TypeInfo> implementedFactory = serviceInfo.interfaceTypeInfo()
                .stream()
                .filter(it -> it.typeName().equals(factoryInterface))
                .findFirst();

        if (implementedFactory.isEmpty()) {
            // the factory interface is not implemented by the service
            return FactoryAnalysis.create();
        }

        // it is implemented
        TypeInfo typeInfo = implementedFactory.get();
        TypeName contract = resolveOptional(typeInfo, requiredTypeArgument(typeInfo), factoryInterface);

        if (contract.packageName().isEmpty()) {
            // we implement a factory of a generated type, "guess" the package
            contract = TypeName.builder(contract)
                    .packageName(serviceInfo.typeName().packageName())
                    .build();
        }

        Set<ResolvedType> contracts = new HashSet<>();
        contracts.add(ResolvedType.create(contract));

        TypeInfo contractInfo = contractInfo(typeInfoFactory, serviceInfo, contract);

        addContracts(contracts,
                     new HashSet<>(),
                     contractInfo);
        return FactoryAnalysis.create(typeInfo.typeName(),
                                      contractInfo.typeName(),
                                      contractInfo,
                                      contracts);
    }

    /**
     * Add contracts from the type (from its implemented interfaces and super types).
     *
     * @param contractSet set of contracts to amend with the contracts of the provided type info
     * @param processed   set of processed contracts, to avoid infinite loop
     * @param typeInfo    type info to analyze for contracts
     */
    public void addContracts(Set<ResolvedType> contractSet,
                             HashSet<ResolvedType> processed,
                             TypeInfo typeInfo) {
        TypeName withGenerics = typeInfo.typeName();
        ResolvedType resolvedType = ResolvedType.create(withGenerics);

        if (!processed.add(resolvedType)) {
            // this type was already fully processed
            return;
        }

        if (!resolvedType.type().typeArguments().isEmpty()) {
            // we also need to add a contract for the type it implements
            // i.e. if this is Circle<Green>, we may want to add Circle<Color> as well, and Circle<?>
            typeInfoFactory.apply(withGenerics.genericTypeName())
                    .ifPresent(declaration -> {
                        TypeName tn = declaration.typeName();

                        List<TypeName> wildcards = new ArrayList<>();
                        List<TypeName> extendses = new ArrayList<>();
                        boolean extendsValid = false;

                        for (int i = 0; i < withGenerics.typeArguments().size(); i++) {
                            TypeName declared = tn.typeArguments().get(i);
                            if (declared.generic()) {
                                // Circle<?>
                                wildcards.add(TypeNames.WILDCARD);

                                // Circle<Color>
                                var asString = declared.toString();
                                int index = asString.indexOf(" extends ");
                                if (index != -1) {
                                    TypeName extendedType = TypeName.create(asString.substring(index + 9));
                                    if (isEligible(extendedType)) {
                                        extendses.add(extendedType);
                                        extendsValid = true;
                                    }
                                }
                            }
                        }
                        if (!wildcards.isEmpty()) {
                            contractSet.add(ResolvedType.create(
                                    TypeName.builder()
                                            .from(withGenerics)
                                            .typeArguments(wildcards)
                                            .build()));
                            if (extendsValid) {
                                contractSet.add(ResolvedType.create(
                                        TypeName.builder()
                                                .from(withGenerics)
                                                .typeArguments(extendses)
                                                .build()));
                            }
                        }
                    });
        }

        // add this type if eligible
        if (isEligible(typeInfo)) {
            contractSet.add(ResolvedType.create(withGenerics));
        }

        // super type
        typeInfo.superTypeInfo()
                .ifPresent(it -> addContracts(
                        contractSet,
                        processed,
                        it
                ));

        // interfaces
        typeInfo.interfaceTypeInfo()
                .forEach(it -> addContracts(
                        contractSet,
                        processed,
                        it
                ));
    }

    private static TypeInfo contractInfo(Function<TypeName, Optional<TypeInfo>> typeInfoFactory,
                                         TypeInfo serviceTypeInfo,
                                         TypeName contract) {
        if (TypeNames.OBJECT.equals(contract)) {
            return OBJECT_INFO;
        }
        var typeInfo = typeInfoFactory.apply(contract);
        if (typeInfo.isPresent()) {
            return typeInfo.get();
        }
        throw new CodegenException("Failed to discover type info for " + contract.fqName(),
                                   serviceTypeInfo.originatingElementValue());
    }

    private static TypeName requiredTypeArgument(TypeInfo typeInfo) {
        return requiredTypeArgument(typeInfo, 0);
    }

    private static boolean isEligible(Function<TypeName, Optional<TypeInfo>> typeInfoFactory,
                                      Set<TypeName> nonContractTypes,
                                      boolean onlyAnnotatedAreEligible,
                                      Set<TypeName> eligibleExternalContracts,
                                      TypeName toCheck) {
        if (eligibleExternalContracts.contains(toCheck)) {
            return true;
        }
        if (nonContractTypes.contains(toCheck)) {
            return false;
        }
        // if the type info does not exist on classpath, and is not an external contract, return false
        return typeInfoFactory.apply(toCheck)
                .map(it -> isEligible(nonContractTypes, onlyAnnotatedAreEligible, eligibleExternalContracts, it))
                .orElse(false);
    }

    private static boolean isEligible(Set<TypeName> nonContractTypes,
                                      boolean onlyAnnotatedAreEligible,
                                      Set<TypeName> eligibleExternalContracts,
                                      TypeInfo toCheck) {
        if (eligibleExternalContracts.contains(toCheck.typeName())) {
            return true;
        }
        if (nonContractTypes.contains(toCheck.typeName())) {
            return false;
        }
        if (toCheck.hasAnnotation(SERVICE_ANNOTATION_CONTRACT)) {
            return true;
        }
        if (onlyAnnotatedAreEligible) {
            return false;
        }
        return toCheck.kind() == ElementKind.INTERFACE;
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

    private static void addExternalContracts(Set<TypeName> eligibleContracts, TypeInfo typeInfo) {
        typeInfo.findAnnotation(SERVICE_ANNOTATION_EXTERNAL_CONTRACTS)
                .flatMap(Annotation::typeValues)
                .ifPresent(eligibleContracts::addAll);
    }

    private TypeName resolveOptional(TypeInfo typeInfo, TypeName typeName, TypeName factoryInterface) {
        // for suppliers, we support optional, all other factory types can return optional by design
        if (factoryInterface.equals(TypeNames.SUPPLIER) && typeName.isOptional()) {
            // Supplier of optionals
            if (typeName.typeArguments().isEmpty()) {
                throw new CodegenException("Invalid declaration of Supplier<Optional>, Optional is missing type argument",
                                           typeInfo.originatingElementValue());
            }
            return typeName.typeArguments().getFirst();
        }
        return typeName;
    }

    /**
     * Result of analysis of provided contracts.
     */
    public interface FactoryAnalysis {

        /**
         * Create a new result for cases where the service does not implement the factory interface.
         *
         * @return a new factory analysis that is not {@link #valid()}
         */
        static FactoryAnalysis create() {
            return new FactoryAnalysisImpl();
        }

        /**
         * The requested factory interface is implemented and provides one or more contracts.
         *
         * @param factoryType       type of the factory implementation (such as {@code Supplier<String>})
         * @param providedType      the type provided (always a contract)
         * @param providedTypeInfo  type info of the provided type
         * @param providedContracts transitive contracts (includes the provided type as well)
         * @return a new analysis result for a valid factory implementation
         */
        static FactoryAnalysis create(TypeName factoryType,
                                      TypeName providedType,
                                      TypeInfo providedTypeInfo,
                                      Set<ResolvedType> providedContracts) {
            return new FactoryAnalysisImpl(factoryType, providedType, providedTypeInfo, providedContracts);
        }

        /**
         * whether the factory interface is implemented.
         *
         * @return if the factory interface is implemented by the service
         */
        boolean valid();

        /**
         * Type of the factory interface with type arguments (such as {@code Supplier×String>}, guard access by {@link #valid()}).
         *
         * @return factory type name
         */
        TypeName factoryType();

        /**
         * The contract provided (guard access by {@link #valid()}).
         *
         * @return provided type name
         */
        TypeName providedType();

        /**
         * Type info of the provided type.
         *
         * @return type info of the {@link #providedType()}
         */
        TypeInfo providedTypeInfo();

        /**
         * All contracts transitively inherited from the provided type (guard access by {@link #valid()}).
         *
         * @return provided contracts
         */
        Set<ResolvedType> providedContracts();
    }
}
