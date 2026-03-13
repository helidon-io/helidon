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
package io.helidon.common;

import java.lang.System.Logger.Level;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Deprecation utility.
 * <p>
 * <b>This is NOT part of any supported API. If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or deletion without notice.</b>
 */
@Api.Internal
public final class DeprecationSupport {

    private static final System.Logger LOGGER = System.getLogger(DeprecationSupport.class.getName());
    private static final Set<String> WARNINGS = ConcurrentHashMap.newKeySet();

    private DeprecationSupport() {
    }

    /**
     * Test if a method is overridden.
     *
     * @param obj            object whose class should override the method
     * @param declaringClass class that declares the method to be overridden
     * @param methodName     the name of the method to be overridden
     * @param parameterTypes the parameter types of the method to be overridden
     * @return {@code true}  if overridden, {@code false} otherwise
     * @throws IllegalStateException         if the method is not found using reflection
     */
    public static boolean isOverridden(Object obj, Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        try {
            var classMethod = obj.getClass().getMethod(methodName, parameterTypes);
            return classMethod.getDeclaringClass() != declaringClass;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Require a method override.
     *
     * @param obj            object whose class should override the method
     * @param declaringClass class that declares the method to be overridden
     * @param methodName     the name of the method to be overridden
     * @param parameterTypes the parameter types of the method to be overridden
     * @throws UnsupportedOperationException if the method is not overridden
     * @throws IllegalStateException         if the method is not found using reflection
     */
    public static void requireOverride(Object obj, Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        try {
            var classMethod = obj.getClass().getMethod(methodName, parameterTypes);
            if (classMethod.getDeclaringClass() == declaringClass) {
                throw new UnsupportedOperationException(
                        "%s does not override method %s(%s)".formatted(obj.getClass(), methodName, parameterTypes));
            } else {
                var className = obj.getClass().getName();
                var signature = signature(methodName, parameterTypes);
                if (WARNINGS.add(className + "." + signature)) {
                    // only log once
                    LOGGER.log(Level.WARNING, "{0} implements a deprecated method: {1}", className, signature);
                }
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String signature(String methodName, Class<?>... parameterTypes) {
        return "%s(%s)".formatted(methodName, Arrays.stream(parameterTypes)
                .map(Class::getName)
                .collect(Collectors.joining(",")));
    }
}
