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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.TypeHierarchy;
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

    private JdbcTypeHierarchy() {
    }

    /**
     * Returns all abstract repository methods with interface type variables resolved.
     *
     * @param repository repository interface
     * @param context code generation context
     * @return resolved methods in declaration order
     */
    static List<TypedElementInfo> abstractMethods(TypeInfo repository, CodegenContext context) {
        Map<ElementSignature, MethodCandidate> methods = new LinkedHashMap<>();
        collectMethods(repository,
                       Map.of(),
                       repository.typeName().genericTypeName(),
                       0,
                       new HashMap<>(),
                       methods,
                       context);
        return methods.values()
                .stream()
                .map(MethodCandidate::method)
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
        List<TypeName> arguments = typeInfo.typeName().typeArguments();
        if (arguments.isEmpty()) {
            return inherited;
        }
        List<String> parameters = typeInfo.declaredType().typeParameters();
        if (parameters.isEmpty()) {
            // Some type metadata exposes declaration variables as generic arguments instead of named parameters.
            parameters = typeInfo.declaredType()
                    .typeArguments()
                    .stream()
                    .filter(TypeName::generic)
                    .map(TypeName::className)
                    .toList();
        }
        Map<String, TypeName> result = new LinkedHashMap<>(inherited);
        for (int index = 0; index < arguments.size() && index < parameters.size(); index++) {
            String parameter = parameters.get(index);
            int boundStart = parameter.indexOf(' ');
            result.put(boundStart < 0 ? parameter : parameter.substring(0, boundStart),
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
        TypeName replacement = type.generic()
                && !type.array()
                && type.typeArguments().isEmpty()
                ? substitutions.get(type.className())
                : null;
        if (replacement != null) {
            return replacement;
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

    private static void collectMethods(TypeInfo typeInfo,
                                       Map<String, TypeName> inheritedSubstitutions,
                                       TypeName repositoryType,
                                       int depth,
                                       Map<TypeName, Integer> visited,
                                       Map<ElementSignature, MethodCandidate> methods,
                                       CodegenContext context) {
        Integer previousDepth = visited.get(typeInfo.typeName());
        // Revisit a declaration only when a shorter path can supply a closer override candidate.
        if (previousDepth != null && previousDepth <= depth) {
            return;
        }
        visited.put(typeInfo.typeName(), depth);

        Map<String, TypeName> substitutions = substitutions(typeInfo, inheritedSubstitutions);
        for (TypedElementInfo element : typeInfo.elementInfo()) {
            if (element.kind() == ElementKind.METHOD
                    && isOverrideCandidate(element)) {
                // Default methods take part in override resolution even though JDBC codegen does not generate them.
                TypedElementInfo method = resolveMethod(element, substitutions, repositoryType);
                merge(methods,
                      TypeHierarchy.methodSignature(method),
                      new MethodCandidate(method, typeInfo, depth),
                      context);
            }
        }

        for (TypeInfo interfaceInfo : typeInfo.interfaceTypeInfo()) {
            TypeName resolvedType = substitute(interfaceInfo.typeName(), substitutions);
            collectMethods(TypeInfo.builder(interfaceInfo).typeName(resolvedType).build(),
                           substitutions,
                           repositoryType,
                           depth + 1,
                           visited,
                           methods,
                           context);
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

    private static void merge(Map<ElementSignature, MethodCandidate> methods,
                              ElementSignature signature,
                              MethodCandidate candidate,
                              CodegenContext context) {
        MethodCandidate existing = methods.get(signature);
        if (existing == null) {
            methods.put(signature, candidate);
            return;
        }
        // Apply Java override precedence first. Unrelated declarations at the same depth must agree on JDBC and
        // transaction annotations before a covariant return type can select the winner.
        if (isSubtype(candidate.owner(), existing.owner())) {
            methods.put(signature, candidate);
            return;
        }
        if (isSubtype(existing.owner(), candidate.owner())) {
            return;
        }
        if (candidate.depth() < existing.depth()) {
            methods.put(signature, candidate);
            return;
        }
        if (candidate.depth() > existing.depth()) {
            return;
        }
        if (!sameMethodAnnotations(existing.method(), candidate.method())) {
            throw new CodegenException("Inherited repository methods have conflicting JDBC or transaction annotations: "
                                               + signature.text(),
                                       existing.method().originatingElementValue(),
                                       candidate.method().originatingElementValue());
        }
        if (isSubtype(candidate.method().typeName(), existing.method().typeName(), context)) {
            methods.put(signature, candidate);
        }
    }

    private static boolean isSubtype(TypeInfo candidate, TypeInfo existing) {
        return !candidate.typeName().genericTypeName().equals(existing.typeName().genericTypeName())
                && candidate.findInHierarchy(existing.typeName().genericTypeName()).isPresent();
    }

    private static boolean isSubtype(TypeName candidate, TypeName existing, CodegenContext context) {
        if (candidate.equals(existing)) {
            return false;
        }
        if (existing.equals(TypeNames.OBJECT) && !candidate.primitive()) {
            return true;
        }
        return context.typeInfo(candidate.genericTypeName())
                .flatMap(typeInfo -> typeInfo.findInHierarchy(existing.genericTypeName()))
                .isPresent();
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

    private record MethodCandidate(TypedElementInfo method, TypeInfo owner, int depth) {
    }
}
