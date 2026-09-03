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

package io.helidon.declarative.codegen.openapi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_REQUIREMENTS_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_REQUIREMENT_ANNOTATION;
import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_SECURITY_SCHEME_REQUIREMENT_ANNOTATION;

final class OpenApiAnnotationHierarchy {
    private static final Set<TypeName> SECURITY_REQUIREMENT_ANNOTATIONS = Set.of(OPENAPI_SECURITY_REQUIREMENTS_ANNOTATION,
                                                                                OPENAPI_SECURITY_REQUIREMENT_ANNOTATION,
                                                                                OPENAPI_SECURITY_SCHEME_REQUIREMENT_ANNOTATION);

    private OpenApiAnnotationHierarchy() {
    }

    static List<List<Annotation>> endpointSecurityAnnotationGroups(TypeInfo endpointType,
                                                                   Function<Collection<Annotation>, ?> semanticKey) {
        Collection<Annotation> declared = endpointType.annotations();
        if (clearsSecurity(declared)) {
            return List.of(List.copyOf(declared));
        }
        List<Annotation> direct = withMetaAnnotations(declared);
        if (hasSecurityRequirementAnnotations(direct)) {
            return List.of(direct);
        }

        List<TypeSecurityAnnotations> candidates = new ArrayList<>();
        Set<TypeName> processedTypes = new HashSet<>();
        endpointType.superTypeInfo()
                .ifPresent(it -> collectEndpointSecurityAnnotations(candidates, processedTypes, it));
        endpointType.interfaceTypeInfo()
                .forEach(it -> collectEndpointSecurityAnnotations(candidates, processedTypes, it));
        List<TypeSecurityAnnotations> applicableCandidates = candidates.stream()
                .filter(candidate -> !isOverriddenCandidate(candidate, candidates))
                .toList();
        long clearCandidates = applicableCandidates.stream()
                .filter(TypeSecurityAnnotations::clearsSecurity)
                .count();
        if (clearCandidates > 0 && clearCandidates < applicableCandidates.size()) {
            throw new CodegenException("Conflicting inherited OpenAPI security requirements on "
                                               + endpointType.typeName().fqName());
        }
        Set<Object> semanticKeys = new HashSet<>();
        Set<Annotation> annotations = new HashSet<>();
        return applicableCandidates.stream()
                .filter(candidate -> semanticKeys.add(semanticKey.apply(candidate.annotations())))
                .map(candidate -> candidate.annotations().stream()
                        .filter(annotations::add)
                        .toList())
                .filter(it -> !it.isEmpty())
                .toList();
    }

    static List<Annotation> withMetaAnnotations(Collection<Annotation> annotations) {
        List<Annotation> result = new ArrayList<>();
        for (Annotation rootAnnotation : annotations) {
            Deque<Map.Entry<Annotation, Set<TypeName>>> remaining = new ArrayDeque<>();
            remaining.add(Map.entry(rootAnnotation, new HashSet<>()));
            while (!remaining.isEmpty()) {
                Map.Entry<Annotation, Set<TypeName>> current = remaining.removeFirst();
                Annotation annotation = current.getKey();
                Set<TypeName> path = current.getValue();
                if (path.add(annotation.typeName())) {
                    result.add(annotation);
                    annotation.metaAnnotations()
                            .forEach(it -> remaining.add(Map.entry(it, new HashSet<>(path))));
                }
            }
        }
        return result;
    }

    private static void collectEndpointSecurityAnnotations(List<TypeSecurityAnnotations> candidates,
                                                           Set<TypeName> processedTypes,
                                                           TypeInfo type) {
        if (!processedTypes.add(type.typeName().genericTypeName())) {
            return;
        }

        List<Annotation> annotations = withMetaAnnotations(type.annotations()).stream()
                .filter(it -> SECURITY_REQUIREMENT_ANNOTATIONS.contains(it.typeName()))
                .toList();
        if (!annotations.isEmpty()) {
            candidates.add(new TypeSecurityAnnotations(type, annotations));
        }
        type.superTypeInfo().ifPresent(it -> collectEndpointSecurityAnnotations(candidates, processedTypes, it));
        type.interfaceTypeInfo().forEach(it -> collectEndpointSecurityAnnotations(candidates, processedTypes, it));
    }

    private static boolean hasSecurityRequirementAnnotations(Collection<Annotation> annotations) {
        return annotations.stream()
                .map(Annotation::typeName)
                .anyMatch(SECURITY_REQUIREMENT_ANNOTATIONS::contains);
    }

    private static boolean clearsSecurity(Collection<Annotation> annotations) {
        List<Annotation> securityAnnotations = annotations.stream()
                .filter(it -> SECURITY_REQUIREMENT_ANNOTATIONS.contains(it.typeName()))
                .toList();
        return !securityAnnotations.isEmpty()
                && securityAnnotations.stream()
                        .allMatch(it -> OPENAPI_SECURITY_REQUIREMENTS_ANNOTATION.equals(it.typeName())
                                && it.annotationValues()
                                        .filter(List::isEmpty)
                                        .isPresent());
    }

    private static boolean isOverriddenCandidate(TypeSecurityAnnotations candidate,
                                                 List<TypeSecurityAnnotations> candidates) {
        TypeName candidateType = candidate.declaringType().typeName().genericTypeName();
        return candidates.stream()
                .map(TypeSecurityAnnotations::declaringType)
                .filter(it -> !it.typeName().genericTypeName().equals(candidateType))
                .anyMatch(it -> it.findInHierarchy(candidateType).isPresent());
    }

    private record TypeSecurityAnnotations(TypeInfo declaringType, List<Annotation> annotations) {
        private boolean clearsSecurity() {
            return OpenApiAnnotationHierarchy.clearsSecurity(annotations);
        }
    }
}
