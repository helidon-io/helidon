/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.processor;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeValues;
import io.helidon.common.types.TypedElementInfo;

final class TypeInfoPredicates {
    private TypeInfoPredicates() {
    }

    static boolean isMethod(TypedElementInfo element) {
        return TypeValues.KIND_METHOD.equals(element.elementTypeKind());
    }

    static boolean isStatic(TypedElementInfo element) {
        // TODO fix once TypeInfo modifier constants are valid
        return element.modifiers().contains("static");
    }

    static boolean isPrivate(TypedElementInfo element) {
        // TODO fix once TypeInfo modifier constants are valid
        return element.modifiers().contains("private");
    }

    static boolean isDefault(TypedElementInfo element) {
        // TODO fix once TypeInfo modifier constants are valid
        return element.modifiers().contains("default");
    }

    static boolean hasNoArgs(TypedElementInfo element) {
        return element.parameterArguments().isEmpty();
    }

    static Predicate<TypedElementInfo> hasAnnotation(TypeName annotation) {
        return element -> element.hasAnnotation(annotation);
    }

    static Predicate<TypedElementInfo> methodName(String methodName) {
        return element -> methodName.equals(element.elementName());
    }

    static Predicate<TypedElementInfo> hasParams(TypeName... paramTypes) {
        return element -> {
            List<TypedElementInfo> arguments = element.parameterArguments();
            if (paramTypes.length != arguments.size()) {
                return false;
            }
            for (int i = 0; i < paramTypes.length; i++) {
                TypeName paramType = paramTypes[i];
                if (!paramType.equals(arguments.get(i))) {
                    return false;
                }
            }
            return true;
        };
    }

    /**
     * Make sure the method should not be ignored.
     *
     * @param ignoredMethods ignored method signatures (such as methods that are defined as default)
     * @param ignoredNames ignored method names (equals, hashCode etc.)
     * @return a new predicate
     */
    static Predicate<? super TypedElementInfo> notIgnoredMethod(Set<TypeContext.MethodSignature> ignoredMethods,
                                                                Set<String> ignoredNames) {
        return it -> {
            // name is enough, signature is not important
            if (ignoredNames.contains(it.elementName())) {
                return false;
            }

            return !ignoredMethods.contains(TypeContext.MethodSignature.create(it));
        };
    }
}
