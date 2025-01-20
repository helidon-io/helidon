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

package io.helidon.metadata.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Reflection based handler of {@link io.helidon.common.types.TypedElementInfo},
 * and reflection based {@link java.lang.reflect.Method}, {@link java.lang.reflect.Parameter},
 * and {@link java.lang.reflect.Field}.
 */
public final class TypedElementFactory {
    private TypedElementFactory() {
    }

    /**
     * Create an element info from a field.
     *
     * @param field field to analyze
     * @return element info for the provided field
     */
    public static TypedElementInfo create(Field field) {
        int modifiers = field.getModifiers();

        var builder = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .annotations(AnnotationFactory.create(field))
                .accessModifier(accessModifier(modifiers))
                .elementModifiers(fieldModifiers(modifiers))
                .typeName(TypeName.create(field.getGenericType()))
                .elementTypeAnnotations(AnnotationFactory.create(field))
                .elementName(field.getName())
                .originatingElement(field)
                .enclosingType(TypeName.create(field.getDeclaringClass()));

        return builder.build();
    }

    /**
     * Create an element info from method.
     *
     * @param method method to analyze
     * @return element info for the provided method
     */
    public static TypedElementInfo create(Method method) {
        int modifiers = method.getModifiers();

        var builder = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .annotations(AnnotationFactory.create(method))
                .accessModifier(accessModifier(modifiers))
                .elementModifiers(methodModifiers(method, modifiers))
                .throwsChecked(checkedExceptions(method))
                .typeName(TypeName.create(method.getGenericReturnType()))
                .elementTypeAnnotations(AnnotationFactory.create(method.getAnnotatedReturnType()))
                .elementName(method.getName())
                .originatingElement(method)
                .parameterArguments(toArguments(method))
                .enclosingType(TypeName.create(method.getDeclaringClass()));

        return builder.build();
    }

    /**
     * Create an element info from parameter.
     *
     * @param parameter parameter to analyze
     * @return element info for the provided parameter
     */
    public static TypedElementInfo create(Parameter parameter) {
        int modifiers = parameter.getModifiers();

        var builder = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .annotations(AnnotationFactory.create(parameter))
                .accessModifier(accessModifier(modifiers))
                .elementModifiers(paramModifiers(modifiers))
                .typeName(TypeName.create(parameter.getParameterizedType()))
                .elementTypeAnnotations(AnnotationFactory.create(parameter.getAnnotatedType()))
                .elementName(parameter.getName())
                .originatingElement(parameter)
                .enclosingType(TypeName.create(parameter.getDeclaringExecutable().getDeclaringClass()));

        return builder.build();
    }

    private static List<TypedElementInfo> toArguments(Method method) {
        List<TypedElementInfo> result = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            result.add(create(parameter));
        }

        return result;
    }

    private static Set<TypeName> checkedExceptions(Method method) {
        return Stream.of(method.getExceptionTypes())
                .filter(Exception.class::isAssignableFrom)
                .map(TypeName::create)
                .collect(Collectors.toSet());
    }

    private static Set<io.helidon.common.types.Modifier> paramModifiers(int modifiers) {
        Set<io.helidon.common.types.Modifier> result = EnumSet.noneOf(io.helidon.common.types.Modifier.class);
        elementModifiers(modifiers, result);
        return result;
    }

    private static Set<io.helidon.common.types.Modifier> methodModifiers(Method method, int modifiers) {
        Set<io.helidon.common.types.Modifier> result = EnumSet.noneOf(io.helidon.common.types.Modifier.class);

        elementModifiers(modifiers, result);

        if (method.isDefault()) {
            result.add(io.helidon.common.types.Modifier.DEFAULT);
        }
        if (Modifier.isAbstract(modifiers)) {
            result.add(io.helidon.common.types.Modifier.ABSTRACT);
        }
        if (Modifier.isSynchronized(modifiers)) {
            result.add(io.helidon.common.types.Modifier.SYNCHRONIZED);
        }
        if (Modifier.isNative(modifiers)) {
            result.add(io.helidon.common.types.Modifier.NATIVE);
        }

        return result;
    }

    private static Set<io.helidon.common.types.Modifier> fieldModifiers(int modifiers) {
        Set<io.helidon.common.types.Modifier> result = EnumSet.noneOf(io.helidon.common.types.Modifier.class);

        elementModifiers(modifiers, result);

        if (Modifier.isTransient(modifiers)) {
            result.add(io.helidon.common.types.Modifier.TRANSIENT);
        }
        if (Modifier.isAbstract(modifiers)) {
            result.add(io.helidon.common.types.Modifier.ABSTRACT);
        }
        if (Modifier.isSynchronized(modifiers)) {
            result.add(io.helidon.common.types.Modifier.SYNCHRONIZED);
        }
        if (Modifier.isNative(modifiers)) {
            result.add(io.helidon.common.types.Modifier.NATIVE);
        }

        return result;
    }

    private static void elementModifiers(int modifiers, Set<io.helidon.common.types.Modifier> result) {
        if (Modifier.isFinal(modifiers)) {
            result.add(io.helidon.common.types.Modifier.FINAL);
        }
        if (Modifier.isStatic(modifiers)) {
            result.add(io.helidon.common.types.Modifier.STATIC);
        }
    }

    private static AccessModifier accessModifier(int modifiers) {
        if (Modifier.isPublic(modifiers)) {
            return AccessModifier.PUBLIC;
        }
        if (Modifier.isProtected(modifiers)) {
            return AccessModifier.PROTECTED;
        }
        if (Modifier.isPrivate(modifiers)) {
            return AccessModifier.PRIVATE;
        }
        return AccessModifier.PACKAGE_PRIVATE;
    }
}
