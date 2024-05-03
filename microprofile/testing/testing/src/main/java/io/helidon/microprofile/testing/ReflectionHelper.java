/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reflection helper.
 */
class ReflectionHelper {

    private ReflectionHelper() {
        // cannot be instanciated
    }

    /**
     * Collect all the methods that have the given method signature in its type hierarchy.
     *
     * @param method method
     * @return hierarchy
     */
    static List<Method> methodHierarchy(Method method) {
        return typeHierarchy(method.getDeclaringClass(), true).stream()
                .flatMap(t -> Stream.of(t.getDeclaredMethods()))
                .filter(override -> isOverride(method, override))
                .toList();
    }

    /**
     * Test if the given method overrides another.
     *
     * @param method   base method
     * @param override override method
     * @return {@code true} if overrides, {@code false otherwise}
     */
    static boolean isOverride(Method method, Method override) {
        return override.getName().equals(method.getName())
               && override.getReturnType().isAssignableFrom(method.getReturnType())
               && Arrays.equals(override.getParameterTypes(), method.getParameterTypes());
    }

    /**
     * Collect all types in the type hiearchy of the given type.
     *
     * @param type type
     * @param all  {@code false} to only include ancestors
     * @return types
     */
    static List<Class<?>> typeHierarchy(Class<?> type, boolean all) {
        List<Class<?>> result = new ArrayList<>();
        Deque<Class<?>> stack = new ArrayDeque<>();
        stack.push(type);
        while (!stack.isEmpty()) {
            Class<?> e = stack.pop();
            if (e.getPackage().getName().startsWith("java.")) {
                continue;
            }
            if (all || !e.equals(type)) {
                result.add(e);
            }
            if (e.getSuperclass() != null) {
                stack.push(e.getSuperclass());
            }
            List.of(e.getInterfaces()).forEach(stack::push);
        }
        return result;
    }

    /**
     * Collect all effective annotations of an annotation class.
     *
     * @param annotationType annotation type
     * @return annotations
     */
    static List<Annotation> annotationHierarchy(Class<? extends Annotation> annotationType) {
        List<Annotation> result = new ArrayList<>();
        Deque<Annotation> stack = new ArrayDeque<>();
        List.of(annotationType.getAnnotations()).forEach(stack::push);
        while (!stack.isEmpty()) {
            Annotation e = stack.pop();
            Class<? extends Annotation> type = e.annotationType();
            if (!type.getPackage().getName().startsWith("io.helidon.")) {
                continue;
            }
            result.add(e);
            List.of(type.getAnnotations()).forEach(stack::push);
        }
        return result;
    }

    /**
     * Annotated element.
     *
     * @param element     element
     * @param annotations annotations
     */
    record Annotated<T extends Annotation>(AnnotatedElement element, List<T> annotations) {

        private static Annotated<?> create(AnnotatedElement element) {
            return new Annotated<>(element, Stream.of(element.getAnnotations())
                    .flatMap(a -> Stream.concat(Stream.of(a), annotationHierarchy(a.annotationType()).stream()))
                    .toList());
        }
    }

    /**
     * Get all annotations for a method and its hierarchy.
     *
     * @param method method
     * @return annotations
     */
    static List<Annotated<?>> annotated(Method method) {
        return annotated(methodHierarchy(method));
    }

    /**
     * Get all annotations for a class and its hierarchy.
     *
     * @param type type
     * @return annotations
     */
    static List<Annotated<?>> annotated(Class<?> type) {
        return annotated(typeHierarchy(type, true));
    }

    /**
     * Get all annotations for the given elements.
     *
     * @param elements elements
     * @return annotations
     */
    static List<Annotated<?>> annotated(List<? extends AnnotatedElement> elements) {
        return elements.stream().map(Annotated::create).collect(Collectors.toList());
    }

    /**
     * Filter annotations of a given type.
     *
     * @param annotated annotations
     * @param aType     annotation type
     * @param cType     container type
     * @param function  function to inflate from container
     * @param <T>       annotation type
     * @param <U>       container type
     * @return annotations
     */
    static <T extends Annotation, U extends Annotation> List<Annotated<T>> filterAnnotated(List<Annotated<?>> annotated,
                                                                                           Class<T> aType,
                                                                                           Class<U> cType,
                                                                                           Function<U, T[]> function) {

        Predicate<Annotation> predicate = a -> a.annotationType().equals(cType) || a.annotationType().equals(aType);
        return annotated.stream()
                .filter(a -> a.annotations.stream().anyMatch(predicate))
                .map(it -> new Annotated<>(it.element, it.annotations.stream()
                        .filter(predicate)
                        .flatMap(at -> {
                            if (at.annotationType().equals(cType)) {
                                return Stream.of(function.apply(cType.cast(at)));
                            }
                            return Stream.of(aType.cast(at));
                        })
                        .toList()))
                .toList();
    }

    /**
     * Filter annotations of a given type.
     *
     * @param annotated annotations
     * @param aType     annotation type
     * @param <T>       container type
     * @return annotations
     */
    static <T extends Annotation> Stream<Annotated<T>> filterAnnotated(List<Annotated<?>> annotated, Class<T> aType) {
        Predicate<Annotation> predicate = a -> a.annotationType().equals(aType);
        return annotated.stream()
                .filter(a -> a.annotations.stream().anyMatch(predicate))
                .map(it -> new Annotated<>(it.element, it.annotations.stream()
                        .filter(predicate)
                        .map(aType::cast)
                        .toList()));
    }

    /**
     * Filter annotations of a given type.
     *
     * @param annotated annotations
     * @param aType     annotation type
     * @param cType     container type
     * @param function  function to inflate from container
     * @param <T>       annotation type
     * @param <U>       container type
     * @return annotations
     */
    static <T extends Annotation, U extends Annotation> Stream<T> filterAnnotations(List<Annotated<?>> annotated,
                                                                                    Class<T> aType,
                                                                                    Class<U> cType,
                                                                                    Function<U, T[]> function) {

        return filterAnnotated(annotated, aType, cType, function).stream()
                .flatMap(it -> it.annotations.stream());
    }

    /**
     * Filter annotations of a given type.
     *
     * @param annotations    annotations
     * @param annotationType annotation type
     * @param <T>            container type
     * @return annotations
     */
    static <T extends Annotation> Stream<T> filterAnnotations(List<Annotated<?>> annotations, Class<T> annotationType) {
        return filterAnnotated(annotations, annotationType)
                .flatMap(it -> it.annotations.stream());
    }

    /**
     * Checks that the given class is public.
     *
     * @param cls class
     * @param <T> class type
     * @return Class
     * @throws java.lang.IllegalArgumentException if the given class is not public
     */
    static <T> Class<T> requirePublic(Class<T> cls) throws IllegalArgumentException {
        if (!Modifier.isPublic(cls.getModifiers())) {
            throw new IllegalArgumentException(cls.getName() + " is not public");
        }
        return cls;
    }

    /**
     * Checks that the given method is static.
     *
     * @param method method
     * @return Method
     * @throws java.lang.IllegalArgumentException if the given class is not public
     */
    static Method requireStatic(Method method) throws IllegalArgumentException {
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException(method + " is not static");
        }
        return method;
    }

    /**
     * Invoke a method.
     *
     * @param type     return type
     * @param method   method
     * @param instance instance
     * @param args     arguments
     * @param <T>      return type
     * @return invocation result
     */
    static <T> T invoke(Class<T> type, Method method, Object instance, Object... args) {
        try {
            method.setAccessible(true);
            Object value = method.invoke(instance, args);
            return type.cast(value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (e.getTargetException() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(target);
        }
    }
}
