/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Iterable list of {@link AnnotatedMethod}s on a single class with convenience
 * getters to provide additional method information.
 */
public class AnnotatedMethodList implements Iterable<AnnotatedMethod> {

    private final AnnotatedMethod[] methods;

    /**
     * Create new method list from the given array of {@link AnnotatedMethod
     * annotated methods}.
     *
     * @param methods methods to be included in the method list.
     */
    private AnnotatedMethodList(AnnotatedMethod... methods) {
        this.methods = methods;
    }

    /**
     * Create an annotated method list for a class.
     * <p>
     * The method list contains {@link Class#getMethods() all methods} available
     * on the class.
     * <p>
     * The {@link java.lang.reflect.Method#isBridge() bridge methods} and methods declared directly
     * on the {@link Object} class are filtered out.
     *
     * @param cls class from which the method list is created
     * @return an {@link AnnotatedMethodList} containing {@link AnnotatedMethod} instances for
     *         all of the methods of the specified class
     */
    public static AnnotatedMethodList create(Class<?> cls) {
        return create(cls, false);
    }

    /**
     * Create an annotated method list for a class.
     * <p>
     * The method list contains {@link Class#getMethods() all methods} available
     * on the class or {@link Class#getDeclaredMethods() declared methods} only,
     * depending on the value of the {@code declaredMethods} parameter.
     * <p>
     * The {@link java.lang.reflect.Method#isBridge() bridge methods} and methods declared directly
     * on the {@link Object} class are filtered out.
     *
     * @param cls             class from which the method list is created
     * @param declaredMethods if {@code true} only the {@link Class#getDeclaredMethods()
     *                        declared methods} will be included in the method list; otherwise
     *                        {@link Class#getMethods() all methods} will be listed
     * @return an {@link AnnotatedMethodList} containing {@link AnnotatedMethod} instances for
     *         the methods of the specified class
     */
    public static AnnotatedMethodList create(Class<?> cls, boolean declaredMethods) {
        return create(declaredMethods ? allDeclaredMethods(cls) : methodList(cls));
    }

    /**
     * Create an annotated method list from the given collection of methods.
     * <p>
     * The {@link Method#isBridge() bridge methods} and methods declared directly
     * on the {@link Object} class are filtered out.
     *
     * @param methods methods to be included in the method list.
     * @return an {@link AnnotatedMethodList} containing {@link AnnotatedMethod} instances for
     *         the methods of the specified class
     */
    public static AnnotatedMethodList create(Collection<Method> methods) {
        AnnotatedMethod[] annotatedMethods
                = methods.stream()
                         .filter(m -> !m.isBridge() && m.getDeclaringClass() != Object.class)
                         .map(AnnotatedMethod::create)
                         .toArray(AnnotatedMethod[]::new);

        return new AnnotatedMethodList(annotatedMethods);
    }

    /**
     * Iterator over the list of {@link AnnotatedMethod annotated methods} contained
     * in this method list.
     *
     * @return method list iterator.
     */
    @Override
    public Iterator<AnnotatedMethod> iterator() {
        return Arrays.asList(methods).iterator();
    }

    /**
     * Get a new sub-list of methods containing all the methods from this method
     * list that are not public.
     *
     * @return new filtered method sub-list.
     */
    public AnnotatedMethodList isNotPublic() {
        return filter(m -> !Modifier.isPublic(m.method().getModifiers()));
    }

    /**
     * Get a new sub-list of methods containing all the methods from this method
     * list that have the specific number of parameters.
     *
     * @param paramCount number of method parameters.
     * @return new filtered method sub-list.
     */
    public AnnotatedMethodList hasParameterCount(int paramCount) {
        return filter(m -> m.parameterTypes().length == paramCount);
    }

    /**
     * Get a new sub-list of methods containing all the methods from this method
     * list that declare the specified return type.
     *
     * @param returnType method return type.
     * @return new filtered method sub-list.
     */
    public AnnotatedMethodList hasReturnType(Class<?> returnType) {
        return filter(m -> m.method().getReturnType() == returnType);
    }

    /**
     * Get a new sub-list of methods containing all the methods from this method
     * list with a specified method name prefix.
     *
     * @param prefix method name prefix.
     * @return new filtered method sub-list.
     */
    public AnnotatedMethodList nameStartsWith(String prefix) {
        return filter(m -> m.method().getName().startsWith(prefix));
    }

    /**
     * Get a new sub-list of methods containing all the methods from this method
     * list with a specified method-level annotation declared.
     *
     * @param <T> annotation type.
     *
     * @param annotation annotation class.
     * @return new filtered method sub-list.
     */
    public <T extends Annotation> AnnotatedMethodList withAnnotation(Class<T> annotation) {
        return filter(m -> m.getAnnotation(annotation) != null);
    }

    /**
     * Get a new sub-list of methods containing all the methods from this method
     * list with a method-level annotation declared that is itself annotated with
     * a specified meta-annotation.
     *
     * @param <T> meta-annotation type.
     *
     * @param annotation meta-annotation class.
     * @return new filtered method sub-list.
     */
    public <T extends Annotation> AnnotatedMethodList withMetaAnnotation(Class<T> annotation) {
        return filter(m -> {
            for (Annotation a : m.getAnnotations()) {
                if (a.annotationType().getAnnotation(annotation) != null) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Get a new sub-list of methods containing all the methods from this method
     * list without a specified method-level annotation declared.
     *
     * @param <T> annotation type.
     *
     * @param annotation annotation class.
     * @return new filtered method sub-list.
     */
    public <T extends Annotation> AnnotatedMethodList withoutAnnotation(Class<T> annotation) {
        return filter(m -> !m.isAnnotationPresent(annotation));
    }

    /**
     * Get a new sub-list of methods containing all the methods from this method
     * list without any method-level annotation declared that would itself be
     * annotated with a specified meta-annotation.
     *
     * @param <T> meta-annotation type.
     *
     * @param annotation meta-annotation class.
     * @return new filtered method sub-list.
     */
    public <T extends Annotation> AnnotatedMethodList withoutMetaAnnotation(Class<T> annotation) {
        return filter(m -> {
            for (Annotation a : m.getAnnotations()) {
                if (a.annotationType().getAnnotation(annotation) != null) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Obtain a {@link Stream} of the {@link Method}s in this {@link AnnotatedMethodList}.
     *
     * @return  a {@link Stream} of the {@link Method}s in this {@link AnnotatedMethodList}
     */
    public Stream<AnnotatedMethod> stream() {
        return Arrays.stream(methods);
    }

    /**
     * Created a new method list containing only the methods supported by the
     * {@link Predicate method list predicate}.
     *
     * @param predicate method list predicate.
     *
     * @return new filtered method list.
     */
    public AnnotatedMethodList filter(Predicate<AnnotatedMethod> predicate) {
        return new AnnotatedMethodList(stream().filter(predicate).toArray(AnnotatedMethod[]::new));
    }

    private static List<Method> methodList(Class<?> c) {
        return Arrays.asList(c.getMethods());
    }

    private static List<Method> allDeclaredMethods(Class<?> c) {
        List<Method> l = new ArrayList<>();
        while (c != null && c != Object.class) {
            l.addAll(AccessController.doPrivileged(ModelHelper.getDeclaredMethodsPA(c)));
            c = c.getSuperclass();
        }
        return l;
    }
}
