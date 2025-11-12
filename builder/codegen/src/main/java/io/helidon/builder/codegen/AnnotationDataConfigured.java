/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.codegen;

import java.util.Optional;
import java.util.function.Predicate;

import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.COMMON_CONFIG;
import static io.helidon.builder.codegen.Types.CONFIG;

record AnnotationDataConfigured(boolean configured, String rootPrefix, boolean isRoot, TypeName configType) {
    static AnnotationDataConfigured create(TypeInfo typeInfo) {
        boolean configured = false;
        boolean isRoot = false;
        String prefix = null;
        TypeName configType = COMMON_CONFIG;

        if (typeInfo.hasAnnotation(Types.PROTOTYPE_CONFIGURED)) {
            configured = true;

            Annotation annotation = typeInfo.annotation(Types.PROTOTYPE_CONFIGURED);
            // if the annotation is present, the value has to be defined (may be empty string)
            prefix = annotation.stringValue().orElse(null);
            if (prefix != null) {
                isRoot = annotation.booleanValue("root").orElse(true);
            }

            configType = configType(typeInfo).orElse(COMMON_CONFIG);
        }

        return new AnnotationDataConfigured(configured, prefix, isRoot, configType);
    }

    private static Optional<TypeName> configType(TypeInfo typeInfo) {
        Optional<TypeName> typed = typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                .filter(AnnotationDataConfigured::isConfigType)
                .map(AnnotationDataConfigured::configType)
                .findFirst();

        if (typed.isPresent()) {
            return typed;
        }
        typed = typeInfo.superTypeInfo()
                .flatMap(AnnotationDataConfigured::configType);

        if (typed.isPresent()) {
            return typed;
        }

        for (TypeInfo info : typeInfo.interfaceTypeInfo()) {
            typed = configType(info);
            if (typed.isPresent()) {
                return typed;
            }
        }
        return Optional.empty();
    }

    private static boolean isConfigType(TypedElementInfo elementInfo) {
        var actualType = elementInfo.typeName();
        boolean found = actualType.equals(CONFIG)
                || actualType.equals(COMMON_CONFIG);
        if (found) {
            return true;
        }

        if (actualType.isOptional() && actualType.typeArguments().size() == 1) {
            actualType = actualType.typeArguments().get(0);
            return actualType.equals(CONFIG)
                    || actualType.equals(COMMON_CONFIG);
        }
        return false;
    }

    private static TypeName configType(TypedElementInfo elementInfo) {
        var actualType = elementInfo.typeName();

        if (actualType.isOptional() && actualType.typeArguments().size() == 1) {
            actualType = actualType.typeArguments().getFirst();
            if (actualType.equals(CONFIG)
                    || actualType.equals(COMMON_CONFIG)) {
                return actualType;
            }
        }

        return actualType;
    }
}
