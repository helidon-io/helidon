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
import java.util.List;

import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
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
