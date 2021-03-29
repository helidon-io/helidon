/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.lra.rest;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Annotation resolver that resolves annotation in a matter similar to JAX-RS/Jakarta REST definitions.
 */
public class AnnotationResolver {

    /**
     * Finds the annotation on the method with the following criteria:
     *
     * 1. Find the annotation on method directly. If not present,
     * 2. Find the annotation on the same method in the superclass (superclass hierarchy). If not present,
     * 3. Find the annotation on the same method in the implemented interfaces (and interfaces implemented by
     * its superclasses). If not found return null.
     *
     * @param annotationClass annotation to look for
     * @param method method to scan
     * @param <T> the actual type of the annotation
     * @return the found annotation object or null if not found
     */
    public static <T extends Annotation> T resolveAnnotation(Class<T> annotationClass, Method method) {
        // current method
        T annotation = method.getAnnotation(annotationClass);
        if (annotation != null) {
            return annotation;
        }

        // search the superclass hierarchy
        annotation = resolveAnnotationInSuperClass(annotationClass, method, method.getDeclaringClass().getSuperclass());
        if (annotation != null) {
            return annotation;
        }

        // search the implemented interfaces in the hierarchy
        annotation = resolveAnnotationInInterfaces(annotationClass, method, method.getDeclaringClass());
        if (annotation != null) {
            return annotation;
        }

        return null;
    }

    /**
     * Returns whether or not the queried annotation is present on the method.
     *
     * @see AnnotationResolver#resolveAnnotation
     * @return true if the annotation is found, false otherwise
     */
    public static boolean isAnnotationPresent(Class<? extends Annotation> annotationClass, Method method) {
        return resolveAnnotation(annotationClass, method) != null;
    }

    private static <T extends Annotation> T resolveAnnotationInSuperClass(Class<T> annotationClass, Method method, Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        try {
            Method superclassMethod = clazz.getMethod(method.getName(), method.getParameterTypes());
            T annotation = superclassMethod.getAnnotation(annotationClass);
            return annotation != null ? annotation : resolveAnnotationInSuperClass(annotationClass, method, clazz.getSuperclass());
        } catch (NoSuchMethodException e) {
            return resolveAnnotationInSuperClass(annotationClass, method, clazz.getSuperclass());
        }
    }

    private static <T extends Annotation> T resolveAnnotationInInterfaces(Class<T> annotationClass, Method method, Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        T annotation;
        Method interfaceMethod;

        for (Class<?> anInterface : clazz.getInterfaces()) {
            try {
                interfaceMethod = anInterface.getMethod(method.getName(), method.getParameterTypes());
                annotation = interfaceMethod.getAnnotation(annotationClass);

                if (annotation != null) {
                    return annotation;
                }

            } catch (NoSuchMethodException e) {
                continue;
            }
        }

        return resolveAnnotationInInterfaces(annotationClass, method, clazz.getSuperclass());
    }
}
