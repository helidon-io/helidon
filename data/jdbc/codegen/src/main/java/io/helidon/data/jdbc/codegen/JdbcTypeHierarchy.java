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
package io.helidon.data.jdbc.codegen;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

/**
 * Resolves repository methods and generic types across an interface hierarchy.
 */
final class JdbcTypeHierarchy {

    private static final List<TypeName> METHOD_ANNOTATIONS = methodAnnotations();
    private static final TypeName CLONEABLE = TypeName.create(Cloneable.class);
    private static final TypeName SERIALIZABLE = TypeName.create(Serializable.class);
    private static final TypeName RECORD = TypeName.create(Record.class);
    private static final TypeName ENUM = TypeName.create(Enum.class);
    private static final TypeName ANNOTATION = TypeName.create(java.lang.annotation.Annotation.class);

    private JdbcTypeHierarchy() {
    }

    /**
     * Returns all abstract repository methods with interface type variables resolved.
     *
     * @param repository repository interface
     * @param context current code generation round
     * @return resolved methods in declaration order
     */
    static List<TypedElementInfo> abstractMethods(TypeInfo repository, RoundContext context) {
        Map<ElementSignature, List<MethodCandidate>> methods = new LinkedHashMap<>();
        collectMethods(repository,
                       Map.of(),
                       repository.typeName().genericTypeName(),
                       false,
                       new HashSet<>(),
                       methods);
        return methods.entrySet()
                .stream()
                .map(entry -> effectiveMethod(entry.getKey(), entry.getValue(), context))
                .filter(method -> method.elementModifiers().contains(Modifier.ABSTRACT))
                .toList();
    }

    /**
     * Resolves the current declaration's type variables from its actual type arguments.
     *
     * @param typeInfo current hierarchy node
     * @param inherited substitutions inherited from the child declaration
     * @return substitutions visible while traversing this node
     */
    static Map<String, TypeName> substitutions(TypeInfo typeInfo, Map<String, TypeName> inherited) {
        return substitutions(typeInfo, inherited, rawType(typeInfo, typeInfo.typeName()));
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

    /**
     * Substitutes generic variables recursively in one type name.
     *
     * @param type source type
     * @param substitutions known generic substitutions
     * @return resolved type
     */
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

    static boolean returnTypeSubtype(TypeName candidate, TypeName existing, RoundContext context) {
        if (sameType(candidate, existing)) {
            return false;
        }
        return returnTypeAssignable(candidate, existing, context);
    }

    private static void collectMethods(TypeInfo typeInfo,
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
                // Default methods take part in override resolution even though JDBC codegen does not generate them.
                TypedElementInfo method = resolveMethod(element, substitutions, repositoryType);
                methods.computeIfAbsent(TypeHierarchy.methodSignature(method), ignored -> new ArrayList<>())
                        .add(new MethodCandidate(method, typeInfo));
            }
        }

        for (TypeInfo interfaceInfo : typeInfo.interfaceTypeInfo()) {
            ResolvedHierarchyType resolved = resolveHierarchyType(interfaceInfo,
                                                                  interfaceInfo.typeName(),
                                                                  substitutions,
                                                                  rawType);
            collectMethods(TypeInfo.builder(interfaceInfo).typeName(resolved.type()).build(),
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

    private static TypedElementInfo effectiveMethod(ElementSignature signature,
                                                    List<MethodCandidate> declarations,
                                                    RoundContext context) {
        List<MethodCandidate> candidates = declarations.stream()
                .filter(candidate -> declarations.stream()
                        .noneMatch(other -> other != candidate && isSubtype(other.owner(), candidate.owner())))
                .toList();
        MethodCandidate first = candidates.getFirst();
        for (int index = 1; index < candidates.size(); index++) {
            MethodCandidate candidate = candidates.get(index);
            if (!sameMethodAnnotations(first.method(), candidate.method())) {
                throw new CodegenException("Inherited repository method '" + signature.text()
                                                   + "' has conflicting JDBC or transaction annotations.",
                                           first.method().originatingElementValue(),
                                           candidate.method().originatingElementValue());
            }
        }

        MethodCandidate selected = null;
        for (MethodCandidate candidate : candidates) {
            boolean compatible = candidates.stream()
                    .allMatch(other -> returnTypeAssignable(candidate.method().typeName(),
                                                            adaptMethodTypeVariables(other.method(), candidate.method()),
                                                            context));
            if (compatible && (selected == null
                    || morePrecise(candidate.method(), selected.method(), candidates, context))) {
                selected = candidate;
            }
        }
        if (selected == null) {
            MethodCandidate second = candidates.size() == 1 ? first : candidates.get(1);
            throw new CodegenException("Inherited repository method '" + signature.text()
                                               + "' has incompatible return types.",
                                       first.method().originatingElementValue(),
                                       second.method().originatingElementValue());
        }
        if (selected.method().elementModifiers().contains(Modifier.ABSTRACT)) {
            validateParameterBindings(signature, candidates);
        }
        return compatibleCheckedExceptions(selected.method(), candidates, context);
    }

    private static void validateParameterBindings(ElementSignature signature, List<MethodCandidate> candidates) {
        if (candidates.size() < 2) {
            return;
        }
        Optional<String> sql = candidates.getFirst()
                .method()
                .findAnnotation(JdbcPersistenceTypes.JDBC_STATEMENT)
                .flatMap(Annotation::stringValue);
        if (sql.isEmpty() || sql.get().isBlank()) {
            return;
        }

        MethodCandidate first = candidates.getFirst();
        ParameterBindingContract firstContract = parameterBindingContract(first.method(), sql.get());
        for (int index = 1; index < candidates.size(); index++) {
            MethodCandidate candidate = candidates.get(index);
            ParameterBindingContract candidateContract = parameterBindingContract(candidate.method(), sql.get());
            if (!firstContract.equals(candidateContract)) {
                throw new CodegenException("Inherited repository method '" + signature.text()
                                                   + "' has incompatible named SQL parameter bindings.",
                                           first.method().originatingElementValue(),
                                           candidate.method().originatingElementValue());
            }
        }
    }

    private static ParameterBindingContract parameterBindingContract(TypedElementInfo method, String sql) {
        List<TypedElementInfo> parameters = method.parameterArguments();
        JdbcSqlParameterPlan plan = JdbcSqlParameterPlan.create(sql, parameters, method);
        List<ParameterBinding> bindings = plan.binds()
                .stream()
                .map(bind -> new ParameterBinding(parameterIndex(parameters, bind.parameter()),
                                                  bind.nullable(),
                                                  bind.nullJdbcTypeConstant()))
                .toList();
        return new ParameterBindingContract(bindings);
    }

    private static int parameterIndex(List<TypedElementInfo> parameters, TypedElementInfo parameter) {
        for (int index = 0; index < parameters.size(); index++) {
            if (parameters.get(index) == parameter) {
                return index;
            }
        }
        throw new IllegalStateException("The JDBC parameter plan contains a repository parameter that is not recognized.");
    }

    private static boolean isSubtype(TypeInfo candidate, TypeInfo existing) {
        return !candidate.typeName().genericTypeName().equals(existing.typeName().genericTypeName())
                && candidate.findInHierarchy(existing.typeName().genericTypeName()).isPresent();
    }

    private static boolean returnTypeAssignable(TypeName candidate, TypeName existing, RoundContext context) {
        if (sameType(candidate, existing)) {
            return true;
        }
        if (typeVariable(candidate)) {
            if (candidate.upperBounds().isEmpty()) {
                return existing.equals(TypeNames.OBJECT);
            }
            return candidate.upperBounds()
                    .stream()
                    .anyMatch(bound -> returnTypeAssignable(bound, existing, context));
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
                return returnTypeAssignable(candidateComponent, existingComponent, context);
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
        return resolveSupertype(candidate, existing.genericTypeName(), context)
                .filter(supertype -> parameterizationAssignable(supertype, existing, context))
                .isPresent();
    }

    private static Optional<TypeName> resolveSupertype(TypeName candidate,
                                                       TypeName expected,
                                                       RoundContext context) {
        if (sameErasure(candidate, expected)) {
            return Optional.of(candidate);
        }
        if (context == null) {
            return Optional.empty();
        }
        return context.typeInfo(declarationType(candidate))
                .flatMap(typeInfo -> resolveSupertype(typeInfo,
                                                      candidate,
                                                      expected,
                                                      rawType(typeInfo, candidate),
                                                      context,
                                                      new HashSet<>()));
    }

    private static Optional<TypeName> resolveSupertype(TypeInfo typeInfo,
                                                       TypeName resolvedType,
                                                       TypeName expected,
                                                       boolean rawType,
                                                       RoundContext context,
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
            Optional<TypeName> found = context.typeInfo(declarationType(implicitType))
                    .flatMap(implicitInfo -> resolveSupertype(implicitInfo,
                                                              implicitType,
                                                              expected,
                                                              rawType(implicitInfo, implicitType),
                                                              context,
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
            Optional<TypeName> found = resolveSupertype(interfaceInfo,
                                                        resolved.type(),
                                                        expected,
                                                        resolved.raw(),
                                                        context,
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
                    return resolveSupertype(superInfo,
                                            resolved.type(),
                                            expected,
                                            resolved.raw(),
                                            context,
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

    private static boolean parameterizationAssignable(TypeName candidate,
                                                       TypeName existing,
                                                       RoundContext context) {
        List<TypeName> existingArguments = existing.typeArguments();
        if (existingArguments.isEmpty()) {
            return true;
        }
        List<TypeName> candidateArguments = candidate.typeArguments();
        if (candidateArguments.isEmpty()) {
            // Java return-type substitutability permits the corresponding unchecked raw conversion.
            return true;
        }
        if (candidateArguments.size() != existingArguments.size()) {
            return false;
        }
        for (int index = 0; index < candidateArguments.size(); index++) {
            if (!typeArgumentContained(candidateArguments.get(index), existingArguments.get(index), context)) {
                return false;
            }
        }
        return true;
    }

    private static boolean typeArgumentContained(TypeName candidate, TypeName existing, RoundContext context) {
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
                            .anyMatch(candidateBound -> returnTypeAssignable(candidateBound, existingBound, context)));
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
                        .anyMatch(candidateBound -> returnTypeAssignable(existingBound, candidateBound, context)));
    }

    private static TypedElementInfo compatibleCheckedExceptions(TypedElementInfo selected,
                                                                 List<MethodCandidate> candidates,
                                                                 RoundContext context) {
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
                        .anyMatch(exception -> returnTypeAssignable(selectedException, exception, context));
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

    private static boolean morePrecise(TypedElementInfo candidate,
                                       TypedElementInfo existing,
                                       List<MethodCandidate> candidates,
                                       RoundContext context) {
        int candidatePrecision = typePrecision(candidate.typeName());
        int existingPrecision = typePrecision(existing.typeName());
        if (candidatePrecision != existingPrecision) {
            return candidatePrecision > existingPrecision;
        }
        int candidateExceptions = compatibleCheckedExceptions(candidate, candidates, context).throwsChecked().size();
        int existingExceptions = compatibleCheckedExceptions(existing, candidates, context).throwsChecked().size();
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
            while (annotationNameEnd < length && annotationNamePart(declaration.charAt(annotationNameEnd))) {
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
                // The source declaration remains valid even if the class-model parser cannot represent an annotation value.
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

    private static boolean annotationNamePart(char current) {
        return Character.isJavaIdentifierPart(current) || current == '.' || current == '$';
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

    private static boolean sameMethodAnnotations(TypedElementInfo first, TypedElementInfo second) {
        for (TypeName annotation : METHOD_ANNOTATIONS) {
            if (!first.findAnnotation(annotation).equals(second.findAnnotation(annotation))) {
                return false;
            }
        }
        return true;
    }

    private static List<TypeName> methodAnnotations() {
        List<TypeName> annotations = new ArrayList<>(4 + JdbcPersistenceTypes.TX_ANNOTATIONS.size());
        annotations.add(JdbcPersistenceTypes.JDBC_STATEMENT);
        annotations.add(JdbcPersistenceTypes.JDBC_EXECUTION);
        annotations.add(JdbcPersistenceTypes.JDBC_GENERATED_KEYS);
        annotations.add(JdbcPersistenceTypes.JDBC_ROW_MAPPER);
        annotations.addAll(JdbcPersistenceTypes.TX_ANNOTATIONS);
        return List.copyOf(annotations);
    }

    private record MethodCandidate(TypedElementInfo method, TypeInfo owner) {
    }

    private record ResolvedHierarchyType(TypeName type, boolean raw) {
    }

    private record ParameterBindingContract(List<ParameterBinding> bindings) {
    }

    private record ParameterBinding(int parameterIndex, boolean nullable, String nullJdbcTypeConstant) {
    }

    private record GenericDeclaration(String name, List<Annotation> annotations) {
    }
}
