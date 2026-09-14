/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static java.util.function.Predicate.not;

final class TypedElements {
    static final ElementMeta DEFAULT_CONSTRUCTOR = new ElementMeta(TypedElementInfo.builder()
                                                                          .typeName(TypeNames.OBJECT)
                                                                          .accessModifier(AccessModifier.PUBLIC)
                                                                          .kind(ElementKind.CONSTRUCTOR)
                                                                          .build());

    private TypedElements() {
    }

    static List<TypedElements.ElementMeta> gatherElements(TypeInfo typeInfo) {
        List<TypedElements.ElementMeta> result = new ArrayList<>();

        List<TypedElementInfo> declaredElements = typeInfo.elementInfo()
                .stream()
                .toList();

        for (TypedElementInfo declaredElement : declaredElements) {
            declaredElement = withTargetEnclosingType(declaredElement, typeInfo.typeName());
            List<TypedElements.DeclaredElement> abstractMethods = new ArrayList<>();

            if (declaredElement.kind() == ElementKind.METHOD) {
                // now find the same method on any interface (if declared there)
                for (TypeInfo info : typeInfo.interfaceTypeInfo()) {
                    findAbstractMethod(info, declaredElement, abstractMethods);
                }
                // and on any super class (must be abstract)
                Optional<TypeInfo> superClass = typeInfo.superTypeInfo();
                while (superClass.isPresent()) {
                    TypeInfo superClassInfo = superClass.get();
                    findAbstractMethod(superClassInfo, declaredElement, abstractMethods);
                    superClass = superClassInfo.superTypeInfo();
                }
            }
            result.add(new TypedElements.ElementMeta(declaredElement, abstractMethods));
        }

        return result;
    }

    static List<TypedElements.ElementMeta> gatherElements(CodegenContext ctx,
                                                          Collection<ResolvedType> contracts,
                                                          TypeInfo typeInfo) {
        return gatherElements(ctx::typeInfo, contracts, typeInfo);
    }

    private static List<TypedElements.ElementMeta> gatherElements(TypeInfoFactory typeInfoFactory,
                                                                  Collection<ResolvedType> contracts,
                                                                  TypeInfo typeInfo) {
        List<TypedElements.ElementMeta> result = new ArrayList<>();
        Set<ElementSignature> processedSignatures = new HashSet<>();
        List<ContractInfo> contractInfos = contractInfos(typeInfoFactory, contracts);

        typeInfo.elementInfo()
                .stream()
                .filter(it -> it.kind() != ElementKind.CLASS)
                .forEach(declaredElement -> {
                    declaredElement = withTargetEnclosingType(declaredElement, typeInfo.typeName());
                    List<TypedElements.DeclaredElement> abstractMethods = new ArrayList<>();

                    if (declaredElement.kind() == ElementKind.METHOD) {
                        // now find the same method on any interface (if declared there)
                        for (ContractInfo contractInfo : contractInfos) {
                            findAbstractMethod(contractInfo.typeInfo(),
                                               contractInfo.resolvedType().type(),
                                               declaredElement,
                                               abstractMethods);
                        }

                        // and on any super class (must be abstract)
                        Optional<TypeInfo> superClass = typeInfo.superTypeInfo();
                        while (superClass.isPresent()) {
                            TypeInfo superClassInfo = superClass.get();
                            findAbstractMethod(superClassInfo, declaredElement, abstractMethods);
                            superClass = superClassInfo.superTypeInfo();
                        }
                    }
                    result.add(new TypedElements.ElementMeta(declaredElement, abstractMethods));
                    processedSignatures.add(declaredElement.signature());
                });

        // we have gathered all the declared elements, now let's gather inherited elements (default methods etc.)
        Map<ElementSignature, List<DeclaredElement>> inheritedMethods = new LinkedHashMap<>();
        for (ContractInfo contract : contractInfos) {
            TypeInfo inheritedContract = contract.typeInfo();
            Map<String, TypeName> typeArguments = typeArgumentMapping(inheritedContract, contract.resolvedType().type());
            inheritedContract.elementInfo()
                    .stream()
                    .filter(it -> it.kind() != ElementKind.CLASS)
                    .filter(it -> it.kind() != ElementKind.METHOD
                            || (!ElementInfoPredicates.isStatic(it) && !ElementInfoPredicates.isPrivate(it)))
                    .map(it -> withEnclosingType(resolveTypeArguments(it, typeArguments), inheritedContract.typeName()))
                    .forEach(superElement -> {
                        TypedElementInfo targetElement = withTargetEnclosingType(superElement, typeInfo.typeName());
                        if (processedSignatures.contains(targetElement.signature())) {
                            // already processed
                            return;
                        }
                        if (targetElement.kind() == ElementKind.METHOD) {
                            inheritedMethods.computeIfAbsent(targetElement.signature(), ignored -> new ArrayList<>())
                                    .add(new DeclaredElement(inheritedContract, superElement));
                            return;
                        }
                        processedSignatures.add(targetElement.signature());
                        result.add(new TypedElements.ElementMeta(targetElement, List.of()));
                    });
        }
        for (List<DeclaredElement> abstractMethods : inheritedMethods.values()) {
            TypedElementInfo element = withTargetEnclosingType(abstractMethods.getFirst().element(), typeInfo.typeName());
            element = mergeAbstractMethods(element,
                                           abstractMethods.subList(1, abstractMethods.size()));
            result.add(new TypedElements.ElementMeta(element, abstractMethods));
            processedSignatures.add(element.signature());
        }

        return result;
    }

    private static List<ContractInfo> contractInfos(TypeInfoFactory typeInfoFactory, Collection<ResolvedType> contracts) {
        List<ContractInfo> contractInfos = contracts.stream()
                .sorted(Comparator.comparing(it -> it.type().toString()))
                .flatMap(it -> {
                    TypeName resolvedType = resolveContractType(typeInfoFactory, contracts, it.type());
                    return typeInfoFactory.typeInfo(resolvedType)
                            .or(() -> typeInfoFactory.typeInfo(it.type()))
                            .map(typeInfo -> new ContractInfo(ResolvedType.create(resolvedType), typeInfo))
                            .stream();
                })
                .toList();

        Map<TypeName, ContractInfo> result = new LinkedHashMap<>();
        contractInfos.stream()
                .filter(it -> !lessSpecificGenericContract(contractInfos, it))
                .forEach(it -> result.putIfAbsent(it.resolvedType().type(), it));

        return List.copyOf(result.values());
    }

    private static boolean lessSpecificGenericContract(Collection<ContractInfo> contracts, ContractInfo contract) {
        TypeName typeName = contract.resolvedType().type();
        if (!genericContract(contract.typeInfo())) {
            return false;
        }
        if (!typeName.typeArguments().isEmpty() && !hasGenericTypeArgument(typeName)) {
            return false;
        }

        return contracts.stream()
                .map(it -> it.resolvedType().type())
                .anyMatch(it -> typeName.genericTypeName().equals(it.genericTypeName())
                        && !it.typeArguments().isEmpty()
                        && !hasGenericTypeArgument(it));
    }

    private static boolean genericContract(TypeInfo info) {
        return !info.typeName().typeParameters().isEmpty()
                || !typeParameterNames(info.declaredType().typeArguments()).isEmpty();
    }

    private static boolean rawGenericContract(TypeInfoFactory typeInfoFactory, TypeName typeName) {
        if (!typeName.typeArguments().isEmpty()) {
            return false;
        }

        return typeInfoFactory.typeInfo(typeName)
                .map(TypedElements::genericContract)
                .orElse(false);
    }

    private static TypeName resolveContractType(TypeInfoFactory typeInfoFactory,
                                                Collection<ResolvedType> contracts,
                                                TypeName typeName) {
        if (!hasGenericTypeArgument(typeName) && !rawGenericContract(typeInfoFactory, typeName)) {
            return typeName;
        }

        for (ResolvedType candidate : contracts) {
            TypeName candidateType = candidate.type();
            if (candidateType.typeArguments().isEmpty()
                    || hasGenericTypeArgument(candidateType)
                    || candidateType.genericTypeName().equals(typeName.genericTypeName())) {
                continue;
            }

            Optional<TypeName> resolvedType = typeInfoFactory.typeInfo(candidateType)
                    .flatMap(it -> resolveInheritedType(it, candidateType, typeName.genericTypeName(), new HashSet<>()));
            if (resolvedType.isPresent() && !hasGenericTypeArgument(resolvedType.get())) {
                return resolvedType.get();
            }
        }

        return typeName;
    }

    private static Optional<TypeName> resolveInheritedType(TypeInfo info,
                                                           TypeName resolvedInfoType,
                                                           TypeName targetType,
                                                           Set<TypeName> processed) {
        if (!processed.add(resolvedInfoType)) {
            return Optional.empty();
        }

        Map<String, TypeName> typeArguments = typeArgumentMapping(info, resolvedInfoType);
        for (TypeInfo interfaceInfo : info.interfaceTypeInfo()) {
            TypeName interfaceType = resolveTypeArguments(interfaceInfo.typeName(), typeArguments);
            if (interfaceType.genericTypeName().equals(targetType)) {
                return Optional.of(interfaceType);
            }
            Optional<TypeName> resolvedType = resolveInheritedType(interfaceInfo, interfaceType, targetType, processed);
            if (resolvedType.isPresent()) {
                return resolvedType;
            }
        }

        return info.superTypeInfo()
                .flatMap(superInfo -> {
                    TypeName superType = resolveTypeArguments(superInfo.typeName(), typeArguments);
                    if (superType.genericTypeName().equals(targetType)) {
                        return Optional.of(superType);
                    }
                    return resolveInheritedType(superInfo, superType, targetType, processed);
                });
    }

    private static void findAbstractMethod(TypeInfo info,
                                           TypedElementInfo declaredElement,
                                           List<DeclaredElement> abstractMethods) {
        findAbstractMethod(info, info.typeName(), declaredElement, abstractMethods);
    }

    private static void findAbstractMethod(TypeInfo info,
                                           TypeName resolvedType,
                                           TypedElementInfo declaredElement,
                                           List<DeclaredElement> abstractMethods) {
        Map<String, TypeName> typeArguments = typeArgumentMapping(info, resolvedType);

        info.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(not(ElementInfoPredicates::isPrivate))
                // we want all methods from interfaces, but only abstract methods from abstract classes
                .filter(it -> ElementInfoPredicates.isAbstract(it) || ElementInfoPredicates.isDefault(it))
                .map(it -> withEnclosingType(resolveTypeArguments(it, typeArguments), resolvedType))
                .filter(it -> declaredElement.signature().equals(it.signature()))
                .findFirst()
                .ifPresent(it -> abstractMethods.add(new TypedElements.DeclaredElement(info, it)));
    }

    private static TypedElementInfo withEnclosingType(TypedElementInfo element, TypeName enclosingType) {
        if (element.enclosingType().isPresent()) {
            return element;
        }
        return TypedElementInfo.builder(element)
                .enclosingType(enclosingType.genericTypeName())
                .build();
    }

    private static TypedElementInfo withTargetEnclosingType(TypedElementInfo element, TypeName enclosingType) {
        TypeName targetType = enclosingType.genericTypeName();
        if (element.enclosingType()
                .map(targetType::equals)
                .orElse(false)) {
            return element;
        }
        return TypedElementInfo.builder(element)
                .enclosingType(targetType)
                .build();
    }

    private static TypedElementInfo resolveTypeArguments(TypedElementInfo element,
                                                         Map<String, TypeName> typeArguments) {
        if (typeArguments.isEmpty()) {
            return element;
        }

        List<TypedElementInfo> parameters = element.parameterArguments()
                .stream()
                .map(it -> TypedElementInfo.builder(it)
                        .typeName(resolveTypeArguments(it.typeName(), typeArguments))
                        .build())
                .toList();

        return TypedElementInfo.builder(element)
                .typeName(resolveTypeArguments(element.typeName(), typeArguments))
                .parameterArguments(parameters)
                .build();
    }

    static TypedElementInfo mergeAbstractMethods(TypedElementInfo element, List<DeclaredElement> abstractMethods) {
        if (abstractMethods.isEmpty()) {
            return element;
        }

        List<TypedElementInfo> contractMethods = abstractMethods.stream()
                .map(DeclaredElement::element)
                .toList();

        List<Annotation> annotations = new ArrayList<>(element.annotations());
        contractMethods.stream()
                .map(TypedElementInfo::annotations)
                .flatMap(List::stream)
                .forEach(it -> addAnnotationIfAbsent(annotations, it));

        List<TypedElementInfo> parameters = new ArrayList<>();
        List<TypedElementInfo> elementParameters = element.parameterArguments();
        for (int i = 0; i < elementParameters.size(); i++) {
            TypedElementInfo parameter = elementParameters.get(i);
            List<Annotation> parameterAnnotations = new ArrayList<>(parameter.annotations());
            TypeName parameterType = parameter.typeName();
            for (TypedElementInfo contractMethod : contractMethods) {
                List<TypedElementInfo> contractParameters = contractMethod.parameterArguments();
                if (contractParameters.size() > i) {
                    TypedElementInfo contractParameter = contractParameters.get(i);
                    contractParameter.annotations().forEach(it -> addAnnotationIfAbsent(parameterAnnotations, it));
                    parameterType = mergeTypeName(parameterType, contractParameter.typeName());
                }
            }
            parameters.add(TypedElementInfo.builder(parameter)
                                   .annotations(parameterAnnotations)
                                   .typeName(parameterType)
                                   .build());
        }

        TypeName returnType = element.typeName();
        for (TypedElementInfo contractMethod : contractMethods) {
            returnType = mergeTypeName(returnType, contractMethod.typeName());
        }

        return TypedElementInfo.builder(element)
                .annotations(annotations)
                .typeName(returnType)
                .parameterArguments(parameters)
                .build();
    }

    private static TypeName resolveTypeArguments(TypeName typeName, Map<String, TypeName> typeArguments) {
        if (typeName.generic()) {
            TypeName resolved = typeArguments.get(typeName.className().trim());
            if (resolved != null) {
                return mergeTypeName(resolved, typeName);
            }
        }

        List<TypeName> resolvedTypeArguments = typeName.typeArguments()
                .stream()
                .map(it -> resolveTypeArguments(it, typeArguments))
                .toList();
        List<TypeName> resolvedLowerBounds = typeName.lowerBounds()
                .stream()
                .map(it -> resolveTypeArguments(it, typeArguments))
                .toList();
        List<TypeName> resolvedUpperBounds = typeName.upperBounds()
                .stream()
                .map(it -> resolveTypeArguments(it, typeArguments))
                .toList();
        Optional<TypeName> resolvedComponentType = typeName.componentType()
                .map(it -> resolveTypeArguments(it, typeArguments));

        if (resolvedTypeArguments.equals(typeName.typeArguments())
                && resolvedLowerBounds.equals(typeName.lowerBounds())
                && resolvedUpperBounds.equals(typeName.upperBounds())
                && resolvedComponentType.equals(typeName.componentType())) {
            return typeName;
        }

        TypeName.Builder builder = TypeName.builder(typeName)
                .typeArguments(resolvedTypeArguments)
                .lowerBounds(resolvedLowerBounds)
                .upperBounds(resolvedUpperBounds);
        resolvedComponentType.ifPresent(builder::componentType);
        return builder.build();
    }

    private static TypeName mergeTypeName(TypeName typeName, TypeName contractTypeName) {
        return TypeHierarchy.mergeTypeNameAnnotations(typeName, contractTypeName);
    }

    private static void addAnnotationIfAbsent(List<Annotation> annotations, Annotation annotation) {
        if (!annotations.contains(annotation)) {
            annotations.add(annotation);
        }
    }

    private static Map<String, TypeName> typeArgumentMapping(TypeInfo info, TypeName resolvedType) {
        TypeName typeName = info.typeName();
        List<String> typeParameters = typeName.typeParameters();
        List<TypeName> typeArguments = resolvedType.typeArguments();
        if (typeArguments.isEmpty()) {
            typeArguments = typeName.typeArguments();
        }
        if (typeParameters.isEmpty()) {
            typeParameters = typeParameterNames(info.declaredType().typeArguments());
        }
        if (typeParameters.isEmpty() || typeArguments.isEmpty()) {
            return Map.of();
        }

        Map<String, TypeName> result = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(typeParameters.size(), typeArguments.size()); i++) {
            result.put(genericTypeName(typeParameters.get(i)), typeArguments.get(i));
        }
        return result;
    }

    private static List<String> typeParameterNames(List<TypeName> typeParameters) {
        return typeParameters.stream()
                .filter(TypeName::generic)
                .filter(not(TypeName::wildcard))
                .map(TypeName::className)
                .map(TypedElements::genericTypeName)
                .toList();
    }

    private static String genericTypeName(String typeParameter) {
        String name = typeParameter.trim();
        int index = name.indexOf(' ');
        return index == -1 ? name : name.substring(0, index);
    }

    private static boolean hasGenericTypeArgument(TypeName typeName) {
        for (TypeName typeArgument : typeName.typeArguments()) {
            if (typeArgument.generic() || typeArgument.wildcard() || hasGenericTypeArgument(typeArgument)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Metadata of an element (field, constructor, method).
     *
     * @param element         element declared on a type
     * @param abstractMethods interface or abstract class methods that define the contract of this method
     */
    record ElementMeta(TypedElementInfo element,
                       List<DeclaredElement> abstractMethods) {
        ElementMeta(TypedElementInfo element) {
            this(element, List.of());
        }

        /**
         * Element with annotations and type-use annotations merged from matching service-contract methods.
         *
         * @return effective element
         */
        TypedElementInfo effectiveElement() {
            return mergeAbstractMethods(element, abstractMethods);
        }
    }

    /**
     * Who declares the method.
     *
     * @param abstractType interface or abstract class
     * @param element      element declared on that type
     */
    record DeclaredElement(TypeInfo abstractType,
                           TypedElementInfo element) {
    }

    private record ContractInfo(ResolvedType resolvedType,
                                TypeInfo typeInfo) {
    }

    private interface TypeInfoFactory {
        Optional<TypeInfo> typeInfo(TypeName typeName);
    }
}
