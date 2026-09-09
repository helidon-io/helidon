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

package io.helidon.microprofile.security;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import org.glassfish.jersey.internal.util.ReflectionHelper;

final class HandlingMethodResolver {

    private HandlingMethodResolver() {
    }

    static Method resolve(Class<?> handlingClass, Method definitionMethod, Method observedMethod) {
        if (!observedMethod.isBridge() && observedMethod.getDeclaringClass().equals(handlingClass)) {
            return observedMethod;
        }

        try {
            List<Method> candidates = Arrays.stream(handlingClass.getMethods())
                    .filter(candidate -> !candidate.isBridge())
                    .filter(candidate -> candidate.getName().equals(observedMethod.getName()))
                    .filter(candidate -> candidate.getParameterCount() == observedMethod.getParameterCount())
                    .toList();
            Method exactMethod = select(candidates,
                                        observedMethod.getParameterTypes(),
                                        observedMethod.getReturnType(),
                                        false,
                                        handlingClass);
            if (exactMethod != null) {
                return exactMethod;
            }
            if (!observedMethod.isBridge()) {
                return observedMethod;
            }

            Class<?>[] parameterTypes = resolveParameterTypes(handlingClass, definitionMethod);
            Class<?> returnType = resolveType(handlingClass,
                                              definitionMethod,
                                              definitionMethod.getReturnType(),
                                              definitionMethod.getGenericReturnType());
            Method resolvedMethod = select(candidates, parameterTypes, returnType, true, handlingClass);
            return resolvedMethod == null ? observedMethod : resolvedMethod;
        } catch (RuntimeException | LinkageError e) {
            return observedMethod;
        }
    }

    private static Class<?>[] resolveParameterTypes(Class<?> handlingClass, Method definitionMethod) {
        Class<?>[] rawTypes = definitionMethod.getParameterTypes();
        Type[] genericTypes = definitionMethod.getGenericParameterTypes();
        Class<?>[] resolvedTypes = new Class<?>[rawTypes.length];
        for (int i = 0; i < rawTypes.length; i++) {
            resolvedTypes[i] = resolveType(handlingClass, definitionMethod, rawTypes[i], genericTypes[i]);
        }
        return resolvedTypes;
    }

    private static Class<?> resolveType(Class<?> handlingClass,
                                        Method definitionMethod,
                                        Class<?> rawType,
                                        Type genericType) {
        return ReflectionHelper.resolveGenericType(handlingClass,
                                                   definitionMethod.getDeclaringClass(),
                                                   rawType,
                                                   genericType)
                .rawClass();
    }

    private static Method select(List<Method> candidates,
                                 Class<?>[] parameterTypes,
                                 Class<?> returnType,
                                 boolean allowCovariantReturn,
                                 Class<?> handlingClass) {
        List<Method> matching = candidates.stream()
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), parameterTypes))
                .filter(candidate -> candidate.getReturnType().equals(returnType)
                        || (allowCovariantReturn && returnType.isAssignableFrom(candidate.getReturnType())))
                .toList();
        if (matching.size() == 1) {
            return matching.get(0);
        }
        List<Method> declared = matching.stream()
                .filter(candidate -> candidate.getDeclaringClass().equals(handlingClass))
                .toList();
        return declared.size() == 1 ? declared.get(0) : null;
    }
}
