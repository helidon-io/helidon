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

package io.helidon.declarative.codegen.grpc;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.common.Api;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static java.util.function.Predicate.not;

/**
 * Resolves the effective instance-method set of a declarative gRPC endpoint.
 */
@Api.Internal
public final class GrpcMethodHierarchy {
    private GrpcMethodHierarchy() {
    }

    /**
     * Resolve effective instance methods, including the complete superclass and interface hierarchy.
     *
     * @param endpoint endpoint type
     * @return effective methods with generic arguments resolved for the endpoint
     */
    public static List<TypedElementInfo> methods(TypeInfo endpoint) {
        Map<ElementSignature, Candidate> methods = new LinkedHashMap<>();
        collect(endpoint,
                endpoint.typeName(),
                endpoint,
                methods,
                new HashSet<>(),
                0,
                true);
        return methods.values()
                .stream()
                .map(Candidate::method)
                .toList();
    }

    private static void collect(TypeInfo type,
                                TypeName resolvedType,
                                TypeInfo endpoint,
                                Map<ElementSignature, Candidate> methods,
                                Set<TypeName> processedTypes,
                                int depth,
                                boolean endpointDeclaration) {
        if (!processedTypes.add(resolvedType)) {
            return;
        }

        Map<String, TypeName> typeArguments = typeArgumentMapping(type, resolvedType);
        boolean interfaceDeclaration = type.kind() == ElementKind.INTERFACE;
        for (TypedElementInfo method : type.elementInfo()) {
            if (!ElementInfoPredicates.isMethod(method)
                    || ElementInfoPredicates.isStatic(method)
                    || ElementInfoPredicates.isPrivate(method)
                    || inaccessibleInheritedMethod(method, type, endpoint, endpointDeclaration)) {
                continue;
            }
            TypedElementInfo resolvedMethod = resolveTypeArguments(method, typeArguments, endpoint.typeName());
            Candidate candidate = new Candidate(type,
                                                resolvedMethod,
                                                interfaceDeclaration,
                                                depth,
                                                endpointDeclaration);
            methods.compute(resolvedMethod.signature(), (_, current) -> moreSpecific(candidate, current));
        }

        type.superTypeInfo().ifPresent(superType -> collect(superType,
                                                            resolveTypeArguments(superType.typeName(), typeArguments),
                                                            endpoint,
                                                            methods,
                                                            processedTypes,
                                                            depth + 1,
                                                            false));
        for (TypeInfo interfaceType : type.interfaceTypeInfo()) {
            collect(interfaceType,
                    resolveTypeArguments(interfaceType.typeName(), typeArguments),
                    endpoint,
                    methods,
                    processedTypes,
                    depth + 1,
                    false);
        }
    }

    private static boolean inaccessibleInheritedMethod(TypedElementInfo method,
                                                       TypeInfo declaringType,
                                                       TypeInfo endpoint,
                                                       boolean endpointDeclaration) {
        return !endpointDeclaration
                && method.accessModifier() != AccessModifier.PUBLIC
                && !declaringType.typeName().packageName().equals(endpoint.typeName().packageName());
    }

    private static Candidate moreSpecific(Candidate candidate, Candidate current) {
        if (current == null || candidate.endpointDeclaration()) {
            return candidate;
        }
        if (current.endpointDeclaration()) {
            return current;
        }
        if (candidate.interfaceDeclaration() != current.interfaceDeclaration()) {
            return candidate.interfaceDeclaration() ? current : candidate;
        }
        if (!candidate.interfaceDeclaration()) {
            return candidate.depth() < current.depth() ? candidate : current;
        }
        return candidate.declaringType()
                .findInHierarchy(current.declaringType().typeName().genericTypeName())
                .isPresent()
                ? candidate
                : current;
    }

    private static TypedElementInfo resolveTypeArguments(TypedElementInfo method,
                                                         Map<String, TypeName> typeArguments,
                                                         TypeName endpointType) {
        Map<String, TypeName> methodTypeArguments = typeArguments;
        if (!method.typeParameters().isEmpty() && !typeArguments.isEmpty()) {
            methodTypeArguments = new LinkedHashMap<>(typeArguments);
            method.typeParameters()
                    .stream()
                    .map(TypeName::className)
                    .forEach(methodTypeArguments::remove);
        }
        Map<String, TypeName> substitutions = methodTypeArguments;
        List<TypedElementInfo> parameters = method.parameterArguments()
                .stream()
                .map(parameter -> TypedElementInfo.builder(parameter)
                        .typeName(resolveTypeArguments(parameter.typeName(), substitutions))
                        .build())
                .toList();
        return TypedElementInfo.builder(method)
                .typeName(resolveTypeArguments(method.typeName(), substitutions))
                .parameterArguments(parameters)
                .enclosingType(endpointType.genericTypeName())
                .build();
    }

    private static TypeName resolveTypeArguments(TypeName typeName, Map<String, TypeName> typeArguments) {
        if (typeName.generic()) {
            TypeName resolved = typeArguments.get(typeName.className().trim());
            if (resolved != null) {
                return TypeHierarchy.mergeTypeNameAnnotations(resolved, typeName);
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

    private static Map<String, TypeName> typeArgumentMapping(TypeInfo type, TypeName resolvedType) {
        List<String> typeParameters = type.typeName().typeParameters();
        List<TypeName> typeArguments = resolvedType.typeArguments();
        if (typeArguments.isEmpty()) {
            typeArguments = type.typeName().typeArguments();
        }
        if (typeParameters.isEmpty()) {
            typeParameters = type.declaredType()
                    .typeArguments()
                    .stream()
                    .filter(TypeName::generic)
                    .filter(not(TypeName::wildcard))
                    .map(TypeName::className)
                    .map(GrpcMethodHierarchy::genericTypeName)
                    .toList();
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

    private static String genericTypeName(String typeParameter) {
        String name = typeParameter.trim();
        int index = name.indexOf(' ');
        return index == -1 ? name : name.substring(0, index);
    }

    private record Candidate(TypeInfo declaringType,
                             TypedElementInfo method,
                             boolean interfaceDeclaration,
                             int depth,
                             boolean endpointDeclaration) {
    }
}
