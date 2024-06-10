/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.processor;

import java.util.List;
import java.util.function.Predicate;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

/**
 * Commonly used predicates to filter typed element info.
 *
 * @see io.helidon.common.types.TypedElementInfo
 * @see io.helidon.common.types.TypeInfo#elementInfo()
 * @deprecated use {@code helidon-codegen} instead.
 */
@Deprecated(forRemoval = true, since = "4.1.0")
public final class ElementInfoPredicates {
    /**
     * Predicate for method element kind.
     *
     * @param element typed element info to test
     * @return whether the element represents a method
     */
    public static boolean isMethod(TypedElementInfo element) {
        return ElementKind.METHOD == element.kind();
    }

    /**
     * Predicate for static modifier.
     *
     * @param element typed element info to test
     * @return whether the element has static modifier
     */
    public static boolean isStatic(TypedElementInfo element) {
        return element.elementModifiers().contains(Modifier.STATIC);
    }

    /**
     * Predicate for private modifier.
     *
     * @param element typed element info to test
     * @return whether the element has private modifier
     */
    public static boolean isPrivate(TypedElementInfo element) {
        return AccessModifier.PRIVATE == element.accessModifier();
    }

    /**
     * Predicate for public modifier.
     *
     * @param element typed element info to test
     * @return whether the element has public modifier
     */
    public static boolean isPublic(TypedElementInfo element) {
        return AccessModifier.PUBLIC == element.accessModifier();
    }


    /**
     * Predicate for default modifier (default methods on interfaces).
     *
     * @param element typed element info to test
     * @return whether the element has default modifier
     */
    public static boolean isDefault(TypedElementInfo element) {
        return element.elementModifiers().contains(Modifier.DEFAULT);
    }

    /**
     * Predicate for void methods.
     *
     * @param element typed element info to test
     * @return whether the element has void return type (both primitive and boxed)
     */
    public static boolean isVoid(TypedElementInfo element) {
        TypeName typeName = element.typeName();
        return TypeNames.PRIMITIVE_VOID.equals(typeName) || TypeNames.BOXED_VOID.equals(typeName);
    }


    /**
     * Predicate for element with no arguments (suitable for methods).
     *
     * @param element typed element info to test
     * @return whether the element has no arguments
     */
    public static boolean hasNoArgs(TypedElementInfo element) {
        return element.parameterArguments().isEmpty();
    }

    /**
     * Predicate for an existence of an annotation.
     *
     * @param annotation Annotation to check for
     * @return a new predicate for the provided annotation
     */
    public static Predicate<TypedElementInfo> hasAnnotation(TypeName annotation) {
        return element -> element.hasAnnotation(annotation);
    }

    /**
     * Predicate for element name (such as method name, or field name).
     * @param name name of the element to check for
     * @return a new predicate for the provided element name
     */
    public static Predicate<TypedElementInfo> elementName(String name) {
        return element -> name.equals(element.elementName());
    }

    /**
     * Predicate for element with the specified parameters types (suitable for methods).
     * The method must have exactly the same number and types of parameters.
     *
     * @param paramTypes expected parameter types
     * @return a new predicate for the provided parameter types
     */
    public static Predicate<TypedElementInfo> hasParams(TypeName... paramTypes) {
        return element -> {
            List<TypedElementInfo> arguments = element.parameterArguments();
            if (paramTypes.length != arguments.size()) {
                return false;
            }
            for (int i = 0; i < paramTypes.length; i++) {
                TypeName paramType = paramTypes[i];
                if (!paramType.equals(arguments.get(i).typeName())) {
                    return false;
                }
            }
            return true;
        };
    }

    private ElementInfoPredicates() {
    }
}
