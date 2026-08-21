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
package io.helidon.codegen;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import io.helidon.common.Api;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

/**
 * Resolves Java methods and generic types across a type hierarchy.
 * <p>
 * The resolver uses the code generation type model as its portable baseline. An
 * environment may override the protected operations when it has an authoritative
 * Java type system. Annotation processing uses this extension point to delegate
 * member substitution, overriding, and assignability to the compiler whenever
 * the relevant elements are available.
 */
@Api.Internal
public class TypeHierarchyResolver {

    private static final TypeName CLONEABLE = TypeName.create(Cloneable.class);
    private static final TypeName SERIALIZABLE = TypeName.create(Serializable.class);
    private static final TypeName RECORD = TypeName.create(Record.class);
    private static final TypeName ENUM = TypeName.create(Enum.class);
    private static final TypeName ANNOTATION = TypeName.create(java.lang.annotation.Annotation.class);

    private final Function<TypeName, Optional<TypeInfo>> typeInfoLookup;

    /**
     * Creates a resolver for an environment specific type information lookup.
     *
     * @param typeInfoLookup lookup used for types that are not already present in a hierarchy
     */
    protected TypeHierarchyResolver(Function<TypeName, Optional<TypeInfo>> typeInfoLookup) {
        this.typeInfoLookup = Objects.requireNonNull(typeInfoLookup,
                                                     "The type information lookup must not be null.");
    }

    /**
     * Creates a resolver that uses the portable code generation type model.
     *
     * @param typeInfoLookup lookup used for types that are not already present in a hierarchy
     * @return a new resolver
     */
    public static TypeHierarchyResolver create(Function<TypeName, Optional<TypeInfo>> typeInfoLookup) {
        return new TypeHierarchyResolver(typeInfoLookup);
    }

    static TypeName substitute(TypeName type, Map<String, TypeName> substitutions) {
        TypeName replacement = typeVariable(type)
                ? substitutions.get(genericName(type.className()))
                : null;
        if (replacement != null) {
            return mergeTypeUseAnnotations(replacement, type);
        }
        TypeName.Builder builder = TypeName.builder(type)
                .typeArguments(type.typeArguments()
                                       .stream()
                                       .map(argument -> substitute(argument, substitutions))
                                       .toList())
                .lowerBounds(type.lowerBounds()
                                     .stream()
                                     .map(bound -> substitute(bound, substitutions))
                                     .toList())
                .upperBounds(type.upperBounds()
                                     .stream()
                                     .map(bound -> substitute(bound, substitutions))
                                     .toList());
        type.componentType()
                .map(component -> substitute(component, substitutions))
                .ifPresent(builder::componentType);
        return builder.build();
    }

    /**
     * Returns effective interface methods with interface type variables resolved.
     * <p>
     * Each result retains every maximally specific declaration that contributed to
     * the method. A provider can therefore apply annotation and parameter policy
     * without asking the common resolver to understand provider annotations.
     *
     * @param interfaceInfo interface to inspect
     * @return resolved methods in declaration order
     */
    public final List<ResolvedMethod> effectiveInterfaceMethods(TypeInfo interfaceInfo) {
        Objects.requireNonNull(interfaceInfo, "The interface information must not be null.");
        Map<ElementSignature, List<MethodCandidate>> methods = new LinkedHashMap<>();
        collectMethods(interfaceInfo,
                       interfaceInfo,
                       Map.of(),
                       interfaceInfo.typeName().genericTypeName(),
                       false,
                       new HashSet<>(),
                       methods);
        return methods.entrySet()
                .stream()
                .map(entry -> effectiveMethod(interfaceInfo, entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Resolves one requested supertype while preserving its actual type arguments.
     *
     * @param candidate type whose hierarchy is inspected
     * @param expected requested supertype
     * @return the resolved supertype, or an empty optional when it is not present
     */
    public final Optional<TypeName> resolveSupertype(TypeName candidate, TypeName expected) {
        Objects.requireNonNull(candidate, "The candidate type must not be null.");
        Objects.requireNonNull(expected, "The requested supertype must not be null.");
        return resolveEnvironmentSupertype(candidate, expected)
                .or(() -> resolveModelSupertype(candidate, expected));
    }

    /**
     * Resolves a declaration as a member of the inspected interface.
     *
     * @param interfaceInfo inspected interface
     * @param declaration source declaration
     * @return the compiler resolved member, or an empty optional to use the portable model
     */
    protected Optional<TypedElementInfo> resolveEnvironmentMember(TypeInfo interfaceInfo,
                                                                  TypedElementInfo declaration) {
        return Optional.empty();
    }

    /**
     * Determines whether one declaration overrides another in the inspected interface.
     *
     * @param interfaceInfo inspected interface
     * @param overrider possible overriding declaration
     * @param overridden possible overridden declaration
     * @return the compiler decision, or an empty optional to use the portable model
     */
    protected Optional<Boolean> environmentOverrides(TypeInfo interfaceInfo,
                                                     TypedElementInfo overrider,
                                                     TypedElementInfo overridden) {
        return Optional.empty();
    }

    /**
     * Determines whether a candidate method has a return type that can implement
     * another declaration.
     *
     * @param interfaceInfo inspected interface
     * @param candidate candidate declaration
     * @param existing declaration that must be implemented
     * @return the compiler decision, or an empty optional to use the portable model
     */
    protected Optional<Boolean> environmentReturnTypeAssignable(TypeInfo interfaceInfo,
                                                                TypedElementInfo candidate,
                                                                TypedElementInfo existing) {
        return Optional.empty();
    }

    /**
     * Resolves a supertype through the environment type system.
     *
     * @param candidate type whose hierarchy is inspected
     * @param expected requested supertype
     * @return the resolved supertype, or an empty optional to use the portable model
     */
    protected Optional<TypeName> resolveEnvironmentSupertype(TypeName candidate, TypeName expected) {
        return Optional.empty();
    }

    private static Map<String, TypeName> substitutions(TypeInfo typeInfo,
                                                       Map<String, TypeName> inherited,
                                                       boolean rawType) {
        List<String> parameters = declaredTypeParameters(typeInfo);
        if (parameters.isEmpty()) {
            return Map.of();
        }
        if (rawType) {
            Map<String, TypeName> result = new LinkedHashMap<>();
            for (int index = 0; index < parameters.size(); index++) {
                TypeName parameter = declaredTypeParameter(typeInfo, parameters.get(index), index);
                result.put(genericName(parameters.get(index)), erasure(parameter, new HashSet<>()));
            }
            return result;
        }

        List<TypeName> arguments = typeInfo.typeName().typeArguments();
        if (arguments.isEmpty()) {
            return Map.of();
        }
        Map<String, TypeName> result = new LinkedHashMap<>();
        for (int index = 0; index < arguments.size() && index < parameters.size(); index++) {
            result.put(genericName(parameters.get(index)),
                       substitute(arguments.get(index), inherited));
        }
        return result;
    }

    private void collectMethods(TypeInfo rootInterface,
                                TypeInfo typeInfo,
                                Map<String, TypeName> inheritedSubstitutions,
                                TypeName repositoryType,
                                boolean rawType,
                                Set<String> visited,
                                Map<ElementSignature, List<MethodCandidate>> methods) {
        if (!visited.add(typeInfo.typeName().resolvedName() + ':' + rawType)) {
            return;
        }

        Map<String, TypeName> substitutions = substitutions(typeInfo, inheritedSubstitutions, rawType);
        for (TypedElementInfo element : typeInfo.elementInfo()) {
            if (element.kind() == ElementKind.METHOD
                    && isOverrideCandidate(element)) {
                // Default methods participate because they can satisfy or override an abstract declaration.
                TypedElementInfo method = resolveEnvironmentMember(rootInterface, element)
                        .orElseGet(() -> resolveMethod(element, substitutions, repositoryType));
                methods.computeIfAbsent(TypeHierarchy.methodSignature(method), ignored -> new ArrayList<>())
                        .add(new MethodCandidate(method, typeInfo));
            }
        }

        for (TypeInfo inheritedInterface : typeInfo.interfaceTypeInfo()) {
            ResolvedHierarchyType resolved = resolveHierarchyType(inheritedInterface,
                                                                  inheritedInterface.typeName(),
                                                                  substitutions,
                                                                  rawType);
            collectMethods(rootInterface,
                           TypeInfo.builder(inheritedInterface).typeName(resolved.type()).build(),
                           substitutions,
                           repositoryType,
                           resolved.raw(),
                           visited,
                           methods);
        }
    }

    private static boolean isOverrideCandidate(TypedElementInfo method) {
        return method.elementModifiers().contains(Modifier.ABSTRACT)
                || method.elementModifiers().contains(Modifier.DEFAULT);
    }

    private static TypedElementInfo resolveMethod(TypedElementInfo method,
                                                  Map<String, TypeName> substitutions,
                                                  TypeName repositoryType) {
        Map<String, TypeName> methodSubstitutions = substitutions;
        // Method type variables shadow interface variables with the same name.
        if (!method.typeParameters().isEmpty() && !substitutions.isEmpty()) {
            methodSubstitutions = new LinkedHashMap<>(substitutions);
            method.typeParameters()
                    .stream()
                    .map(TypeName::className)
                    .forEach(methodSubstitutions::remove);
        }

        Map<String, TypeName> resolvedSubstitutions = methodSubstitutions;
        List<TypedElementInfo> parameters = method.parameterArguments()
                .stream()
                .map(parameter -> TypedElementInfo.builder(parameter)
                        .typeName(substitute(parameter.typeName(), resolvedSubstitutions))
                        .enclosingType(repositoryType)
                        .build())
                .toList();
        LinkedHashSet<TypeName> checkedExceptions = new LinkedHashSet<>();
        method.throwsChecked()
                .stream()
                .map(type -> substitute(type, resolvedSubstitutions))
                .forEach(checkedExceptions::add);
        List<TypeName> typeParameters = method.typeParameters()
                .stream()
                .map(type -> substitute(type, resolvedSubstitutions))
                .toList();
        return TypedElementInfo.builder(method)
                .typeName(substitute(method.typeName(), resolvedSubstitutions))
                .parameterArguments(parameters)
                .throwsChecked(checkedExceptions)
                .typeParameters(typeParameters)
                .enclosingType(repositoryType)
                .build();
    }

    private ResolvedMethod effectiveMethod(TypeInfo interfaceInfo,
                                           ElementSignature signature,
                                           List<MethodCandidate> declarations) {
        List<MethodCandidate> candidates = declarations.stream()
                .filter(candidate -> declarations.stream()
                        .noneMatch(other -> other != candidate
                                && environmentOverrides(interfaceInfo, other.method(), candidate.method())
                                        .orElseGet(() -> isSubtype(other.owner(), candidate.owner()))))
                .toList();
        MethodCandidate first = candidates.getFirst();

        MethodCandidate selected = null;
        for (MethodCandidate candidate : candidates) {
            boolean compatible = candidates.stream()
                    .allMatch(other -> environmentReturnTypeAssignable(interfaceInfo,
                                                                       candidate.method(),
                                                                       other.method())
                            .orElseGet(() -> returnTypeAssignable(candidate.method().typeName(),
                                                                 adaptMethodTypeVariables(other.method(),
                                                                                          candidate.method()))));
            if (compatible && (selected == null
                    || morePrecise(interfaceInfo, candidate.method(), selected.method(), candidates))) {
                selected = candidate;
            }
        }
        if (selected == null) {
            MethodCandidate second = candidates.size() == 1 ? first : candidates.get(1);
            throw new CodegenException("Inherited method '" + signature.text()
                                               + "' has return types that cannot be implemented by one method.",
                                       first.method().originatingElementValue(),
                                       second.method().originatingElementValue());
        }
        TypedElementInfo method = compatibleCheckedExceptions(selected.method(), candidates);
        return new ResolvedMethod(method,
                                  candidates.stream()
                                          .map(MethodCandidate::method)
                                          .toList());
    }

    private static boolean isSubtype(TypeInfo candidate, TypeInfo existing) {
        return !candidate.typeName().genericTypeName().equals(existing.typeName().genericTypeName())
                && candidate.findInHierarchy(existing.typeName().genericTypeName()).isPresent();
    }

    private boolean returnTypeAssignable(TypeName candidate, TypeName existing) {
        if (sameType(candidate, existing)) {
            return true;
        }
        if (typeVariable(candidate)) {
            if (candidate.upperBounds().isEmpty()) {
                return existing.equals(TypeNames.OBJECT);
            }
            return candidate.upperBounds()
                    .stream()
                    .anyMatch(bound -> returnTypeAssignable(bound, existing));
        }
        if (typeVariable(existing)) {
            return false;
        }
        if (candidate.array()) {
            if (existing.array()) {
                TypeName candidateComponent = candidate.componentType().orElseThrow();
                TypeName existingComponent = existing.componentType().orElseThrow();
                if (candidateComponent.primitive() || existingComponent.primitive()) {
                    return candidateComponent.equals(existingComponent);
                }
                return returnTypeAssignable(candidateComponent, existingComponent);
            }
            return existing.equals(TypeNames.OBJECT)
                    || existing.equals(CLONEABLE)
                    || existing.equals(SERIALIZABLE);
        }
        if (existing.array()) {
            return false;
        }
        if (candidate.primitive() || existing.primitive()) {
            return false;
        }
        if (existing.equals(TypeNames.OBJECT) && !candidate.primitive()) {
            return true;
        }
        return resolveSupertype(candidate, existing.genericTypeName())
                .filter(supertype -> parameterizationAssignable(supertype, existing))
                .isPresent();
    }

    private Optional<TypeName> resolveModelSupertype(TypeName candidate, TypeName expected) {
        if (sameErasure(candidate, expected)) {
            return Optional.of(candidate);
        }
        return typeInfoLookup.apply(declarationType(candidate))
                .flatMap(typeInfo -> resolveModelSupertype(typeInfo,
                                                           candidate,
                                                           expected,
                                                           rawType(typeInfo, candidate),
                                                           new HashSet<>()));
    }

    private Optional<TypeName> resolveModelSupertype(TypeInfo typeInfo,
                                                     TypeName resolvedType,
                                                     TypeName expected,
                                                     boolean rawType,
                                                     Set<String> visited) {
        if (sameErasure(resolvedType, expected)) {
            return Optional.of(resolvedType);
        }
        if (!visited.add(resolvedType.resolvedName() + ':' + rawType)) {
            return Optional.empty();
        }

        Map<String, TypeName> substitutions = substitutions(TypeInfo.builder(typeInfo)
                                                                    .typeName(resolvedType)
                                                                    .build(),
                                                               Map.of(),
                                                               rawType);
        for (TypeName implicitType : implicitSupertypes(typeInfo, resolvedType)) {
            if (sameErasure(implicitType, expected)) {
                return Optional.of(implicitType);
            }
            Optional<TypeName> found = typeInfoLookup.apply(declarationType(implicitType))
                    .flatMap(implicitInfo -> resolveModelSupertype(implicitInfo,
                                                                   implicitType,
                                                                   expected,
                                                                   rawType(implicitInfo, implicitType),
                                                                   visited));
            if (found.isPresent()) {
                return found;
            }
        }
        for (TypeInfo interfaceInfo : typeInfo.interfaceTypeInfo()) {
            ResolvedHierarchyType resolved = resolveHierarchyType(interfaceInfo,
                                                                  interfaceInfo.typeName(),
                                                                  substitutions,
                                                                  rawType);
            Optional<TypeName> found = resolveModelSupertype(interfaceInfo,
                                                             resolved.type(),
                                                             expected,
                                                             resolved.raw(),
                                                             visited);
            if (found.isPresent()) {
                return found;
            }
        }
        return typeInfo.superTypeInfo()
                .flatMap(superInfo -> {
                    ResolvedHierarchyType resolved = resolveHierarchyType(superInfo,
                                                                          superInfo.typeName(),
                                                                          substitutions,
                                                                          rawType);
                    return resolveModelSupertype(superInfo,
                                                 resolved.type(),
                                                 expected,
                                                 resolved.raw(),
                                                 visited);
                });
    }

    private static List<TypeName> implicitSupertypes(TypeInfo typeInfo, TypeName resolvedType) {
        return switch (typeInfo.kind()) {
            case RECORD -> List.of(RECORD);
            case ENUM -> List.of(TypeName.builder(ENUM)
                                         .addTypeArgument(resolvedType)
                                         .build());
            case ANNOTATION_TYPE -> List.of(ANNOTATION);
            default -> List.of();
        };
    }

    private static TypeName declarationType(TypeName type) {
        return TypeName.builder(type.genericTypeName())
                .annotations(List.of())
                .inheritedAnnotations(List.of())
                .build();
    }

    private boolean parameterizationAssignable(TypeName candidate, TypeName existing) {
        List<TypeName> existingArguments = existing.typeArguments();
        if (existingArguments.isEmpty()) {
            return true;
        }
        List<TypeName> candidateArguments = candidate.typeArguments();
        if (candidateArguments.isEmpty()) {
            // Java return type substitutability permits the corresponding unchecked raw conversion.
            return true;
        }
        if (candidateArguments.size() != existingArguments.size()) {
            return false;
        }
        for (int index = 0; index < candidateArguments.size(); index++) {
            if (!typeArgumentContained(candidateArguments.get(index), existingArguments.get(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean typeArgumentContained(TypeName candidate, TypeName existing) {
        if (sameType(candidate, existing)) {
            return true;
        }
        if (!existing.wildcard()) {
            return false;
        }
        if (existing.upperBounds().isEmpty() && existing.lowerBounds().isEmpty()) {
            return true;
        }
        if (!existing.upperBounds().isEmpty()) {
            if (candidate.wildcard() && !candidate.lowerBounds().isEmpty()) {
                return false;
            }
            List<TypeName> candidateBounds = candidate.wildcard()
                    ? candidate.upperBounds()
                    : List.of(candidate);
            if (candidateBounds.isEmpty()) {
                candidateBounds = List.of(TypeNames.OBJECT);
            }
            List<TypeName> resolvedCandidateBounds = candidateBounds;
            return existing.upperBounds()
                    .stream()
                    .allMatch(existingBound -> resolvedCandidateBounds.stream()
                            .anyMatch(candidateBound -> returnTypeAssignable(candidateBound, existingBound)));
        }
        if (candidate.wildcard() && candidate.lowerBounds().isEmpty()) {
            return false;
        }
        List<TypeName> candidateBounds = candidate.wildcard()
                ? candidate.lowerBounds()
                : List.of(candidate);
        return existing.lowerBounds()
                .stream()
                .allMatch(existingBound -> candidateBounds.stream()
                        .anyMatch(candidateBound -> returnTypeAssignable(existingBound, candidateBound)));
    }

    private TypedElementInfo compatibleCheckedExceptions(TypedElementInfo selected,
                                                         List<MethodCandidate> candidates) {
        LinkedHashSet<TypeName> exceptions = new LinkedHashSet<>();
        for (TypeName selectedException : selected.throwsChecked()) {
            boolean compatible = true;
            for (MethodCandidate candidate : candidates) {
                if (candidate.method() == selected) {
                    continue;
                }
                Map<String, TypeName> aliases = methodTypeAliases(candidate.method(), selected);
                boolean declared = candidate.method().throwsChecked()
                        .stream()
                        .map(exception -> substitute(exception, aliases))
                        .anyMatch(exception -> returnTypeAssignable(selectedException, exception));
                if (!declared) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                exceptions.add(selectedException);
            }
        }
        if (exceptions.equals(selected.throwsChecked())) {
            return selected;
        }
        return TypedElementInfo.builder(selected)
                .throwsChecked(exceptions)
                .build();
    }

    private static TypeName adaptMethodTypeVariables(TypedElementInfo source, TypedElementInfo target) {
        return substitute(source.typeName(), methodTypeAliases(source, target));
    }

    private static Map<String, TypeName> methodTypeAliases(TypedElementInfo source, TypedElementInfo target) {
        List<TypeName> sourceParameters = source.typeParameters();
        List<TypeName> targetParameters = target.typeParameters();
        if (sourceParameters.size() != targetParameters.size()) {
            return Map.of();
        }
        Map<String, TypeName> aliases = new LinkedHashMap<>();
        for (int index = 0; index < sourceParameters.size(); index++) {
            aliases.put(genericName(sourceParameters.get(index).className()), targetParameters.get(index));
        }
        return aliases;
    }

    private boolean morePrecise(TypeInfo interfaceInfo,
                                TypedElementInfo candidate,
                                TypedElementInfo existing,
                                List<MethodCandidate> candidates) {
        Optional<Boolean> compilerDecision = environmentReturnTypeAssignable(interfaceInfo, candidate, existing);
        Optional<Boolean> reverseCompilerDecision = environmentReturnTypeAssignable(interfaceInfo, existing, candidate);
        if (compilerDecision.orElse(false) != reverseCompilerDecision.orElse(false)) {
            return compilerDecision.orElse(false);
        }
        int candidatePrecision = typePrecision(candidate.typeName());
        int existingPrecision = typePrecision(existing.typeName());
        if (candidatePrecision != existingPrecision) {
            return candidatePrecision > existingPrecision;
        }
        int candidateExceptions = compatibleCheckedExceptions(candidate, candidates).throwsChecked().size();
        int existingExceptions = compatibleCheckedExceptions(existing, candidates).throwsChecked().size();
        if (candidateExceptions != existingExceptions) {
            return candidateExceptions > existingExceptions;
        }
        return candidate.elementModifiers().contains(Modifier.DEFAULT)
                && !existing.elementModifiers().contains(Modifier.DEFAULT);
    }

    private static int typePrecision(TypeName type) {
        int precision = type.wildcard() ? 0 : 2;
        precision += type.upperBounds().size() + type.lowerBounds().size();
        for (TypeName argument : type.typeArguments()) {
            precision += 1 + typePrecision(argument);
        }
        if (type.componentType().isPresent()) {
            precision += typePrecision(type.componentType().orElseThrow());
        }
        return precision;
    }

    private static boolean sameType(TypeName first, TypeName second) {
        return first.primitive() == second.primitive()
                && first.array() == second.array()
                && first.generic() == second.generic()
                && first.wildcard() == second.wildcard()
                && first.name().equals(second.name())
                && sameTypes(first.typeArguments(), second.typeArguments())
                && sameTypes(first.lowerBounds(), second.lowerBounds())
                && sameTypes(first.upperBounds(), second.upperBounds())
                && sameComponents(first, second);
    }

    private static boolean sameTypes(List<TypeName> first, List<TypeName> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!sameType(first.get(index), second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameComponents(TypeName first, TypeName second) {
        Optional<TypeName> firstComponent = first.componentType();
        Optional<TypeName> secondComponent = second.componentType();
        return firstComponent.isEmpty()
                ? secondComponent.isEmpty()
                : secondComponent.isPresent() && sameType(firstComponent.get(), secondComponent.get());
    }

    private static boolean sameErasure(TypeName first, TypeName second) {
        return first.primitive() == second.primitive()
                && first.array() == second.array()
                && first.name().equals(second.name());
    }

    private static boolean typeVariable(TypeName type) {
        return type.generic()
                && !type.wildcard()
                && !type.array()
                && type.typeArguments().isEmpty()
                && type.packageName().isEmpty()
                && type.enclosingNames().isEmpty();
    }

    private static ResolvedHierarchyType resolveHierarchyType(TypeInfo typeInfo,
                                                              TypeName declaredType,
                                                              Map<String, TypeName> substitutions,
                                                              boolean rawOwner) {
        boolean parameterizedDeclaration = !declaredTypeParameters(typeInfo).isEmpty();
        if (rawOwner && parameterizedDeclaration) {
            return new ResolvedHierarchyType(declaredType.genericTypeName(), true);
        }
        TypeName resolvedType = substitute(declaredType, substitutions);
        boolean rawType = parameterizedDeclaration && resolvedType.typeArguments().isEmpty();
        return new ResolvedHierarchyType(rawType ? resolvedType.genericTypeName() : resolvedType, rawType);
    }

    private static boolean rawType(TypeInfo typeInfo, TypeName resolvedType) {
        return !declaredTypeParameters(typeInfo).isEmpty() && resolvedType.typeArguments().isEmpty();
    }

    private static List<String> declaredTypeParameters(TypeInfo typeInfo) {
        List<String> parameters = typeInfo.declaredType().typeParameters();
        if (!parameters.isEmpty()) {
            return parameters;
        }
        // Some type metadata exposes declaration variables as generic arguments instead of named parameters.
        return typeInfo.declaredType()
                .typeArguments()
                .stream()
                .filter(TypeName::generic)
                .map(TypeName::className)
                .toList();
    }

    private static TypeName declaredTypeParameter(TypeInfo typeInfo, String parameter, int index) {
        List<TypeName> arguments = typeInfo.declaredType().typeArguments();
        if (index < arguments.size() && typeVariable(arguments.get(index))) {
            return arguments.get(index);
        }
        return TypeName.createFromGenericDeclaration(parameter);
    }

    private static String genericName(String declaration) {
        return genericDeclaration(declaration).name();
    }

    private static GenericDeclaration genericDeclaration(String declaration) {
        int length = declaration.length();
        int index = skipWhitespace(declaration, 0);
        List<Annotation> annotations = new ArrayList<>();
        while (index < length && declaration.charAt(index) == '@') {
            int annotationStart = index + 1;
            int annotationNameEnd = annotationStart;
            while (annotationNameEnd < length
                    && (Character.isJavaIdentifierPart(declaration.charAt(annotationNameEnd))
                    || declaration.charAt(annotationNameEnd) == '.'
                    || declaration.charAt(annotationNameEnd) == '$')) {
                annotationNameEnd++;
            }
            int annotationEnd = skipWhitespace(declaration, annotationNameEnd);
            if (annotationEnd < length && declaration.charAt(annotationEnd) == '(') {
                annotationEnd = annotationEnd(declaration, annotationEnd);
            }
            String definition = declaration.substring(annotationStart, annotationEnd).trim();
            try {
                annotations.add(io.helidon.codegen.classmodel.Annotation.parse(definition).toTypesAnnotation());
            } catch (IllegalArgumentException ignored) {
                // The source remains valid even if the class model parser cannot represent the annotation value.
            }
            index = skipWhitespace(declaration, annotationEnd);
        }

        int nameStart = index;
        while (index < length && Character.isJavaIdentifierPart(declaration.charAt(index))) {
            index++;
        }
        String name = declaration.substring(nameStart, index).trim();
        if (name.isEmpty()) {
            int boundStart = declaration.indexOf(' ');
            name = (boundStart < 0 ? declaration : declaration.substring(0, boundStart)).trim();
        }
        return new GenericDeclaration(name, List.copyOf(annotations));
    }

    private static int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int annotationEnd(String declaration, int bodyStart) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = bodyStart; index < declaration.length(); index++) {
            char current = declaration.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return index + 1;
            }
        }
        return declaration.length();
    }

    private static TypeName erasure(TypeName type, Set<String> visited) {
        if (type.array()) {
            TypeName component = erasure(type.componentType().orElseThrow(), visited);
            return TypeName.builder(component)
                    .array(true)
                    .componentType(component)
                    .build();
        }
        if (!typeVariable(type)) {
            return type.genericTypeName();
        }
        if (!visited.add(genericName(type.className()))) {
            return TypeNames.OBJECT;
        }
        return type.upperBounds()
                .stream()
                .findFirst()
                .map(bound -> erasure(bound, visited))
                .orElse(TypeNames.OBJECT);
    }

    private static TypeName mergeTypeUseAnnotations(TypeName target, TypeName source) {
        List<Annotation> annotations = new ArrayList<>(target.annotations());
        source.annotations().forEach(annotation -> addAnnotationIfAbsent(annotations, annotation));
        genericDeclaration(source.className())
                .annotations()
                .forEach(annotation -> addAnnotationIfAbsent(annotations, annotation));
        List<Annotation> inheritedAnnotations = new ArrayList<>(target.inheritedAnnotations());
        source.inheritedAnnotations()
                .forEach(annotation -> addAnnotationIfAbsent(inheritedAnnotations, annotation));
        return TypeName.builder(target)
                .annotations(annotations)
                .inheritedAnnotations(inheritedAnnotations)
                .build();
    }

    private static void addAnnotationIfAbsent(List<Annotation> annotations, Annotation annotation) {
        if (!annotations.contains(annotation)) {
            annotations.add(annotation);
        }
    }

    /**
     * One effective method and the maximally specific declarations that define it.
     *
     * @param method resolved method selected for generation
     * @param declarations resolved source declarations that contribute to the method
     */
    public record ResolvedMethod(TypedElementInfo method, List<TypedElementInfo> declarations) {
        /**
         * Creates a resolved method.
         *
         * @param method resolved method selected for generation
         * @param declarations resolved source declarations that contribute to the method
         */
        public ResolvedMethod {
            Objects.requireNonNull(method, "The resolved method must not be null.");
            declarations = List.copyOf(Objects.requireNonNull(declarations,
                                                              "The source declarations must not be null."));
        }
    }

    private record MethodCandidate(TypedElementInfo method, TypeInfo owner) {
    }

    private record ResolvedHierarchyType(TypeName type, boolean raw) {
    }

    private record GenericDeclaration(String name, List<Annotation> annotations) {
    }
}
