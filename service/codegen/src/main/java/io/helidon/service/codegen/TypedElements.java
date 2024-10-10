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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.AccessModifier;
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

        for (TypedElementInfo declaredMethod : declaredElements) {
            List<TypedElements.DeclaredElement> interfaceMethods = new ArrayList<>();

            if (declaredMethod.kind() == ElementKind.METHOD) {
                // now find the same method on any interface (if declared there)
                for (TypeInfo info : typeInfo.interfaceTypeInfo()) {
                    info.elementInfo()
                            .stream()
                            .filter(ElementInfoPredicates::isMethod)
                            .filter(not(ElementInfoPredicates::isStatic))
                            .filter(not(ElementInfoPredicates::isPrivate))
                            .filter(it -> declaredMethod.signature().equals(it.signature()))
                            .findFirst()
                            .ifPresent(it -> interfaceMethods.add(new TypedElements.DeclaredElement(info, it)));
                }
            }
            result.add(new TypedElements.ElementMeta(declaredMethod, interfaceMethods));
        }

        return result;
    }

    static List<TypedElements.ElementMeta> gatherElements(CodegenContext ctx, Collection<ResolvedType> contracts, TypeInfo typeInfo) {
        List<TypedElements.ElementMeta> result = new ArrayList<>();
        Set<ElementSignature> processedSignatures = new HashSet<>();

        typeInfo.elementInfo()
                .stream()
                .filter(it -> it.kind() != ElementKind.CLASS)
                .forEach(declaredElement -> {
                    List<TypedElements.DeclaredElement> interfaceMethods = new ArrayList<>();

                    if (declaredElement.kind() == ElementKind.METHOD) {
                        // now find the same method on any interface (if declared there)
                        for (TypeInfo info : typeInfo.interfaceTypeInfo()) {
                            if (!contracts.contains(ResolvedType.create(info.typeName()))) {
                                // only interested in contracts
                                continue;
                            }
                            info.elementInfo()
                                    .stream()
                                    .filter(ElementInfoPredicates::isMethod)
                                    .filter(not(ElementInfoPredicates::isStatic))
                                    .filter(not(ElementInfoPredicates::isPrivate))
                                    .filter(it -> declaredElement.signature().equals(it.signature()))
                                    .findFirst()
                                    .ifPresent(it -> interfaceMethods.add(new TypedElements.DeclaredElement(info, it)));
                        }
                    }
                    result.add(new TypedElements.ElementMeta(declaredElement, interfaceMethods));
                    processedSignatures.add(declaredElement.signature());
                });

        // we have gathered all the declared elements, now let's gather inherited elements (default methods etc.)
        for (TypeName contract : contracts) {
            Optional<TypeInfo> contractTypeInfo = ctx.typeInfo(contract);
            if (contractTypeInfo.isPresent()) {
                TypeInfo inheritedContract = contractTypeInfo.get();
                inheritedContract.elementInfo()
                        .stream()
                        .filter(it -> it.kind() != ElementKind.CLASS)
                        .forEach(superElement -> {
                            if (!processedSignatures.add(superElement.signature())) {
                                // already processed
                                return;
                            }
                            List<TypedElements.DeclaredElement> interfaceMethods = new ArrayList<>();
                            if (superElement.kind() == ElementKind.METHOD) {
                                interfaceMethods.add(new DeclaredElement(inheritedContract, superElement));
                            }
                            result.add(new TypedElements.ElementMeta(superElement, interfaceMethods));
                        });
            }
        }

        return result;
    }

    record ElementMeta(TypedElementInfo element,
                       List<DeclaredElement> interfaceMethods) {
        ElementMeta(TypedElementInfo element) {
            this(element, List.of());
        }
    }

    record DeclaredElement(TypeInfo iface,
                           TypedElementInfo element) {
    }
}
